plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.noapp.container"
    compileSdk = 36

    defaultConfig {
        applicationId = "gift.dhamma.noapp"
        minSdk = 24
        targetSdk = 36
        // The only version to edit. versionName is what people see; versionCode is Play's
        // internal ordering number, which must grow with every upload — derived here so it
        // can't be forgotten or collide: major*10000 + minor*100 + patch (0.5.1 -> 501).
        val appVersion = "0.5.1"
        versionName = appVersion
        versionCode = appVersion.split(".").map { it.toInt() }.let { (major, minor, patch) -> major * 10000 + minor * 100 + patch }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
