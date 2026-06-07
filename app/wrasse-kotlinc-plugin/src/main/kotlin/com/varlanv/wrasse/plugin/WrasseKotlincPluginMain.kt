package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.config.WrasseConfig
import com.varlanv.wrasse.lang.ConfigValueJsonc
import com.varlanv.wrasse.lang.FileWalkUp
import com.varlanv.wrasse.rules.NoSemicolonsRule
import java.nio.file.Path

private val configFileNames = setOf("wrasse.jsonc", "wrasse.json")

/**
 * Wrasse plugin entrypoint, decoupled from kotlinc lifecycle.
 */
fun wrasseMain(sourceRoots: List<Path>): Result<WrassePlugin> {
    val config = loadConfig(sourceRoots).getOrElse { return Result.failure(it) }
    return Result.success(
        WrassePlugin(
            config = config,
            rules = listOf(NoSemicolonsRule())
        )
    )
}

private fun loadConfig(sourceRoots: List<Path>): Result<WrasseConfig> {
    for (root in sourceRoots) {
        val startDir = if (root.toFile().isFile) root.parent ?: continue else root
        val configPath = FileWalkUp.find(startDir) { it in configFileNames }
            .getOrElse {
                return Result.failure(
                    Exception(
                        "wrasse: error searching for config from $root: ${it.message}",
                        it
                    )
                )
            }
            ?: continue
        val text = configPath.toFile().readText()
        val configValue = ConfigValueJsonc.parse(text)
            .getOrElse { return Result.failure(Exception("wrasse: failed to parse $configPath: ${it.message}", it)) }
        return WrasseConfig.from(configValue)
            .getOrElse { return Result.failure(Exception("wrasse: invalid config in $configPath: ${it.message}", it)) }
            .let { Result.success(it) }
    }
    throw Exception("wrasse: config file not found. Searched upward from source roots: $sourceRoots for: $configFileNames")
}
