package com.flowercards.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flowercards.feature.game.render.rememberCardImages

private val BoardGreen = Color(0xFF14532D)

/**
 * Phase 2-B 정적 보드 (PLAN-phase2 §4·§5).
 *
 * 카드 이미지 49종 프리로드가 끝나면 8밴드 보드를 P1 관점(하단 고정)으로 read-only 렌더한다.
 * 실제 터치/드래그/선택 입력은 2-C, 고·스톱/특수상황 오버레이는 2-D 범위 — 여기서는 만들지 않는다.
 * 액션바의 디버그 버튼(현재 턴 첫 장 내기/새 게임)은 2-A 상태 배선 검증용으로 유지한다.
 */
@Composable
fun GameRoute(
    onOpenSettings: () -> Unit = {},
    viewModel: GameViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cardImages = rememberCardImages()

    if (cardImages == null) {
        LoadingScreen()
    } else {
        GameBoard(
            uiState = uiState,
            cardImages = cardImages,
            onPlayFirstCard = viewModel::playFirstCardOfCurrentTurn,
            onNewGame = { viewModel.newGame() },
            onOpenSettings = onOpenSettings,
        )
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BoardGreen),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "카드 준비 중…", color = Color.White)
    }
}
