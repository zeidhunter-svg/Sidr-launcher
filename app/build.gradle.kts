plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
    alias(libs.plugins.baselineprofile)
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

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
    implementation(project(":data:prayer"))
    implementation(project(":feature:launcher"))
    implementation(project(":feature:assistant"))
    implementation(project(":feature:suggestions"))
    implementation(project(":feature:permission_education"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:prayer"))

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

    // Block Q: WorkManager (one-shot model download) + Hilt @HiltWorker support. The worker shell,
    // HiltWorkerFactory and Configuration.Provider live here (composition root, already kapt+Hilt);
    // the correctness-critical provisioning logic stays in :data:ai-local (ModelProvisioner).
    implementation(libs.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    kapt(libs.androidx.hilt.compiler)

    implementation(libs.datastore.preferences)
    implementation(libs.room.runtime)

    // Block K: the cloud-AI HttpClient is provided here (composition root) and injected into the
    // :data:ai-cloud engine, which stays Hilt-free. Only the client/engine types cross into :app DI.
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit4)
    testImplementation(libs.coroutines.test)

    baselineProfile(project(":baselineprofile"))

    debugImplementation(libs.compose.ui.tooling)
}

baselineProfile {
    mergeIntoMain = true
}
