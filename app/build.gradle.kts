import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.kkocaerm.otobustakip"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.kkocaerm.otobustakip"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Uygulamanın çalışma zamanı bağımlılığı yok: yalnızca Android çatısı.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
