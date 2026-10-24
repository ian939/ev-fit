// 내비 경로·충전 정차·경유지 설정을 Pleos NaviHelper SDK로 감싼 게이트웨이
package com.ian.evfit.platform

import ai.pleos.playground.navi.constants.RoutePointPoiType
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import com.ian.evfit.engine.Station
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class NamedPoint(
    val name: String, val lat: Double, val lng: Double,
    val poiId: String = "", val poiSubId: String = "0", val address: String = "",
)

/** 내비가 계획한(또는 사용자가 정한) 충전 정차 */
data class PlannedStop(val name: String, val lat: Double, val lng: Double, val maxKw: Int, val arriveSoc: Int?, val targetSoc: Int?)

data class RoutePlan(val destination: NamedPoint?, val stops: List<PlannedStop>)

interface NaviGateway {
    val connected: StateFlow<Boolean>
    val route: StateFlow<RoutePlan?>
    val arrivedAt: SharedFlow<String>
    fun start()
    fun stop()
    fun setWaypoint(station: Station)
    fun setDestination(station: Station)
    fun openNearbyChargers()
}

class PleosNaviGateway(context: Context) : NaviGateway {
    private val navi = NaviHelper(context)
    private val _connected = MutableStateFlow(false)
    private val _route = MutableStateFlow<RoutePlan?>(null)
    private val _arrived = MutableSharedFlow<String>(extraBufferCapacity = 4)
    override val connected: StateFlow<Boolean> = _connected
    override val route: StateFlow<RoutePlan?> = _route
    override val arrivedAt: SharedFlow<String> = _arrived

    private var destination: DestinationInfo? = null
    private var waypoints: List<WaypointInfo> = emptyList()
    private var driving: DrivingInfo? = null
    private var trip: TripInfo? = null

    private val listener = object : NaviHelperEventListener {
        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            destination = destinationInfo
            runCatching {
                navi.requestTrip(TripRequest(destinationInfo.latitude, destinationInfo.longitude,
                    destinationInfo.poiId, destinationInfo.name, destinationInfo.poiSubId ?: "0"))
            }
            publish()
        }

        override fun onWayPointInfo(waypointInfoList: List<WaypointInfo>) {
            waypoints = waypointInfoList
            publish()
        }

        override fun onDrivingInfo(drivingInfo: DrivingInfo) {
            driving = drivingInfo
            publish()
        }

        override fun onTripInfo(tripInfo: TripInfo) {
            trip = tripInfo
            publish()
        }

        override fun onRouteStarted(routeStartInfo: RouteStartInfo) = refresh()
        override fun onDestinationChanged() = refresh()
        override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) = refresh()

        override fun onRouteCancelled() {
            destination = null; waypoints = emptyList(); driving = null; trip = null
            _route.value = null
        }

        override fun onWayPointArrived(waypointArrivedInfo: WaypointArrivedInfo) {
            _arrived.tryEmit(waypoints.firstOrNull()?.name ?: "경유지")
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            _arrived.tryEmit(destination?.name ?: "목적지")
        }
    }

    private fun refresh() {
        runCatching { navi.getDestinationInfo(); navi.getWaypointInfo() }
    }

    /** 목적지·경유지·주행 정보·trip 경로점을 합쳐 앱 모델로 만든다 */
    private fun publish() {
        val dest = destination?.let { NamedPoint(it.name, it.latitude, it.longitude, it.poiId, it.poiSubId ?: "0", it.address) }
        val chargingStates = driving?.waypoints.orEmpty().filter { it.isChargingStation }
        val fromWaypoints = waypoints.mapNotNull { w ->
            val s = chargingStates.firstOrNull { it.name == w.name } ?: return@mapNotNull null
            PlannedStop(w.name, w.latitude, w.longitude, s.maxChargingPower, s.chargingPlan?.startSoc, s.chargingPlan?.endSoc)
        }
        val fromTrip = trip?.summary?.routePoints.orEmpty()
            .filter { it.poiType == RoutePointPoiType.EV_CHARGING && fromWaypoints.none { s -> s.name == it.poiName } }
            .map { p ->
                val s = chargingStates.firstOrNull { it.name == p.poiName }
                PlannedStop(p.poiName, p.latitude, p.longitude, s?.maxChargingPower ?: 0, s?.chargingPlan?.startSoc, s?.chargingPlan?.endSoc)
            }
        _route.value = if (dest == null && fromWaypoints.isEmpty() && fromTrip.isEmpty()) null
        else RoutePlan(dest, fromWaypoints + fromTrip)
    }

    override fun start() {
        runCatching {
            navi.initialize(onConnected = {
                _connected.value = true
                navi.addListener(listener)
                refresh()
            }, onFailed = { Log.w(TAG, "NaviHelper 연결 실패: $it") })
        }.onFailure { Log.w(TAG, "NaviHelper 시작 실패: $it") }
    }

    override fun stop() {
        runCatching { navi.removeListener(listener); navi.release() }
    }

    override fun setWaypoint(station: Station) {
        runCatching {
            navi.addWaypoint(RequestWaypointInfo(
                latitude = station.lat, longitude = station.lng, poiName = station.name,
                waypointIndex = WaypointIndex.FIRST, poiId = station.id, address = station.addr, poiSubId = "0",
            ))
        }.onFailure { Log.w(TAG, "경유지 설정 실패: $it") }
    }

    override fun setDestination(station: Station) {
        runCatching {
            navi.requestRoute(RouteInfo(
                latitude = station.lat, longitude = station.lng, poiName = station.name,
                poiId = station.id, address = station.addr, poiSubId = "0",
            ))
        }.onFailure { Log.w(TAG, "목적지 설정 실패: $it") }
    }

    override fun openNearbyChargers() {
        runCatching { navi.requestNavigationScreen(NavigationScreenRequest.NearbyChargingStations) }
            .onFailure { Log.w(TAG, "내비 화면 요청 실패: $it") }
    }
}
