package com.autosec.pie.autopieapp.presentation.elements

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun OptionLayout(
    modifier: Modifier = Modifier,
    optionList: List<OptionItem>
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        optionList.forEachIndexed { index, item ->
            val position: OptionPosition = if (index == 0) {
                if (optionList.size == 1) OptionPosition.ALONE
                else OptionPosition.TOP
            } else if (index == optionList.size - 1) OptionPosition.BOTTOM
            else OptionPosition.MIDDLE
            val summary: (@Composable () -> Unit)? = if (item.summary.isNullOrBlank()) null else {
                {
                    Text(text = item.summary)
                }
            }
            OptionButton(
                modifier = Modifier.fillMaxWidth(),
                textContainer = {
                    Text(text = item.text)
                },
                summaryContainer = summary,
                enabled = item.enabled,
                containerColor = item.containerColor
                    ?: MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = item.contentColor
                    ?: MaterialTheme.colorScheme.onSurface,
                position = position,
                onClick = {
                    item.onClick(item.summary.toString())
                }
            )
        }
    }
}

@Composable
fun OptionButton(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    textContainer: @Composable () -> Unit,
    summaryContainer: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    position: OptionPosition = OptionPosition.ALONE,
    onClick: () -> Unit
) {
    val mod = modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 56.dp)
        .background(
            color = containerColor,
            shape = position.shape()
        )
        .clip(position.shape())
        .clickable(
            enabled = enabled,
            onClick = onClick
        )
        .alpha(if (enabled) 1f else 0.4f)
        .padding(16.dp)
    if (summaryContainer != null) {
        Column(
            modifier = mod,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProvideTextStyle(
                value = MaterialTheme.typography.labelLarge.copy(
                    color = contentColor,
                    fontWeight = FontWeight.Bold
                )
            ) {
                textContainer()
            }
            ProvideTextStyle(
                value = MaterialTheme.typography.labelMedium.copy(
                    color = contentColor,
                    //fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            ) {
                summaryContainer()
            }
        }
    } else {
        Box(
            modifier = mod,
            contentAlignment = Alignment.Center
        ) {
            ProvideTextStyle(
                value = MaterialTheme.typography.bodyMedium.copy(color = contentColor)
            ) {
                textContainer()
            }
        }
    }
}

object OptionShape {

    val Top = RoundedCornerShape(
        topEnd = 12.dp,
        topStart = 12.dp,
        bottomEnd = 1.dp,
        bottomStart = 1.dp
    )

    val Middle = RoundedCornerShape(
        topEnd = 1.dp,
        topStart = 1.dp,
        bottomEnd = 1.dp,
        bottomStart = 1.dp
    )

    val Bottom = RoundedCornerShape(
        topEnd = 1.dp,
        topStart = 1.dp,
        bottomEnd = 12.dp,
        bottomStart = 12.dp
    )

    val Alone = RoundedCornerShape(
        topEnd = 12.dp,
        topStart = 12.dp,
        bottomEnd = 12.dp,
        bottomStart = 12.dp
    )
}

data class OptionItem(
    val text: String,
    val summary: String? = null,
    val onClick: (summary: String) -> Unit,
    val enabled: Boolean = true,
    val containerColor: Color? = null,
    val contentColor: Color? = null,
)
enum class OptionPosition {
    TOP, MIDDLE, BOTTOM, ALONE
}


fun OptionPosition.shape(): RoundedCornerShape = when (this) {
    OptionPosition.TOP -> OptionShape.Top
    OptionPosition.MIDDLE -> OptionShape.Middle
    OptionPosition.BOTTOM -> OptionShape.Bottom
    OptionPosition.ALONE -> OptionShape.Alone
}