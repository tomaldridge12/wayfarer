import java.security.MessageDigest

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
    // Adventure text (no server) for testing player-facing wording.
    testImplementation("io.papermc.paper:paper-api:$paperVersion")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

// The resource pack: a reproducible zip of resourcepack/ (fixed timestamps and order, so the same source always
// gives the same SHA-1), and wayfarer-pack.properties in the jar so the plugin offers exactly that pack.
val packSource = rootProject.layout.projectDirectory.dir("resourcepack")
val resourcePack = tasks.register<Zip>("resourcePack") {
    from(packSource)
    archiveFileName = "wayfarer-pack.zip"
    destinationDirectory = rootProject.layout.buildDirectory.dir("resourcepack")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    dirPermissions { unix("755") }
    filePermissions { unix("644") }
}
val packProperties = tasks.register("resourcePackProperties") {
    val zip = resourcePack.flatMap { it.archiveFile }
    val out = layout.buildDirectory.dir("generated/pack")
    inputs.file(zip)
    outputs.dir(out)
    doLast {
        val sha1 = MessageDigest.getInstance("SHA-1").digest(zip.get().asFile.readBytes())
            .joinToString("") { "%02x".format(it) }
        val tag = "pack-" + sha1.take(12)
        val file = out.get().file("wayfarer-pack.properties").asFile
        file.parentFile.mkdirs()
        file.writeText("sha1=$sha1\ntag=$tag\nurl=https://github.com/tomaldridge12/wayfarer/releases/download/$tag/wayfarer-pack.zip\n")
        println("Resource pack $tag sha1 $sha1")
    }
}
sourceSets.main { resources.srcDir(packProperties) }

tasks {
    test {
        useJUnitPlatform()
        systemProperty("wayfarer.pack.dir", packSource.asFile.path)
        systemProperty("wayfarer.pack.zip", rootProject.layout.buildDirectory.file("resourcepack/wayfarer-pack.zip").get().asFile.path)
    }
    processResources {
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filesMatching("paper-plugin.yml") { expand(props) }
    }
    runServer {
        minecraftVersion("26.3")
        // Server working dir is gitignored; delete server/ to reset the world. The smoke test passes
        // -PrunDir=build/smoke-server so it never touches the world you play in.
        runDirectory = rootProject.layout.projectDirectory.dir(providers.gradleProperty("runDir").getOrElse("server"))
        jvmArgs("-Dcom.mojang.eula.agree=true")
    }
}
