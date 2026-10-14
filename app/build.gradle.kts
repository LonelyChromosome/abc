plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}
val mapsApiKey = providers.environmentVariable("MAPS_API_KEY").orElse("").get()
android {
  namespace = "com.lonelychromosome.parkinglab"
  compileSdk = 35
  defaultConfig {
    applicationId = "com.lonelychromosome.parkinglab.scheduled"
    minSdk = 26
    targetSdk = 35
    versionCode = 7
    versionName = "0.4.2-scheduled"
    manifestPlaceholders["GOOGLE_MAPS_API_KEY"] =
      if (mapsApiKey.isBlank()) "NOT_CONFIGURED" else mapsApiKey
    buildConfigField("boolean", "MAPS_READY", mapsApiKey.isNotBlank().toString())
  }
  buildFeatures { buildConfig = true }
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
