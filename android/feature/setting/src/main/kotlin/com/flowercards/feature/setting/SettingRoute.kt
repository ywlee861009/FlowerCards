package com.flowercards.feature.setting

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun SettingRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingScreen(
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
private fun SettingScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC)),
        color = Color(0xFFF8FAFC),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "설정",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827),
                )
                TextButton(onClick = onBack) {
                    Text(text = "완료")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "오픈소스 라이선스",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF111827),
            )
            Spacer(modifier = Modifier.height(12.dp))

            LicenseItem(label = "이미지", value = "Hwatu card images")
            LicenseItem(
                label = "저작자",
                value = "Marcus Richert, Louie Mantia, Jr., Spenĉjo and Wikimedia Commons contributors",
            )
            LicenseItem(
                label = "출처",
                value = "https://commons.wikimedia.org/wiki/Category:SVG_Hwatu",
            )
            LicenseItem(
                label = "라이선스",
                value = "Creative Commons Attribution-ShareAlike 4.0 International",
            )
            LicenseItem(
                label = "라이선스 URL",
                value = "https://creativecommons.org/licenses/by-sa/4.0/",
            )
            LicenseItem(label = "변경사항", value = "없음")
        }
    }
}

@Composable
private fun LicenseItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFF475569),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF111827),
        )
        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = Color(0xFFE2E8F0))
        Spacer(modifier = Modifier.height(14.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingScreenPreview() {
    MaterialTheme {
        SettingScreen(onBack = {})
    }
}
