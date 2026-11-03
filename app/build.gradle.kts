plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.david.carpcast"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.david.carpcast"
        minSdk = 24
        targetSdk = 36
        versionCode = 200
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        // Enable core library desugaring to use java.time APIs on lower Android API levels
        isCoreLibraryDesugaringEnabled = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.animation.core.lint)
    // Core library desugaring dependency for java.time support on older devices
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.3")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.osmdroid)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.play.services.location)
    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Keep the headless Android runtime and SDK cache inside the build directory.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    val runtimeHome = layout.buildDirectory.dir("test-home").get().asFile
    val runtimeTmp = layout.buildDirectory.dir("test-tmp").get().asFile
    doFirst { runtimeHome.mkdirs(); runtimeTmp.mkdirs() }
    systemProperty("java.io.tmpdir", runtimeTmp.absolutePath)
    systemProperty("user.home", runtimeHome.absolutePath)
    systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
    maxHeapSize = "2g"
}
