plugins {
    id("com.android.application")
    id("kotlin-android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

// frontend/.env 파일을 파싱한다. 시크릿(KAKAO_NATIVE_APP_KEY 등)을 git 에 올리지 않고
// manifestPlaceholders 로 주입하기 위함. 셸 환경변수로도 덮어쓸 수 있도록 폴백을 둔다.
val dotenv: Map<String, String> = run {
    val envFile = rootProject.file("../.env")
    if (!envFile.exists()) {
        emptyMap()
    } else {
        envFile.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val idx = line.indexOf('=')
                if (idx <= 0) null
                else line.substring(0, idx).trim() to line.substring(idx + 1).trim()
            }
            .toMap()
    }
}

fun envOrDefault(key: String, default: String): String =
    System.getenv(key)?.takeIf { it.isNotEmpty() } ?: dotenv[key]?.takeIf { it.isNotEmpty() } ?: default

android {
    namespace = "com.example.frontend"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "com.example.frontend"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName

        manifestPlaceholders["kakaoScheme"] =
            "kakao${envOrDefault("KAKAO_NATIVE_APP_KEY", "YOUR_NATIVE_APP_KEY")}"
    }

    buildTypes {
        release {
            // TODO: Add your own signing config for the release build.
            // Signing with the debug keys for now, so `flutter run --release` works.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

flutter {
    source = "../.."
}
