plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

allprojects {
    layout.buildDirectory.set(file("C:/Users/p.k.palanisamy/AppData/Local/Temp/DriveMount_build/${project.name}"))
}
