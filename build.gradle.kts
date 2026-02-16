plugins {
    id("parent-logic")
    id ("architectury-plugin") version "3.4-SNAPSHOT" apply false
    id ("dev.architectury.loom") version "1.7-SNAPSHOT" apply false
    id ("maven-publish")
}

defaultTasks = arrayListOf("build", "publishToMavenLocal")

val ignored = listOf("common", "platforms")

subprojects {
    if (!ignored.contains(project.name)) {
        apply(plugin = "base-conventions")
    }
}

version = project.property("otg_version").toString()
group = project.property("otg_group").toString()

listOf(
    project(":platforms:fabric"),
    project(":platforms:neoforge"),
).forEach { proj ->
    proj.afterEvaluate {
        proj.tasks.withType<JavaCompile>() {
            options.compilerArgs.add("-Xmaxerrs")
            options.compilerArgs.add("5000")
        }
    }
}
