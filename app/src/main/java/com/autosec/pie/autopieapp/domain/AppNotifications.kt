package com.autosec.pie.autopieapp.domain

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autosec.pie.autopieapp.presentation.elements.BannerType
import com.autosec.pie.autopieapp.presentation.viewModels.MainViewModel
import org.koin.java.KoinJavaComponent
import org.koin.java.KoinJavaComponent.inject

sealed class ViewModelError() : Exception(), Notification {
    object CameraPermissionDenied : ViewModelError()
    object StoragePermissionDenied : ViewModelError()
    object ProductNotFound : ViewModelError()
    data object CouldNotAddProduct : ViewModelError()
    data class InvalidJson(val name: String) : ViewModelError()
    object Timeout : ViewModelError()
    object Unauthorized : ViewModelError()
    object Conflict : ViewModelError()
    object UserForbidden : ViewModelError()
    object NetworkError : ViewModelError()
    object ShareConfigUnavailable : ViewModelError()
    object CronConfigUnavailable : ViewModelError()
    object ObserverConfigUnavailable : ViewModelError()
    object InvalidShareConfig : ViewModelError()
    object InvalidCronConfig : ViewModelError()
    object InvalidObserverConfig : ViewModelError()
    object CommandNotFound : ViewModelError()



    object Unknown : ViewModelError()

    override val title = "Error"

    override val description: String
        get() = when (this) {
            is CameraPermissionDenied -> "App requires camera permission."
            is ProductNotFound -> "Product Not Available"
            is CouldNotAddProduct -> "Unable to add product to history"
            is InvalidJson -> "$name Config is not valid JSON."
            is StoragePermissionDenied -> "Storage Permission not granted."
            else -> "An Unknown Error has occurred"
        }

    override val type: BannerType = BannerType.Error

    override val infinite: Boolean
        get() = when (this) {
            else -> false
        }

    override val hasAction: Boolean
        get() = when (this) {
            else -> false
        }

    override val actionButton: @Composable () -> Unit
        get() = when (this) {
            else -> {
                {}
            }
        }

}

sealed class AppNotification : Notification {
    data object ClearedPackageCache : AppNotification()
    data object InstallingPythonPackages : AppNotification()
    data object InstallingPythonPackagesSuccess : AppNotification()
    data object FeatureWIP : AppNotification()
    object FailedDownloadingArchive : AppNotification()
    object FailedExtractingArchive : AppNotification()
    data object DownloadingInitPackages : AppNotification()
    data object DownloadedInitPackages : AppNotification()
    data object InstallingInitPackages : AppNotification()
    data object InstallingInitPackagesSuccess : AppNotification()
    data object ShowCloseSheetInfo : AppNotification()
    data class UpdatesAvailable(val url: String) : AppNotification()



    override val title = "Notification"

    override val description: String
        get() = when (this) {
            is ClearedPackageCache -> "Package Cache Cleared"
            is InstallingPythonPackages -> "Installing Python. Don't close the app."
            is InstallingPythonPackagesSuccess -> "Installing Python: Success"
            is FeatureWIP -> "This feature is 'Work In Progress'"
            is FailedDownloadingArchive -> "Failed Downloading Bootstrap binaries. Check Internet."
            is FailedExtractingArchive -> "Failed Extracting Init Archive. Please manually download zip from github"

            is DownloadingInitPackages -> "Downloading Init packages"
            is DownloadedInitPackages -> "Downloading Init packages: Success"
            is InstallingInitPackages -> "Installing Init packages"
            is InstallingInitPackagesSuccess -> "Installing Init packages: Success"
            is ShowCloseSheetInfo -> "Press the back button to close the bottom sheet."
            is UpdatesAvailable -> "Updates are available."
            else -> "An Event Occurred"
        }

    override val type: BannerType
        get() = when (this) {
            is InstallingPythonPackages -> BannerType.Warning
            is InstallingPythonPackagesSuccess -> BannerType.Success
            is UpdatesAvailable -> BannerType.Warning
            else -> BannerType.Info
        }

    override val infinite: Boolean
        get() = when (this) {
            is InstallingPythonPackages -> true
            is FailedDownloadingArchive -> true
            is UpdatesAvailable -> true
            is DownloadingInitPackages -> true
            else -> false
        }
    override val hasAction: Boolean
        get() = when (this) {
            is UpdatesAvailable -> true
            is InstallingPythonPackages -> true
            is DownloadingInitPackages -> true
            else -> false
        }

    override val actionButton: @Composable () -> Unit
        get() = when (this) {
//            is ShowCameraPermissionRationale -> {
//                { ActionButton(ACTION_APPLICATION_DETAILS_SETTINGS, true) }
//            }
//            is LocationPermissionDenied -> {
//                { ActionButton(ACTION_APPLICATION_DETAILS_SETTINGS, true) }
//            }
            is InstallingPythonPackages -> {
                { LoadingIndicator() }
            }

            is DownloadingInitPackages -> {
                { LoadingIndicator() }
            }

            is UpdatesAvailable -> {
                {
                    val uriHandler = LocalUriHandler.current

                    val mainViewModel: MainViewModel by inject(MainViewModel::class.java)


                    NotificationButton("Update") {
                        uriHandler.openUri(this.url)
                        mainViewModel.clearAllBanners()
                    }
                }
            }

            else -> {
                {}
            }
        }

}

@Composable
fun ActionButton(action: String, appendData: Boolean = false) {
    val context = LocalContext.current
    OutlinedButton(
        modifier = Modifier
            .padding(horizontal = 10.dp)
            .fillMaxWidth(),
        onClick = {
            //viewModel.openLocationSettings()
            val i = Intent(action)
            if (appendData) i.data = (Uri.fromParts("package", context.packageName, null))
            context.startActivity(i)
        },
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Color.White,
            containerColor = Color.Black.copy(alpha = 0.06F)

        ),
        border = null,
        contentPadding = PaddingValues(horizontal = 5.dp)
    ) {
        Text("Enable", color = Color.White, fontSize = 13.sp)
    }
}

@Composable
fun NotificationButton(action: String, onClick: () -> Unit) {
    val context = LocalContext.current
    OutlinedButton(
        modifier = Modifier
            .padding(horizontal = 10.dp)
            .fillMaxWidth(),
        onClick = {
            onClick()
        },
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Color.White,
            containerColor = Color.Black.copy(alpha = 0.06F)

        ),
        border = null,
        contentPadding = PaddingValues(horizontal = 5.dp)
    ) {
        Text(action, color = Color.White, fontSize = 13.sp)
    }
}

@Composable
fun LoadingIndicator() {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator(
            color = Color.White,
            modifier = Modifier
                .align(Alignment.Center)
                .size(27.dp)
        )
        //Text("THE")
    }
}

interface Notification {
    val title: String
    val description: String
    val type: BannerType
    val infinite: Boolean
    val hasAction: Boolean
    val actionButton: @Composable () -> Unit
}