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
 * Phase 2-C 인게임 보드 (PLAN-phase2 §4·§5·§6).
 *
 * 카드 이미지 49종 프리로드 후 8밴드 보드를 좌석 고정(P1 하단/P2 상단)으로 렌더하고,
 * 현재 턴 플레이어 손패의 드래그/탭 입력을 [GameViewModel.onAction]에 배선한다.
 * 고·스톱 모달/특수상황 오버레이는 2-D, 결과 화면은 2-E 범위 — 여기서는 만들지 않는다.
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
            events = viewModel.events,
            onAction = viewModel::onAction,
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
