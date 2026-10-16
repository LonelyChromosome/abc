plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}
android {
  namespace = "com.lonelychromosome.parkinglab"
  compileSdk = 35
  defaultConfig {
    applicationId = "com.lonelychromosome.parkinglab.fieldtest"
    minSdk = 26
    targetSdk = 35
    versionCode = 9
    versionName = "1.0.0-real-gps-fieldtest"
  }
  defaultConfig.manifestPlaceholders["MAPS_API_KEY"] = providers.environmentVariable("BPA_MAPS_API_KEY").orElse(providers.gradleProperty("BPA_MAPS_API_KEY")).orElse("NO_KEY").get()
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions { jvmTarget = "17" }
}
dependencies {
  implementation("com.google.android.gms:play-services-location:21.3.0")
  implementation("com.google.android.gms:play-services-maps:19.2.0")
}
