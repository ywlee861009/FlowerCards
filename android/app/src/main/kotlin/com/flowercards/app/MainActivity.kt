package com.flowercards.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flowercards.feature.game.GameRoute
import com.flowercards.feature.game.GameViewModel
import com.flowercards.feature.setting.SettingRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 시스템 바를 투명하게 하고 콘텐츠를 바 뒤까지 그린다(targetSdk 35에서 이미 강제됨).
        // 각 화면은 배경만 풀블리드하고 콘텐츠는 safeDrawing 인셋으로 밀어 겹침을 막는다.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // 앱 진입 = 메인 메뉴. 게임 화면은 "게임 시작"을 눌러야 들어간다.
            var screen by remember { mutableStateOf(AppScreen.Menu) }
            // 설정에서 [완료]를 누르면 들어온 화면(메뉴/게임)으로 되돌아간다.
            var settingsOrigin by remember { mutableStateOf(AppScreen.Menu) }

            // 게임 상태는 Activity 스코프로 보유해 "게임 시작"에서 새 판을 딜한다.
            val gameViewModel: GameViewModel = viewModel()

            MaterialTheme {
                when (screen) {
                    AppScreen.Menu -> MenuScreen(
                        onStartGame = {
                            gameViewModel.newGame()
                            screen = AppScreen.Game
                        },
                        onOpenSettings = {
                            settingsOrigin = AppScreen.Menu
                            screen = AppScreen.Setting
                        },
                    )

                    AppScreen.Game -> GameRoute(
                        viewModel = gameViewModel,
                        onOpenSettings = {
                            settingsOrigin = AppScreen.Game
                            screen = AppScreen.Setting
                        },
                    )

                    AppScreen.Setting -> SettingRoute(
                        onBack = { screen = settingsOrigin },
                    )
                }
            }
        }
    }
}

private enum class AppScreen {
    Menu,
    Game,
    Setting,
}
