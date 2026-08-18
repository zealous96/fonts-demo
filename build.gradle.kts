buildscript {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin/")
        gradlePluginPortal()
    }
}

plugins {
    java
}

group = "com.cell.demos"
version = "1.0"

repositories {
    maven("https://maven.aliyun.com/repository/public/")
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation("com.alibaba.fastjson2:fastjson2:2.0.64")
}
