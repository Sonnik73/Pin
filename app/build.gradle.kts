import java.time.Duration
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Версия и код версии задаются в gradle.properties.
val appVersion = providers.gradleProperty("appVersion").get()
val appVersionCode = providers.gradleProperty("appVersionCode").get().toInt()

// Ключ подписи — тот же, что у SonnikToPower и Крепости (секреты GitHub Actions).
// Только с общим ключом Крепость получает разрешение читать данные задач.
val signingStoreFile = System.getenv("SIGNING_STORE_FILE")

android {
    namespace = "com.sonnik.pin"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sonnik.pin"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersion
    }

    signingConfigs {
        if (signingStoreFile != null) {
            create("sonnik") {
                storeFile = file(signingStoreFile)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Без секретов релиз подписывается отладочным ключом, чтобы APK можно было поставить.
            signingConfig = if (signingStoreFile != null) {
                signingConfigs.getByName("sonnik")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    // Журнал изменений (changelog/*.md) встраивается в приложение — экран «История версий».
    sourceSets.getByName("main").assets.srcDir("$rootDir/changelog")

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime.ktx)
    // Только для входа в Google Диск (Authorization API, область drive.file).
    implementation(libs.play.services.auth)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
}

tasks.withType<Test>().configureEach {
    timeout.set(Duration.ofMinutes(8))
    // Скриншоты — часть входов: иначе прогон без них попадёт в кэш, и ручная сборка скриншотов не перерисует.
    inputs.property("screenshots", System.getenv("SCREENSHOTS") ?: "")
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = TestExceptionFormat.FULL
    }
}
