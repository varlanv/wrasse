plugins {
    alias(libs.plugins.internalConvention)
}

wrasseKotlinMinorMatrix {
    minor.set("2.2")
    patches.set(listOf("2.2.0", "2.2.10", "2.2.20", "2.2.21"))
}
