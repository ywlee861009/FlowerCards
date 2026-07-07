package com.flowercards.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Phase 1 placeholder. 보드 UI는 Phase 2에서 Compose Canvas로 구현한다 (android/PLAN.md §4).
 */
@Composable
fun GameRoute() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF14532D)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "FlowerCards", color = Color.White)
    }
}
