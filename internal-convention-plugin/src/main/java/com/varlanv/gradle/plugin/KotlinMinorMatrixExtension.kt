package com.varlanv.gradle.plugin

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

interface KotlinMinorMatrixExtension {

    companion object {
        const val NAME = "wrasseKotlinMinorMatrix"
    }

    val minor: Property<String>
    val patches: ListProperty<String>
}
