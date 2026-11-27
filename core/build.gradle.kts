import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Google sign-in web client ID: env var (CI secret) wins over gitignored local.properties. Blank = sign-in hidden.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
val webClientId = System.getenv("GOOGLE_WEB_CLIENT_ID")?.takeIf { it.isNotBlank() }
    ?: localProps.getProperty("GOOGLE_WEB_CLIENT_ID")?.trim().orEmpty()

android {
    namespace = "com.aiyu.ridelink.core"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$webClientId\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303") // android.jar stubs org.json on the JVM
}
