plugins {
    alias(libs.plugins.internalConvention)
    alias(libs.plugins.jmhPlugin)
}

description = "JMH walk-throughput benchmarks (Phase A.5 tripwire) — run via :testing:wrasse-benchmarks:jmh, not part of build/test/check"

dependencies {
    jmh(projects.libs.wrasseModel)
    jmh(projects.libs.wrasseKotlincAdapter)
    jmh(projects.libs.wrasseRules)
    jmh(projects.libs.wrasseLang)
    jmh(libs.kotlin.compiler.embeddable)
}
