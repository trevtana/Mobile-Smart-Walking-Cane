plugins {
    alias(libs.plugins.androidApplication)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.arkanardiansyah.smartwalkingcane"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.arkanardiansyah.smartwalkingcane"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildFeatures {
        viewBinding = true
    }

    // Updated packaging block with exclusions
    packaging {
        resources.excludes.add("META-INF/INDEX.LIST")
        resources.excludes.add("META-INF/*.kotlin_module")
        resources.excludes.add("META-INF/io.netty.versions.properties") // Exclude netty versions properties file
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // --- TAMBAHAN YANG DIPERLUKAN UNTUK VIEWMODEL & LIVEDATA ---
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0") // Atau versi stabil terbaru
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")  // Atau versi stabil terbaru
    // Opsional, tapi seringkali berguna dengan arsitektur modern:
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-common-java8:2.7.0") // Jika Anda menggunakan fitur Java 8 dengan Lifecycle
    // --- AKHIR TAMBAHAN ---

    implementation("com.google.firebase:firebase-database:20.3.0") // Anda punya versi 20.3.0 dan 20.3.1 (via BOM?) -> pilih satu atau biarkan BOM yg atur
    implementation ("com.google.firebase:firebase-storage:20.3.0")
    implementation(platform("com.google.firebase:firebase-bom:33.12.0")) // Perhatikan versi BOM
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")

    // Duplikasi Appcompat & Material, biasanya salah satu cara sudah cukup (libs. atau string eksplisit)
    // Jika libs.appcompat sudah merujuk ke versi yang benar, baris di bawah ini bisa jadi tidak perlu
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    // Duplikasi ConstraintLayout
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    implementation ("com.github.bumptech.glide:glide:4.12.0") // Versi ini agak lama, pertimbangkan update ke 4.16.0
    annotationProcessor ("com.github.bumptech.glide:compiler:4.12.0")

    // Google Maps
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.0.1") // Pertimbangkan update ke 21.2.0

    // Lottie Animation
    implementation("com.airbnb.android:lottie:6.3.0") // Pertimbangkan update ke 6.4.0

    // MQTT
    implementation("com.hivemq:hivemq-mqtt-client:1.3.5") // Anda punya 1.3.5 dan 1.3.1, konsistenkan versinya
    // Jika Anda menggunakan 'implementation("com.hivemq:hivemq-mqtt-client:1.3.5")'
    // maka 'platform' untuk versi yang sama mungkin tidak perlu jika Anda tidak menggunakan BOM HiveMQ
    implementation(platform("com.hivemq:hivemq-mqtt-client-websocket:1.3.5"))
    implementation(platform("com.hivemq:hivemq-mqtt-client-proxy:1.3.5"))
    implementation(platform("com.hivemq:hivemq-mqtt-client-epoll:1.3.5"))
    implementation("com.hivemq:hivemq-mqtt-client-reactor:1.3.5") // Periksa apakah ini benar-benar dipakai bersama async client
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5") // Tetap ada sesuai permintaan

    // JSON (sudah ada di Android SDK, tapi ok jika eksplisit)
    // Tidak ada di daftar Anda sebelumnya, jadi tidak saya tambahkan,
    // tapi jika ada error parsing JSON, Anda mungkin perlu:
    implementation("org.json:json:20231013")

    // Circle image
    implementation("de.hdodenhof:circleimageview:3.1.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}