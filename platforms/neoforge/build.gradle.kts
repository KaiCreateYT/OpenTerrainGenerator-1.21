plugins {
    id("platform-conventions")
    id("com.gradleup.shadow")
    id("architectury-plugin")
    id("dev.architectury.loom")
}

repositories {
    maven {
        name = "NeoForged"
        url = uri("https://maven.neoforged.net/releases")
    }
}

architectury {
    platformSetupLoomIde()
    neoForge()
}

val otg: Configuration by configurations.creating
configurations {
    implementation {
        extendsFrom(otg)
    }
}

dependencies {
    "neoForge"("net.neoforged:neoforge:${project.property("neo_version")}")

    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings(loom.officialMojangMappings())

    otg(project(":common:common-core"))
    otg(project(":platforms:shared"))

    // High-performance cache - bundled in JAR
    otg("com.github.ben-manes.caffeine:caffeine:3.1.8")

    compileOnly("org.projectlombok:lombok:1.18.32")
    annotationProcessor("org.projectlombok:lombok:1.18.32")
}

loom {
    accessWidenerPath = file("src/main/resources/META-INF/otg.accesswidener")
}

tasks {
    processResources {
        inputs.property("version", project.property("otg_version"))
        inputs.property("minecraft_version", project.property("minecraft_version"))
        inputs.property("neo_version", project.property("neo_version"))

        filesMatching("META-INF/neoforge.mods.toml") {
            val map = mapOf(
                "version" to inputs.properties["version"].toString(),
                "minecraft_version" to inputs.properties["minecraft_version"].toString(),
                "neo_version" to inputs.properties["neo_version"].toString(),
            )
            expand(map)
        }
    }

    shadowJar {
        dependencyFilter.apply {
            include(project(":common:common-annotation"))
            include(project(":common:common-util"))
            include(project(":common:common-customobject"))
            include(project(":common:common-generator"))
            include(project(":common:common-core"))
            include(project(":platforms:shared"))
            include(dependency("com.github.ben-manes.caffeine:caffeine"))
        }
        relocate("com.github.benmanes.caffeine", "com.pg85.otg.dependency.caffeine")
        exclude("architectury.common.json")
        configurations = listOf(otg)
        archiveClassifier.set("deobf-all")
    }

    remapJar {
        injectAccessWidener = true
        dependsOn(shadowJar)
        inputFile.set(shadowJar.get().archiveFile)
        archiveVersion = project.property("otg_version").toString()
    }
}

otgPlatform {
    productionJar.set(tasks.remapJar.flatMap { it.archiveFile })
}
