package com.autosec.pie.autopieapp.presentation.screens

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.outlined.ArrowCircleRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autosec.pie.BuildConfig
import com.autosec.pie.autopieapp.domain.AppNotification
import com.autosec.pie.autopieapp.presentation.elements.SettingsHeader
import com.autosec.pie.autopieapp.data.services.GithubApiService
import com.autosec.pie.terminal.TerminalEmulatorActivity
import com.autosec.pie.ui.theme.PastelYellow
import com.autosec.pie.autopieapp.presentation.viewModels.MainViewModel
import com.autosec.pie.ui.theme.PastelGreen
import com.termux.app.TermuxActivity
import org.koin.java.KoinJavaComponent
import timber.log.Timber

@Composable
fun SettingsScreen(innerPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(15.dp)
        ) {
            Column(Modifier) {
                Spacer(modifier = Modifier.height(15.dp))
                Text(
                    text = "Settings",
                    fontSize = 33.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(15.dp))
            }

            SettingsHeader()
            SettingsToggles()
        }
    }
}


@Composable
fun GoToPageIcon() {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(50.dp)
            .padding(5.dp),
        contentAlignment = Alignment.Center
    )
    {
        Icon(
            modifier = Modifier
                .fillMaxSize(),
            imageVector = Icons.Outlined.ArrowCircleRight,
            contentDescription = "Go to transactions page.",
            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5F)
        )
    }
}


@Composable
fun SettingsToggles() {

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current


    val mainViewModel: MainViewModel by KoinJavaComponent.inject(MainViewModel::class.java)


    Column(
        verticalArrangement = Arrangement.SpaceEvenly, modifier = Modifier
            .clip(RoundedCornerShape(15.dp))
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
            .padding(vertical = 7.dp, horizontal = 15.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clickable(
                    indication = null,
                    enabled = true,
                    interactionSource = remember { MutableInteractionSource() })
                {
                    //mainViewModel.showNotification(AppNotification.FeatureWIP)

                    try {
                        val intent = Intent(context, TermuxActivity::class.java)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

//                    try {
//                        Class.forName("com.termux.app.TermuxActivity")
//                        Timber.d( "TermuxActivity loaded fine")
//                    } catch (e: Exception) {
//                        Timber.e("Failed to load TermuxActivity: $e")
//                    }


                }
        ) {
            Text("Terminal")
            GoToPageIcon()
        }

        Row(horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)

                .clickable(
                    indication = null,
                    enabled = true,
                    interactionSource = remember { MutableInteractionSource() })
                {
                    mainViewModel.showNotification(AppNotification.FeatureWIP)
                }
        ) {
            Text(
                "Commands History",
                color = MaterialTheme.colorScheme.onSurface
            )
            GoToPageIcon()
        }

    }

    Spacer(modifier = Modifier.height(20.dp))

    //TODO: Themes - Work in Progress

    Column(
        verticalArrangement = Arrangement.SpaceEvenly, modifier = Modifier
            .clip(RoundedCornerShape(15.dp))
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
            .padding(vertical = 7.dp, horizontal = 15.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
        ) {
            Text(
                "Dark Theme",
                color = if (false) MaterialTheme.colorScheme.onSurface else Color.Gray
            )
            Switch(
                checked = false,
                enabled = false,
                onCheckedChange = {

                })
        }

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
        ) {
            Text("Use System Theme")
            Switch(checked = true, onCheckedChange = {


            })
        }
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
        ) {
            Text(
                "Enable Dynamic Colors",
                color = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MaterialTheme.colorScheme.onSurface else Color.Gray
            )
            Switch(checked = true,
                enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                onCheckedChange = {

                })
        }


    }

    Spacer(modifier = Modifier.height(20.dp))

    Column(
        verticalArrangement = Arrangement.Center, modifier = Modifier
            .clip(RoundedCornerShape(15.dp))
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
            .fillMaxWidth()
            .padding(vertical = 7.dp, horizontal = 15.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)

                //.height(90.dp)
        ) {
            Column(Modifier.fillMaxWidth(0.8F)){
                Text("Turn On File Observers")
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "Turn off if you don't want to use FileObserver feature.",
                    softWrap = true,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                )
            }
            //Spacer(modifier = Modifier.width(20.dp))
            Switch(checked = !mainViewModel.turnOffFileObservers, onCheckedChange = {
                mainViewModel.toggleFileObservers()
            })
        }

    }

    Spacer(modifier = Modifier.height(20.dp))

    Column(
        verticalArrangement = Arrangement.Center, modifier = Modifier
            .clip(RoundedCornerShape(15.dp))
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
            .fillMaxWidth()
            .padding(vertical = 7.dp, horizontal = 15.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
                .clickable(
                    indication = null,
                    enabled = true,
                    interactionSource = remember { MutableInteractionSource() })
                {
                    mainViewModel.clearPackagesCache()
                }

            //.height(90.dp)
        ) {
            Column(Modifier.fillMaxWidth(0.8F)){
                Text("Clear Package Cache")
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "Useful when old cached versions of packages are incorrectly used.",
                    softWrap = true,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                )
            }
        }

    }


    Spacer(modifier = Modifier.height(20.dp))

    //MARK: BACKUPS


    Column(
        verticalArrangement = Arrangement.Center, modifier = Modifier
            .clip(RoundedCornerShape(15.dp))
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
            .fillMaxWidth()
            .padding(vertical = 7.dp, horizontal = 15.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
                .clickable(
                    indication = null,
                    enabled = true,
                    interactionSource = remember { MutableInteractionSource() })
                {
                    mainViewModel.showNotification(AppNotification.FeatureWIP)
                }

            //.height(90.dp)
        ) {
            Column(Modifier.fillMaxWidth(0.8F)){
                Text("Generate Backup File")
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "Generates a tar.xz backup file and stores it to the base directory of your storage.",
                    softWrap = true,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
                .clickable(
                    indication = null,
                    enabled = true,
                    interactionSource = remember { MutableInteractionSource() })
                {
                    mainViewModel.showNotification(AppNotification.FeatureWIP)
                }

            //.height(90.dp)
        ) {
            Column(Modifier.fillMaxWidth(0.8F)){
                Text("Restore From Backup")
            }
        }

    }




    Spacer(modifier = Modifier.height(20.dp))

    Button(
        onClick = { if(mainViewModel.mcpServerActive) mainViewModel.stopMCPServer() else mainViewModel.startMCPServer() },
        colors = ButtonDefaults.buttonColors(
            contentColor = MaterialTheme.colorScheme.onTertiary,
            disabledContentColor = MaterialTheme.colorScheme.onTertiary.copy(alpha = .12f),
            containerColor = if(mainViewModel.mcpServerActive) PastelGreen else MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = .12f)
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)

    ) {
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = if(mainViewModel.mcpServerActive) "MCP Server Running" else "Start MCP Server" )
        Spacer(modifier = Modifier.width(8.dp))
    }


    Spacer(modifier = Modifier.height(20.dp))


    Column(
        verticalArrangement = Arrangement.Center, modifier = Modifier
            .clip(RoundedCornerShape(15.dp))
            .background(if(mainViewModel.updatesAreAvailable != true) MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp) else PastelYellow)
            .fillMaxWidth()

            .padding(vertical = 7.dp, horizontal = 15.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
                .clickable(
                    indication = null,
                    enabled = true,
                    interactionSource = remember { MutableInteractionSource() })
                {
                    if(mainViewModel.updatesAreAvailable == true && mainViewModel.updateDetails != null){
                        val url = GithubApiService.getAarch64ApkUrl(mainViewModel.updateDetails!!) ?: return@clickable
                        uriHandler.openUri(url)
                    }
                }

            //.height(90.dp)
        ) {
            Column(Modifier.fillMaxWidth(1F)){
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Version", fontSize = 15.4.sp, color = if(mainViewModel.updatesAreAvailable != true) MaterialTheme.colorScheme.onSurface else Color.Black)
                    Text(BuildConfig.VERSION_NAME, fontSize = 15.4.sp, color = if(mainViewModel.updatesAreAvailable != true) MaterialTheme.colorScheme.onSurface else Color.Black)
                }
                Spacer(modifier = Modifier.height(4.dp))
                if(mainViewModel.updatesAreAvailable == true){
                    Text(
                        "Click here to update to version ${mainViewModel.updateDetails?.tag_name}",
                        softWrap = true,
                        fontSize = 14.sp,
                        color = if(mainViewModel.updatesAreAvailable != true) MaterialTheme.colorScheme.onSurface else Color.Black
                    )
                }
            }
        }

    }

    Spacer(modifier = Modifier.height(20.dp))

}