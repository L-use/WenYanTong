package com.wenyantong.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wenyantong.app.core.designsystem.WenYanTheme
import com.wenyantong.app.ui.WenYanApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: AppViewModel = viewModel()
            val state by vm.state.collectAsStateWithLifecycle()
            WenYanTheme(state.theme) {
                val light=MaterialTheme.colorScheme.background.luminance()>0.5f
                SideEffect {
                    WindowCompat.getInsetsController(window,window.decorView).apply {
                        isAppearanceLightStatusBars=light
                        isAppearanceLightNavigationBars=light
                    }
                }
                WenYanApp(vm,state)
            }
        }
    }
}
