package com.varlanv.gradle.plugin

import org.gradle.api.provider.Property

interface InternalKonventionExtension {

    companion object {
        const val NAME = "internalConvention"
    }

    val mainClass: Property<String>
}
