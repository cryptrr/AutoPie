package com.autosec.pie

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

class BrowserActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val addressBar = EditText(this).apply {
            hint = "Enter URL"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setSingleLine(true)
            setText(DEFAULT_URL)
        }

        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                addressBar,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            addView(webView)
        }
        setContentView(root)

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                title = if (newProgress < 100) {
                    "Loading $newProgress%"
                } else {
                    view.title ?: "AutoPie Browser"
                }
            }

            override fun onReceivedTitle(view: WebView, pageTitle: String?) {
                if (!pageTitle.isNullOrBlank()) title = pageTitle
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                addressBar.setText(url)
                injectUserScript()
            }
        }

        addressBar.setOnEditorActionListener { _, actionId, event ->
            val enterPressed = event?.let {
                it.keyCode == KeyEvent.KEYCODE_ENTER && it.action == KeyEvent.ACTION_DOWN
            } == true

            if (actionId == EditorInfo.IME_ACTION_GO || enterPressed) {
                loadUrl(addressBar.text.toString())
                addressBar.clearFocus()
                hideKeyboard(addressBar)
                true
            } else {
                false
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
            webView.loadUrl(DEFAULT_URL)
        }
    }

    private fun loadUrl(input: String) {
        val url = normalizeUrl(input) ?: return
        webView.loadUrl(url)
    }

    private fun normalizeUrl(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        return if (
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    private fun injectUserScript() {
        operateOnCurrentDom(
            operation = """
                if (window.__userScriptInjected) return false;

                const body = dom.querySelector("body");
                if (!body) return false;

                window.__userScriptInjected = true;
                body.style.background = "#FF0000";
                body.style.color = "#00FF00";

                if (navigator.clipboard && navigator.clipboard.writeText) {
                    const originalWriteText = navigator.clipboard.writeText.bind(navigator.clipboard);
                    try {
                        navigator.clipboard.writeText = function(text) {
                            console.log("Intercepted clipboard text:", text);
                            return originalWriteText(text);
                        };
                    } catch (error) {
                        console.warn("Unable to intercept clipboard writes", error);
                    }
                }

                if (!dom.querySelector("#autopie-injected-button")) {
                    const button = document.createElement("button");
                    button.id = "autopie-injected-button";
                    button.textContent = "Injected Button";
                    button.style.position = "fixed";
                    button.style.bottom = "20px";
                    button.style.right = "20px";
                    button.style.zIndex = "999999";
                    button.onclick = function() { alert("Hello from injected JS"); };
                    body.appendChild(button);
                }

                return true;
            """.trimIndent()
        )
    }

    /** Runs [operation] against the live DOM of the page currently displayed by the WebView. */
    private fun operateOnCurrentDom(
        operation: String,
        onResult: (String?) -> Unit = {}
    ) {
        val script = """
            (function() {
                const dom = document.documentElement;
                if (!dom) return null;

                return (function(dom) {
                    $operation
                })(dom);
            })();
        """.trimIndent()

        webView.post {
            webView.evaluateJavascript(script) { result -> onResult(result) }
        }
    }

    private fun hideKeyboard(view: View) {
        val inputMethodManager =
            getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
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
        const val DEFAULT_URL = "https://stackoverflow.com"
    }
}
