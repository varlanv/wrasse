plugins {
    alias(libs.plugins.internalConvention)
}

wrasseKotlinMinorMatrix {
    minor.set("2.3")
    patches.set(listOf("2.3.0", "2.3.10", "2.3.20", "2.3.21"))
}
