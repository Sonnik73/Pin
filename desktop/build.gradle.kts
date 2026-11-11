import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
}

val appVersion = providers.gradleProperty("appVersion").get()
val appVersionCode = providers.gradleProperty("appVersionCode").get().toInt()

// Общий код с телефоном: ядро (engine), сущности, формат .md и архив Pin.zip — те же файлы, без копий в репозитории.
val sharedSources = tasks.register<Sync>("sharedSources") {
    from("$rootDir/app/src/main/java") {
        include("com/sonnik/pin/engine/**")
        include("com/sonnik/pin/data/Entities.kt", "com/sonnik/pin/data/MdFiles.kt", "com/sonnik/pin/data/Archive.kt")
        include("com/sonnik/pin/ui/Palettes.kt", "com/sonnik/pin/ui/Icons.kt", "com/sonnik/pin/ui/MomentCards.kt")
    }
    into(layout.buildDirectory.dir("shared"))
}

// Версия для экрана «О программе» — из gradle.properties, как у телефона.
val versionSource = tasks.register("versionSource") {
    val out = layout.buildDirectory.dir("version")
    inputs.property("version", appVersion)
    outputs.dir(out)
    doLast {
        val f = out.get().file("com/sonnik/pin/desktop/Version.kt").asFile
        f.parentFile.mkdirs()
        f.writeText("package com.sonnik.pin.desktop\n\nconst val APP_VERSION = \"$appVersion\"\n")
    }
}

// «Что нового» в программе — тот же журнал изменений, что у телефона.
tasks.named<ProcessResources>("processResources") {
    from("$rootDir/changelog/changelog_ru.md")
}

kotlin {
    jvmToolchain(17)
    sourceSets.named("main") {
        kotlin.srcDir(sharedSources)
        kotlin.srcDir(versionSource)
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.androidx.room.common)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

compose.desktop {
    application {
        mainClass = "com.sonnik.pin.desktop.MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8")
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Pin"
            // MSI понимает только версии вида X.Y.Z с X > 0: код версии растёт с каждым выпуском.
            packageVersion = "1.0.$appVersionCode"
            // Только латиница: WiX собирает установщик в кодовой странице 1252.
            description = "Pin - tasks, birthdays and plans"
            vendor = "Sonnik"
            modules("java.prefs", "java.desktop", "java.net.http")
            windows {
                // Постоянный код обновления: новая версия ставится поверх старой, а не рядом.
                upgradeUuid = "3251eb79-fbe8-4753-8106-858bf6ff3367"
                menuGroup = "Pin"
                shortcut = true
                menu = true
                perUserInstall = true
                dirChooser = false
                iconFile.set(project.file("icons/pin.ico"))
            }
        }
    }
}
