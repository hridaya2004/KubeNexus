plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.hridaya.kubenexus.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 35

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    compileOnly(
        fileTree(
            mapOf(
                "dir" to "libs",
                "include" to listOf("*.jar", "*.aar"),
                "exclude" to listOf("*-sources.jar"),
            ),
        ),
    )
    testImplementation(
        fileTree(
            mapOf(
                "dir" to "libs",
                "include" to listOf("*.jar", "*.aar"),
                "exclude" to listOf("*-sources.jar"),
            ),
        ),
    )

    implementation(project(":core"))
    implementation(project(":domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.snakeyaml.engine)
    implementation(libs.androidx.datastore.preferences)


    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.json)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
