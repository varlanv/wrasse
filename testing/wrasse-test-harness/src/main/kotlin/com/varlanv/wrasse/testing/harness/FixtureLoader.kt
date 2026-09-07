package com.varlanv.wrasse.testing.harness

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import org.json.JSONObject

class Fixture(
    val ruleId: String,
    val fixtureId: String,
    val config: String,
    val source: String,
    val expectations: List<ExpectedDiagnostic>,
    val expectClean: Boolean,
    val warnOnly: Boolean,
    val multiPassFix: Boolean = false,
    val extraConfigFiles: Map<String, String> = emptyMap(),
    val auxSources: List<TestSource> = emptyList(),
    val fixedSource: String? = null,
)

object FixtureLoader {
    private const val AUX_DIR_NAME = "aux"
    private const val FIXED_SUFFIX = ".fixed.kt"

    fun load(fixturesDir: Path): List<Fixture> {
        require(Files.isDirectory(fixturesDir)) { "Fixtures dir does not exist: $fixturesDir" }

        val baseConfigFile = fixturesDir.resolve("wrasse.json")
        require(baseConfigFile.isRegularFile()) { "Missing base wrasse.json in $fixturesDir" }
        val baseConfig = JSONObject(baseConfigFile.readText())

        val fixtures = mutableListOf<Fixture>()

        for (ruleDir in Files.list(fixturesDir).use { it.filter { p -> p.isDirectory() }.toList() }) {
            val ruleId = ruleDir.name
            val overrideFile = ruleDir.resolve("wrasse.json")
            val mergedConfig = if (overrideFile.isRegularFile()) {
                deepMerge(JSONObject(baseConfig.toString()), JSONObject(overrideFile.readText())).toString()
            } else {
                baseConfig.toString()
            }

            val extraConfigs = mutableMapOf<String, String>()
            for (jsonFile in Files.list(ruleDir).use {
                it.filter { p ->
                    p.isRegularFile() &&
                        (p.name.endsWith(".json") || p.name.endsWith(".jsonc")) &&
                        p.name != "wrasse.json"
                }.toList()
            }) {
                extraConfigs[jsonFile.name] = jsonFile.readText()
            }

            val companionsByFixtureId = mutableMapOf<String, String>()
            for (companionFile in Files
                .list(ruleDir)
                .use { it.filter { p -> p.isRegularFile() && p.name.endsWith(FIXED_SUFFIX) }.toList() }) {
                companionsByFixtureId[companionFile.name.removeSuffix(FIXED_SUFFIX)] = companionFile.readText()
            }

            val fixtureIdsInDir = mutableSetOf<String>()
            for (fixtureFile in Files.list(ruleDir).use {
                it.filter {
                    p ->
                    p.isRegularFile() &&
                        p.name.endsWith(".kt") &&
                        !p.name.endsWith(FIXED_SUFFIX) &&
                        p.parent.name != AUX_DIR_NAME
                }.toList()
            }) {
                val fixtureId = fixtureFile.nameWithoutExtension
                fixtureIdsInDir.add(fixtureId)
                val parsed = FixtureParser.parse(fixtureFile.readText())
                val auxSources = parsed.auxFiles.map { relativePath ->
                    val auxFile = ruleDir.resolve(relativePath)
                    require(auxFile.isRegularFile()) {
                        "Fixture $fixtureId declares fixture-aux-file '$relativePath' but $auxFile does not exist"
                    }
                    TestSource("sample/$relativePath", auxFile.readText())
                }
                fixtures.add(
                    Fixture(
                        ruleId = ruleId,
                        fixtureId = fixtureId,
                        config = mergedConfig,
                        source = parsed.strippedSource,
                        expectations = parsed.expectations,
                        expectClean = parsed.expectClean,
                        warnOnly = parsed.warnOnly,
                        multiPassFix = parsed.multiPassFix,
                        extraConfigFiles = extraConfigs,
                        auxSources = auxSources,
                        fixedSource = companionsByFixtureId[fixtureId],
                    ),
                )
            }

            for (companionFixtureId in companionsByFixtureId.keys) {
                require(companionFixtureId in fixtureIdsInDir) {
                    "Companion file '$companionFixtureId$FIXED_SUFFIX' in $ruleDir has no matching fixture " +
                        "'$companionFixtureId.kt'"
                }
            }
        }

        fixtures.sortWith(compareBy({ it.ruleId }, { it.fixtureId }))
        return fixtures
    }

    private fun deepMerge(base: JSONObject, override: JSONObject): JSONObject {
        for (key in override.keySet()) {
            val overrideVal = override.get(key)
            if (overrideVal is JSONObject && base.has(key) && base.get(key) is JSONObject) {
                deepMerge(base.getJSONObject(key), overrideVal)
            } else {
                base.put(key, overrideVal)
            }
        }
        return base
    }
}
