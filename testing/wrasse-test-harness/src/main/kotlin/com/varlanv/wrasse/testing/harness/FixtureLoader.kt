package com.varlanv.wrasse.testing.harness

import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText

class Fixture(
    val ruleId: String,
    val fixtureId: String,
    val config: String,
    val source: String,
    val expectations: List<ExpectedDiagnostic>,
    val expectClean: Boolean,
    val warnOnly: Boolean,
)

object FixtureLoader {

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

            for (fixtureFile in Files.list(ruleDir).use { it.filter { p -> p.isRegularFile() && p.name.endsWith(".kt") }.toList() }) {
                val fixtureId = fixtureFile.nameWithoutExtension
                val parsed = FixtureParser.parse(fixtureFile.readText())
                fixtures.add(
                    Fixture(
                        ruleId = ruleId,
                        fixtureId = fixtureId,
                        config = mergedConfig,
                        source = parsed.strippedSource,
                        expectations = parsed.expectations,
                        expectClean = parsed.expectClean,
                        warnOnly = parsed.warnOnly,
                    )
                )
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
