plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("kotlinx-serialization")
}

android {
    namespace = "com.zhufucdev.update"
    compileSdk = Versions.targetSdk

    defaultConfig {
        minSdk = Versions.minSdk

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures {
        viewBinding = true
        compose = true
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
}

dependencies {
    implementation("androidx.core:core-ktx:${Versions.coreKtVersion}")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:${Versions.materialVersion}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.coroutineVersion}")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerializationVersion}")
    // Ktor
    implementation("io.ktor:ktor-client-core-jvm:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-client-okhttp:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-client-serialization-jvm:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-client-content-negotiation:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-serialization-kotlinx-json:${Versions.ktorVersion}")
    // Compose
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:${Versions.lifecycleRuntimeVersion}")
    implementation("androidx.activity:activity-compose:${Versions.composeActivityVersion}")
    implementation("androidx.compose.ui:ui:${Versions.composeUiVersion}")
    implementation("androidx.compose.ui:ui-tooling-preview:${Versions.composeUiVersion}")
    implementation("androidx.compose.material3:material3:${Versions.composeMaterialVersion}")
    // Internal
    api(project(":api"))

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}