plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val appVersion = rootProject.file("VERSION").readText().trim()

android {
    namespace = "com.drpaulog.senhasporto"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.drpaulog.senhasporto"
        minSdk = 26
        targetSdk = 35
        versionName = appVersion
        versionCode = appVersion.split(".").fold(0) { acc, part -> acc * 100 + part.toInt() }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Assinado com a chave de debug para que o APK de release possa ser instalado diretamente.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // O mesmo extrator de senhas que a versão Windows usa.
    sourceSets["main"].assets.srcDir("../../SenhasPorto/js")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
