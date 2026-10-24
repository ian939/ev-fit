// EV FIT 화면 상태와 충전 흐름(추천→경유지→충전 준비→충전→리포트)을 관리하는 ViewModel
package com.ian.evfit.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ian.evfit.App
import com.ian.evfit.cpo.PncContract
import com.ian.evfit.data.AppData
import com.ian.evfit.data.ChargeRecord
import com.ian.evfit.data.Prefs
import com.ian.evfit.demo.DemoDriver
import com.ian.evfit.engine.Credential
import com.ian.evfit.engine.LatLng
import com.ian.evfit.engine.PayOption
import com.ian.evfit.engine.PriceEngine
import com.ian.evfit.engine.Quote
import com.ian.evfit.engine.RankedStation
import com.ian.evfit.engine.SocSample
import com.ian.evfit.engine.Station
import com.ian.evfit.engine.StationRanker
import com.ian.evfit.engine.SyntheticStats
import com.ian.evfit.engine.Wallet
import com.ian.evfit.platform.PlannedStop
import com.ian.evfit.platform.RoutePlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class Tab { HOME, FINDER, REPORT, WALLET }
enum class FinderCenter { ROUTE_STOP, DESTINATION, CURRENT }
enum class FinderSort { FIT, PRICE, DISTANCE }

data class Nav(
    val tab: Tab = Tab.HOME,
    val detailStationId: String? = null,
    val prepOpen: Boolean = false,
    val chargingOpen: Boolean = false,
)

/** 경로상 다음 충전 정차 + 그 지점(같은 휴게소) 충전소들의 내 단가 */
data class StopAdvice(
    val stop: PlannedStop,
    val kwh: Double,
    val atStop: List<RankedStation>,
    val nearby: List<RankedStation>,
    /** 같은 지점에서 내 사업자 계정으로는 결제가 안 되고 환경부 카드만 되는 충전소 */
    val moeOnly: List<RankedStation>,
)

data class FinderState(
    val center: FinderCenter = FinderCenter.CURRENT,
    val sort: FinderSort = FinderSort.FIT,
    val results: List<RankedStation> = emptyList(),
    val kwh: Double = 0.0,
)

data class PrepState(
    val station: Station,
    val quote: Quote,
    val contract: PncContract? = null,
    val portResult: String? = null,
    val limitResult: String? = null,
    val tips: List<String> = emptyList(),
)

data class SessionState(
    val station: Station,
    val option: PayOption,
    val fromSoc: Int,
    val soc: Int,
    val kwh: Double,
    val costKrw: Long,
    val instantKw: Double?,
    val samples: List<SocSample>,
    val simulated: Boolean,
)

data class UiState(
    val loading: Boolean = true,
    val data: AppData? = null,
    val demoMode: Boolean = true,
    val restricted: Boolean = false,
    val wallet: Wallet = Wallet(),
    val soc: Int? = null,
    val socAssumed: Boolean = false,
    val location: LatLng? = null,
    val route: RoutePlan? = null,
    val nextStop: StopAdvice? = null,
    val finder: FinderState = FinderState(),
    val prep: PrepState? = null,
    val session: SessionState? = null,
    val lastRecord: ChargeRecord? = null,
    val records: List<ChargeRecord> = emptyList(),
    val voiceAvailable: Boolean = false,
    val sttAvailable: Boolean = false,
    val banner: String? = null,
    val nav: Nav = Nav(),
) {
    val batteryKwh: Double get() = wallet.batteryKwh ?: DEFAULT_BATTERY_KWH
    val vehicleAssumed: Boolean get() = wallet.batteryKwh == null

    companion object {
        const val DEFAULT_BATTERY_KWH = 70.0
        const val ASSUMED_SOC = 30
    }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as App).graph
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    private var vin: String? = null
    private var lastSpoken: String? = null
    private var demoJob: Job? = null
    private var liveSamples = mutableListOf<SocSample>()

    init {
        viewModelScope.launch {
            val data = graph.assets.load()
            val demo = graph.prefs.demoMode
            var wallet = graph.prefs.wallet
            if (demo && !graph.prefs.hasWallet) {
                wallet = DemoDriver.presetWallet(Prefs.currentMonth())
                graph.prefs.wallet = wallet
            }
            _ui.update {
                it.copy(
                    loading = false, data = data, demoMode = demo, wallet = wallet,
                    records = graph.prefs.records, lastRecord = graph.prefs.records.lastOrNull(),
                    soc = if (demo) DemoDriver.START_SOC else it.soc,
                    location = if (demo) DemoDriver.START_LOCATION else it.location,
                )
            }
            refreshFinder()
        }
        graph.vehicle.readVin { vin = it }
        viewModelScope.launch { graph.vehicle.soc.collect { s -> if (!_ui.value.demoMode) onLiveSoc(s) } }
        viewModelScope.launch { graph.vehicle.charging.collect { c -> if (!_ui.value.demoMode) onLiveCharging(c) } }
        viewModelScope.launch {
            graph.navi.route.collect { r -> if (!_ui.value.demoMode) { _ui.update { it.copy(route = r) }; refreshNextStop() } }
        }
        viewModelScope.launch {
            graph.navi.arrivedAt.collect { if (!_ui.value.demoMode) arrive() }
        }
        viewModelScope.launch {
            graph.location.location.collect { l -> if (!_ui.value.demoMode && l != null) _ui.update { it.copy(location = l) } }
        }
        viewModelScope.launch { graph.restriction.restricted.collect { r -> _ui.update { it.copy(restricted = r) } } }
        viewModelScope.launch { graph.voice.available.collect { v -> _ui.update { it.copy(voiceAvailable = v) } } }
        viewModelScope.launch { graph.voice.sttAvailable.collect { v -> _ui.update { it.copy(sttAvailable = v) } } }
    }

    // ---------- 공통 계산 ----------

    private fun startSoc(stop: PlannedStop? = null): Pair<Int, Boolean> {
        val s = _ui.value
        stop?.arriveSoc?.let { return it to false }
        return (s.soc ?: UiState.ASSUMED_SOC) to (s.soc == null)
    }

    private fun kwhFor(fromSoc: Int): Double {
        val s = _ui.value
        return PriceEngine.kwhNeeded(s.batteryKwh, fromSoc, s.wallet.targetSoc)
    }

    private fun quote(station: Station, kwh: Double): Quote? {
        val s = _ui.value
        val d = s.data ?: return null
        return PriceEngine.quote(station, kwh, s.wallet, d.table, d.plans, d::operatorName)
    }

    /** 예상 충전 시간(분) = kWh ÷ 동일 차종 평균 kW(합성 벤치) × 60 */
    private fun chargeMinutes(station: Station, kwh: Double): Double {
        val s = _ui.value
        val bench = s.data?.benchFor(s.wallet.vehicleModel)
        val avgKw = bench?.avgKwByClass?.get(AppData.chargerClass(station.maxKw).toString())
            ?: minOf(station.maxKw * 0.6, 100.0)
        return kwh / avgKw.coerceAtLeast(1.0) * 60.0
    }

    private fun rankFit(cands: List<Pair<Station, Double>>, kwh: Double): List<RankedStation> {
        val s = _ui.value
        val d = s.data ?: return emptyList()
        return StationRanker.rankFit(
            cands, kwh, s.wallet, d.table, d.plans, d::operatorName,
            chargeMinutes = ::chargeMinutes, successPct = { SyntheticStats.successPct(it.id) },
        )
    }

    private fun refreshNextStop() {
        viewModelScope.launch {
            val s = _ui.value
            val d = s.data ?: return@launch
            val stop = s.route?.stops?.firstOrNull()
            if (stop == null) {
                _ui.update { it.copy(nextStop = null) }
                return@launch
            }
            val (from, _) = startSoc(stop)
            val kwh = kwhFor(from)
            val advice = withContext(Dispatchers.Default) {
                val center = LatLng(stop.lat, stop.lng)
                val atStopCands = StationRanker.nearby(d.stations, center, 0.3, 10)
                val atStop = StationRanker.rank(atStopCands, kwh, s.wallet, d.table, d.plans, d::operatorName)
                val nearby = rankFit(StationRanker.nearby(d.stations, center, 15.0, 40), kwh).take(5)
                val moeOnly = atStop.filter { r -> r.quote.options.none { it.credential is Credential.Account } }
                StopAdvice(stop, kwh, atStop, nearby, moeOnly)
            }
            _ui.update { it.copy(nextStop = advice) }
            speakAdvice(advice)
        }
    }

    private fun speakAdvice(a: StopAdvice) {
        val top = a.atStop.firstOrNull() ?: a.nearby.firstOrNull() ?: return
        val best = top.quote.best ?: return
        val saving = top.quote.savingVsMoe ?: 0
        val operator = _ui.value.data?.operatorName(top.quote.station.bid) ?: ""
        val text = if (saving > 0) {
            "다음 충전소 ${a.stop.name}는 $operator 충전소입니다. ${best.credential.label}로 결제하면 환경부 카드보다 ${saving}원 아낍니다."
        } else {
            "다음 충전소 ${a.stop.name}는 $operator 충전소입니다. 환경부 카드로 결제하세요."
        }
        speakOnce(text)
    }

    private fun speakOnce(text: String) {
        if (text == lastSpoken) return
        lastSpoken = text
        graph.voice.speak(text)
        if (!_ui.value.voiceAvailable) _ui.update { it.copy(banner = text) }
    }

    // ---------- 충전소 찾기 ----------

    fun findStations(center: FinderCenter = _ui.value.finder.center, sort: FinderSort = _ui.value.finder.sort) {
        _ui.update { it.copy(finder = it.finder.copy(center = center, sort = sort)) }
        refreshFinder()
    }

    private fun refreshFinder() {
        viewModelScope.launch {
            val s = _ui.value
            val d = s.data ?: return@launch
            val f = s.finder
            val stop = s.route?.stops?.firstOrNull()
            val (point, radius, limit) = when (f.center) {
                FinderCenter.ROUTE_STOP -> Triple(stop?.let { LatLng(it.lat, it.lng) }, 15.0, 40)
                FinderCenter.DESTINATION -> Triple(s.route?.destination?.let { LatLng(it.lat, it.lng) }, 10.0, 40)
                FinderCenter.CURRENT -> Triple(s.location ?: DemoDriver.START_LOCATION, 10.0, 40)
            }
            if (point == null) {
                _ui.update { it.copy(finder = f.copy(results = emptyList())) }
                return@launch
            }
            val (from, assumed) = startSoc(if (f.center == FinderCenter.ROUTE_STOP) stop else null)
            val kwh = kwhFor(from)
            val results = withContext(Dispatchers.Default) {
                val cands = StationRanker.nearby(d.stations, point, radius, limit)
                when (f.sort) {
                    FinderSort.FIT -> rankFit(cands, kwh)
                    FinderSort.PRICE -> rankFit(cands, kwh)
                        .sortedWith(compareBy<RankedStation> { it.quote.best!!.costKrw }.thenBy { it.distanceKm })
                    FinderSort.DISTANCE -> rankFit(cands, kwh).sortedBy { it.distanceKm }
                }
            }
            _ui.update {
                it.copy(
                    socAssumed = assumed,
                    finder = it.finder.copy(results = results, kwh = kwh),
                )
            }
        }
    }

    fun quoteFor(station: Station): Quote? {
        val (from, _) = startSoc(_ui.value.nextStop?.takeIf { a -> a.atStop.any { it.quote.station.id == station.id } }?.stop)
        return quote(station, kwhFor(from))
    }

    fun fitFor(stationId: String): RankedStation? =
        _ui.value.finder.results.firstOrNull { it.quote.station.id == stationId }
            ?: _ui.value.nextStop?.nearby?.firstOrNull { it.quote.station.id == stationId }

    // ---------- 내비 ----------

    fun setAsWaypoint(station: Station) {
        graph.navi.setWaypoint(station)
        if (_ui.value.demoMode) {
            val r = _ui.value.route ?: DemoDriver.route()
            val stop = PlannedStop(station.name, station.lat, station.lng, station.maxKw, DemoDriver.START_SOC, _ui.value.wallet.targetSoc)
            _ui.update { it.copy(route = r.copy(stops = listOf(stop)), banner = "${station.name}을(를) 경유지로 설정했어요") }
            refreshNextStop()
        } else {
            _ui.update { it.copy(banner = "${station.name}을(를) 내비 경유지로 요청했어요") }
        }
    }

    fun setDestination(station: Station) {
        graph.navi.setDestination(station)
        _ui.update { it.copy(banner = "${station.name}을(를) 목적지로 요청했어요") }
    }

    fun openNearbyChargersInNavi() = graph.navi.openNearbyChargers()

    // ---------- 시연 ----------

    fun startDemo() {
        demoJob?.cancel()
        lastSpoken = null
        _ui.update {
            it.copy(
                route = DemoDriver.route(), soc = DemoDriver.START_SOC, location = DemoDriver.START_LOCATION,
                prep = null, session = null, nav = Nav(),
            )
        }
        refreshNextStop()
        refreshFinder()
    }

    fun arrive() {
        val target = _ui.value.nextStop?.atStop?.firstOrNull()?.quote?.station
            ?: _ui.value.nextStop?.nearby?.firstOrNull()?.quote?.station
        if (target == null) {
            _ui.update { it.copy(banner = "도착한 충전 정차지가 없어요. 경로를 먼저 정하세요") }
            return
        }
        prepare(target)
    }

    // ---------- 충전 준비 ----------

    fun prepare(station: Station) {
        val q = quoteFor(station) ?: return
        val best = q.best
        if (best == null) {
            _ui.update { it.copy(banner = "이 충전소는 지갑의 결제 수단으로 결제할 수 없어요") }
            return
        }
        _ui.update { it.copy(prep = PrepState(station, q, tips = graph.cpo.prechargeTips(best.credential)), nav = it.nav.copy(prepOpen = true, detailStationId = null)) }
        viewModelScope.launch {
            val contract = graph.cpo.switchPncContract(vin, best.credential)
            _ui.update { s -> s.copy(prep = s.prep?.copy(contract = contract)) }
        }
        graph.vehicle.openChargePort { r ->
            val text = r.fold({ "충전구를 열었어요" }, { "충전구 열기: 에뮬레이터 미지원(${it.javaClass.simpleName})" })
            _ui.update { s -> s.copy(prep = s.prep?.copy(portResult = text)) }
        }
        val target = _ui.value.wallet.targetSoc
        graph.vehicle.setChargeLimit(target) { r ->
            val text = r.fold({ "목표 충전량 ${it}%로 설정했어요" }, { "목표 충전량: 에뮬레이터 미지원(${it.javaClass.simpleName})" })
            _ui.update { s -> s.copy(prep = s.prep?.copy(limitResult = text)) }
        }
        val operator = _ui.value.data?.operatorName(station.bid) ?: ""
        speakOnce("$operator 충전기에서 ${best.credential.label}로 인증합니다. 예상 요금은 ${best.costKrw}원입니다.")
    }

    fun closePrep() = _ui.update { it.copy(nav = it.nav.copy(prepOpen = false)) }

    // ---------- 충전 세션 ----------

    fun startDemoCharging() {
        val s = _ui.value
        val d = s.data ?: return
        val prep = s.prep ?: return
        val option = prep.quote.best ?: return
        val bench = d.benchFor(s.wallet.vehicleModel) ?: d.bench.models.first()
        val from = s.soc ?: DemoDriver.START_SOC
        val samples = DemoDriver.chargingSamples(bench, AppData.chargerClass(prep.station.maxKw), s.batteryKwh, from, s.wallet.targetSoc)
        demoJob?.cancel()
        demoJob = viewModelScope.launch {
            _ui.update {
                it.copy(
                    session = SessionState(prep.station, option, from, from, 0.0, 0, null, listOf(samples.first()), true),
                    nav = it.nav.copy(prepOpen = false, chargingOpen = true),
                )
            }
            for (i in 1 until samples.size) {
                delay((samples[i].tMillis - samples[i - 1].tMillis) / DemoDriver.PLAYBACK_SPEEDUP)
                updateSession(samples.subList(0, i + 1))
            }
            finishSession()
        }
    }

    private fun updateSession(samples: List<SocSample>) {
        val s = _ui.value
        val sess = s.session ?: return
        val last = samples.last()
        val kwh = PriceEngine.kwhNeeded(s.batteryKwh, sess.fromSoc, last.soc)
        val cost = quote(sess.station, kwh)?.options?.firstOrNull { it.credential == sess.option.credential }?.costKrw ?: sess.costKrw
        val kw = if (samples.size >= 2) {
            val a = samples[samples.size - 2]
            val dtH = (last.tMillis - a.tMillis) / 3_600_000.0
            if (dtH > 0) (last.soc - a.soc) / 100.0 * s.batteryKwh / dtH else null
        } else null
        // 충전기 최대 출력을 넘는 값은 SOC 순간 변경(Virtual Console 등)이라 표시하지 않는다
        val shownKw = kw?.takeIf { it <= sess.station.maxKw * 1.05 }
        _ui.update { it.copy(soc = last.soc, session = sess.copy(soc = last.soc, kwh = kwh, costKrw = cost, instantKw = shownKw, samples = samples)) }
    }

    fun finishSession() {
        demoJob?.cancel()
        val s = _ui.value
        val sess = s.session ?: return
        val kwh = PriceEngine.kwhNeeded(s.batteryKwh, sess.fromSoc, sess.soc)
        val q = quote(sess.station, kwh)
        val opt = q?.options?.firstOrNull { it.credential == sess.option.credential } ?: sess.option
        val record = ChargeRecord(
            id = UUID.randomUUID().toString(),
            startedAt = System.currentTimeMillis(),
            stationId = sess.station.id,
            stationName = sess.station.name,
            bid = sess.station.bid,
            credentialLabel = opt.credential.label,
            planId = opt.appliedPlan?.id,
            fromSoc = sess.fromSoc,
            toSoc = sess.soc,
            kwh = kwh,
            costKrw = opt.costKrw,
            moeReferenceKrw = q?.moeReferenceKrw,
            samples = sess.samples,
            simulated = sess.simulated,
        )
        // 적용된 구독 약정 차감
        val plan = opt.appliedPlan
        val wallet = if (plan == null) s.wallet else s.wallet.copy(subscriptions = s.wallet.subscriptions.map {
            if (it.planId == plan.id) it.copy(usedKwhThisMonth = it.usedKwhThisMonth + opt.planKwh) else it
        })
        val records = s.records + record
        graph.prefs.wallet = wallet
        graph.prefs.records = records
        _ui.update {
            it.copy(
                wallet = wallet, records = records, lastRecord = record, session = null, prep = null,
                nav = Nav(tab = Tab.REPORT),
                // 충전을 마쳤으니 계획된 첫 충전 정차는 끝난 것으로 본다(홈에 같은 정차지가 다시 뜨지 않게)
                route = it.route?.let { r -> r.copy(stops = r.stops.drop(1)) },
            )
        }
        refreshNextStop()
        val saving = record.moeReferenceKrw?.let { it - record.costKrw } ?: 0
        speakOnce("충전이 끝났어요. ${record.costKrw}원, 환경부 카드보다 ${saving}원 아꼈어요.")
    }

    private fun onLiveSoc(soc: Int?) {
        _ui.update { it.copy(soc = soc) }
        val sess = _ui.value.session ?: return
        if (soc == null || sess.simulated) return
        liveSamples += SocSample(System.currentTimeMillis(), soc)
        updateSession(liveSamples.toList())
        if (soc >= _ui.value.wallet.targetSoc) finishSession()
    }

    private fun onLiveCharging(charging: Boolean?) {
        val s = _ui.value
        if (charging == true && s.session == null) {
            val prep = s.prep ?: return
            val option = prep.quote.best ?: return
            val from = s.soc ?: return
            liveSamples = mutableListOf(SocSample(System.currentTimeMillis(), from))
            _ui.update {
                it.copy(
                    session = SessionState(prep.station, option, from, from, 0.0, 0, null, liveSamples.toList(), false),
                    nav = it.nav.copy(prepOpen = false, chargingOpen = true),
                )
            }
        } else if (charging == false && s.session?.simulated == false) {
            finishSession()
        }
    }

    // ---------- 지갑·모드 ----------

    fun updateWallet(w: Wallet) {
        graph.prefs.wallet = w
        _ui.update { it.copy(wallet = w) }
        refreshNextStop()
        refreshFinder()
    }

    fun setDemoMode(on: Boolean) {
        graph.prefs.demoMode = on
        demoJob?.cancel()
        _ui.update {
            it.copy(
                demoMode = on, session = null, prep = null,
                soc = if (on) DemoDriver.START_SOC else graph.vehicle.soc.value,
                route = if (on) null else graph.navi.route.value,
                location = if (on) DemoDriver.START_LOCATION else graph.location.location.value,
            )
        }
        refreshNextStop()
        refreshFinder()
    }

    fun loadDemoWallet() = updateWallet(DemoDriver.presetWallet(Prefs.currentMonth()))

    fun clearBanner() = _ui.update { it.copy(banner = null) }

    // ---------- 음성 명령 ----------

    fun listenVoice() {
        graph.voice.listen { text -> viewModelScope.launch { handleVoice(text) } }
    }

    private fun handleVoice(text: String) {
        when {
            listOf("싼", "저렴", "충전소").any { it in text } -> {
                val center = if (_ui.value.route?.stops?.isNotEmpty() == true) FinderCenter.ROUTE_STOP else FinderCenter.CURRENT
                selectTab(Tab.FINDER)
                findStations(center, FinderSort.FIT)
                viewModelScope.launch {
                    delay(600)
                    val top = _ui.value.finder.results.firstOrNull() ?: return@launch
                    val best = top.quote.best ?: return@launch
                    lastSpoken = null
                    speakOnce("가장 잘 맞는 곳은 ${top.quote.station.name}, ${best.credential.label}로 ${best.costKrw}원입니다.")
                }
            }
            "리포트" in text -> selectTab(Tab.REPORT)
            "지갑" in text -> selectTab(Tab.WALLET)
            else -> _ui.update { it.copy(banner = "다시 말씀해 주세요") }
        }
    }

    // ---------- 화면 이동 ----------

    fun selectTab(tab: Tab) = _ui.update { it.copy(nav = Nav(tab = tab)) }
    fun openDetail(stationId: String) = _ui.update { it.copy(nav = it.nav.copy(detailStationId = stationId)) }
    fun closeCharging() = _ui.update { it.copy(nav = it.nav.copy(chargingOpen = false)) }

    /** Back 처리 — 처리했으면 true, 최상위면 false(액티비티가 moveTaskToBack) */
    fun back(): Boolean {
        val n = _ui.value.nav
        when {
            n.chargingOpen -> _ui.update { it.copy(nav = n.copy(chargingOpen = false)) }
            n.prepOpen -> _ui.update { it.copy(nav = n.copy(prepOpen = false)) }
            n.detailStationId != null -> _ui.update { it.copy(nav = n.copy(detailStationId = null)) }
            n.tab != Tab.HOME -> _ui.update { it.copy(nav = Nav()) }
            else -> return false
        }
        return true
    }
}
