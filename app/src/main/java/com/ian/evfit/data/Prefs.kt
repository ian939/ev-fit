// 지갑·충전 기록·시연 모드를 SharedPreferences에 JSON으로 저장하는 저장소
package com.ian.evfit.data

import android.content.Context
import com.ian.evfit.engine.SocSample
import com.ian.evfit.engine.Wallet
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.YearMonth

@Serializable
data class ChargeRecord(
    val id: String,
    val startedAt: Long,
    val stationId: String,
    val stationName: String,
    val bid: String,
    val credentialLabel: String,
    val planId: String? = null,
    val fromSoc: Int,
    val toSoc: Int,
    val kwh: Double,
    val costKrw: Long,
    val moeReferenceKrw: Long? = null,
    val samples: List<SocSample> = emptyList(),
    val simulated: Boolean,
)

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("evfit", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val hasWallet: Boolean get() = sp.contains(KEY_WALLET)

    var wallet: Wallet
        get() = sp.getString(KEY_WALLET, null)
            ?.let { runCatching { json.decodeFromString<Wallet>(it) }.getOrNull() }
            ?.let { rollMonth(it) } ?: Wallet()
        set(v) = sp.edit().putString(KEY_WALLET, json.encodeToString(v)).apply()

    var demoMode: Boolean
        get() = sp.getBoolean(KEY_DEMO, true)
        set(v) = sp.edit().putBoolean(KEY_DEMO, v).apply()

    var records: List<ChargeRecord>
        get() = sp.getString(KEY_RECORDS, null)
            ?.let { runCatching { json.decodeFromString<List<ChargeRecord>>(it) }.getOrNull() } ?: emptyList()
        set(v) = sp.edit().putString(KEY_RECORDS, json.encodeToString(v)).apply()

    companion object {
        private const val KEY_WALLET = "wallet"
        private const val KEY_DEMO = "demoMode"
        private const val KEY_RECORDS = "records"

        fun currentMonth(): String = YearMonth.now().toString()

        /** 구독 사용량은 월 단위 — 기록된 월이 지나면 0으로 되돌린다 */
        fun rollMonth(w: Wallet, month: String = currentMonth()): Wallet =
            w.copy(subscriptions = w.subscriptions.map {
                if (it.month != month) it.copy(usedKwhThisMonth = 0.0, month = month) else it
            })
    }
}
