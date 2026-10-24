// EV FIT 단일 액티비티 — Compose 화면을 띄우고, 화면 스택이 비면 Back으로 앱을 숨긴다(Pleos CCP 규칙)
package com.ian.evfit

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.ian.evfit.platform.PleosLocationGateway
import com.ian.evfit.ui.AppViewModel
import com.ian.evfit.ui.EvFitApp

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this) { if (!vm.back()) moveTaskToBack(true) }
        (application as App).graph.location.start(this)
        setContent { EvFitApp(vm) }
    }

    @Deprecated("ComponentActivity의 권한 콜백(FusedLocation 런타임 권한 결과 처리용)")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PleosLocationGateway.REQUEST_CODE) {
            (application as App).graph.location.onPermissionResult(grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
        }
    }
}
