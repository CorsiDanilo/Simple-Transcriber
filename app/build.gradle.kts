import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.ksp)
}

// --- Lettura sicura della API Key da local.properties ---
val localProperties = Properties().apply {
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        load(localPropsFile.inputStream())
    }
}
val geminiApiKey: String = localProperties.getProperty("GEMINI_API_KEY") ?: ""

android {
    namespace = "com.example.simpletranscriberapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.simpletranscriberapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // Espone la chiave API come costante BuildConfig accessibile nel codice Kotlin
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        // Abilita la generazione della classe BuildConfig (necessaria per GEMINI_API_KEY)
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.navigation.compose)

    // Google Generative AI SDK (Gemini)
    implementation(libs.generativeai)

    // On-device AI: ML Kit GenAI Speech Recognition (AICore)
    // TODO: Add back when upgrading Kotlin to 2.2+
    // implementation(libs.mlkit.genai.speech)

    // On-device AI: LiteRT runtime
    implementation(libs.litert)
    implementation("com.google.ai.edge.litertlm:litertlm-android:+")

    debugImplementation(libs.androidx.ui.tooling)
}
