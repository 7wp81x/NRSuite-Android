import java.io.FileInputStream
import java.util.Properties

val keystorePropertiesFile = rootProject.file("keystore.properties")
val hasReleaseSigning = keystorePropertiesFile.exists()
val keystoreProperties = Properties().apply {
    if (hasReleaseSigning) {
        FileInputStream(keystorePropertiesFile).use { load(it) }
    }
}

// A/B switch for external UART-bridge bulk I/O:
//   native (default) = libusb fd handoff used by the beta.3.1.x hotfix
//   java             = usb-serial-for-android UsbDeviceConnection bulk path
// Override for a test build with: -Pnrsuite.uartBulkBackend=java
val uartBulkBackend = providers.gradleProperty("nrsuite.uartBulkBackend")
    .orElse("native")
    .get()
    .lowercase()
require(uartBulkBackend == "native" || uartBulkBackend == "java") {
    "nrsuite.uartBulkBackend must be 'native' or 'java', was '$uartBulkBackend'"
}
val usbVerboseIo = providers.gradleProperty("nrsuite.usbVerboseIo")
    .orElse("false")
    .get()
    .toBooleanStrictOrNull() ?: false

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.swp81x.nrsuite"
    compileSdk {
        version = release(36)
    }
    ndkVersion = "30.0.16248370"

    defaultConfig {
        applicationId = "com.swp81x.nrsuite"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "1.0.0-rc.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "UART_BULK_BACKEND", "\"$uartBulkBackend\"")
        buildConfigField("boolean", "USB_VERBOSE_IO", usbVerboseIo.toString())

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        externalNativeBuild {
            ndkBuild {
                arguments += "APP_PLATFORM=android-26"
            }
        }
    }

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/cpp/Android.mk")
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                null
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        buildConfig = true
        compose = true
        aidl = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.zxing.core)
    implementation(libs.ssh.jsch)
    implementation(libs.bouncycastle.bcprov)
    implementation(libs.usb.serial) {
        // The JitPack POM asks for Kotlin stdlib 2.2.x. This project uses Kotlin 2.0.21;
        // the serial library itself is Java, so exclude the transitive stdlib.
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    testImplementation(libs.junit)
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}