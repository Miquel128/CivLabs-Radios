plugins {
    java
}

group = "com.civlabs"
version = "1.0.3"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.maxhenkel.de/repository/public")
}

dependencies {
    // Paper API from Maven
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")

    // Use your local Simple Voice Chat plugin JAR for compileOnly (API 2.6.4 not on Maven)
    compileOnly(files("C:\\Users\\gavin\\Downloads\\voicechat-bukkit-2.6.4.jar"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}