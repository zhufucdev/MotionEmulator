plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.zhufucdev.stub_plugin"
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // Internal
    implementation(project(":stub"))
    // Ktor
    implementation("io.ktor:ktor-client-core-jvm:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-client-okhttp:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-client-serialization-jvm:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-client-content-negotiation:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-serialization-kotlinx-protobuf:${Versions.ktorVersion}")
    // Android stuff
    implementation("androidx.core:core-ktx:${Versions.coreKtVersion}")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}