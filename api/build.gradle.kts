import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    id("kotlinx-serialization")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType(KotlinJvmCompile::class.java).configureEach {
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerializationVersion}")
    implementation("io.ktor:ktor-client-core-jvm:${Versions.ktorVersion}")
}