plugins {
    id("java")
    id("application")
}

group = "org.javakov"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Vosk API для распознавания речи
    implementation("com.alphacephei:vosk:0.3.45")
    
    // JSON обработка
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Jave2 - FFmpeg встроенный
    implementation("ws.schild:jave-all-deps:3.5.0")
    
    // HTTP клиент для API переводчика
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Логирование
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("org.slf4j:slf4j-simple:2.0.9")
    
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("org.javakov.SpeechRecognitionApp")
}

tasks.test {
    useJUnitPlatform()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// Настройка JAR с зависимостями
tasks.jar {
    manifest {
        attributes["Main-Class"] = "org.javakov.SpeechRecognitionApp"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}

tasks.register<JavaExec>("runSpRec") {
    group = "application"
    description = "Запускает распознание голоса по входному файлу"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.javakov.SpeechRecognitionApp")
    standardInput = System.`in`
}