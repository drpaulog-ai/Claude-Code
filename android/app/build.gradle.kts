plugins {
    id("com.android.application")
}

// O número da compilação no GitHub Actions passa a ser a versão da app
val build = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "pt.pj.capadesfocada"
    compileSdk = 34

    defaultConfig {
        applicationId = "pt.pj.capadesfocada"
        minSdk = 24
        targetSdk = 34
        versionCode = build
        versionName = "1.0.$build"
    }

    // Chave fixa no repositório para que cada nova versão instale por cima da anterior
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.11.0")
}
