package com.autosec.pie.autopieapp.data.services

import com.google.gson.Gson
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL


data class ReleaseInfo(
    val tag_name: String,
    val name: String,
    val published_at: String,
    val assets: List<Asset>
)

data class Asset(
    val name: String,
    val browser_download_url: String
)

//TODO: Migrate to Ktor
class GithubApiService {

    companion object{

        const val GITHUB_RELEASE_LATEST_URL = "https://api.github.com/repos/cryptrr/AutoPie/releases/latest"

        fun getLatestRelease(): ReleaseInfo? {
            try {
                val url = URL(GITHUB_RELEASE_LATEST_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"

                // Read the response
                val reader = InputStreamReader(connection.inputStream)

                // Parse the response with Gson
                val releaseInfo: ReleaseInfo = Gson().fromJson(reader, ReleaseInfo::class.java)
                reader.close()

                return releaseInfo
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }

        fun getAarch64ApkUrl(releaseInfo: ReleaseInfo): String? {
            //TODO: Needs to change when multiple archs are supported.
            val apkAsset = releaseInfo.assets.find { asset ->
                asset.name.endsWith("aarch64.apk")
            }

            return apkAsset?.browser_download_url
        }

        fun compareVersions(version1: String, version2: String): Int {
            val v1Parts = version1.split("-", limit = 2)
            val v2Parts = version2.split("-", limit = 2)

            val v1Numbers = v1Parts[0].split(".")
            val v2Numbers = v2Parts[0].split(".")

            // Compare the numeric parts (major.minor.patch)
            for (i in 0 until maxOf(v1Numbers.size, v2Numbers.size)) {
                val num1 = v1Numbers.getOrNull(i)?.toIntOrNull() ?: 0
                val num2 = v2Numbers.getOrNull(i)?.toIntOrNull() ?: 0
                if (num1 != num2) return num1.compareTo(num2)
            }

            // Compare the pre-release parts if any (e.g. "beta")
            if (v1Parts.size == 2 && v2Parts.size == 2) {
                return v1Parts[1].compareTo(v2Parts[1])
            } else if (v1Parts.size == 2) {
                return 1  // version1 has a pre-release part, version2 does not
            } else if (v2Parts.size == 2) {
                return -1 // version2 has a pre-release part, version1 does not
            }

            return 0 // Versions are equal
        }
    }




}