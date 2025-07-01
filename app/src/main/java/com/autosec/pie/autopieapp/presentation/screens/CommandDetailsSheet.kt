package com.autosec.pie.autopieapp.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.autosec.pie.autopieapp.data.CommandModel
import com.autosec.pie.autopieapp.data.ShareInputs
import com.autosec.pie.autopieapp.domain.AppNotification
import com.autosec.pie.autopieapp.domain.ViewModelEvent
import com.autosec.pie.autopieapp.presentation.elements.AutoPiePrimaryButton
import com.autosec.pie.autopieapp.presentation.viewModels.CloudPackagesViewModel
import com.autosec.pie.autopieapp.presentation.viewModels.CreateCommandViewModel
import com.autosec.pie.autopieapp.presentation.viewModels.MainViewModel
import com.autosec.pie.autopieapp.presentation.viewModels.ShareReceiverViewModel
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.component.inject
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandDetailsSheet(
    state: SheetState,
    open: MutableState<Boolean>,
    card: CommandModel,
    key: String? = null,
    onHide: () -> Unit = {},
    onExpand: () -> Unit = {}
) {

    val scope = rememberCoroutineScope()

    val shareReceiverViewModel: ShareReceiverViewModel = koinViewModel()
    val createCommandViewModel: CreateCommandViewModel = koinViewModel()


    val optionsList = remember{
        listOf(
            OptionItem(
                text = "EDIT",
                enabled = true,
                onClick = {
                    scope.launch {
                        shareReceiverViewModel.main.currentCommandKey.value = card.name
                        shareReceiverViewModel.main.dispatchEvent(ViewModelEvent.OpenEditCommandSheet)
                        open.value = false
                    }

                }
            ),
            OptionItem(
                text = "CLONE",
                enabled = true,
                onClick = {
                    scope.launch {
                        createCommandViewModel.cloneCommand(command = card)
                        open.value = false
                        shareReceiverViewModel.main.dispatchEvent(ViewModelEvent.RefreshCommandsList)
                    }
                }
            ),

            OptionItem(
                text = "DETAILS",
                enabled = true,
                onClick = {
                    shareReceiverViewModel.main.showNotification(AppNotification.FeatureWIP)
                }
            ),
        )
    }


    @Composable
    fun bottomSheetContent() {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                //.height(700.dp)
                .wrapContentHeight(),
            contentAlignment = Alignment.TopStart
            //.windowInsetsPadding(WindowInsets.navigationBars)

        )
        {

           Column(Modifier.padding(15.dp)){

               Spacer(modifier = Modifier.height(10.dp))

               Text(
                   text = card.name,
                   lineHeight = 32.sp,
                   fontSize = 28.sp,
                   fontWeight = FontWeight.Bold,
                   color = MaterialTheme.colorScheme.onPrimaryContainer
               )

               Spacer(modifier = Modifier.height(20.dp))

               OptionLayout(Modifier, optionList = optionsList)

               Spacer(Modifier.height(7.dp))

               AutoPiePrimaryButton("RUN") {
                   shareReceiverViewModel.currentExtrasDetails.value =
                       Triple(true, card, ShareInputs())
               }

           }

        }


    }


    ModalBottomSheet(
        sheetState = state,
        content = { bottomSheetContent() },
        shape = RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp),
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        onDismissRequest = {
            scope.launch {
                open.value = false
            }
        }
    )
}

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