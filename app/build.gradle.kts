import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Release signing: env vars (CI) win over gitignored keystore.properties (local); neither = unsigned release.
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

fun signingValue(env: String, property: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(property)?.takeIf { it.isNotBlank() }

val keystoreFile = signingValue("ANDROID_KEYSTORE_FILE", "storeFile")?.let(rootProject::file)

// Version name lives in the root VERSION file, bumped by the release PR (.github/workflows/release.yml).
val appVersionName = rootProject.file("VERSION").readText().trim()

/**
 * Packs X.Y.Z[-alpha|beta.N] into a code that rises with every release: 1.4.2-beta.3 = 10402103, 1.4.2 = 10402200.
 * [build] (RIDELINK_BUILD, set by a `!patch` rebuild) fills a stable release's free last two digits.
 */
fun versionCodeOf(name: String, build: Int = System.getenv("RIDELINK_BUILD")?.toIntOrNull() ?: 0): Int {
    val match = Regex("""^(\d+)\.(\d+)\.(\d+)(?:-(alpha|beta)\.(\d+))?$""").matchEntire(name)
        ?: error("VERSION '$name' is not X.Y.Z or X.Y.Z-alpha|beta.N")
    val (major, minor, patch, label, number) = match.destructured
    require(minor.toInt() < 100 && patch.toInt() < 100 && (number.toIntOrNull() ?: 0) < 100) {
        "VERSION '$name' overflows a version code slot"
    }
    require(build in 0..99) { "RIDELINK_BUILD $build overflows a version code slot" }
    val stage = when (label) { "alpha" -> 0; "beta" -> 1; else -> 2 }
    val tail = if (label.isEmpty()) build else number.toInt()
    return major.toInt() * 10_000_000 + minor.toInt() * 100_000 + patch.toInt() * 1_000 + stage * 100 + tail
}

android {
    namespace = "com.aiyu.ridelink"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.aiyu.ridelink"
        minSdk = 26
        targetSdk = 35
        versionCode = versionCodeOf(appVersionName)
        versionName = appVersionName
    }

    buildFeatures {
        compose = true
    }

    signingConfigs {
        if (keystoreFile?.exists() == true) {
            create("release") {
                storeFile = keystoreFile
                storePassword = signingValue("ANDROID_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("ANDROID_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("ANDROID_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":ui"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.service)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
