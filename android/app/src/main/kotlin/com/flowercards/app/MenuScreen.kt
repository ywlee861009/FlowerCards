package com.flowercards.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BoardGreen = Color(0xFF14532D)
private val Gold = Color(0xFFD4AF37)

/**
 * 메인 메뉴 화면. 앱 진입점에서 처음 보이는 화면으로, 게임 시작·난이도 조정·설정으로 분기한다.
 *
 * - **게임 시작**: 새 판을 딜하고 게임 화면으로 이동.
 * - **난이도 조정**: AI 상대(Phase 3)가 붙어야 의미가 생기므로 지금은 비활성(자리표시).
 * - **설정**: 라이선스 등 설정 화면으로 이동.
 */
@Composable
fun MenuScreen(
    onStartGame: () -> Unit,
    onOpenDifficulty: () -> Unit = {},
    onOpenSettings: () -> Unit,
    difficultyEnabled: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BoardGreen)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "화투 고스톱",
            color = Gold,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(64.dp))

        MenuButton(label = "게임 시작", onClick = onStartGame)

        Spacer(modifier = Modifier.height(16.dp))

        MenuButton(
            label = if (difficultyEnabled) "난이도 조정" else "난이도 조정  (준비 중)",
            onClick = onOpenDifficulty,
            enabled = difficultyEnabled,
        )

        Spacer(modifier = Modifier.height(16.dp))

        MenuButton(label = "설정", onClick = onOpenSettings)
    }
}

@Composable
private fun MenuButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 360.dp)
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Gold,
            contentColor = BoardGreen,
        ),
    ) {
        Text(text = label, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}
