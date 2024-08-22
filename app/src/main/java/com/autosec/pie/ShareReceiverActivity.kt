package com.autosec.pie

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.autosec.pie.data.ShareItemModel
import com.autosec.pie.elements.AutoPieLogo
import com.autosec.pie.ui.theme.AutoPieTheme
import com.autosec.pie.viewModels.ShareReceiverViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber


class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {


//        WindowCompat.setDecorFitsSystemWindows(window, false)
//
//        if (Build.VERSION.SDK_INT in 21..29) {
//            window.statusBarColor = TRANSPARENT
//            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
//            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
//            window.decorView.systemUiVisibility =
//                SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or SYSTEM_UI_FLAG_LAYOUT_STABLE
//
//        } else if (Build.VERSION.SDK_INT >= 30) {
//            window.statusBarColor = TRANSPARENT
//            // Making status bar overlaps with the activity
//            WindowCompat.setDecorFitsSystemWindows(window, false)
//        }
//
//
//        window.statusBarColor = Color.Transparent.toArgb()
//        window.navigationBarColor = Color.Transparent.toArgb()
//
//
//
//        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
//        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = true


        super.onCreate(savedInstanceState)
        //requestWindowFeature(Window.FEATURE_NO_TITLE)
        //window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT));

        Timber.d("What is this")
        Timber.d(this.intent.toString())


        val data = intent?.getStringExtra(Intent.EXTRA_TEXT)


        val files = mutableListOf<String>()


//        val intent = intent
//        val action = intent.action
//        val type = intent.type
//        val scheme = intent.scheme
//
//        if (Intent.ACTION_SEND == action && type != null) {
//            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
//            val sharedUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
//            val sharedPath = intent.getParcelableExtra<Uri>("extra_file_uris")
//
//            Timber.d("sharedText : $sharedText")
//            Timber.d("sharedUri : $sharedUri")
//            Timber.d("sharedPath : $sharedPath")
//
//
//            if (sharedText != null) {
//                if (isValidUrl(sharedText)) {
//                    //handleUrlAndFinish(sharedText)
//                } else {
//                    //handleDirectText(sharedText)
//                }
//            } else if (sharedUri != null) {
//                if (sharedPath != null) {
//                    //removing starting "file://" from path
//                    val newPath = sharedPath.path
//                    //handleRealPath(newPath)
//                } else if (sharedUri.path != null) {
////                    val file: DocumentFile = DocumentFile.fromSingleUri(context, sharedUri)
////                    if (file == null) {
////                        showErrorDialogAndQuit("Unable to receive any file or URL.")
////                    }
////                    val abs_path: String = DocumentFileUtils.getAbsolutePath(file, context)
////
////                    Log.d("absolute path", abs_path)
////                    handleRealPath(abs_path)
//                } else {
//                    //handleContentUri(sharedUri, intent.getStringExtra(Intent.EXTRA_TITLE))
//                }
//            } else {
//                //showErrorDialogAndQuit("Send action without content - nothing to save.")
//                Toast.makeText(this, "Send action without content - nothing to save.", Toast.LENGTH_LONG).show()
//            }
//        } else if (Intent.ACTION_SEND_MULTIPLE == action && type != null) {
//            val sharedPaths = intent.getParcelableArrayListExtra<Uri>("extra_file_uris")
//
//            Timber.d(sharedPaths.toString())
//            if (sharedPaths != null) {
//                //handleMultipleFiles(sharedPaths)
//            } else {
//                //showErrorDialogAndQuit("Unable to receive files")
//
//                Toast.makeText(this, "Unable to receive files", Toast.LENGTH_LONG).show()
//
//            }
//            //sendArrayToIntent();
//        } else if ("content" == scheme) {
//            val sharedUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
//
//            val realPath = intent.getStringExtra("real_path")
//            Timber.d("Real Path : $realPath")
//
//            if (realPath != null) {
//                //handleRealPath(realPath)
//            } else if (sharedUri!!.path != null) {
//                //handleRealPath(sharedUri.path)
//            } else {
//                //handleContentUri(intent.data, intent.getStringExtra(Intent.EXTRA_TITLE))
//            }
//        } else if ("file" == scheme) {
//            // When e.g. clicking on a downloaded apk:
//            val path = intent.data!!.path
//            val file = File(path)
//            try {
//                val `in` = FileInputStream(file)
//                //promptNameAndSave(`in`, file.name)
//            } catch (e: FileNotFoundException) {
//                //showErrorDialogAndQuit("Cannot open file: " + e.message + ".")
//
//                Toast.makeText(this, "Cannot open file: " + e.message + ".", Toast.LENGTH_LONG).show()
//
//            }
//        } else {
//            //showErrorDialogAndQuit("Unable to receive any file or URL.")
//            Toast.makeText(this, "Unable to receive any file or URL.", Toast.LENGTH_LONG).show()
//
//        }












        when {
            Intent.ACTION_SEND_MULTIPLE == intent?.action -> {

                val sharedPaths = intent.getParcelableArrayListExtra<Uri>("extra_file_uris")

                Timber.d(sharedPaths.toString())

                sharedPaths?.map {
                    it.path.let{
                        files.add(it!!)
                    }
                }

//                if (intent.type!!.startsWith("image/")) {
//                    val clipData: ClipData? = intent.clipData
//                    if (clipData != null) {
//                        val itemCount: Int = clipData.itemCount
//                        for (i in 0 until itemCount) {
//                            val item: ClipData.Item = clipData.getItemAt(i)
//                            val imageUri: Uri? = item.uri
//                            if (imageUri != null) {
//                                files.add(imageUri)
//                            }
//                        }
//                    }
//                }

            }
            Intent.ACTION_SEND == intent?.action -> {

                val sharedPath = intent.getParcelableExtra<Uri>("extra_file_uris")

                Timber.d(sharedPath.toString())

                sharedPath?.path.let{
                    if (it != null) {
                        files.add(it)
                    }
                }
                
            }

        }

        Timber.d(files.toString())
        Timber.d(data.toString())




        setContent {

            AutoPieTheme {

                ShareContextMenuBottomSheet(currentLink = data, fileUris = files)

            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareContextMenuBottomSheet(
    currentLink: String?,
    fileUris: List<String>,
    onHide: () -> Unit = {},
    onExpand: () -> Unit = {}
) {

    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val scope = rememberCoroutineScope()

    val activity = (LocalContext.current as? Activity)

    val shareReceiverViewModel: ShareReceiverViewModel by inject(ShareReceiverViewModel::class.java)


    @Composable
    fun bottomSheetContent() {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                //.height(700.dp)
                .fillMaxHeight(0.75F)
            ,
            contentAlignment = Alignment.TopStart
            //TODO: Expect actual implement
            //.windowInsetsPadding(WindowInsets.navigationBars)

        )
        {


            Column(
                Modifier
                    .fillMaxSize()
                   ){

                LazyColumn(verticalArrangement = Arrangement.spacedBy(15.dp), contentPadding = PaddingValues(10.dp)){
                    item {
                        AutoPieLogo()
                        Spacer(modifier = Modifier.height(15.dp))
                    }
                    items(shareReceiverViewModel.shareItemsResult){ item ->
                        ShareCard(card = item, currentLink, fileUris)
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
                state.hide()
                activity?.finish()
            }
        }
    )
}

@Composable
fun ShareCard(
    card: ShareItemModel,
    currentLink: String?,
    fileUris: List<String>
) {

    val activity = (LocalContext.current as? Activity)
    var isLoading by remember {
        mutableStateOf(false)
    }

    val shareReceiverViewModel: ShareReceiverViewModel by inject(ShareReceiverViewModel::class.java)

    ElevatedCard(onClick = {
        shareReceiverViewModel.viewModelScope.launch {
            shareReceiverViewModel.runShareCommand(card, currentLink, fileUris)
            isLoading = true
            //delay(1500)
            //activity?.finish()
        }

    },

        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp), shape = RoundedCornerShape(15.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.Black.copy(alpha = 0.15F))) {

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center){
            if(isLoading){
                CircularProgressIndicator(strokeWidth = 2.dp)
            }else{
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(15.dp), verticalArrangement = Arrangement.Center){
                    Text(text = card.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = "${card.exec.split("/").last()} ${card.command}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

