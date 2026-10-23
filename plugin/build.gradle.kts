plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

version = "0.1.0"

val paperVersion = "26.3.build.214-stable"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperVersion")
    testImplementation("com.google.code.gson:gson:2.11.0")
    testImplementation("org.yaml:snakeyaml:2.2")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    test {
        useJUnitPlatform()
    }
    processResources {
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filesMatching("paper-plugin.yml") { expand(props) }
    }
    runServer {
        minecraftVersion("26.3")
        // Server working dir is gitignored; delete server/ to reset the world.
        runDirectory = rootProject.layout.projectDirectory.dir("server")
        jvmArgs("-Dcom.mojang.eula.agree=true")
    }
}
