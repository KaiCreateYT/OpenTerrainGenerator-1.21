description = "Core files of OpenTerrainGenerator"

dependencies {
    api(project(":common:common-util"))
    api(project(":common:common-customobject"))
    api(project(":common:common-generator"))

    compileOnly("org.projectlombok:lombok:1.18.32")
    annotationProcessor("org.projectlombok:lombok:1.18.32")
}
