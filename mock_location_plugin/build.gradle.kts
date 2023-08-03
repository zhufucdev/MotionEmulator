plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val servers = properties("server.properties")

android {
    namespace = "com.zhufucdev.mock_location_plugin"
    compileSdk = Versions.targetSdk

    defaultConfig {
        applicationId = "com.zhufucdev.mock_location_plugin"
        minSdk = Versions.minSdk
        targetSdk = Versions.targetSdk
        versionCode = 2
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SERVER_URI", servers.getProperty("SERVER_URI", ""))
        buildConfigField("String", "PRODUCT", servers.getProperty("PRODUCT", ""))
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
    composeOptions {
        kotlinCompilerExtensionVersion = Versions.composeCompilerVersion
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        compose = true
    }
}

dependencies {
    // Android stuff
    implementation("androidx.core:core-ktx:${Versions.coreKtVersion}")
    implementation("androidx.appcompat:appcompat:${Versions.appcompatVersion}")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.navigation:navigation-fragment-ktx:${Versions.navVersion}")
    implementation("androidx.navigation:navigation-ui-ktx:${Versions.navVersion}")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:${Versions.lifecycleRuntimeVersion}")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:${Versions.lifecycleRuntimeVersion}")
    implementation("com.google.android.material:material:${Versions.materialVersion}")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:${Versions.lifecycleRuntimeVersion}")
    implementation("androidx.activity:activity-compose:${Versions.composeActivityVersion}")
    implementation("androidx.compose.ui:ui:${Versions.composeUiVersion}")
    implementation("androidx.compose.ui:ui-tooling-preview:${Versions.composeUiVersion}")
    implementation("androidx.compose.material3:material3:${Versions.composeMaterialVersion}")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:${Versions.composeUiVersion}")
    debugImplementation("androidx.compose.ui:ui-tooling:${Versions.composeUiVersion}")
    debugImplementation("androidx.compose.ui:ui-test-manifest:${Versions.composeUiVersion}")
    // Misc
    implementation("com.aventrix.jnanoid:jnanoid:2.0.0")
    implementation(project(":stub"))
    // Internal
    implementation(project(":update"))
    implementation(project(":stub_plugin"))

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}