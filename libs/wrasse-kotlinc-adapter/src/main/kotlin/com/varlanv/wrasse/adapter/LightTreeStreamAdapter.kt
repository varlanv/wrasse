package com.varlanv.wrasse.adapter

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.StreamDispatch
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeRule
import com.varlanv.wrasse.model.WReporter
import org.jetbrains.kotlin.KtLightSourceElement
import org.jetbrains.kotlin.com.intellij.lang.LighterASTNode
import org.jetbrains.kotlin.com.intellij.lang.LighterASTTokenNode
import org.jetbrains.kotlin.com.intellij.openapi.util.Ref
import org.jetbrains.kotlin.com.intellij.util.diff.FlyweightCapableTreeStructure

/**
 * SAX-style single-pass walker that translates kotlinc's LightTree into rule events.
 *
 * Replaces the old two-pass approach (build WNode tree, then traverse). Instead, walks
 * the LightTree recursively and emits events directly to rules via [StreamDispatch]:
 *
 * 1. For leaf tokens — dispatches to matching [WLeafRule]s, all [WStreamRule]s, and
 *    any active [WNodeRule]s that requested child forwarding.
 * 2. For interior nodes — fires enterNode/exitNode on matching [WNodeRule]s and all
 *    [WStreamRule]s. For [WBufferedNodeRule]s, the framework collects direct children
 *    into a [ChildBuffer] between enter and exit.
 *
 * No WNode objects are created, and no source text string is allocated. The [WContext]
 * is a single mutable struct reused across all events — zero heap allocation per event.
 * Line position state (column, indent) is tracked incrementally during the walk.
 *
 * Uses recursion (not an explicit stack). Kotlin file depth is typically 20-30, well
 * within JVM default stack limits.
 */
object LightTreeStreamAdapter {

    /**
     * Walk the LightTree from the given compiler source element, dispatching SAX events
     * to rules registered in [dispatch]. Violations are collected through [reporter]. [ctx]
     * is constructed by the caller (so it can be read back after the walk, e.g. for hashing
     * or patch writing) and reused as-is; [WContext.sourceText] is set once here, before rules
     * see anything.
     *
     * Lifecycle: beforeFile → recursive walk → afterFile → WFileRules.
     */
    fun walk(
        source: KtLightSourceElement,
        ctx: WContext,
        dispatch: StreamDispatch,
        reporter: WReporter,
    ) {
        val tree = source.treeStructure
        ctx.sourceText = tree.toString(source.lighterASTNode)

        for (rule in dispatch.allRules) {
            rule.beforeFile(ctx = ctx)
        }

        val activeNodeRules = ArrayList<ActiveNodeEntry>()
        val ref = Ref<Array<LighterASTNode?>>()
        walkNode(
            tree = tree,
            astNode = tree.root,
            ctx = ctx,
            dispatch = dispatch,
            reporter = reporter,
            ref = ref,
            activeNodeRules = activeNodeRules
        )

        for (rule in dispatch.allRules) {
            rule.afterFile(ctx = ctx, reporter = reporter)
        }
        for (rule in dispatch.fileRules) {
            rule.visit(ctx = ctx, reporter = reporter)
        }
    }

    /**
     * Recursive walk of a single LightTree node. For leaves, dispatches to leaf/stream/active
     * rules, tracks newline/indent state, and updates prevLeaf. For interior nodes, fires
     * enter/exit events, pushes/pops the ancestor stack, copies the children array (Ref is
     * reused by deeper calls), and recurses.
     */
    private fun walkNode(
        tree: FlyweightCapableTreeStructure<LighterASTNode>,
        astNode: LighterASTNode,
        ctx: WContext,
        dispatch: StreamDispatch,
        reporter: WReporter,
        ref: Ref<Array<LighterASTNode?>>,
        activeNodeRules: ArrayList<ActiveNodeEntry>,
    ) {
        // todo: potentially lookup by `astNode.tokenType.index` in some array instead of hashmap `.get`
        val type = WNodeTypeMapping.map(elementType = astNode.tokenType)
        val isLeaf = astNode is LighterASTTokenNode

        ctx.type = type
        ctx.startOffset = astNode.startOffset
        ctx.endOffset = astNode.endOffset
        ctx.leafText = if (isLeaf) astNode.text else null

        if (isLeaf) {
            if (dispatch.hasLeafRules) {
                val leafRules = dispatch.leafRulesForType(type = type)
                for (rule in leafRules) {
                    rule.visitLeaf(ctx = ctx, reporter = reporter)
                }
            }

            if (dispatch.hasStreamRules) {
                for (rule in dispatch.streamRules) {
                    rule.visitLeaf(ctx = ctx, reporter = reporter)
                }
            }

            for (i in activeNodeRules.indices) {
                activeNodeRules[i].rule.onChildLeaf(ctx = ctx, reporter = reporter)
            }

            trackLastNewline(ctx)

            ctx.prevLeafType = type
            ctx.prevLeafStart = astNode.startOffset
            ctx.prevLeafEnd = astNode.endOffset
            ctx.prevLeafText = ctx.leafText
        } else {
            if (dispatch.hasStreamRules) {
                for (rule in dispatch.streamRules) {
                    rule.enterNode(ctx = ctx)
                }
            }

            val depth = ctx.ancestors.size
            val nodeRules = if (dispatch.hasNodeRules) dispatch.nodeRulesForType(type) else emptyList()
            val enteredCount =
                enterNodeRules(
                    nodeRules = nodeRules,
                    ctx = ctx,
                    reporter = reporter,
                    activeNodeRules = activeNodeRules,
                    depth = depth
                )

            ctx.ancestors.push(type = type, startOffset = astNode.startOffset, endOffset = astNode.endOffset)
            val count = tree.getChildren(astNode, ref)
            val childArray = ref.get()
            if (childArray != null && count > 0) {
                val children = childArray.copyOfRange(0, count)
                for (i in children.indices) {
                    val child = children[i] ?: continue
                    ctx.childIndex = i

                    walkNode(
                        tree = tree,
                        astNode = child,
                        ctx = ctx,
                        dispatch = dispatch,
                        reporter = reporter,
                        ref = ref,
                        activeNodeRules = activeNodeRules
                    )

                    if (enteredCount > 0) {
                        val childType = WNodeTypeMapping.map(elementType = child.tokenType)
                        val childIsLeaf = child is LighterASTTokenNode
                        val childText = if (childIsLeaf) child.text else null
                        val activeStart = activeNodeRules.size - enteredCount
                        for (j in activeStart until activeNodeRules.size) {
                            val entry = activeNodeRules[j]
                            entry.buffer?.add(
                                type = childType,
                                start = child.startOffset,
                                end = child.endOffset,
                                text = childText
                            )
                        }
                    }
                }
            }

            ctx.ancestors.pop()

            ctx.type = type
            ctx.startOffset = astNode.startOffset
            ctx.endOffset = astNode.endOffset
            ctx.leafText = null

            exitNodeRules(
                activeNodeRules = activeNodeRules,
                enteredCount = enteredCount,
                ctx = ctx,
                reporter = reporter
            )

            if (dispatch.hasStreamRules) {
                for (rule in dispatch.streamRules) {
                    rule.exitNode(ctx = ctx)
                }
            }
        }
    }

    private fun trackLastNewline(ctx: WContext) {
        val lt = ctx.leafText ?: return
        for (i in lt.length - 1 downTo 0) {
            if (lt[i] == '\n') {
                ctx.lastNewlineOffset = ctx.startOffset + i
                return
            }
        }
    }

    private fun enterNodeRules(
        nodeRules: List<WNodeRule>,
        ctx: WContext,
        reporter: WReporter,
        activeNodeRules: ArrayList<ActiveNodeEntry>,
        depth: Int,
    ): Int {
        var enteredCount = 0
        for (rule in nodeRules) {
            val wantChildren = rule.enterNode(ctx = ctx, reporter = reporter)
            if (wantChildren) {
                val buffer = if (rule is WBufferedNodeRule) ChildBuffer() else null
                activeNodeRules.add(ActiveNodeEntry(rule = rule, buffer = buffer, depth = depth))
                enteredCount++
            }
        }
        return enteredCount
    }

    private fun exitNodeRules(
        activeNodeRules: ArrayList<ActiveNodeEntry>,
        enteredCount: Int,
        ctx: WContext,
        reporter: WReporter,
    ) {
        if (enteredCount == 0) {
            return
        }
        val start = activeNodeRules.size - enteredCount
        for (i in start until activeNodeRules.size) {
            val entry = activeNodeRules[i]
            if (entry.rule is WBufferedNodeRule && entry.buffer != null) {
                entry.rule.exitNode(ctx = ctx, children = entry.buffer, reporter = reporter)
            } else {
                entry.rule.exitNode(ctx = ctx, reporter = reporter)
            }
        }
        for (i in 0 until enteredCount) {
            activeNodeRules.removeAt(activeNodeRules.size - 1)
        }
    }

    private class ActiveNodeEntry(
        val rule: WNodeRule,
        val buffer: ChildBuffer?,
        val depth: Int,
    )
}
