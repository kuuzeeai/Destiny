#!/usr/bin/env bash
# Limpieza del repo + compilación release firmada y fija.
# Ejecutar en la raíz del repo:  bash limpiar.sh
set -euo pipefail

[ -f settings.gradle.kts ] && [ -d app ] || { echo "Ejecuta esto en la raíz del repo (donde está settings.gradle.kts)"; exit 1; }

echo "==> Borrando CI de release, docs, scripts y tests"
rm -rf .github/workflows/release.yml \
       .github/workflows/close-outside-prs.yml \
       .github/workflows/ci.yml \
       .github/release \
       .github/CODEOWNERS \
       docs scripts AGENTS.md

echo "==> Borrando idiomas (se queda inglés y español)"
find . -type d -regextype posix-extended \
  -regex '.*/src/[^/]+/res/values-[a-z]{2}(-r[A-Z]{2})?' ! -name 'values-es' -prune -exec rm -rf {} +

echo "==> Escribiendo workflow de compilación"
cat > .github/workflows/build.yml <<'EOF'
# Compila el APK release (R8 activado) firmado con tu llave fija y lo publica
# siempre en el mismo enlace: releases/download/ultima/destiny.apk
name: Compilar APK

on:
  push:
    branches: [dev]
  workflow_dispatch:

concurrency:
  group: build-${{ github.ref }}
  cancel-in-progress: true

permissions:
  contents: write

jobs:
  build:
    runs-on: ubuntu-latest
    timeout-minutes: 60
    env:
      OC_VERSION_CODE: ${{ github.run_number }}
      GH_TOKEN: ${{ github.token }}
      GH_REPO: ${{ github.repository }}
    steps:
      - uses: actions/checkout@11d5960a326750d5838078e36cf38b85af677262 # v4.4.0
        with:
          persist-credentials: false

      - name: Versión
        run: echo "OC_VERSION_SUFFIX=$(git rev-parse --short=7 HEAD)" >> "$GITHUB_ENV"

      - uses: actions/setup-java@cf277c60eb25467037889841efdb72551f06f6c3 # v4.9.1
        with:
          distribution: temurin
          java-version: 17

      - uses: android-actions/setup-android@9fc6c4e9069bf8d3d10b2204b1fb8f6ef7065407 # v3.2.2
        with:
          packages: platform-tools

      - name: SDK y build-tools
        run: yes | sdkmanager "platforms;android-37.0" "build-tools;37.0.0" > /dev/null

      - uses: gradle/actions/setup-gradle@ed408507eac070d1f99cc633dbcf757c94c7933a # v4.4.3

      - name: Mirror de Maven Central
        run: install -Dm644 .github/gradle/maven-mirror.init.gradle.kts "${GRADLE_USER_HOME:-$HOME/.gradle}/init.d/maven-mirror.init.gradle.kts"

      - name: Restaurar llave de firma
        env:
          KEYSTORE_BASE64: ${{ secrets.KEYSTORE_BASE64 }}
        run: |
          test -n "$KEYSTORE_BASE64" || { echo "Falta el secreto KEYSTORE_BASE64"; exit 1; }
          echo "$KEYSTORE_BASE64" | base64 -d > "$RUNNER_TEMP/destiny.jks"
          echo "KEYSTORE_FILE=$RUNNER_TEMP/destiny.jks" >> "$GITHUB_ENV"

      - name: Compilar release
        env:
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: ./gradlew :app:assembleRelease --stacktrace

      - name: Nombrar APK
        run: cp app/build/outputs/apk/release/app-release.apk destiny.apk

      - uses: actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02 # v4.6.2
        with:
          name: destiny-${{ env.OC_VERSION_SUFFIX }}
          path: destiny.apk
          retention-days: 14

      - name: Publicar en el release "ultima"
        run: |
          title="ultima · ${OC_VERSION_SUFFIX}"
          notes="Build release de ${GITHUB_SHA} (código ${GITHUB_RUN_NUMBER})."
          if gh release view ultima > /dev/null 2>&1; then
            gh api -X PATCH "repos/${GH_REPO}/git/refs/tags/ultima" -f sha="${GITHUB_SHA}" -F force=true > /dev/null
            gh release edit ultima --title "$title" --notes "$notes" --prerelease
            gh release upload ultima destiny.apk --clobber
          else
            gh release create ultima destiny.apk --prerelease --latest=false --target "${GITHUB_SHA}" --title "$title" --notes "$notes"
          fi
EOF

echo "==> Escribiendo app/build.gradle.kts"
cat > app/build.gradle.kts <<'EOF'
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
EOF

echo "==> Escribiendo gradle.properties y README"
cat > gradle.properties <<'EOF'
org.gradle.jvmargs=-Xmx4g -XX:+UseParallelGC -Dfile.encoding=UTF-8
kotlin.daemon.jvmargs=-Xmx2g
org.gradle.workers.max=4
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true
org.gradle.configuration-cache.parallel=true
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
EOF

cat > README.md <<'EOF'
# Destiny (Android)

App Android en Kotlin + Jetpack Compose (base: cliente OpenCode).

## Compilar

Cada `git push` a `dev` compila el APK release (R8 activado, firmado con llave fija) y lo
publica siempre en el mismo enlace:

`https://github.com/<usuario>/<repo>/releases/download/ultima/destiny.apk`

Secretos necesarios en GitHub (Settings → Secrets → Actions):
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

Guarda una copia de `destiny.jks`: sin esa llave no se puede actualizar la app ya instalada.
EOF

echo "==> Listo. Ahora:"
echo "    git add -A && git commit -m 'Limpieza y build release firmado' && git push"
