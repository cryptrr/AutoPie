package com.autosec.pie.use_case

import android.os.Environment
import java.io.File

class GetInstalledPackages(){
    operator fun invoke(appFilesDir: File) : List<File> {

        val binLocation = File(appFilesDir, "build/bin").listFiles()
        val usrBinLocation = File(appFilesDir, "build/usr/bin")
        val autosecBinLocation = File(Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/bin")

        val packages = listOf(
            binLocation?.toList() ?: emptyList(),
            usrBinLocation.listFiles()?.toList() ?: emptyList(),
            autosecBinLocation.listFiles()?.toList() ?: emptyList()
        ).flatten().toSet().filter { !it.name.startsWith(".") }


        return packages.toList()

    }
}