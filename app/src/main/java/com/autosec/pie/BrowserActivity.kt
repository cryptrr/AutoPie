package com.autosec.pie

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.autopi.autopieapp.data.CommandModel
import com.autopi.autopieapp.data.ScriptFlags
import com.autopi.ui.theme.AutoPieTheme
import com.autopi.use_case.AutoPieUseCases
import com.autopi.utils.Utils
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber

class BrowserActivity : AppCompatActivity() {

    private val useCases: AutoPieUseCases by inject(AutoPieUseCases::class.java)
    private lateinit var webView: WebView

    private var address by mutableStateOf(
        TextFieldValue(DEFAULT_URL, selection = TextRange(DEFAULT_URL.length))
    )
    private var loadingProgress by mutableIntStateOf(0)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val browserCommands = runCatching {
            useCases.getCommandsList()
                .filter { Utils.hasScriptHeader(it.command, ScriptFlags.BROWSER) }
                .sortedBy { it.name.lowercase() }
        }.onFailure { error ->
            Timber.e(error, "Unable to load browser commands")
        }.getOrDefault(emptyList())

        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    loadingProgress = newProgress
                    this@BrowserActivity.title = if (newProgress < 100) {
                        "Loading $newProgress%"
                    } else {
                        view.title ?: "AutoPie Browser"
                    }
                }

                override fun onReceivedTitle(view: WebView, pageTitle: String?) {
                    if (!pageTitle.isNullOrBlank()) this@BrowserActivity.title = pageTitle
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    address = TextFieldValue(url, selection = TextRange(url.length))
                }
            }
        }

        setContent {
            AutoPieTheme {
                BrowserScreen(
                    browserCommands = browserCommands,
                    webView = webView,
                    address = address,
                    loadingProgress = loadingProgress,
                    onAddressChange = { address = it },
                    onNavigate = ::loadUrl,
                    onRunCommand = ::runBrowserCommand
                )
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            loadUrl(sharedUrlFrom(intent) ?: DEFAULT_URL)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedUrlFrom(intent)?.let(::loadUrl)
    }

    private fun loadUrl(input: String) {
        val normalizedUrl = normalizeUrl(input) ?: return
        webView.loadUrl(normalizedUrl)
    }

    private fun normalizeUrl(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        if (
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return trimmed.takeIf { Uri.parse(it).host?.isNotBlank() == true }
                ?: googleSearchUrl(trimmed)
        }

        return if (Patterns.WEB_URL.matcher(trimmed).matches()) {
            "https://$trimmed"
        } else {
            googleSearchUrl(trimmed)
        }
    }

    private fun googleSearchUrl(query: String): String {
        return "$GOOGLE_SEARCH_URL${Uri.encode(query)}"
    }

    private fun sharedUrlFrom(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        if (intent.type?.startsWith("text/") != true) return null

        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        if (sharedText.isEmpty()) return null

        return HTTP_URL.find(sharedText)?.value ?: sharedText
    }

    private fun runBrowserCommand(command: CommandModel) {
        val script = Utils.stripScriptHeaders(command.command)
        webView.evaluateJavascript(script) { result ->
            Timber.d("Browser command %s returned %s", command.name, result)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.webChromeClient = null
        webView.webViewClient = WebViewClient()
        webView.destroy()
        super.onDestroy()
    }

    private companion object {
        const val DEFAULT_URL = "https://google.com"
        const val GOOGLE_SEARCH_URL = "https://www.google.com/search?q="
        val HTTP_URL = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserScreen(
    browserCommands: List<CommandModel>,
    webView: WebView,
    address: TextFieldValue,
    loadingProgress: Int,
    onAddressChange: (TextFieldValue) -> Unit,
    onNavigate: (String) -> Unit,
    onRunCommand: (CommandModel) -> Unit
) {
    var commandMenuExpanded by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold(
        bottomBar = {
            Column(modifier = Modifier.imePadding()) {
                if (loadingProgress in 0..99) {
                    LinearProgressIndicator(
                        progress = { loadingProgress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                BottomAppBar {
                    OutlinedTextField(
                        value = address,
                        onValueChange = onAddressChange,
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    onAddressChange(
                                        address.copy(
                                            selection = TextRange(0, address.text.length)
                                        )
                                    )
                                }
                            },
                        singleLine = true,
                        shape = RoundedCornerShape(15.dp),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        placeholder = { Text("Search or type URL") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                onNavigate(address.text)
                                keyboardController?.hide()
                            }
                        )
                    )

                    Box {
                        IconButton(onClick = { commandMenuExpanded = true }, modifier = Modifier.padding(horizontal = 10.dp)) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = "Browser commands"
                            )
                        }
                        DropdownMenu(
                            expanded = commandMenuExpanded,
                            onDismissRequest = { commandMenuExpanded = false },
                            modifier = Modifier.widthIn(min = 240.dp, max = 340.dp)
                        ) {
                            if (browserCommands.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No browser commands") },
                                    onClick = {},
                                    enabled = false
                                )
                            } else {
                                browserCommands.forEach { command ->
                                    DropdownMenuItem(
                                        text = { Text(command.name) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Terminal,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            commandMenuExpanded = false
                                            onRunCommand(command)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { contentPadding ->
        AndroidView(
            factory = { webView },
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        )
    }
}
