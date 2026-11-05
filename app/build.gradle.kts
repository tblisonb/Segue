import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release builds are signed with the key in ~/.android/mediasync-release.properties (or the
// properties file -Psegue.signing names): the MediaSync app's key, since Segue and MediaSync share
// a signature-level permission for their integration (see README). Without one, release builds
// are unsigned; debug builds always work.
val releaseSigning = file(
    providers.gradleProperty("segue.signing").orNull ?: providers.gradleProperty("mediasync.signing").orNull
        ?: "${System.getProperty("user.home")}/.android/mediasync-release.properties",
).takeIf { it.isFile }?.let { f -> Properties().apply { f.inputStream().use(::load) } }

android {
    namespace = "com.tlisonbee.segue"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.tlisonbee.segue"
        minSdk = 33
        targetSdk = 36
        versionCode = 10
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = file(releaseSigning.getProperty("storeFile"))
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
            }
        }
    }

    // -Psegue.testBuildType=release runs the instrumented tests against the release build, signed
    // like the installed app, so testing on the phone doesn't mean uninstalling it.
    testBuildType = providers.gradleProperty("segue.testBuildType").getOrElse("debug")

    buildTypes {
        release {
            // Not shrunk: jaudiotagger relies on reflection.
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.jaudiotagger)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.dash)
    implementation(libs.material.color.utilities)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.media3.ffmpeg.decoder)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
    testImplementation(libs.media3.exoplayer)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.junit)
}
