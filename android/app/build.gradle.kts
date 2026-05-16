plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.daeri.gpsspoofer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.daeri.gpsspoofer"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // local.properties 또는 환경에 따라 다음 값을 채워주세요.
        // Kakao Developers > 내 애플리케이션 > 앱 키 > "네이티브 앱 키" 와 "REST API 키"
        val kakaoNativeKey: String = providers.gradleProperty("KAKAO_NATIVE_APP_KEY").orNull
            ?: project.findProperty("KAKAO_NATIVE_APP_KEY") as String? ?: "PUT_KAKAO_NATIVE_APP_KEY_HERE"
        val kakaoRestKey: String = providers.gradleProperty("KAKAO_REST_API_KEY").orNull
            ?: project.findProperty("KAKAO_REST_API_KEY") as String? ?: "PUT_KAKAO_REST_API_KEY_HERE"

        manifestPlaceholders["KAKAO_NATIVE_APP_KEY"] = kakaoNativeKey
        buildConfigField("String", "KAKAO_REST_API_KEY", "\"$kakaoRestKey\"")
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")

    // Kakao Map SDK (v2)
    implementation("com.kakao.maps.open:android:2.9.5")

    // HTTP client (장소 검색용)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // JSON
    implementation("org.json:json:20240303")
}
