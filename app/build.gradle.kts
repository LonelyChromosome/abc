plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}
val sideBySideFieldTest = providers.gradleProperty("BPA_SIDE_BY_SIDE").orNull == "true"
android {
  namespace = "com.lonelychromosome.parkinglab"
  compileSdk = 35
  defaultConfig {
    applicationId = if (sideBySideFieldTest) "com.lonelychromosome.parkinglab.fieldtest.v13" else "com.lonelychromosome.parkinglab.fieldtest"
    minSdk = 26
    targetSdk = 35
    versionCode = 11
    versionName = "1.3.0-stop-validation-gps-uncertainty"
  }
  defaultConfig.manifestPlaceholders["APP_LABEL"] = if(sideBySideFieldTest) "Find My Bike REAL 1.3" else "Find My Bike REAL"
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
