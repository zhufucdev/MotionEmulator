// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-serialization:${Versions.kotlinVersion}")
    }

    repositories {
        maven { setUrl("https://redempt.dev") }
    }
}

plugins {
    id("com.android.application") version "8.4.0-alpha03" apply false
    id("com.android.library") version "8.4.0-alpha03" apply false
    id("org.jetbrains.kotlin.jvm") version Versions.kotlinVersion apply false
    id("org.jetbrains.kotlin.android") version Versions.kotlinVersion apply false
    id("com.google.devtools.ksp") version "1.9.0-1.0.12" apply false
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin") version "2.0.1" apply false
}

tasks {
    register("clean", Delete::class) {
        delete(rootProject.layout.buildDirectory)
    }
}

