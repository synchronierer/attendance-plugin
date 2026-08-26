plugins {
    java
    id("com.gradleup.shadow") version "9.0.0"
}

repositories {
    mavenCentral()
}

val studentDatabaseJar = providers.gradleProperty("studentDatabaseJar").orNull
    ?: throw GradleException(
        "The backend JAR must be configured with " +
            "-PstudentDatabaseJar=/absolute/path/student-database.jar"
    )

val studentDatabaseJarFiles = files(studentDatabaseJar)

dependencies {
    compileOnly(studentDatabaseJarFiles)
    testCompileOnly(studentDatabaseJarFiles)
    compileOnly("org.slf4j:slf4j-api:2.0.13")
    testRuntimeOnly(studentDatabaseJarFiles)
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testImplementation("com.google.code.gson:gson:2.14.0")
    implementation("com.google.zxing:core:3.5.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<Jar> {
    manifest {
        attributes["Implementation-Title"] = "Attendance Plugin"
        attributes["Implementation-Version"] = project.version
    }
}

version = "v0.1.0"
group = "igs-landstuhl.plugins"


tasks.shadowJar {
    archiveClassifier.set("")
    dependencies { include(dependency("com.google.zxing:core")) }
}

tasks.build { dependsOn(tasks.shadowJar) }
