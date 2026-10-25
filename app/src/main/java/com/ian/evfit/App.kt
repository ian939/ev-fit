// EV FIT 애플리케이션 — 앱 전역 객체(AppGraph)를 만들고 Pleos SDK를 앱 생성 시점에 시작한다
package com.ian.evfit

import android.app.Application
import com.ian.evfit.cpo.CpoGateway
import com.ian.evfit.cpo.MockCpoGateway
import com.ian.evfit.cpo.MockStatusGateway
import com.ian.evfit.cpo.StatusGateway
import com.ian.evfit.data.AssetRepository
import com.ian.evfit.data.Prefs
import com.ian.evfit.demo.DemoDriver
import com.ian.evfit.platform.LocationGateway
import com.ian.evfit.platform.NaviGateway
import com.ian.evfit.platform.PleosLocationGateway
import com.ian.evfit.platform.PleosNaviGateway
import com.ian.evfit.platform.PleosVehicleGateway
import com.ian.evfit.platform.PleosVoiceGateway
import com.ian.evfit.platform.RestrictionMonitor
import com.ian.evfit.platform.VehicleGateway
import com.ian.evfit.platform.VoiceGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppGraph(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val assets = AssetRepository(app)
    val prefs = Prefs(app)
    val vehicle: VehicleGateway = PleosVehicleGateway(app, scope)
    val navi: NaviGateway = PleosNaviGateway(app)
    val location: LocationGateway = PleosLocationGateway(app)
    val voice: VoiceGateway = PleosVoiceGateway(app)
    val restriction = RestrictionMonitor(app)
    val cpo: CpoGateway = MockCpoGateway()
    val status: StatusGateway = MockStatusGateway(DemoDriver.LIVE_STATUS)
}

class App : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        graph.vehicle.start()
        graph.navi.start()
        graph.voice.start()
        graph.restriction.start()
    }
}
