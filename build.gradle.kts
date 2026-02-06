import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import com.github.jk1.license.LicenseReportExtension
import com.github.jk1.license.render.ReportRenderer
import com.github.jk1.license.render.TextReportRenderer
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
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
    alias(libs.plugins.ksp)
    alias(libs.plugins.dependencyLicenseReport)
    alias(libs.plugins.compose.compiler)
}

val licenseResDir = "$projectDir/build/dependency-license-res"

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

android {
    namespace = "org.kde.kdeconnect_tp"
    compileSdk = 36
    defaultConfig {
        applicationId = "org.kde.kdeconnect_tp"
        minSdk = 23
        targetSdk = 35
        versionCode = 13502
        versionName = "1.35.2"
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
    }

    sourceSets.getByName("main") {
        res.directories += licenseResDir
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11

        // Flag to enable support for the new language APIs
        isCoreLibraryDesugaringEnabled = true
    }

    androidResources {
        generateLocaleConfig = true
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
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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
                        val backportClass = "org/kde/kdeconnect/helpers/CollectionsBackport"

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

ksp {
    arg("com.albertvaka.classindexksp.annotations", "org.kde.kdeconnect.plugins.PluginFactory.LoadablePlugin")
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
    implementation(libs.androidx.compose.material.icons)
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
    implementation(libs.google.android.material)
    implementation(libs.disklrucache) //For caching album art bitmaps. FIXME: Not updated in 10+ years. Replace with Kache.
    implementation(libs.slf4j.api)
    implementation(libs.slf4j.handroid)

    implementation(libs.apache.sshd.core)
    implementation(libs.apache.sshd.sftp)
    implementation(libs.apache.sshd.scp)
    implementation(libs.apache.sshd.mina)
    implementation(libs.apache.mina.core)

    implementation(libs.bcpkix.jdk15on) //For SSL certificate generation

    ksp(libs.classindexksp)

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
    testImplementation(libs.mockk)
    testImplementation(libs.slf4j.simple) // do not try to use the Android logger backend in tests
    testImplementation(libs.jsonassert)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)

    // For device controls
    implementation(libs.reactive.streams)
    implementation(libs.rxjava)
}

licenseReport {
    configurations = LicenseReportExtension.ALL
    renderers = arrayOf<ReportRenderer>(TextReportRenderer())
}

tasks.named("generateLicenseReport") {
    val outputFile = file("$licenseResDir/raw/license")
    val inputFiles = files(
        layout.projectDirectory.file("COPYING"),
        layout.buildDirectory.file("reports/dependency-license/THIRD-PARTY-NOTICES.txt")
    )
    outputs.file(outputFile)
    doLast {
        outputFile.apply {
            parentFile.mkdirs()
            writeText(inputFiles.joinToString(separator = "\n") { it.readText() })
        }
    }
}

tasks.named("preBuild") {
    dependsOn("generateLicenseReport")
}
