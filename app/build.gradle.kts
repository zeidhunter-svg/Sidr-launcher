plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.sidr.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sidr.launcher"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
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

kotlin {
    jvmToolchain(17)
}

kapt {
    correctErrorTypes = true
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:ui"))
    implementation(project(":core:android"))
    implementation(project(":domain"))
    implementation(project(":data:ai-cloud"))
    implementation(project(":data:ai-local"))
    implementation(project(":data:repository"))
    implementation(project(":feature:launcher"))
    implementation(project(":feature:assistant"))
    implementation(project(":feature:suggestions"))
    implementation(project(":feature:permission_education"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.navigation.compose)

    implementation(libs.datastore.preferences)
    implementation(libs.room.runtime)

    // Block K: the cloud-AI HttpClient is provided here (composition root) and injected into the
    // :data:ai-cloud engine, which stays Hilt-free. Only the client/engine types cross into :app DI.
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)

    debugImplementation(libs.compose.ui.tooling)
}
