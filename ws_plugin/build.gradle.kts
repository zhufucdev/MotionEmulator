plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.zhufucdev.ws_plugin"
    compileSdk = Versions.targetSdk

    defaultConfig {
        applicationId = "com.zhufucdev.ws_plugin"
        minSdk = Versions.minSdk
        targetSdk = Versions.targetSdk
        versionCode = 3
        versionName = "1.2.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:${Versions.coreKtVersion}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.coroutineVersion}")
    implementation(project(":stub_plugin_xposed"))
    // Xposed
    ksp("com.highcapable.yukihookapi:ksp-xposed:${Versions.yukiVersion}")
    implementation("com.highcapable.yukihookapi:api:${Versions.yukiVersion}")
    compileOnly("de.robv.android.xposed:api:82")
    // Internal
    implementation(project(":stub"))
    implementation(project(":stub_plugin"))

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}