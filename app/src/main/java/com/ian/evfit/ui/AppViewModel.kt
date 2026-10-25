// EV FIT 화면 상태와 충전 흐름(경로 회랑 추천→경유지→충전 준비→충전→리포트)을 관리하는 ViewModel
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
import com.ian.evfit.engine.AvailFilter
import com.ian.evfit.engine.CorridorRanker
import com.ian.evfit.engine.CorridorStation
import com.ian.evfit.engine.Credential
import com.ian.evfit.engine.Geo
import com.ian.evfit.engine.LatLng
import com.ian.evfit.engine.PayOption
import com.ian.evfit.engine.PriceEngine
import com.ian.evfit.engine.Quote
import com.ian.evfit.engine.RangeModel
import com.ian.evfit.engine.RankedStation
import com.ian.evfit.engine.RouteLine
import com.ian.evfit.engine.RouteMode
import com.ian.evfit.engine.RouteProjection
import com.ian.evfit.engine.SocSample
import com.ian.evfit.engine.Station
import com.ian.evfit.engine.StationRanker
import com.ian.evfit.engine.SyntheticStats
import com.ian.evfit.engine.Wallet
import com.ian.evfit.platform.PlannedStop
import com.ian.evfit.platform.RoutePlan
import com.ian.evfit.ui.theme.EvFitColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.roundToInt

enum class Tab { ROUTE, REPORT, WALLET }

data class Nav(
    val tab: Tab = Tab.ROUTE,
    val detailStationId: String? = null,
    val prepOpen: Boolean = false,
    val chargingOpen: Boolean = false,
)

/** 경로상 다음 충전 정차 + 그 지점(같은 휴게소) 충전소들의 내 단가 */
data class StopAdvice(
    val stop: PlannedStop,
    val kwh: Double,
    val atStop: List<RankedStation>,
    /** 같은 지점에서 내 사업자 계정으로는 결제가 안 되고 환경부 카드만 되는 충전소 */
    val moeOnly: List<RankedStation>,
)

/** 지도 화면 상태 — 경로선, 차 위치, 도달 범위, 회랑 후보와 탭 기준 순위 */
data class TripState(
    val line: RouteLine? = null,
    /** 실차 모드: 내비가 경로 좌표를 주지 않아 현재 위치·정차지·목적지를 직선으로 이었다 */
    val estimated: Boolean = false,
    val routeSource: String = "",
    val carKm: Double = 0.0,
    val rangeKm: Double = 0.0,
    val mode: RouteMode = RouteMode.FIT,
    /** 지금 충전 가능한 급속 충전기 수로 거르기 */
    val filter: AvailFilter = AvailFilter.ACTIVE,
    /** 회랑 안 후보 전체(도달 못 하는 곳 포함) — 지도 점 */
    val all: List<CorridorStation> = emptyList(),
    /** 도달 가능하고 거르기를 통과한 곳을 탭 기준으로 줄 세운 것 */
    val ranked: List<CorridorStation> = emptyList(),
    val kwh: Double = 0.0,
    /** 금액 기준 출발 SOC(계획 정차 도착 SOC 또는 현재 SOC) */
    val basisSoc: Int = 0,
    val basisAssumed: Boolean = false,
    val stopKm: Double? = null,
    val selectedId: String? = null,
    /** 시연 주행 연출 중 */
    val driving: Boolean = false,
) {
    fun find(id: String?): CorridorStation? = all.firstOrNull { it.station.id == id }
}

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
    val location: LatLng? = null,
    val route: RoutePlan? = null,
    val nextStop: StopAdvice? = null,
    val trip: TripState = TripState(),
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
    val kmPerKwh: Double get() = data?.vehicle(wallet.vehicleModel)?.kmPerKwh ?: RangeModel.DEFAULT_KM_PER_KWH

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
    private var corridorJob: Job? = null
    private var lastCorridorAt: LatLng? = null

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
                    // 설정을 읽기 전(demoMode 기본값 true)에 들어온 실차 값은 수집기가 버렸으므로 여기서 현재 값을 읽는다
                    soc = if (demo) DemoDriver.CURRENT_SOC else graph.vehicle.soc.value,
                    location = if (demo) DemoDriver.START_LOCATION else graph.location.location.value,
                    route = if (demo) it.route else graph.navi.route.value,
                    trip = it.trip.copy(filter = graph.prefs.availFilter),
                )
            }
            refreshNextStop()
            refreshCorridor()
        }
        graph.vehicle.readVin { vin = it }
        viewModelScope.launch { graph.vehicle.soc.collect { s -> if (!_ui.value.demoMode) onLiveSoc(s) } }
        viewModelScope.launch { graph.vehicle.charging.collect { c -> if (!_ui.value.demoMode) onLiveCharging(c) } }
        viewModelScope.launch {
            graph.navi.route.collect { r ->
                if (!_ui.value.demoMode) {
                    _ui.update { it.copy(route = r) }
                    refreshNextStop()
                    refreshCorridor()
                }
            }
        }
        viewModelScope.launch {
            graph.navi.arrivedAt.collect { if (!_ui.value.demoMode) arrive() }
        }
        viewModelScope.launch {
            graph.location.location.collect { l ->
                if (_ui.value.demoMode || l == null) return@collect
                _ui.update { it.copy(location = l) }
                // 1km 넘게 움직였을 때만 회랑을 다시 계산한다
                if (lastCorridorAt?.let { Geo.distanceKm(it, l) > 1.0 } != false) refreshCorridor()
            }
        }
        viewModelScope.launch { graph.restriction.restricted.collect { r -> _ui.update { it.copy(restricted = r) } } }
        viewModelScope.launch { graph.voice.available.collect { v -> _ui.update { it.copy(voiceAvailable = v) } } }
        viewModelScope.launch { graph.voice.sttAvailable.collect { v -> _ui.update { it.copy(sttAvailable = v) } } }
    }

    // ---------- 공통 계산 ----------

    private fun startSoc(stop: PlannedStop? = null): Int {
        val s = _ui.value
        stop?.arriveSoc?.let { return it }
        return s.soc ?: UiState.ASSUMED_SOC
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

    private fun refreshNextStop() {
        viewModelScope.launch {
            val s = _ui.value
            val d = s.data ?: return@launch
            val stop = s.route?.stops?.firstOrNull()
            if (stop == null) {
                _ui.update { it.copy(nextStop = null) }
                return@launch
            }
            val kwh = kwhFor(startSoc(stop))
            val advice = withContext(Dispatchers.Default) {
                val atStopCands = StationRanker.nearby(d.stations, LatLng(stop.lat, stop.lng), 0.3, 10)
                val atStop = StationRanker.rank(atStopCands, kwh, s.wallet, d.table, d.plans, d::operatorName)
                val moeOnly = atStop.filter { r -> r.quote.options.none { it.credential is Credential.Account } }
                StopAdvice(stop, kwh, atStop, moeOnly)
            }
            _ui.update { it.copy(nextStop = advice) }
            speakAdvice(advice)
        }
    }

    private fun speakAdvice(a: StopAdvice) {
        val top = a.atStop.firstOrNull() ?: return
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

    // ---------- 경로 회랑 ----------

    /** 시연은 OSRM 경로선, 실차는 현재 위치 → 정차지 → 목적지 직선(내비가 좌표를 주지 않음) */
    private fun lineFor(s: UiState, d: AppData): Triple<RouteLine?, Boolean, String> {
        val r = s.route ?: return Triple(null, false, "")
        if (s.demoMode) return Triple(RouteLine(d.demoRoute), false, d.demoRouteSource)
        val pts = listOfNotNull(s.location) + r.stops.map { LatLng(it.lat, it.lng) } +
            listOfNotNull(r.destination?.let { LatLng(it.lat, it.lng) })
        return if (pts.size >= 2) Triple(RouteLine(pts), true, "직선 경로(추정)") else Triple(null, false, "")
    }

    private fun refreshCorridor() {
        corridorJob?.cancel()
        corridorJob = viewModelScope.launch {
            val s = _ui.value
            val d = s.data ?: return@launch
            val (line, estimated, source) = lineFor(s, d)
            val soc = s.soc ?: UiState.ASSUMED_SOC
            val carKm = when {
                line == null -> 0.0
                s.demoMode -> s.trip.carKm
                else -> s.location?.let { line.project(it).alongKm } ?: 0.0
            }
            val rangeKm = RangeModel.rangeKm(soc, s.batteryKwh, s.kmPerKwh)
            val stop = s.route?.stops?.firstOrNull()
            val stopKm = if (line != null && stop != null) line.project(LatLng(stop.lat, stop.lng)).alongKm else null
            var basis = if (stop != null && stopKm != null && stopKm >= carKm - 0.5) startSoc(stop) else soc
            // 이미 목표 가까이 찼으면 30% 도착을 가정해 금액을 보여 준다
            val assumed = s.soc == null || basis >= s.wallet.targetSoc - 5
            if (basis >= s.wallet.targetSoc - 5) basis = UiState.ASSUMED_SOC
            val kwh = kwhFor(basis)
            val built = withContext(Dispatchers.Default) {
                val cands = if (line != null) {
                    CorridorRanker.candidates(d.stations, line, carKm)
                } else {
                    val center = s.location ?: DemoDriver.START_LOCATION
                    StationRanker.nearby(d.stations, center, 10.0, 60).map { (st, dist) ->
                        CorridorRanker.Candidate(st, RouteProjection(0.0, dist, true), onRoute = false)
                    }
                }
                CorridorRanker.build(
                    cands, kwh, s.wallet, d.table, d.plans, d::operatorName,
                    chargeMinutes = ::chargeMinutes, successPct = { SyntheticStats.successPct(it.id) },
                    carKm = if (line != null) carKm else 0.0, rangeKm = rangeKm,
                    // 경로 없이 주변을 볼 때는 도심이라 나들목 시간을 더하지 않는다
                    exitPenalty = line != null,
                )
            }
            // 급속 충전기 실시간 상태를 붙인다(시연은 합성)
            val live = graph.status.statusOf(built.map { it.station })
            val all = built.map { it.copy(live = live[it.station.id]) }
            lastCorridorAt = s.location
            _ui.update {
                val ranked = CorridorRanker.rank(it.trip.mode, all, it.trip.filter)
                val keep = it.trip.selectedId?.takeIf { id -> ranked.any { r -> r.station.id == id } }
                it.copy(
                    trip = it.trip.copy(
                        line = line, estimated = estimated, routeSource = source, carKm = carKm, rangeKm = rangeKm,
                        all = all, ranked = ranked, kwh = kwh, basisSoc = basis, basisAssumed = assumed,
                        stopKm = stopKm, selectedId = keep ?: ranked.firstOrNull()?.station?.id,
                    ),
                )
            }
        }
    }

    fun setMode(mode: RouteMode) = _ui.update {
        val ranked = CorridorRanker.rank(mode, it.trip.all, it.trip.filter)
        it.copy(trip = it.trip.copy(mode = mode, ranked = ranked, selectedId = ranked.firstOrNull()?.station?.id), nav = it.nav.copy(detailStationId = null))
    }

    fun setFilter(filter: AvailFilter) {
        graph.prefs.availFilter = filter
        _ui.update {
            val ranked = CorridorRanker.rank(it.trip.mode, it.trip.all, filter)
            it.copy(trip = it.trip.copy(filter = filter, ranked = ranked, selectedId = ranked.firstOrNull()?.station?.id))
        }
    }

    fun select(stationId: String) = _ui.update { it.copy(trip = it.trip.copy(selectedId = stationId)) }

    fun quoteFor(station: Station): Quote? {
        val stop = _ui.value.nextStop?.takeIf { a -> a.atStop.any { it.quote.station.id == station.id } }?.stop
        return quote(station, kwhFor(startSoc(stop)))
    }

    // ---------- 내비 ----------

    fun setAsWaypoint(station: Station) {
        graph.navi.setWaypoint(station)
        val s = _ui.value
        if (s.demoMode) {
            val r = s.route ?: DemoDriver.route()
            val c = s.trip.find(station.id)
            val arrive = c?.let { RangeModel.socAfter(s.soc ?: DemoDriver.CURRENT_SOC, it.alongKm - s.trip.carKm + it.offsetKm, s.batteryKwh, s.kmPerKwh) }
                ?: DemoDriver.ARRIVE_SOC
            val stop = PlannedStop(station.name, station.lat, station.lng, station.maxKw, arrive, s.wallet.targetSoc)
            _ui.update { it.copy(route = r.copy(stops = listOf(stop)), banner = "${station.name}을(를) 경유지로 설정했어요") }
            refreshNextStop()
            refreshCorridor()
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
            val line = it.data?.let { d -> RouteLine(d.demoRoute) }
            it.copy(
                route = DemoDriver.route(), soc = DemoDriver.CURRENT_SOC,
                location = line?.pointAt(DemoDriver.CAR_KM) ?: DemoDriver.START_LOCATION,
                trip = it.trip.copy(carKm = DemoDriver.CAR_KM, selectedId = null, driving = false),
                prep = null, session = null, nav = Nav(),
            )
        }
        refreshNextStop()
        refreshCorridor()
    }

    /**
     * [충전 준비] — 시연에서는 차가 그 충전소까지 경로를 따라 이동하는 연출(2.4초) 뒤 준비 화면을 연다.
     * 도착 SOC: 계획 정차면 내비 값(22%), 아니면 전비로 계산.
     */
    fun goCharge(station: Station) {
        val s = _ui.value
        val line = s.trip.line
        val target = s.trip.find(station.id)
        if (!s.demoMode || line == null || target == null || target.alongKm <= s.trip.carKm + 0.5) {
            prepare(station)
            return
        }
        demoJob?.cancel()
        demoJob = viewModelScope.launch {
            val fromKm = s.trip.carKm
            val toKm = target.alongKm
            val fromSoc = s.soc ?: DemoDriver.CURRENT_SOC
            val atStop = s.nextStop?.atStop?.any { it.quote.station.id == station.id } == true
            val endSoc = if (atStop) startSoc(s.nextStop?.stop)
            else RangeModel.socAfter(fromSoc, toKm - fromKm + target.offsetKm, s.batteryKwh, s.kmPerKwh)
            _ui.update { it.copy(trip = it.trip.copy(driving = true, selectedId = station.id), nav = it.nav.copy(detailStationId = null)) }
            val steps = 60
            for (i in 1..steps) {
                val x = i / steps.toFloat()
                val f = if (x < 0.5f) 2 * x * x else 1 - (-2 * x + 2) * (-2 * x + 2) / 2 // ease-in-out
                val km = fromKm + (toKm - fromKm) * f
                val soc = (fromSoc + (endSoc - fromSoc) * f).roundToInt()
                _ui.update {
                    it.copy(
                        soc = soc,
                        location = line.pointAt(km),
                        trip = it.trip.copy(carKm = km, rangeKm = RangeModel.rangeKm(soc, it.batteryKwh, it.kmPerKwh)),
                    )
                }
                delay(40)
            }
            _ui.update { it.copy(trip = it.trip.copy(driving = false)) }
            prepare(station)
        }
    }

    fun arrive() {
        val target = _ui.value.nextStop?.atStop?.firstOrNull()?.quote?.station
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
        val from = s.soc ?: DemoDriver.ARRIVE_SOC
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
                // 충전을 마쳤으니 계획된 첫 충전 정차는 끝난 것으로 본다(지도에 같은 정차지가 다시 뜨지 않게)
                route = it.route?.let { r -> r.copy(stops = r.stops.drop(1)) },
            )
        }
        refreshNextStop()
        refreshCorridor()
        val saving = record.moeReferenceKrw?.let { it - record.costKrw } ?: 0
        speakOnce("충전이 끝났어요. ${record.costKrw}원, 환경부 카드보다 ${saving}원 아꼈어요.")
    }

    private fun onLiveSoc(soc: Int?) {
        val first = _ui.value.soc == null && soc != null
        _ui.update { it.copy(soc = soc) }
        // 첫 SOC가 들어오면 30% 가정으로 계산해 둔 예상 충전량·금액을 다시 계산한다
        if (first) {
            refreshNextStop()
            refreshCorridor()
        }
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
        refreshCorridor()
    }

    fun setDemoMode(on: Boolean) {
        graph.prefs.demoMode = on
        demoJob?.cancel()
        _ui.update {
            it.copy(
                demoMode = on, session = null, prep = null,
                soc = if (on) DemoDriver.CURRENT_SOC else graph.vehicle.soc.value,
                route = if (on) null else graph.navi.route.value,
                location = if (on) DemoDriver.START_LOCATION else graph.location.location.value,
                trip = it.trip.copy(carKm = 0.0, selectedId = null),
            )
        }
        refreshNextStop()
        refreshCorridor()
    }

    fun setDarkTheme(on: Boolean) {
        graph.prefs.darkTheme = on
        EvFitColors.dark = on
    }

    fun loadDemoWallet() = updateWallet(DemoDriver.presetWallet(Prefs.currentMonth()))

    fun clearBanner() = _ui.update { it.copy(banner = null) }

    // ---------- 음성 명령 ----------

    fun listenVoice() {
        graph.voice.listen { text -> viewModelScope.launch { handleVoice(text) } }
    }

    private fun handleVoice(text: String) {
        when {
            listOf("싼", "저렴").any { it in text } -> voicePick(RouteMode.PRICE)
            listOf("빠른", "빨리").any { it in text } -> voicePick(RouteMode.FAST)
            "충전소" in text -> voicePick(RouteMode.FIT)
            "리포트" in text -> selectTab(Tab.REPORT)
            "지갑" in text -> selectTab(Tab.WALLET)
            else -> _ui.update { it.copy(banner = "다시 말씀해 주세요") }
        }
    }

    private fun voicePick(mode: RouteMode) {
        selectTab(Tab.ROUTE)
        setMode(mode)
        val top = _ui.value.trip.ranked.firstOrNull() ?: return
        val best = top.ranked.quote.best ?: return
        lastSpoken = null
        speakOnce("${mode.label} 기준으로 ${top.station.name}, ${best.credential.label}로 ${best.costKrw}원입니다.")
    }

    // ---------- 화면 이동 ----------

    fun selectTab(tab: Tab) = _ui.update { it.copy(nav = Nav(tab = tab)) }
    fun openDetail(stationId: String) = _ui.update { it.copy(nav = it.nav.copy(detailStationId = stationId), trip = it.trip.copy(selectedId = stationId)) }
    fun closeDetail() = _ui.update { it.copy(nav = it.nav.copy(detailStationId = null)) }
    fun closeCharging() = _ui.update { it.copy(nav = it.nav.copy(chargingOpen = false)) }

    /** Back 처리 — 처리했으면 true, 최상위면 false(액티비티가 moveTaskToBack) */
    fun back(): Boolean {
        val n = _ui.value.nav
        when {
            n.chargingOpen -> _ui.update { it.copy(nav = n.copy(chargingOpen = false)) }
            n.prepOpen -> _ui.update { it.copy(nav = n.copy(prepOpen = false)) }
            n.detailStationId != null -> _ui.update { it.copy(nav = n.copy(detailStationId = null)) }
            n.tab != Tab.ROUTE -> _ui.update { it.copy(nav = Nav()) }
            else -> return false
        }
        return true
    }
}
