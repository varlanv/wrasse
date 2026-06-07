package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.adapter.LightTreeAdapter
import com.varlanv.wrasse.config.WrasseConfig
import com.varlanv.wrasse.config.WrasseSeverity
import com.varlanv.wrasse.lang.ConfigValueJsonc
import com.varlanv.wrasse.lang.FileWalkUp
import com.varlanv.wrasse.model.WRule
import com.varlanv.wrasse.rules.NoSemicolonsRule
import org.jetbrains.kotlin.KtLightSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFileChecker
import org.jetbrains.kotlin.fir.declarations.FirFile
import java.nio.file.Paths

object WrasseSyntacticChecker : FirFileChecker(MppCheckerKind.Common) {

    private val CONFIG_FILE_NAMES = setOf("wrasse.jsonc", "wrasse.json")

    private val rules: List<WRule> = listOf(
        NoSemicolonsRule(),
    )

    @Volatile
    private var cachedConfig: Result<WrasseConfig>? = null

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFile) {
        val source = declaration.source ?: return
        if (source !is KtLightSourceElement) return

        val config = (cachedConfig ?: loadConfig(declaration).also { cachedConfig = it })
            .getOrElse { ex ->
                reporter.reportOn(declaration.source, WrasseErrors.RESTRICTED_API, ex.message ?: "wrasse: config error")
                return
            }

        val wFile = LightTreeAdapter.adapt(source, declaration.name)

        for (rule in rules) {
            for (violation in rule.check(wFile, config)) {
                val diagnostic = when (violation.severity) {
                    WrasseSeverity.ERROR -> WrasseErrors.RESTRICTED_API
                    WrasseSeverity.WARNING -> WrasseErrors.WRASSE_WARNING
                }
                val violationSource = KtLightSourceElement(
                    source.lighterASTNode,
                    violation.node.startOffset,
                    violation.node.endOffset,
                    source.treeStructure,
                )
                reporter.reportOn(
                    violationSource,
                    diagnostic,
                    "${violation.ruleId}: ${violation.message}",
                )
            }
        }
    }

    private fun loadConfig(declaration: FirFile): Result<WrasseConfig> {
        val filePath = declaration.sourceFile?.path
            ?: return Result.failure(Exception("wrasse: cannot determine source file path for ${declaration.name}"))
        val startDir = Paths.get(filePath).parent
            ?: return Result.failure(Exception("wrasse: cannot determine directory for $filePath"))
        val configPath = FileWalkUp.find(startDir) { it in CONFIG_FILE_NAMES }
            .getOrElse { return Result.failure(Exception("wrasse: error searching for config from $startDir: ${it.message}", it)) }
            ?: return Result.failure(Exception("wrasse: config file not found. Searched upward from $startDir for: $CONFIG_FILE_NAMES"))
        val text = configPath.toFile().readText()
        val configValue = ConfigValueJsonc.parse(text)
            .getOrElse { return Result.failure(Exception("wrasse: failed to parse $configPath: ${it.message}", it)) }
        return WrasseConfig.from(configValue)
    }
}
