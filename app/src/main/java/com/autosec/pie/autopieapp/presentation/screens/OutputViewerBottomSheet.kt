package com.autopi.autopieapp.presentation.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autopi.autopieapp.presentation.viewModels.OutputViewerViewModel
import com.autopi.utils.conditional
import com.autopi.utils.getActivity
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutputViewerBottomSheet(
    state: SheetState,
    open: MutableState<Boolean>,
    parentSheetState: SheetState? = null,
    callerName: String = "SHARE",
    onHide: () -> Unit = {},
    onExpand: () -> Unit = {},
) {

    val viewModel: OutputViewerViewModel = koinViewModel()

    val scope = rememberCoroutineScope()

    val activity = LocalContext.current.getActivity()

    LaunchedEffect(key1 = state.targetValue) {
        if (state.targetValue == SheetValue.Expanded) {
            parentSheetState?.hide()
        } else {
            parentSheetState?.show()
        }
    }

    val logsState = viewModel.logContent.collectAsState()

    val scroll = rememberScrollState()
    val horizontal = rememberScrollState()
    var wordWrap by remember { mutableStateOf(true) }

    LaunchedEffect(logsState.value) {
        scroll.scrollTo(scroll.maxValue)
    }


    @SuppressLint("UnusedBoxWithConstraintsScope")
    @Composable
    fun bottomSheetContent() {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                //.wrapContentHeight()
                .fillMaxHeight(0.80F)
            ,
            contentAlignment = Alignment.TopStart

        )
        {
            Column(
                Modifier
                    //.fillMaxSize()
                    .padding(15.dp)
                    .fillMaxWidth()
            ) {

                Text(
                    text = viewModel.currentCommandName.value,
                    lineHeight = 32.sp,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Spacer(modifier = Modifier.height(17.dp))

                Row (Modifier.fillMaxWidth(), Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically){
                    Text(
                        text = "Logs",
                        lineHeight = 32.sp,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7F)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically){
                        Checkbox(checked = wordWrap, onCheckedChange = { wordWrap = it })
                        Spacer(Modifier.width(5.dp))
                        Text("Word Wrap", fontWeight = FontWeight.SemiBold)
                    }

                }



                Spacer(modifier = Modifier.height(20.dp))

                viewModel.currentLogPath.value?.let {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(15.dp))
                            .fillMaxHeight()
                            .background(Color.Black.copy(alpha = 0.25F))
                            .padding(horizontal = 15.dp)
                            .verticalScroll(scroll) // Only vertical scroll on the parent
                    ) {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            // If word wrap is disabled, keep the scroll content at least as wide as the visible log area.
                            val contentModifier = if (!wordWrap) {
                                Modifier
                                    .widthIn(min = maxWidth)
                                    .horizontalScroll(horizontal)
                            } else {
                                Modifier.fillMaxWidth()
                            }

                            Box(modifier = contentModifier) {
                                SelectionContainer {
                                    Text(
                                        text = logsState.value,
                                        fontFamily = FontFamily.Monospace,
                                        softWrap = wordWrap,
                                        modifier = Modifier.padding(vertical = 15.dp)
                                    )
                                }
                            }
                        }
                    }
                }

            }
        }


    }

    ModalBottomSheet(
        sheetState = state,
        content = { bottomSheetContent() },
        shape = RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        onDismissRequest = {
            scope.launch {
                activity?.finish()
                viewModel.currentLogPath.value = null
            }
        }
    )
}
