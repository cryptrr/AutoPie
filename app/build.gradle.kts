plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    kotlin("plugin.serialization") version "2.2.0"
    alias(libs.plugins.compose.compiler)
    id("com.google.devtools.ksp") version "2.2.0-2.0.2"
    id("androidx.room")
}

android {
    namespace = "com.autosec.pie"
    compileSdk = 35

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.autosec.pie"
        minSdk = 27
        //noinspection EditedTargetSdkVersion,ExpiredTargetSdkVersion
        targetSdk = 28
        versionCode = 21
        versionName = "\"0.14.2-beta\""

        testInstrumentationRunner = "com.autosec.pie.AutoPieTestRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            buildConfigField("String", "VERSION_NAME", "${android.defaultConfig.versionName}")
            buildConfigField("int", "VERSION_CODE", "${android.defaultConfig.versionCode}")
            applicationIdSuffix = ".debug"
            versionNameSuffix = ".debug"
            manifestPlaceholders["appIcon"]="@mipmap/ic_launcher_debug"
            manifestPlaceholders["appIconRound"]="@mipmap/ic_launcher_debug_round"
        }
        release {
            manifestPlaceholders += mapOf()
            isMinifyEnabled = false
            manifestPlaceholders["appIcon"]="@mipmap/ic_launcher"
            manifestPlaceholders["appIconRound"]="@mipmap/ic_launcher_round"
            buildConfigField("String", "VERSION_NAME", "${android.defaultConfig.versionName}")
            buildConfigField("int", "VERSION_CODE", "${android.defaultConfig.versionCode}")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_20
        targetCompatibility = JavaVersion.VERSION_20
    }
    kotlinOptions {
        jvmTarget = "20"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
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

configurations.all {
    resolutionStrategy {
        force("com.google.guava:guava:24.1-jre")
    }
}

dependencies {
    implementation(libs.material)
    implementation(libs.timber)
    implementation(libs.core.ktx)
    implementation(libs.navigation.compose)
    implementation(libs.material.icons.extended)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.gson)
    implementation(libs.commons.compress)
    implementation(libs.datastore.preferences)
    implementation(libs.xz)
    implementation(libs.ktsh)
    implementation(libs.ui)
    implementation(libs.editorkit)
    implementation(libs.language.shell)
    implementation(libs.work.runtime.ktx)
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
    //implementation(project(":termux-shared"))
    implementation(project(":terminal-view"))
    implementation(project(":terminal-emulator"))

    //TEST IMPLS
    implementation("androidx.compose.ui:ui-tooling:1.8.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.16")
    testImplementation ("io.mockk:mockk-agent:1.13.16")
    testImplementation ("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("io.mockk:mockk-android:1.13.16")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation ("io.insert-koin:koin-test:3.5.6")
    testImplementation ("io.insert-koin:koin-test-junit4:3.5.6")
    androidTestImplementation ("io.insert-koin:koin-test:3.5.6")
    androidTestImplementation ("io.insert-koin:koin-test-junit4:3.5.6")
    debugImplementation("androidx.compose.ui:ui-tooling:1.8.3")
}