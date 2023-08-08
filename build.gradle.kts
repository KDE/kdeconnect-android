import com.github.jk1.license.LicenseReportExtension
import com.github.jk1.license.render.ReportRenderer
import com.github.jk1.license.render.TextReportRenderer

buildscript {
    dependencies {
        classpath(libs.android.gradlePlugin)
        classpath(libs.kotlin.gradlePlugin)
    }
}

@Suppress("DSL_SCOPE_VIOLATION")    // TODO: remove once https://youtrack.jetbrains.com/issue/KTIJ-19369 is fixed
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.dependencyLicenseReport)
}

val licenseResDir = File("$projectDir/build/dependency-license-res")

fun String.runCommand(
    workingDir: File = File("."),
    timeoutAmount: Long = 60,
    timeoutUnit: TimeUnit = TimeUnit.SECONDS
): String = ProcessBuilder(split("\\s(?=(?:[^'\"`]*(['\"`])[^'\"`]*\\1)*[^'\"`]*$)".toRegex()))
    .directory(workingDir)
    .redirectOutput(ProcessBuilder.Redirect.PIPE)
    .redirectError(ProcessBuilder.Redirect.PIPE)
    .start()
    .apply { waitFor(timeoutAmount, timeoutUnit) }
    .run {
        val error = errorStream.bufferedReader().readText().trim()
        if (error.isNotEmpty()) {
            throw Exception(error)
        }
        inputStream.bufferedReader().readText().trim()
    }

android {
    namespace = "org.kde.kdeconnect_tp"
    compileSdk = 33
    defaultConfig {
        minSdk = 21
        targetSdk = 32
        proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
    }
    buildFeatures {
        viewBinding = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8

        // Flag to enable support for the new language APIs
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    androidResources {
        generateLocaleConfig = true
    }
    sourceSets {
        getByName("main") {
            manifest.srcFile("AndroidManifest.xml")
            java.setSrcDirs(listOf("src"))
            resources.setSrcDirs(listOf("resources"))
            res.setSrcDirs(listOf(licenseResDir, "res"))
            assets.setSrcDirs(listOf("assets"))
        }
        getByName("test") {
            java.setSrcDirs(listOf("tests"))
        }
    }

    packaging {
        resources {
            merges += listOf("META-INF/DEPENDENCIES", "META-INF/LICENSE", "META-INF/NOTICE")
        }
    }
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
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
        }
        // keep minifyEnabled false above for faster builds; set to 'true'
        // when testing to make sure ProGuard/R8 is not deleting important stuff
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
        }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    testOptions {
        unitTests.all {
            it.jvmArgs = it.jvmArgs.orEmpty() + listOf(
                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "--add-opens=java.base/java.security=ALL-UNNAMED",
                    "--add-opens=java.base/sun.security.rsa=ALL-UNNAMED",
                    "--add-opens=java.base/sun.security.x509=ALL-UNNAMED",
                    "--add-opens=java.base/java.util=ALL-UNNAMED",
                    "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED"
            )
        }
    }

    applicationVariants.all {
        val variant = this
        logger.quiet("Found a variant called ${variant.name}")

        if (variant.buildType.isDebuggable) {
            variant.outputs.all {
                val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
                if (output.outputFile.name.endsWith(".apk")) {
                    // Default output filename is "${project.name}-${v.name}.apk". We want
                    // the Git commit short-hash to be added onto that default filename.
                    try {
                        val hash = "git rev-parse --short HEAD".runCommand(workingDir = rootDir)
                        val newName = "${project.name}-${variant.name}-${hash}.apk"
                        logger.quiet("    Found an output file ${output.outputFile.name}, renaming to ${newName}")
                        output.outputFileName = newName
                    } catch (ignored: Exception) {
                        logger.warn("Could not make use of the 'git' command-line tool. Output filenames will not be customized.")
                    }
                }
            }
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.android.desugarJdkLibs)

    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.accompanist.themeadapter.material3)
    implementation(libs.androidx.constraintlayout.compose)

    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.media)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.extensions)
    implementation(libs.androidx.lifecycle.common.java8)
    implementation(libs.androidx.gridlayout)
    implementation(libs.material)
    implementation(libs.disklrucache) //For caching album art bitmaps

    implementation(libs.apache.sshd.core)
    implementation(libs.apache.mina.core) //For some reason, makes sshd-core:0.14.0 work without NIO, which isn't available until Android 8 (api 26)

    //implementation("com.github.bright:slf4android:0.1.6") { transitive = true } // For org.apache.sshd debugging
    implementation(libs.bcpkix.jdk15on) //For SSL certificate generation

    implementation(libs.classindex)
    annotationProcessor(libs.classindex)

    // The android-smsmms library is the only way I know to handle MMS in Android
    // (Shouldn't a phone OS make phone things easy?)
    // This library was originally authored as com.klinkerapps at https://github.com/klinker41/android-smsmms.
    // However, that version is under-loved. I have therefore made "some fixes" and published it.
    // Please see https://invent.kde.org/sredman/android-smsmms/-/tree/master
    implementation(libs.android.smsmms)
    implementation(libs.logger)

    implementation(libs.commons.io)
    implementation(libs.commons.collections4)
    implementation(libs.commons.lang3)

    implementation(libs.univocity.parsers)

    // Kotlin
    implementation(libs.kotlin.stdlib.jdk8)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.powermock.core)
    testImplementation(libs.powermock.module.junit4)
    testImplementation(libs.powermock.api.mockito2)
    testImplementation(libs.mockito.core) // powermock isn't compatible with mockito 4
    testImplementation(libs.jsonassert)

    // For device controls
    implementation(libs.reactive.streams)
    implementation(libs.rxjava)
}

licenseReport {
    configurations = LicenseReportExtension.ALL
    renderers = arrayOf<ReportRenderer>(TextReportRenderer())
}

tasks.named("generateLicenseReport") {
    doLast {
        val target = File(licenseResDir, "raw/license")
        target.parentFile.mkdirs()
        target.writeText(
            files(
                layout.projectDirectory.file("COPYING"),
                layout.buildDirectory.file("reports/dependency-license/THIRD-PARTY-NOTICES.txt")
            ).joinToString(separator = "\n") {
                it.readText()
            }
        )
    }
}

tasks.named("preBuild") {
    dependsOn("generateLicenseReport")
}
