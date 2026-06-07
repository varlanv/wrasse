package com.varlanv.wrasse.compiler

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.KtLightSourceElement
import org.jetbrains.kotlin.com.intellij.lang.LighterASTNode
import org.jetbrains.kotlin.com.intellij.lang.LighterASTTokenNode
import org.jetbrains.kotlin.com.intellij.openapi.util.Ref
import org.jetbrains.kotlin.com.intellij.util.diff.FlyweightCapableTreeStructure

class WrasseFirChecker(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val declarationCheckers = object : DeclarationCheckers() {
        override val functionCheckers = setOf(LightTreeProbeChecker)
    }
}

object LightTreeProbeChecker : FirFunctionChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFunction) {
        val source = declaration.source ?: return
        if (source !is KtLightSourceElement) return

        val tree = source.treeStructure
        val root = tree.root
        walkLightTree(tree, root, depth = 0)
    }

    private fun walkLightTree(
        tree: FlyweightCapableTreeStructure<LighterASTNode>,
        node: LighterASTNode,
        depth: Int,
    ) {
        val indent = "  ".repeat(depth)
        val type = node.tokenType
        val start = node.startOffset
        val end = node.endOffset

        val isLeaf = node is LighterASTTokenNode
        val text = if (isLeaf) {
            node.text.toString().replace("\n", "\\n").take(80)
        } else {
            ""
        }

        println("${indent}${type}${if (isLeaf) " [leaf]" else ""} [$start..$end]${if (text.isNotEmpty()) " \"$text\"" else ""}")

        if (!isLeaf) {
            val ref = Ref<Array<LighterASTNode?>>()
            val count = tree.getChildren(node, ref)
            val children = ref.get()
            if (children != null) {
                for (i in 0 until count) {
                    val child = children[i] ?: continue
                    walkLightTree(tree, child, depth + 1)
                }
            }
        }
    }
}
