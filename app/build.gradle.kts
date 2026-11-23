plugins {
    id("opencode.android.application")
}

// Idiomas de la app: inglés (por defecto) y español.
val appLanguages = listOf("en", "es")

android {
    namespace = "ai.opencode.app"
    defaultConfig {
        applicationId = "ai.opencode.mobilecode"
        // CI pone OC_VERSION_CODE = número de ejecución: cada build instala encima de la anterior.
        versionCode = providers.environmentVariable("OC_VERSION_CODE").orNull?.toIntOrNull() ?: 1
        versionName = providers.environmentVariable("OC_VERSION_NAME").orNull
            ?: ("0.1.0" + (providers.environmentVariable("OC_VERSION_SUFFIX").orNull?.let { "-$it" } ?: ""))
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Solo arm64 (todos los celulares actuales): APK mucho más chico, sin librerías x86/arm32.
        ndk { abiFilters += "arm64-v8a" }
    }
    // Llave fija: viene de los secretos de GitHub (ver workflow). Sin ella, firma con la de debug.
    signingConfigs {
        create("fija") {
            providers.environmentVariable("KEYSTORE_FILE").orNull?.let { path ->
                storeFile = file(path)
                storePassword = providers.environmentVariable("KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("KEY_PASSWORD").orNull
            }
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            // R8: recorta código y recursos y optimiza Compose. Es lo que hace la app rápida.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(
                if (providers.environmentVariable("KEYSTORE_FILE").isPresent) "fija" else "debug",
            )
        }
    }
    buildFeatures {
        buildConfig = true
    }
    lint {
        // No frenar la compilación release por avisos de lint.
        checkReleaseBuilds = false
    }
    androidResources {
        localeFilters += appLanguages
        generateLocaleConfig = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":server"))
    implementation(project(":feature:session"))
    implementation(project(":feature:transcript"))
    implementation(project(":feature:composer"))
    implementation(project(":feature:drawer"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:voice"))
    implementation(project(":feature:media"))
    implementation(project(":feature:system"))
    implementation(project(":devtools"))
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.adaptive)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.work.runtime)
    debugImplementation(libs.compose.ui.tooling)
    constraints {
        implementation(libs.androidx.fragment) {
            because("Firebase Messaging's Play services pull in fragment 1.1.0, which Google Play flags as outdated")
        }
    }
}
