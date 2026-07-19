plugins {
    alias(libs.plugins.internalConvention)
}

wrasseKotlinMinorMatrix {
    minor.set("2.1")
    patches.set(listOf("2.1.0", "2.1.10", "2.1.20", "2.1.21"))
}
