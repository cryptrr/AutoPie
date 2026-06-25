package com.autopi.autopieapp.presentation.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autopi.autopieapp.presentation.viewModels.OutputViewerViewModel
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
                .fillMaxHeight(0.88F)
                .background(Color(0xFF090B0F)),
            contentAlignment = Alignment.TopStart
        )
        {
            viewModel.currentLogPath.value?.let {
                Column(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF10141B))
                            .padding(horizontal = 18.dp, vertical = 12.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1F)) {
                                Text(
                                    text = viewModel.currentCommandName.value.ifBlank { "Command" },
                                    lineHeight = 22.sp,
                                    fontSize = 21.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF3F6FA)
                                )

                                Spacer(modifier = Modifier.height(5.dp))

                                Text(
                                    text = "Logs",
                                    lineHeight = 20.sp,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF9EA7B3)
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = wordWrap, onCheckedChange = { wordWrap = it })
                                Spacer(Modifier.width(1.dp))
                                Text(
                                    "Word wrap",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFD7DDE5)
                                )
                            }
                        }
                    }

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scroll)
                            .padding(horizontal = 18.dp, vertical = 14.dp)
                    ) {
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
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = Color(0xFFE8EDF4),
                                    softWrap = wordWrap
                                )
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
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        containerColor = Color(0xFF090B0F),
        dragHandle = null,
        onDismissRequest = {
            scope.launch {
                activity?.finish()
                viewModel.currentLogPath.value = null
            }
        }
    )
}
