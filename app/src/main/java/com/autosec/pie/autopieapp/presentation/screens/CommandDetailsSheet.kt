package com.autosec.pie.autopieapp.presentation.screens

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autosec.pie.BuildConfig
import com.autosec.pie.R
import com.autosec.pie.autopieapp.data.ShareInputs
import com.autosec.pie.autopieapp.domain.ViewModelEvent
import com.autosec.pie.autopieapp.presentation.elements.AutoPiePrimaryButton
import com.autosec.pie.autopieapp.presentation.elements.OptionItem
import com.autosec.pie.autopieapp.presentation.elements.OptionLayout
import com.autosec.pie.autopieapp.presentation.viewModels.CreateCommandViewModel
import com.autosec.pie.autopieapp.presentation.viewModels.ShareReceiverViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandDetailsSheet(
    state: SheetState,
    open: MutableState<Boolean>,
    key: String? = null,
    onHide: () -> Unit = {},
    onExpand: () -> Unit = {}
) {


    val scope = rememberCoroutineScope()

    val shareReceiverViewModel: ShareReceiverViewModel = koinViewModel()
    val createCommandViewModel: CreateCommandViewModel = koinViewModel()

    val card = shareReceiverViewModel.main.currentSelectedCommand.value ?: return

    val context = LocalContext.current

    fun pinAppShortcut(context: Context, commandId: String, shortLabel: String, longLabel: String) {
        try {
            val shortcutManager = context.getSystemService(ShortcutManager::class.java)

            if (shortcutManager.isRequestPinShortcutSupported) {
                val shortcut = ShortcutInfo.Builder(context, commandId)
                    .setShortLabel(shortLabel)
                    .setLongLabel(longLabel)
                    .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
                    .setIntent(
                        Intent(Intent.ACTION_MAIN).apply {
                            setClassName(context, BuildConfig.APPLICATION_ID + ".DirectCommandActivity")
                            putExtra("commandId", commandId)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    )
                    .build()

                val pinnedShortcutCallbackIntent = shortcutManager.createShortcutResultIntent(shortcut)
                val successCallback = PendingIntent.getBroadcast(
                    context, 0, pinnedShortcutCallbackIntent, PendingIntent.FLAG_IMMUTABLE
                )

                shortcutManager.requestPinShortcut(shortcut, successCallback.intentSender)
            }
        }catch (e: Exception){
            Timber.e(e)
        }
    }


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
                text = "HISTORY",
                enabled = true,
                onClick = {
                    scope.launch {
                        open.value = false
                        shareReceiverViewModel.main.dispatchEvent(ViewModelEvent.OpenCommandHistory(card))
                    }
                }
            ),
            OptionItem(
                text = "ADD TO HOME SCREEN",
                enabled = true,
                onClick = {
                    scope.launch {
                        pinAppShortcut(context = context, card.name, card.name, card.name)
                        open.value = false
                    }
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
                   open.value = false
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
