import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import com.github.jk1.license.LicenseReportExtension
import com.github.jk1.license.render.ReportRenderer
import com.github.jk1.license.render.TextReportRenderer
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.CHECKCAST
import org.objectweb.asm.Opcodes.INVOKESTATIC

buildscript {
    dependencies {
        classpath(libs.android.gradlePlugin)
        classpath(libs.kotlin.gradlePlugin)
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.dependencyLicenseReport)
    alias(libs.plugins.compose.compiler)
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
    compileSdk = 34
    defaultConfig {
        minSdk = 21
        targetSdk = 33
        proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
    }
    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_9
        targetCompatibility = JavaVersion.VERSION_1_9

        // Flag to enable support for the new language APIs
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "9"
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
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
        }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
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
                        logger.quiet("    Found an output file ${output.outputFile.name}, renaming to $newName")
                        output.outputFileName = newName
                    } catch (ignored: Exception) {
                        logger.warn("Could not make use of the 'git' command-line tool. Output filenames will not be customized.")
                    }
                }
            }
        }
    }
}

/**
 * Fix PosixFilePermission class type check issue.
 *
 * It fixed the class cast exception when lib desugar enabled and minSdk < 26.
 */
abstract class FixPosixFilePermissionClassVisitorFactory :
    AsmClassVisitorFactory<FixPosixFilePermissionClassVisitorFactory.Params> {

    override fun createClassVisitor(
        classContext: ClassContext,
        nextClassVisitor: ClassVisitor
    ): ClassVisitor {
        return object : ClassVisitor(instrumentationContext.apiVersion.get(), nextClassVisitor) {
            override fun visitMethod(
                access: Int,
                name: String?,
                descriptor: String?,
                signature: String?,
                exceptions: Array<out String>?
            ): MethodVisitor {
                if (name == "attributesToPermissions") { // org.apache.sshd.sftp.common.SftpHelper.attributesToPermissions
                    return object : MethodVisitor(
                        instrumentationContext.apiVersion.get(),
                        super.visitMethod(access, name, descriptor, signature, exceptions)
                    ) {
                        override fun visitTypeInsn(opcode: Int, type: String?) {
                            // We need to prevent Android Desugar modifying the `PosixFilePermission` classname.
                            //
                            // Android Desugar will replace `CHECKCAST java/nio/file/attribute/PosixFilePermission`
                            // to `CHECKCAST j$/nio/file/attribute/PosixFilePermission`.
                            // We need to replace it with `CHECKCAST java/lang/Enum` to prevent Android Desugar from modifying it.
                            if (opcode == CHECKCAST && type == "java/nio/file/attribute/PosixFilePermission") {
                                println("Bypass PosixFilePermission type check success.")
                                // `Enum` is the superclass of `PosixFilePermission`.
                                // Due to `Object` is not the superclass of `Enum`, we need to use `Enum` instead of `Object`.
                                super.visitTypeInsn(opcode, "java/lang/Enum")
                            } else {
                                super.visitTypeInsn(opcode, type)
                            }
                        }
                    }
                }
                return super.visitMethod(access, name, descriptor, signature, exceptions)
            }
        }
    }

    override fun isInstrumentable(classData: ClassData): Boolean {
        return (classData.className == "org.apache.sshd.sftp.common.SftpHelper").also {
            if (it) println("SftpHelper Found! Instrumenting...")
        }
    }

    interface Params : InstrumentationParameters
}

/**
 * Collections.unmodifiableXXX is not exist when Android API level is lower than 26.
 * So we replace the call to Collections.unmodifiableXXX with the original collection by removing the call.
 */
abstract class FixCollectionsClassVisitorFactory :
    AsmClassVisitorFactory<FixCollectionsClassVisitorFactory.Params> {
    override fun createClassVisitor(
        classContext: ClassContext,
        nextClassVisitor: ClassVisitor
    ): ClassVisitor {
        return object : ClassVisitor(instrumentationContext.apiVersion.get(), nextClassVisitor) {
            override fun visitMethod(
                access: Int,
                name: String?,
                descriptor: String?,
                signature: String?,
                exceptions: Array<out String>?
            ): MethodVisitor {
                return object : MethodVisitor(
                    instrumentationContext.apiVersion.get(),
                    super.visitMethod(access, name, descriptor, signature, exceptions)
                ) {
                    override fun visitMethodInsn(
                        opcode: Int,
                        type: String?,
                        name: String?,
                        descriptor: String?,
                        isInterface: Boolean
                    ) {
                        val backportClass = "org/kde/kdeconnect/Helpers/CollectionsBackport"

                        if (opcode == INVOKESTATIC && type == "java/util/Collections") {
                            val replaceRules = mapOf(
                                "unmodifiableNavigableSet" to "(Ljava/util/NavigableSet;)Ljava/util/NavigableSet;",
                                "unmodifiableSet" to "(Ljava/util/Set;)Ljava/util/Set;",
                                "unmodifiableNavigableMap" to "(Ljava/util/NavigableMap;)Ljava/util/NavigableMap;",
                                "emptyNavigableMap" to "()Ljava/util/NavigableMap;")
                            if (name in replaceRules && descriptor == replaceRules[name]) {
                                super.visitMethodInsn(opcode, backportClass, name, descriptor, isInterface)
                                val calleeClass = classContext.currentClassData.className
                                println("Replace Collections.$name call with CollectionsBackport.$name from $calleeClass success.")
                                return
                            }
                        }
                        super.visitMethodInsn(opcode, type, name, descriptor, isInterface)
                    }
                }
            }
        }
    }

    override fun isInstrumentable(classData: ClassData): Boolean {
        return classData.className.startsWith("org.apache.sshd") // We only need to fix the Apache SSHD library
    }

    interface Params : InstrumentationParameters
}

androidComponents {
    onVariants { variant ->
        variant.instrumentation.transformClassesWith(
            FixPosixFilePermissionClassVisitorFactory::class.java,
            InstrumentationScope.ALL
        ) { }
        variant.instrumentation.transformClassesWith(
            FixCollectionsClassVisitorFactory::class.java,
            InstrumentationScope.ALL
        ) { }
    }
}

dependencies {
    // It has a bug that causes a crash when using PosixFilePermission and minSdk < 26.
    // It has been used in SSHD Core.
    // We have taken a workaround to fix it.
    // See `FixPosixFilePermissionClassVisitorFactory` for more details.
    coreLibraryDesugaring(libs.android.desugarJdkLibsNio)

    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
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
    implementation(libs.slf4j.handroid)

    implementation(libs.apache.sshd.core)
    implementation(libs.apache.sshd.sftp)
    implementation(libs.apache.sshd.scp)
    implementation(libs.apache.sshd.mina)
    implementation(libs.apache.mina.core)

    //implementation("com.github.bright:slf4android:0.1.6") { transitive = true } // For org.apache.sshd debugging
    implementation(libs.bcpkix.jdk15on) //For SSL certificate generation

    implementation(libs.classindex)
    kapt(libs.classindex)

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
    testImplementation(libs.mockito.core)
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
