package com.autosec.pie.elements

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.autosec.pie.ui.theme.Purple10

@Composable
fun AutoPieLogo(){
    Text(
        text = buildAnnotatedString {
            append("auto")
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                append("pie")
            }
        },
        fontSize = 33.sp,
        letterSpacing = 1.6.sp,
        fontWeight = FontWeight.Bold
    )
}