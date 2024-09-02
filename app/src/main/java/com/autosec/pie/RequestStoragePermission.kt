package com.autosec.pie

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autosec.pie.domain.ViewModelEvent
import com.autosec.pie.services.AutoPieCoreService
import com.autosec.pie.viewModels.MainViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent
import timber.log.Timber


@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun RequestManageStoragePermission(context: Activity, innerPadding: PaddingValues) {

    val mainViewModel: MainViewModel by KoinJavaComponent.inject(MainViewModel::class.java)

    val android10Permissions = arrayOf(
        android.Manifest.permission.READ_EXTERNAL_STORAGE,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE)

    val android10PermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val permissionsGranted = permissions.values.reduce { acc, isPermissionGranted ->
                acc && isPermissionGranted
            }

            if (!permissionsGranted) {
                Timber.d("Permission not granted")
            }
            else{
                mainViewModel.storageManagerPermissionGranted = true
                AutoPieCoreService.initAutosec()
            }
        })



    var isInstallingPython by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(key1 = context) {
        launch {
            mainViewModel.eventFlow.collectLatest {
                when (it) {
                    is ViewModelEvent.InstallingPython -> isInstallingPython = true
                    is ViewModelEvent.InstalledPythonSuccessfully -> isInstallingPython = false
                    else -> {}
                }
            }
        }
    }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val hasPermission = Environment.isExternalStorageManager()
                if (hasPermission) {
                    Timber.d("All files access granted")
                    mainViewModel.storageManagerPermissionGranted = true
                    AutoPieCoreService.initAutosec()
                } else {
                    Timber.d("All files access denied")
                }
            }
        }


    Box(
        Modifier
            .fillMaxSize()
            .padding(innerPadding), contentAlignment = Alignment.Center
    ) {
        if (isInstallingPython) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .height(500.dp)
                    .fillMaxWidth(), contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(Modifier.size(150.dp))
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = "Please wait while installing python...",
                        color = Color.White.copy(0.7F),
                        fontSize = 15.7.sp
                    )
                }
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    Button(
                        modifier = Modifier.height(50.dp),
                        shape = RoundedCornerShape(15.dp),
                        onClick = {

                            val intent =
                                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = Uri.parse("package:" + context.packageName)
                            launcher.launch(intent, null)

                        }) {
                        Text(text = "Request Manage Storage Permission")
                    }
                } else {
                    Text(text = "Permission already granted")
                }
            } else {
                // Handle permissions for Android 10 and below

                Button(
                    modifier = Modifier.height(50.dp),
                    shape = RoundedCornerShape(15.dp),
                    onClick = {

                        android10PermissionsLauncher.launch(android10Permissions)

                    }) {
                    Text(text = "Request Storage Permission")
                }

                LaunchedEffect(key1 = context) {

                }

            }
        }
    }
}
