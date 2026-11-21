plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Existing installations need these outside the bootstrap ZIP: upgrading the APK
// preserves the installed prefix and does not extract a new bootstrap.
val dpkgRepairAssets = layout.buildDirectory.dir("generated/assets/dpkgRepair")
val bootstrapAsset = layout.projectDirectory.file("src/main/assets/bootstrap-aarch64.zip")
val prepareDpkgRepairAssets by tasks.registering(Sync::class) {
    from(rootProject.layout.projectDirectory.dir("scripts/bootstrap")) {
        include("dpkg_setup.py", "dpkg.py")
        into("autopie-dpkg")
    }
    from({
        if (bootstrapAsset.asFile.isFile) zipTree(bootstrapAsset.asFile) else emptyList<File>()
    }) {
        include("bin/am", "libexec/termux-am/am.apk")
        eachFile { path = "autopie-dpkg/$name" }
        filePermissions { unix("0644") }
        includeEmptyDirs = false
    }
    into(dpkgRepairAssets)

    doFirst {
        // Older builds copied am.apk with the bootstrap's read-only mode.
        val previousAmApk = destinationDir.resolve("autopie-dpkg/am.apk")
        if (previousAmApk.isFile && !previousAmApk.canWrite()) {
            check(previousAmApk.setWritable(true)) {
                "Could not make generated asset writable: $previousAmApk"
            }
        }
    }
}

tasks.named("preBuild") {
    dependsOn(prepareDpkgRepairAssets)
}

android {
    namespace = "com.autopi"
    compileSdk = 35

    sourceSets.getByName("main").assets.srcDir(dpkgRepairAssets)

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.autopi"
        minSdk = 27
        //noinspection EditedTargetSdkVersion,ExpiredTargetSdkVersion
        targetSdk = 28
        versionCode = 84
        versionName = "0.21.0-beta"

        testInstrumentationRunner = "com.autopi.AutoPieTestRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            //applicationIdSuffix = ".debug"
            //versionNameSuffix = ".debug"
            manifestPlaceholders["appIcon"]="@mipmap/ic_launcher_debug"
            manifestPlaceholders["appIconRound"]="@mipmap/ic_launcher_debug_round"
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            manifestPlaceholders += mapOf()
            isMinifyEnabled = false
            optimization {
                baselineProfile {
                    ignoreFromAllExternalDependencies = true
                }
            }
            manifestPlaceholders["appIcon"]="@mipmap/ic_launcher"
            manifestPlaceholders["appIconRound"]="@mipmap/ic_launcher_round"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            //signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true

        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes +="META-INF/LICENSE.md"
            excludes +="META-INF/LICENSE-notice.md"
            excludes +="META-INF/AL2.0"
            excludes +="META-INF/LGPL2.1"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.material)
    implementation(libs.timber)
    implementation(libs.core.ktx)
    implementation(libs.navigation.compose)
    implementation(libs.material.icons.extended)
    implementation(libs.mmkv)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.gson)
    implementation(libs.glance.appwidget)
    implementation(libs.commons.compress)
    implementation(libs.snakeyaml)
    implementation(libs.datastore.preferences)
    implementation(libs.xz)
    implementation(libs.ktsh)
    implementation(libs.ui)
    implementation(libs.editorkit)
    implementation(libs.language.shell)
    implementation(libs.language.json)
    implementation(libs.work.runtime.ktx)
    implementation(libs.webkit)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.multiplatform.markdown.renderer.m3)
    implementation(libs.multiplatform.markdown.renderer.coil3)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.material3)
    implementation(libs.accompanist.permissions)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.constraintlayout.compose)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(project(":termux-app"))
    implementation(project(":termux-shared"))
    implementation(project(":terminal-view"))
    implementation(project(":terminal-emulator"))

    implementation(libs.appcompat)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Keep runtime and test coroutines aligned with the established resolved version.
    implementation(platform(libs.coroutines.bom))
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.mockk.agent)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit4)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.ui.test.junit4)
    androidTestImplementation(libs.koin.test)
    androidTestImplementation(libs.koin.test.junit4)

    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
}

val signingStoreFile = providers.environmentVariable("AUTOPIE_SIGNING_STORE_FILE")
val signingStorePassword = providers.environmentVariable("AUTOPIE_SIGNING_STORE_PASSWORD")
val signingKeyAlias = providers.environmentVariable("AUTOPIE_SIGNING_KEY_ALIAS")
val signingKeyPassword = providers.environmentVariable("AUTOPIE_SIGNING_KEY_PASSWORD")
    .orElse(signingStorePassword)
val apkSignerExecutable = androidComponents.sdkComponents.sdkDirectory.map { sdkDirectory ->
    val executableName = if (System.getProperty("os.name").startsWith("Windows")) {
        "apksigner.bat"
    } else {
        "apksigner"
    }
    sdkDirectory.file("build-tools/${android.buildToolsVersion}/$executableName")
}

tasks.register<Exec>("signReleaseApk") {
    group = "build"
    description = "Signs an existing unsigned release APK using the AutoPie signing environment variables."

    val unsignedApk = layout.buildDirectory.file("outputs/apk/release/app-release-unsigned.apk")
    val signedApk = layout.buildDirectory.file("outputs/apk/release/app-release.apk")

    inputs.file(unsignedApk)
    inputs.file(signingStoreFile).optional().withPropertyName("signingStoreFile")
    inputs.property("signingKeyAlias", signingKeyAlias.orElse(""))
    outputs.file(signedApk)

    doFirst {
        fun requiredVariable(name: String, value: String?): String {
            return value?.takeIf { it.isNotBlank() }
                ?: throw GradleException("Missing signing environment variable: $name")
        }

        val storeFile = requiredVariable("AUTOPIE_SIGNING_STORE_FILE", signingStoreFile.orNull)
        val storePassword = requiredVariable("AUTOPIE_SIGNING_STORE_PASSWORD", signingStorePassword.orNull)
        val keyAlias = requiredVariable("AUTOPIE_SIGNING_KEY_ALIAS", signingKeyAlias.orNull)
        val keyPassword = requiredVariable("AUTOPIE_SIGNING_KEY_PASSWORD", signingKeyPassword.orNull)
        val apkSigner = apkSignerExecutable.get().asFile
        if (!apkSigner.isFile) {
            throw GradleException("apksigner was not found at ${apkSigner.absolutePath}")
        }

        environment("AUTOPIE_APKSIGNER_STORE_PASSWORD", storePassword)
        environment("AUTOPIE_APKSIGNER_KEY_PASSWORD", keyPassword)
        commandLine(
            apkSigner.absolutePath,
            "sign",
            "--ks", storeFile,
            "--ks-key-alias", keyAlias,
            "--ks-pass", "env:AUTOPIE_APKSIGNER_STORE_PASSWORD",
            "--key-pass", "env:AUTOPIE_APKSIGNER_KEY_PASSWORD",
            "--out", signedApk.get().asFile.absolutePath,
            unsignedApk.get().asFile.absolutePath,
        )
    }
}
