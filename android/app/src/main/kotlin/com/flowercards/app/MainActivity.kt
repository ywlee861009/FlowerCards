package com.flowercards.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.flowercards.feature.game.GameRoute
import com.flowercards.feature.setting.SettingRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var screen by remember { mutableStateOf(AppScreen.Game) }

            MaterialTheme {
                when (screen) {
                    AppScreen.Game -> GameRoute(
                        onOpenSettings = { screen = AppScreen.Setting },
                    )

                    AppScreen.Setting -> SettingRoute(
                        onBack = { screen = AppScreen.Game },
                    )
                }
            }
        }
    }
}

private enum class AppScreen {
    Game,
    Setting,
}
