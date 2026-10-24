// EV FIT 단일 액티비티 — Compose 화면을 띄우고 최상위 Back은 앱을 숨긴다(Pleos CCP 규칙)
package com.ian.evfit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this) { moveTaskToBack(true) }
        setContent { Text("EV FIT") }
    }
}
