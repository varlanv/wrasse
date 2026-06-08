plugins {
    alias(libs.plugins.internalConvention)
}

dependencies {
    implementation(projects.testing.wrasseTestHarness)
    implementation(projects.testing.commonTest)
    implementation(projects.app.wrasseKotlincPlugin)
}

tasks.withType<Test>().configureEach {
    val fixturesDir = project(":testing:wrasse-test-harness")
        .file("src/main/resources/fixtures").absolutePath
    systemProperty("wrasse.fixtures.dir", fixturesDir)
}
