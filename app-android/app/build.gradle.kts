import java.net.URI

plugins {
    alias(libs.plugins.android.application)
}

// This is the portal server root, without /api. Login lives at /auth/login.
val configuredApiUrl = providers.gradleProperty("portalApiUrl")
    .orElse(providers.environmentVariable("PORTAL_API_URL")).orNull?.trim()?.takeIf { it.isNotEmpty() }
fun apiRoot(value: String): String {
    val uri = URI(value)
    require(uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank() &&
        uri.userInfo == null && uri.query == null && uri.fragment == null &&
        !uri.path.orEmpty().trimEnd('/').endsWith("/api")) {
        "portalApiUrl / PORTAL_API_URL must be a server root (http[s]://host[:port]/), without /api, credentials, query or fragment."
    }
    return value.trimEnd('/') + "/"
}
val debugApiUrl = apiRoot(configuredApiUrl ?: "http://127.0.0.1:5125/")

android {
    namespace = "br.ufpr.lab_mobile"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "br.ufpr.lab_mobile"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            resValue("string", "api_base_url", debugApiUrl)
        }
        release {
            resValue("string", "api_base_url", configuredApiUrl?.let(::apiRoot) ?: "https://configure-api-url.invalid/")
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
    buildFeatures {
        buildConfig = true
        resValues = true
        viewBinding = true
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        require(configuredApiUrl != null && URI(apiRoot(configuredApiUrl)).scheme == "https") {
            "Release requires an HTTPS portal root: -PportalApiUrl=https://your-api/ or PORTAL_API_URL."
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
