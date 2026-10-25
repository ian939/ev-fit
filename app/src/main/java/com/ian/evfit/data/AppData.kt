// 앱 자산(JSON)을 읽어 요금표·충전소·구독·차종·합성 통계로 바꾸는 저장소
package com.ian.evfit.data

import android.content.Context
import com.ian.evfit.engine.LatLng
import com.ian.evfit.engine.PricingTable
import com.ian.evfit.engine.Stage
import com.ian.evfit.engine.Station
import com.ian.evfit.engine.SubscriptionPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class VehicleSpec(val model: String, val brand: String, val batteryKwh: Double, val kmPerKwh: Double? = null)

@Serializable
data class BandStat(val from: Int, val to: Int, val p25: Double, val median: Double, val p75: Double)

@Serializable
data class ModelBench(
    val model: String, val peakKw: Int, val members: Int, val successRatePct: Double, val satisfaction: Double,
    val avgKwByClass: Map<String, Double>, val bands: Map<String, List<BandStat>>,
)

@Serializable
data class BenchmarkData(val synthetic: Boolean, val note: String, val models: List<ModelBench>)

@Serializable
private data class BasemapJson(val land: List<List<List<Double>>>, val borders: List<List<List<Double>>>, val labels: List<LabelJson>)

@Serializable
private data class LabelJson(val name: String, val lat: Double, val lng: Double)

@Serializable
private data class RouteJson(val source: String, val points: List<List<Double>>)

data class MapLabel(val name: String, val pos: LatLng)

/** 지도 바탕(Natural Earth 육지 고리·시도 경계선·도시 이름) */
data class Basemap(val land: List<List<LatLng>>, val borders: List<List<LatLng>>, val labels: List<MapLabel>)

@Serializable
private data class PricesJson(val snapshotId: Int, val collectedAt: String, val self: Map<String, Double>, val roaming: Map<String, Double>)

@Serializable
private data class OperatorJson(val bid: String, val name: String, val fastStations: Int)

@Serializable
private data class PlansJson(val asOf: String, val plans: List<SubscriptionPlan>)

data class AppData(
    val table: PricingTable,
    val pricesAsOf: String,
    val operators: Map<String, String>,
    val operatorFastStations: Map<String, Int>,
    val stations: List<Station>,
    val plans: Map<String, SubscriptionPlan>,
    val plansAsOf: String,
    val vehicles: List<VehicleSpec>,
    val bench: BenchmarkData,
    val stationsYm: String,
    val basemap: Basemap,
    /** 시연 경로선(양재 → 망향휴게소 → 정부대전청사, OSRM) */
    val demoRoute: List<LatLng>,
    val demoRouteSource: String,
) {
    private val stationById = stations.associateBy { it.id }

    fun station(id: String): Station? = stationById[id]
    fun operatorName(bid: String): String = operators[bid] ?: bid.removePrefix("X:")
    fun vehicle(model: String?): VehicleSpec? = vehicles.firstOrNull { it.model == model }
    fun benchFor(model: String?): ModelBench? = bench.models.firstOrNull { it.model == model }

    companion object {
        val CLASSES = listOf(350, 200, 100, 50)

        /** 충전소 최대 출력 이하의 가장 큰 벤치 클래스(최소 50kW) */
        fun chargerClass(maxKw: Int): Int = CLASSES.firstOrNull { it <= maxKw } ?: 50
    }
}

/** 파일 읽기 함수만 받아 파싱하는 순수 파서 — 단위 테스트에서 assets 폴더를 직접 읽게 하려고 분리 */
object AssetParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(read: (String) -> String): AppData {
        val prices = json.decodeFromString<PricesJson>(read("prices.json"))
        val ops = json.decodeFromString<List<OperatorJson>>(read("operators.json"))
        val plans = json.decodeFromString<PlansJson>(read("plans.json"))
        val vehicles = json.decodeFromString<List<VehicleSpec>>(read("vehicles.json"))
        val bench = json.decodeFromString<BenchmarkData>(read("benchmarks.json"))
        val map = json.decodeFromString<BasemapJson>(read("basemap.json"))
        val route = json.decodeFromString<RouteJson>(read("route_demo.json"))
        fun pts(xs: List<List<Double>>) = xs.map { LatLng(it[0], it[1]) }

        val st = json.parseToJsonElement(read("stations.json")) as JsonObject
        val fields = st.getValue("fields").jsonArray.map { it.jsonPrimitive.content }
        fun idx(name: String) = fields.indexOf(name)
        val iId = idx("id"); val iName = idx("name"); val iAddr = idx("addr"); val iLat = idx("lat"); val iLng = idx("lng")
        val iBid = idx("bid"); val iMax = idx("maxKw"); val iStages = idx("stages"); val iHw = idx("highway"); val iFast = idx("nFast")
        val stations = st.getValue("rows").jsonArray.map { e ->
            val r = e as JsonArray
            Station(
                id = r[iId].jsonPrimitive.content,
                name = r[iName].jsonPrimitive.content,
                addr = r[iAddr].jsonPrimitive.content,
                lat = r[iLat].jsonPrimitive.double,
                lng = r[iLng].jsonPrimitive.double,
                bid = r[iBid].jsonPrimitive.content,
                maxKw = r[iMax].jsonPrimitive.int,
                stages = r[iStages].jsonArray.map { Stage.parse(it.jsonPrimitive.content) },
                highway = r[iHw].jsonPrimitive.boolean,
                fastCount = r[iFast].jsonPrimitive.int,
            )
        }

        return AppData(
            table = PricingTable(prices.self, prices.roaming),
            pricesAsOf = prices.collectedAt,
            operators = ops.associate { it.bid to it.name },
            operatorFastStations = ops.associate { it.bid to it.fastStations },
            stations = stations,
            plans = plans.plans.associateBy { it.id },
            plansAsOf = plans.asOf,
            vehicles = vehicles,
            bench = bench,
            stationsYm = st.getValue("snapshotYm").jsonPrimitive.content,
            basemap = Basemap(map.land.map(::pts), map.borders.map(::pts), map.labels.map { MapLabel(it.name, LatLng(it.lat, it.lng)) }),
            demoRoute = pts(route.points),
            demoRouteSource = route.source,
        )
    }
}

class AssetRepository(private val context: Context) {
    suspend fun load(): AppData = withContext(Dispatchers.IO) {
        AssetParser.parse { name -> context.assets.open("data/$name").bufferedReader(Charsets.UTF_8).use { it.readText() } }
    }
}
