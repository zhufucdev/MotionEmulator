plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.zhufucdev.cp_plugin"
    compileSdk = Versions.targetSdk

    defaultConfig {
        applicationId = "com.zhufucdev.cp_plugin"
        minSdk = Versions.minSdk
        targetSdk = Versions.targetSdk
        versionCode = 2
        versionName = "1.2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:${Versions.coreKtVersion}")
    implementation("io.ktor:ktor-client-serialization-jvm:${Versions.ktorVersion}")
    implementation("org.jetbrains.kotlin:kotlin-reflect:${Versions.kotlinVersion}")
    implementation("androidx.work:work-runtime-ktx:${Versions.workRuntimeVersion}")
    // Xposed
    ksp("com.highcapable.yukihookapi:ksp-xposed:${Versions.yukiVersion}")
    implementation("com.highcapable.yukihookapi:api:${Versions.yukiVersion}")
    compileOnly("de.robv.android.xposed:api:82")
    // Internal
    implementation("com.zhufucdev.me:stub:${Versions.stubVersion}")
    implementation("com.zhufucdev.me:plugin:${Versions.stubVersion}")
    implementation("com.zhufucdev.me:xposed:${Versions.stubVersion}")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}