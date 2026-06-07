package com.varlanv.gradle.plugin

import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.plugin.use.PluginDependency
import kotlin.jvm.optionals.getOrNull

class InternalProperties(private val versionCatalogsExtension: VersionCatalogsExtension) {

    companion object {
        const val NAME = "__internal_convention_properties__"
    }

    private val versionCatalog = versionCatalogsExtension.named("libs")

    fun getLib(name: String): Provider<MinimalExternalModuleDependency> {
        return versionCatalog
            .findLibrary(name)
            .getOrNull() ?: error("Unable to find library [$name]")
    }

    fun getPlugin(name: String): Provider<PluginDependency> {
        return versionCatalog
            .findPlugin(name)
            .orElse(null) ?: error("Unable to find plugin [$name]")
    }

    fun getVersion(name: String): String {
        return versionCatalog.findVersion(name).getOrNull()?.requiredVersion
            ?: error("Unable to find version [$name]")
    }
}
