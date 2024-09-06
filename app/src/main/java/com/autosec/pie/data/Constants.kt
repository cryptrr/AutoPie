package com.autosec.pie.data

class AutoPieConstants {
    companion object {
        const val AUTOPIE_INIT_ARCHIVE_URL = "https://github.com/cryptrr/AutoPie/releases/download/v0.08-beta/autopie-init-0.02.tar.xz"
        const val AUTOPIE_SHELL_RESULT_REGEX = "<AUTOPIE_RESULT>(.*?)<AUTOPIE_RESULT/>"
    }
}

class AutoPieStrings {
    companion object {
        const val EXTRAS_DESCRIPTION = "You can define extra command argument variables here. These can be used in the command field like {NAME_OF_EXTRA}. Which will be replaced by its value."
        const val EXTRAS_DESCRIPTION_TO_REPLACE = "NAME_OF_EXTRA"
        const val APP_DEVELOPER = "cryptrr (Amal Shaji)"
        const val GITHUB_URL = "https://github.com/cryptrr/AutoPie"
        const val DONATE_TEXT = "Please consider donating if this app helped you. Or you can star us on Github."
    }
}