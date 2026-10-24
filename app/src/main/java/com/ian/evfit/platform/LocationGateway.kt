// 차량 위치를 Pleos FusedLocation SDK로 받는 게이트웨이 — 미지원·권한 거부면 null로 남는다
package com.ian.evfit.platform

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.ian.evfit.engine.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface LocationGateway {
    val location: StateFlow<LatLng?>
    fun start(activity: Activity)
    fun onPermissionResult(granted: Boolean)
    fun stop()
}

class PleosLocationGateway(context: Context) : LocationGateway {
    private val fused = FusedLocation(context)
    private val _location = MutableStateFlow<LatLng?>(null)
    override val location: StateFlow<LatLng?> = _location
    private var registered = false

    private val listener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            location ?: return
            _location.value = LatLng(location.latitude, location.longitude)
        }

        override fun onError(exception: Exception) {
            Log.w(TAG, "위치 수신 오류: $exception")
        }
    }

    override fun start(activity: Activity) {
        val supported = runCatching { fused.isSupported() }.getOrDefault(false)
        if (!supported) {
            Log.i(TAG, "FusedLocation 미지원 — 시연 위치 사용")
            return
        }
        if (activity.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED) register()
        else activity.requestPermissions(arrayOf(PERMISSION), REQUEST_CODE)
    }

    override fun onPermissionResult(granted: Boolean) {
        if (granted) register()
    }

    private fun register() {
        if (registered) return
        runCatching {
            fused.initialize()
            fused.registerFusedLocationCallback(listener)
            fused.getLastKnownLocation(listener)
            registered = true
        }.onFailure { Log.w(TAG, "FusedLocation 시작 실패: $it") }
    }

    override fun stop() {
        if (!registered) return
        runCatching { fused.unregisterFusedLocationCallback(listener); fused.release() }
        registered = false
    }

    companion object {
        const val PERMISSION = "pleos.car.permission.FUSED_LOCATION"
        const val REQUEST_CODE = 41
    }
}
