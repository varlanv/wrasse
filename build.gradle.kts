tasks.register("testMinorHarness") {
    group = "verification"
    description = "Run fixture tests against all supported Kotlin minor versions"
    dependsOn(":testing:wrasse-kotlinc-plugin-tests-2-1-x:testMinor")
    dependsOn(":testing:wrasse-kotlinc-plugin-tests-2-2-x:testMinor")
    dependsOn(":testing:wrasse-kotlinc-plugin-tests-2-3-x:testMinor")
    dependsOn(":testing:wrasse-kotlinc-plugin-tests-2-4-x:testMinor")
}

tasks.register("testPatchHarness") {
    group = "verification"
    description = "Run fixture tests against all Kotlin patch versions"
    dependsOn(":testing:wrasse-kotlinc-plugin-tests-2-1-x:testPatchHarness")
    dependsOn(":testing:wrasse-kotlinc-plugin-tests-2-2-x:testPatchHarness")
    dependsOn(":testing:wrasse-kotlinc-plugin-tests-2-3-x:testPatchHarness")
    dependsOn(":testing:wrasse-kotlinc-plugin-tests-2-4-x:testPatchHarness")
}
