package com.autopi

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import android.util.Patterns
import android.widget.Toast
import coil3.compose.SubcomposeAsyncImage
import com.autopi.autopieapp.output.*
import com.autopi.ui.theme.AutoPieTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Displays OUTPUT independently of stdout/stderr logs. */
class OutputPresentationActivity : ComponentActivity() {
    private var request by mutableStateOf(Intent())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        request = intent
        setContent { AutoPieTheme { OutputSheet(request, ::finish) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        request = intent
    }

    companion object {
        const val EXTRA_OUTPUT = "output"
        const val EXTRA_OUTPUT_FILE = "outputFile"
        const val EXTRA_COMMAND_NAME = "commandName"

        fun createIntent(context: Context, output: String, title: String = "Output") =
            Intent(context, OutputPresentationActivity::class.java)
                .putExtra(EXTRA_OUTPUT, output).putExtra(EXTRA_COMMAND_NAME, title)

        fun fileIntent(context: Context, file: File, title: String) =
            Intent(context, OutputPresentationActivity::class.java)
                .putExtra(EXTRA_OUTPUT_FILE, file.absolutePath).putExtra(EXTRA_COMMAND_NAME, title)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OutputSheet(request: Intent, onClose: () -> Unit) {
    val context = LocalContext.current
    val result by produceState<OutputElement?>(null, request) {
        value = null
        value = withContext(Dispatchers.IO) {
            runCatching {
                val raw = request.getStringExtra(OutputPresentationActivity.EXTRA_OUTPUT)
                    ?: request.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                    ?: request.getStringExtra(OutputPresentationActivity.EXTRA_OUTPUT_FILE)?.let { path ->
                        val uri = Uri.parse(path)
                        if (uri.scheme == "content") {
                            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                                ?: error("Cannot open output file")
                        } else {
                            File(if (uri.scheme == "file") requireNotNull(uri.path) else path).readText()
                        }
                    }
                parseOutputPresentation(raw)
            }.getOrElse { OutputElement.Text("Unable to read output. The file may have been removed or access is unavailable.") }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(request.getStringExtra(OutputPresentationActivity.EXTRA_COMMAND_NAME) ?: "Output", style = MaterialTheme.typography.titleLarge)
                    Text("Output", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = onClose) { Text("Close") }
            }
            val output = result
            if (output == null) {
                CircularProgressIndicator(Modifier.padding(24.dp))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (output is OutputElement.Items) {
                        output.title?.let { item { Text(it, style = MaterialTheme.typography.titleMedium) } }
                        if (output.values.isEmpty()) item { Text("Empty list") }
                        items(output.values) { OutputCard(it) }
                    } else item { OutputCard(output) }
                }
            }
        }
    }
}

@Composable
private fun OutputCard(element: OutputElement) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutputContent(element)
        }
    }
}

@Composable
private fun OutputContent(element: OutputElement) {
    when (element) {
        is OutputElement.Text -> LinkedOutputText(element)
        is OutputElement.Link -> {
            val context = LocalContext.current
            TextButton(onClick = { openOutputUrl(context, element.url) }) { Text(element.label) }
        }
        is OutputElement.Image -> {
            val source = remember(element.source) {
                if (element.source.startsWith("/")) File(element.source) else element.source
            }
            SubcomposeAsyncImage(
                model = source,
                contentDescription = element.caption ?: "Output image",
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 360.dp),
                loading = { Box(Modifier.padding(24.dp)) { CircularProgressIndicator() } },
                error = { Text("Image unavailable: ${element.source}", Modifier.padding(16.dp)) }
            )
            element.caption?.let { LinkedOutputText(OutputElement.Text(it)) }
        }
        is OutputElement.Items -> {
            element.title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            if (element.values.isEmpty()) Text("Empty list")
            element.values.forEach { OutputCard(it) }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun LinkedOutputText(element: OutputElement.Text) {
    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(element.value, linkColor) {
        buildAnnotatedString {
            append(element.value)
            val matcher = Patterns.WEB_URL.matcher(element.value)
            while (matcher.find()) {
                val url = matcher.group()
                if (isOutputWebUrl(url)) {
                    addStringAnnotation("url", url, matcher.start(), matcher.end())
                    addStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline), matcher.start(), matcher.end())
                }
            }
        }
    }
    SelectionContainer {
        ClickableText(
            text = annotated,
            style = (if (element.numeric) MaterialTheme.typography.displaySmall else MaterialTheme.typography.bodyLarge)
                .copy(color = MaterialTheme.colorScheme.onSurface, fontFamily = if (element.json) FontFamily.Monospace else FontFamily.Default),
            onClick = { offset -> annotated.getStringAnnotations("url", offset, offset).firstOrNull()?.let { openOutputUrl(context, it.item) } }
        )
    }
}

private fun openOutputUrl(context: Context, url: String) {
    if (!isOutputWebUrl(url)) return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        .onFailure { Toast.makeText(context, "No app available to open this link", Toast.LENGTH_SHORT).show() }
}
