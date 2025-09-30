plugins {
    id("com.android.application")
    // Safe Args es OPCIONAL (solo si quieres clases Directions):
    // id("androidx.navigation.safeargs")
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

        val mapsKey = (project.findProperty("MAPS_API_KEY") as String?) ?: ""
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsKey\"")
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    // --- AndroidX UI ---
    implementation("androidx.core:core:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // --- Navigation (¡clave para los atributos app:startDestination, app:destination, app:argType!) ---
    val nav = "2.7.7"
    implementation("androidx.navigation:navigation-fragment:$nav")
    implementation("androidx.navigation:navigation-ui:$nav")
    // explícito para evitar el error de aapt en algunos setups
    implementation("androidx.navigation:navigation-runtime:$nav")

    // --- Google Maps / Places ---
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.libraries.places:places:3.5.0")

    // --- Room ---
    implementation("androidx.room:room-runtime:2.6.1")
    annotationProcessor("androidx.room:room-compiler:2.6.1")

    // --- Glide ---
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

}
