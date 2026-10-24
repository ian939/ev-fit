// 차량(EV 배터리) 상태 조회와 충전 제어를 Pleos Vehicle SDK로 감싼 게이트웨이
package com.ian.evfit.platform

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.EvBattery
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

const val TAG = "EvFit"

interface VehicleGateway {
    /** 배터리 SOC(%) — 모르면 null */
    val soc: StateFlow<Int?>
    /** 충전 중 여부 — 모르면 null */
    val charging: StateFlow<Boolean?>
    fun start()
    fun stop()
    fun openChargePort(onResult: (Result<Unit>) -> Unit)
    fun setChargeLimit(percent: Int, onResult: (Result<Int>) -> Unit)
    fun readVin(onResult: (String?) -> Unit)
}

class PleosVehicleGateway(context: Context, private val scope: CoroutineScope) : VehicleGateway {
    private val vehicle = Vehicle(context)
    private val _soc = MutableStateFlow<Int?>(null)
    private val _charging = MutableStateFlow<Boolean?>(null)
    override val soc: StateFlow<Int?> = _soc
    override val charging: StateFlow<Boolean?> = _charging
    private var ev: EvBattery? = null
    private var pollJob: Job? = null

    private val socListener = object : EvBatteryStateOfChargeListener {
        override fun onEvBatteryStateOfChargeUpdated(value: Int?) {
            _soc.value = value
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "SOC 구독 실패: $e")
        }
    }

    override fun start() {
        runCatching {
            vehicle.initialize()
            val battery = vehicle.getEvBattery()
            ev = battery
            battery.getEvBatteryStateOfCharge({ _soc.value = it }, { Log.w(TAG, "SOC 조회 실패: $it") })
            battery.registerEvBatteryStateOfCharge(socListener)
        }.onFailure { Log.w(TAG, "Vehicle SDK 시작 실패: $it") }
        pollJob = scope.launch {
            while (isActive) {
                runCatching {
                    ev?.getEvChargeSwitchState({ _charging.value = it }, { Log.w(TAG, "충전 상태 조회 실패: $it") })
                }
                delay(5_000)
            }
        }
    }

    override fun stop() {
        pollJob?.cancel()
        runCatching { ev?.unregisterEvBatteryStateOfCharge(socListener) }
        runCatching { vehicle.release() }
    }

    override fun openChargePort(onResult: (Result<Unit>) -> Unit) {
        val battery = ev ?: return onResult(Result.failure(IllegalStateException("Vehicle SDK 미연결")))
        runCatching {
            battery.setEvChargePortOpen(true, { onResult(Result.success(Unit)) }, { onResult(Result.failure(it)) })
        }.onFailure { onResult(Result.failure(it)) }
    }

    override fun setChargeLimit(percent: Int, onResult: (Result<Int>) -> Unit) {
        val battery = ev ?: return onResult(Result.failure(IllegalStateException("Vehicle SDK 미연결")))
        runCatching {
            battery.checkEvChargePercentLimitCapabilities({ _, supported ->
                // 지원 값 중 요청 이하 최댓값, 없으면 요청값 그대로
                val value = supported.filter { it <= percent }.maxOrNull() ?: percent.toFloat()
                battery.setEvChargePercentLimit(value, { onResult(Result.success((it ?: value).toInt())) }, { onResult(Result.failure(it)) })
            }, { onResult(Result.failure(it)) })
        }.onFailure { onResult(Result.failure(it)) }
    }

    override fun readVin(onResult: (String?) -> Unit) {
        runCatching { vehicle.getCarInfo().getVINInfo({ onResult(it) }, { onResult(null) }) }
            .onFailure { onResult(null) }
    }
}
