package com.flowercards.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flowercards.domain.engine.GamePhase
import com.flowercards.domain.engine.GameResult

/**
 * Phase 2-A 최소 검증 UI (PLAN-phase2 §2 "상태 배선").
 *
 * 딜된 초기 상태를 P1 관점으로 텍스트 표시하고, hotseat 교대 검증을 위해 **현재 턴 플레이어**의
 * 손패 첫 장을 PlayCard로 내는 임시 버튼을 둔다. 캔버스 보드/카드 이미지/드래그는 2-B 이후 범위.
 */
@Composable
fun GameRoute(
    onOpenSettings: () -> Unit = {},
    viewModel: GameViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    GameDebugScreen(
        uiState = uiState,
        seed = viewModel.seed,
        onPlayFirstCard = viewModel::playFirstCardOfCurrentTurn,
        onNewGame = { viewModel.newGame() },
        onOpenSettings = onOpenSettings,
    )
}

@Composable
private fun GameDebugScreen(
    uiState: GameUiState,
    seed: Long,
    onPlayFirstCard: () -> Unit,
    onNewGame: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF14532D))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Header("FlowerCards — 2-A 상태 배선 검증")
        Line("시드", seed.toString())
        Line("단계(phase)", uiState.phase.name)
        Line("턴(turn)", uiState.turn.name)
        Line("점수 (P1/P2)", "${uiState.myScore} / ${uiState.oppScore}")
        Line("고 가능(P1, canGo)", uiState.canGo.toString())
        Line("남은 더미(pile)", uiState.pileCount.toString())
        Line("상대 손패 수(P2)", uiState.oppHandCount.toString())
        Line("내 획득(P1)", uiState.myCaptured.label())
        Line("상대 획득(P2)", uiState.oppCaptured.label())
        Line("흔들기 가능 월(P1)", uiState.shakeableMonths.joinToString().ifEmpty { "-" })
        Line("폭탄 가능 월(P1)", uiState.bombableMonths.joinToString().ifEmpty { "-" })

        uiState.result?.let { Line("결과(result)", it.label()) }

        Spacer(Modifier.height(6.dp))
        Header("바닥패 (${uiState.floor.size})")
        Body(uiState.floor.joinToString(" · ") { it.id }.ifEmpty { "(없음)" })

        Spacer(Modifier.height(6.dp))
        Header("내 손패 · P1 (${uiState.myHand.size})")
        Body(uiState.myHand.joinToString(" · ") { it.id }.ifEmpty { "(없음)" })

        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onPlayFirstCard,
            enabled = uiState.phase == GamePhase.AWAITING_PLAY,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // 표시는 P1 관점이지만 이 버튼은 현재 턴 플레이어를 진행시킨다(hotseat 검증)
            Text(text = "현재 턴(${uiState.turn.name}) 첫 장 내기 (PlayCard)")
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onNewGame, modifier = Modifier.weight(1f)) {
                Text("새 게임")
            }
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.weight(1f)) {
                Text("설정")
            }
        }
    }
}

private fun CapturedSummary.label(): String =
    "광 $gwang · 열 $yeol · 띠 $tti · 피 $pi (총 $total)"

private fun GameResult.label(): String = when (this) {
    is GameResult.Win -> "승 ${winner.name} · ${score.total}점"
    is GameResult.ChongtongWin -> "총통 ${winner.name} · ${month.koreanName} · ${score}점"
    GameResult.Nagari -> "나가리(무승부)"
}

@Composable
private fun Header(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = Color.White,
    )
}

@Composable
private fun Line(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFA7F3D0),
            modifier = Modifier.weight(0.42f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.weight(0.58f),
        )
    }
}

@Composable
private fun Body(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = Color.White,
    )
}
