plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.serialization)
    alias(libs.plugins.secrets)
}

android {
    compileSdk = 34

    defaultConfig {
        applicationId = "com.zhufucdev.motion_emulator"
        minSdk = 24
        targetSdk = 34
        versionCode = 24
        versionName = "1.2.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    splits {
        abi {
            isEnable = true
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
    }
    namespace = "com.zhufucdev.motion_emulator"
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.6"
    }
    packaging {
        resources {
            excludes += "META-INF/*"
            excludes += "META-INF/licenses/*"
            excludes += "**/attach_hotspot_windows.dll"
        }
    }
}

dependencies {
    // Internal
    implementation(libs.sdk)
    implementation(libs.stub)
    implementation(libs.plugin)
    implementation(libs.update)
    // Ktor
    implementation(libs.ktor.client.jvm)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.serialization.jvm)
    implementation(libs.ktor.client.contentnegotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.serialization.protobuf)
    implementation(libs.ktor.server.jvm)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.contentnegotiation)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.websockets.jvm)
    implementation(libs.madgag.spongycastle)
    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.work.runtime.ktx)
    // KotlinX
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization.json)
    // Compose
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.constraintlayout.compose)
    implementation(libs.androidx.compose.mdi)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.kotlin.reflect)
    implementation(libs.redempt.crunch)
    implementation(libs.google.guava)
    implementation(libs.aventrix.jnanoid)
    implementation(libs.apache.commons.compress)

    // AMap SDK
    implementation(libs.amap.map)
    implementation(libs.amap.search)

    // Google Maps SDK
    implementation(libs.google.maps.ktx)
    implementation(libs.google.maps.utils)
    implementation(libs.google.gms.maps)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

