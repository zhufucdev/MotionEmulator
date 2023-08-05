plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlinx-serialization")
    id("com.google.devtools.ksp")
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
}

val localProperties = rootProject.properties("local.properties")
val serverProperties = properties("server.properties")

android {
    compileSdk = Versions.targetSdk

    defaultConfig {
        applicationId = "com.zhufucdev.motion_emulator"
        minSdk = Versions.minSdk
        targetSdk = Versions.targetSdk
        versionCode = 23
        versionName = "1.2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "AMAP_WEB_KEY", localProperties.getProperty("amap.web.key"))
        buildConfigField("String", "PRODUCT", serverProperties.getProperty("PRODUCT", ""))
        buildConfigField("String", "SERVER_URI", serverProperties.getProperty("SERVER_URI", ""))
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
    }
    namespace = "com.zhufucdev.motion_emulator"
    composeOptions {
        kotlinCompilerExtensionVersion = Versions.composeCompilerVersion
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
    implementation(project(":stub"))
    implementation(project(":update"))
    // Ktor
    implementation("io.ktor:ktor-client-core-jvm:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-client-okhttp:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-client-serialization-jvm:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-client-content-negotiation:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-serialization-kotlinx-json:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-serialization-kotlinx-protobuf:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-server-core-jvm:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-server-netty-jvm:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-server-content-negotiation:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-server-websockets:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-server-websockets-jvm:${Versions.ktorVersion}")
    implementation("com.madgag.spongycastle:bcpkix-jdk15on:1.58.0.0")
    // AndroidX
    implementation("androidx.core:core-ktx:${Versions.coreKtVersion}")
    implementation("androidx.preference:preference-ktx:1.2.0")
    implementation("androidx.appcompat:appcompat:${Versions.appcompatVersion}")
    implementation("com.google.android.material:material:${Versions.materialVersion}")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.navigation:navigation-fragment-ktx:${Versions.navVersion}")
    implementation("androidx.navigation:navigation-ui-ktx:${Versions.navVersion}")
    implementation("androidx.navigation:navigation-dynamic-features-fragment:${Versions.navVersion}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.coroutineVersion}")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerializationVersion}")
    implementation("androidx.work:work-runtime-ktx:2.8.1")
    // Compose
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:${Versions.lifecycleRuntimeVersion}")
    implementation("androidx.activity:activity-compose:${Versions.composeActivityVersion}")
    implementation("androidx.compose.ui:ui:${Versions.composeUiVersion}")
    implementation("androidx.compose.ui:ui-tooling-preview:${Versions.composeUiVersion}")
    implementation("androidx.compose.material3:material3:${Versions.composeMaterialVersion}")
    implementation("androidx.navigation:navigation-compose:${Versions.navVersion}")
    implementation("androidx.constraintlayout:constraintlayout-compose:1.0.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:${Versions.composeUiVersion}")
    debugImplementation("androidx.compose.ui:ui-tooling:${Versions.composeUiVersion}")
    debugImplementation("androidx.compose.ui:ui-test-manifest:${Versions.composeUiVersion}")

    implementation("org.jetbrains.kotlin:kotlin-reflect:${Versions.kotlinVersion}")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("net.edwardday.serialization:kprefs:0.12.0")
    implementation("com.github.Redempt:Crunch:1.1.2")
    implementation("com.google.guava:guava:31.1-android")
    implementation("com.aventrix.jnanoid:jnanoid:2.0.0")
    implementation("org.apache.commons:commons-compress:1.22")

    // AMap SDK
    implementation("com.amap.api:3dmap:9.5.0")
    implementation("com.amap.api:search:9.5.0")

    // Google Maps SDK
    implementation("com.google.android.gms:play-services-maps:18.1.0")
    implementation("com.google.maps.android:maps-ktx:3.2.1")
    implementation("com.google.maps.android:android-maps-utils:3.4.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation(project(":stub_plugin"))
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(project(":stub_plugin"))
}

