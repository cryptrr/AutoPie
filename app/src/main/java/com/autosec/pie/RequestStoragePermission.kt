package com.autosec.pie

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat.startActivity
import com.autosec.pie.domain.ViewModelEvent
import com.autosec.pie.viewModels.MainViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent

@RequiresApi(Build.VERSION_CODES.R)
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RequestManageStoragePermission(context: Activity, innerPadding: PaddingValues) {

    val manageExternalStorageState = rememberPermissionState(
        android.Manifest.permission.MANAGE_EXTERNAL_STORAGE
    )

    val mainViewModel: MainViewModel by KoinJavaComponent.inject(MainViewModel::class.java)

    var isInstallingPython by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(key1 = context) {
        launch {
            mainViewModel.eventFlow.collectLatest {
                when(it){
                    is ViewModelEvent.InstallingPython -> isInstallingPython = true
                    is ViewModelEvent.InstalledPythonSuccessfully -> isInstallingPython = false
                    else -> {}
                }
            }
        }
    }


    Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center){
        if(isInstallingPython){
            Box(
                Modifier
                    .fillMaxHeight()
                    .height(500.dp)
                    .fillMaxWidth(), contentAlignment = Alignment.Center){
                Column(horizontalAlignment = Alignment.CenterHorizontally){
                    CircularProgressIndicator(Modifier.size(150.dp))
                    Spacer(Modifier.height(20.dp))
                    Text(text = "Please wait while installing python...", color = Color.White.copy(0.7F), fontSize = 15.7.sp)
                }
            }
        }else{
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    Button(
                        modifier = Modifier.height(50.dp),
                        shape = RoundedCornerShape(15.dp),
                        onClick = {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = Uri.parse("package:" + context.packageName)
                            startActivity(context, intent, null)

                        }) {
                        Text(text = "Request Manage Storage Permission")
                    }
                } else {
                    Text(text = "Permission already granted")
                }
            } else {
                // Handle permissions for Android 10 and below
                Text(text = "This feature requires Android 11 or higher")
            }
        }
    }
}
