plugins {
    id("com.android.application")
}

android {
    namespace = "com.hfad.encomiendas"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hfad.encomiendas"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Lee MAPS_API_KEY de gradle.properties (o deja "" si aún no la tienes)
        val mapsKey = (project.findProperty("MAPS_API_KEY") as String?) ?: ""

        // Exponerla a código si la quieres usar con BuildConfig
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsKey\"")

        // Si NO tienes una <meta-data ...> en el manifest, este placeholder NO es necesario.
        // Puedes dejarlo, pero no lo usaremos:
        manifestPlaceholders["MAPS_API_KEY"] = mapsKey
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
        sourceCompatibility = JavaVersion.VERSION_15
        targetCompatibility = JavaVersion.VERSION_15
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    // Core/UI
    implementation("androidx.core:core:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")


    // Navigation (no dupliques)
    implementation("androidx.navigation:navigation-fragment:2.7.7")
    implementation("androidx.navigation:navigation-ui:2.7.7")

    // ✅ SOLO Places (AUTOCOMPLETE). Quita Maps si no usas MapView:
    implementation("com.google.android.libraries.places:places:3.5.0")

    // ---------- ROOM ----------
    implementation("androidx.room:room-runtime:2.6.1")
    annotationProcessor("androidx.room:room-compiler:2.6.1")

    // Tests
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
