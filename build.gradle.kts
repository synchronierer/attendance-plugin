plugins {
    java
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
    compileOnly("org.slf4j:slf4j-api:2.0.13")
    runtimeOnly(studentDatabaseJarFiles)
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
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
