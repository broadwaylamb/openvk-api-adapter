plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
}

group = "com.broadwaylamb"
version = "0.0.1"

application {
    mainClass = "com.broadwaylamb.openvk.api.adapter.ApplicationKt"
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.logback.classic)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.java)
}
