package com.autopi.autopieapp.data.apiService

import android.os.Build
import com.autopi.BuildConfig

object AutoPieUserAgent {
    val value: String
        get() = format(BuildConfig.VERSION_NAME, Build.VERSION.RELEASE)

    internal fun format(appVersion: String, androidVersion: String): String =
        "AutoPie/$appVersion Android/$androidVersion"
}
