package com.autopi.use_case

import com.autopi.autopieapp.data.preferences.AutoPieConfigPathProvider
import java.io.File

class GetInstalledPackages(private val autoPieConfigPathProvider: AutoPieConfigPathProvider){
    operator fun invoke(appFilesDir: File) : List<File> {

        val binLocation = File(appFilesDir, "usr/bin").listFiles()
        val usrBinLocation = File(appFilesDir, "build/usr/bin")
        val autosecBinLocation = autoPieConfigPathProvider.getBinDirectory()

        val packages = listOf(
            binLocation?.toList() ?: emptyList(),
            usrBinLocation.listFiles()?.toList() ?: emptyList(),
            autosecBinLocation.listFiles()?.toList() ?: emptyList()
        ).flatten().toSet().filter { !it.name.startsWith(".") }


        return packages.toList()

    }
}
