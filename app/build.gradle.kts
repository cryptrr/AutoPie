plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    kotlin("plugin.serialization") version "1.9.25"
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
        versionCode = 15
        versionName = "\"0.12.1-beta\""

        testInstrumentationRunner = "com.autosec.pie.AutoPieTestRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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
            isMinifyEnabled = false
            manifestPlaceholders["appIcon"]="@mipmap/ic_launcher"
            manifestPlaceholders["appIconRound"]="@mipmap/ic_launcher_round"
            buildConfigField("String", "VERSION_NAME", "${android.defaultConfig.versionName}")
            buildConfigField("int", "VERSION_CODE", "${android.defaultConfig.versionCode}")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
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

dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.compose.material:material-icons-extended:1.7.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.apache.commons:commons-compress:1.21")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.tukaani:xz:1.9")
    implementation("com.jaredrummler:ktsh:1.0.0")
    implementation("androidx.compose.ui:ui")
    implementation("com.blacksquircle.ui:editorkit:2.0.0")
    implementation("com.blacksquircle.ui:language-shell:2.0.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("io.ktor:ktor-client-core:2.3.7")
    implementation("io.ktor:ktor-client-cio:2.3.7")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.28.0")
    implementation("com.mikepenz:multiplatform-markdown-renderer-coil3:0.28.0")
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")

    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("com.google.accompanist:accompanist-permissions:0.35.1-alpha")
    implementation("io.insert-koin:koin-android:3.5.6")
    implementation("io.insert-koin:koin-androidx-compose:3.5.6")
    implementation("androidx.constraintlayout:constraintlayout-compose:1.1.0")

    //TEST IMPLS

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
}