import java.io.File

plugins {
    id("net.fabricmc.fabric-loom") version "1.17.17"
    id("io.freefair.lombok") version "9.1.0"
}

version = "${project.property("mod_version")}+mc${project.property("minecraft_version")}"
group = project.property("maven_group") as String

repositories {
    maven {
        url = uri("https://maven.uku3lig.net/releases")
    }
    maven {
        url = uri("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
    }
}

dependencies {
    // To change the versions see the gradle.properties file
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    // not every loom/mc combination puts the loader's bundled mixinextras on the compile classpath
    implementation("io.github.llamalad7:mixinextras-fabric:0.5.4")

    implementation(fabricApi.module("fabric-command-api-v2", project.property("fabric_api_version") as String))
    // additional fabric-api modules for old versions (key binding / lifecycle events are
    // only bundled by ukulib's registerKeybinding helper on 1.21+)
    (project.findProperty("extra_fabric_modules") as String?)
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.forEach { module ->
            implementation(fabricApi.module(module, project.property("fabric_api_version") as String))
        }

    implementation("net.uku3lig:${project.property("ukulib_artifact")}:${project.property("ukulib_version")}")
    include("net.uku3lig:${project.property("ukulib_artifact")}:${project.property("ukulib_version")}")

    runtimeOnly("me.djtheredstoner:DevAuth-fabric:${project.property("devauth_version")}")
}

base {
    archivesName = project.property("archives_base_name") as String
}

java {
    sourceCompatibility = JavaVersion.toVersion(project.property("java_target").toString())
    targetCompatibility = JavaVersion.toVersion(project.property("java_target").toString())
}

tasks.processResources {
    inputs.property("version", project.version)
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}

// In the obfuscated (remap) era the publishable artifact comes from remapJar,
// in the no-remap era the plain jar already is the final artifact.
val mcVersion = project.property("minecraft_version") as String
val modsDir = (project.findProperty("norisk_mods_dir") as String?)?.let { file(it) }

val installTask: Task = tasks.findByName("remapJar") ?: tasks.getByName("jar")
installTask.doLast(object : Action<Task> {
    override fun execute(task: Task) {
    val jarFile = (task as org.gradle.api.tasks.bundling.AbstractArchiveTask).archiveFile.get().asFile
    if (modsDir == null) return
    modsDir.mkdirs()
    modsDir.listFiles()
        ?.filter { it.isFile && it.name.startsWith("AxotiersTiertagger-") && (it.name.endsWith("+mc$mcVersion.jar") || it.name.endsWith("-dev.jar")) && it.name != jarFile.name }
        ?.forEach { old -> old.delete() }

    val dest = File(modsDir, jarFile.name)
    try {
        jarFile.copyTo(dest, overwrite = true)
        println("Installed ${jarFile.name} -> ${dest.absolutePath}")
    } catch (e: Exception) {
        logger.warn("Could not install ${jarFile.name} into ${dest.absolutePath}: ${e.message}")
        logger.warn("File may be locked (close NoRiskClient/Minecraft) and copy manually later.")
    }
    }
})
