// AAOS 주행 중 UX 제한(CarUxRestrictions)을 구독해 기능을 줄일지 알려 주는 모니터
package com.ian.evfit.platform

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class RestrictionMonitor(private val context: Context) {
    private val _restricted = MutableStateFlow(false)
    val restricted: StateFlow<Boolean> = _restricted
    private var car: Car? = null

    fun start() {
        try {
            val c = Car.createCar(context) ?: return
            car = c
            val mgr = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            _restricted.value = mgr.currentCarUxRestrictions.isRequiresDistractionOptimization
            mgr.registerListener { r -> _restricted.value = r.isRequiresDistractionOptimization }
        } catch (t: Throwable) {
            Log.i(TAG, "주행 제한 정보 없음(AAOS 아님 또는 미지원): $t")
            _restricted.value = false
        }
    }

    fun stop() {
        runCatching { car?.disconnect() }
    }
}
