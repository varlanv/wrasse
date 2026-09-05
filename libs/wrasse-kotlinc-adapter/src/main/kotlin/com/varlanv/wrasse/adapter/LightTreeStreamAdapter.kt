package com.varlanv.wrasse.adapter

import com.varlanv.wrasse.lang.StringSlice
import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.StreamDispatch
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeRule
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import org.jetbrains.kotlin.KtLightSourceElement
import org.jetbrains.kotlin.com.intellij.lang.LighterASTNode
import org.jetbrains.kotlin.com.intellij.lang.LighterASTTokenNode
import org.jetbrains.kotlin.com.intellij.openapi.util.Ref
import org.jetbrains.kotlin.com.intellij.util.diff.FlyweightCapableTreeStructure

/**
 * SAX-style single-pass walker that translates kotlinc's LightTree into rule events,
 * dispatched directly via [StreamDispatch] with no intermediate tree built:
 *
 * 1. For leaf tokens — dispatches to matching [WLeafRule]s, all [WStreamRule]s, and
 *    any active [WNodeRule]s that requested child forwarding.
 * 2. For interior nodes — fires enterNode/exitNode on matching [WNodeRule]s and all
 *    [WStreamRule]s. For [WBufferedNodeRule]s, the framework collects direct children
 *    into a [ChildBuffer] between enter and exit.
 *
 * The [WContext] is a single mutable struct reused across all events — zero heap
 * allocation per event. Line position state (column, indent) is tracked incrementally
 * during the walk. Uses recursion (not an explicit stack); Kotlin file depth is
 * typically 20-30, well within JVM default stack limits.
 */
object LightTreeStreamAdapter {
    private val newlineFreeByOrdinal: BooleanArray = BooleanArray(WNodeType.SIZE).also { arr ->
        for (type in arrayOf(
            WNodeType.IDENTIFIER,
            WNodeType.INTEGER_LITERAL,
            WNodeType.FLOAT_LITERAL,
            WNodeType.CHARACTER_LITERAL,
            WNodeType.EOL_COMMENT,
            WNodeType.OPEN_QUOTE,
            WNodeType.CLOSING_QUOTE,
            WNodeType.SHORT_TEMPLATE_ENTRY_START,
            WNodeType.LONG_TEMPLATE_ENTRY_START,
            WNodeType.LONG_TEMPLATE_ENTRY_END,
            WNodeType.LPAR,
            WNodeType.RPAR,
            WNodeType.LBRACE,
            WNodeType.RBRACE,
            WNodeType.LBRACKET,
            WNodeType.RBRACKET,
            WNodeType.COMMA,
            WNodeType.DOT,
            WNodeType.SAFE_ACCESS,
            WNodeType.ELVIS,
            WNodeType.RANGE,
            WNodeType.COLONCOLON,
            WNodeType.COLON,
            WNodeType.SEMICOLON,
            WNodeType.ARROW,
            WNodeType.DOUBLE_ARROW,
            WNodeType.EQ,
            WNodeType.EQEQ,
            WNodeType.EXCLEQ,
            WNodeType.LT,
            WNodeType.GT,
            WNodeType.LTEQ,
            WNodeType.GTEQ,
            WNodeType.PLUS,
            WNodeType.MINUS,
            WNodeType.MUL,
            WNodeType.DIV,
            WNodeType.PERC,
            WNodeType.PLUSEQ,
            WNodeType.MINUSEQ,
            WNodeType.MULEQ,
            WNodeType.DIVEQ,
            WNodeType.PERCEQ,
            WNodeType.ANDAND,
            WNodeType.OROR,
            WNodeType.EXCL,
            WNodeType.PLUSPLUS,
            WNodeType.MINUSMINUS,
            WNodeType.EXCLEXCL,
            WNodeType.AT,
            WNodeType.QUEST,
            WNodeType.AS_SAFE,
            WNodeType.KW_FUN,
            WNodeType.KW_VAL,
            WNodeType.KW_VAR,
            WNodeType.KW_CLASS,
            WNodeType.KW_INTERFACE,
            WNodeType.KW_OBJECT,
            WNodeType.KW_IF,
            WNodeType.KW_ELSE,
            WNodeType.KW_WHEN,
            WNodeType.KW_FOR,
            WNodeType.KW_WHILE,
            WNodeType.KW_DO,
            WNodeType.KW_RETURN,
            WNodeType.KW_THROW,
            WNodeType.KW_BREAK,
            WNodeType.KW_CONTINUE,
            WNodeType.KW_TRY,
            WNodeType.KW_CATCH,
            WNodeType.KW_FINALLY,
            WNodeType.KW_IN,
            WNodeType.KW_IS,
            WNodeType.KW_AS,
            WNodeType.KW_NULL,
            WNodeType.KW_TRUE,
            WNodeType.KW_FALSE,
            WNodeType.KW_THIS,
            WNodeType.KW_SUPER,
            WNodeType.KW_PACKAGE,
            WNodeType.KW_IMPORT,
            WNodeType.KW_PUBLIC,
            WNodeType.KW_PRIVATE,
            WNodeType.KW_PROTECTED,
            WNodeType.KW_INTERNAL,
            WNodeType.KW_OPEN,
            WNodeType.KW_ABSTRACT,
            WNodeType.KW_SEALED,
            WNodeType.KW_DATA,
            WNodeType.KW_OVERRIDE,
            WNodeType.KW_SUSPEND,
            WNodeType.KW_INLINE,
            WNodeType.KW_TAILREC,
            WNodeType.KW_OPERATOR,
            WNodeType.KW_INFIX,
            WNodeType.KW_COMPANION,
            WNodeType.KW_CONST,
            WNodeType.KW_LATEINIT,
            WNodeType.KW_ENUM,
            WNodeType.KW_TYPEALIAS,
            WNodeType.KW_FILE,
            WNodeType.KW_FIELD,
            WNodeType.KW_BY,
            WNodeType.KW_CONSTRUCTOR,
            WNodeType.KW_INIT,
            WNodeType.KW_OUT,
            WNodeType.KW_VARARG,
            WNodeType.KW_REIFIED,
            WNodeType.KW_ANNOTATION,
            WNodeType.KW_GET,
            WNodeType.KW_SET,
        )) {
            arr[type.ordinal] = true
        }
    }

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
        ctx.sourceText = tree.toString(source.lighterASTNode).toString()

        for (rule in dispatch.allRules) {
            rule.beforeFile(ctx = ctx)
        }

        val activeNodeRules = ActiveNodeRules()
        val ref = Ref<Array<LighterASTNode?>>()
        val pool = ChildArrayPool()
        val bufferPool = ChildBufferPool()
        val root = source.lighterASTNode
        val rootType = WNodeTypeMapping.map(elementType = root.tokenType)
        val rootIsLeaf = root is LighterASTTokenNode
        walkNode(
            tree = tree,
            astNode = root,
            type = rootType,
            isLeaf = rootIsLeaf,
            ctx = ctx,
            dispatch = dispatch,
            reporter = reporter,
            ref = ref,
            pool = pool,
            bufferPool = bufferPool,
            activeNodeRules = activeNodeRules,
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
     * enter/exit events, pushes/pops the ancestor stack, borrows the children array from
     * [tree] into a per-depth [pool] slot, and recurses. [type]/[isLeaf]/[leafText] are
     * computed once by the caller (the parent's child loop, or [walk] for the root) and
     * passed in rather than recomputed here.
     */
    private fun walkNode(
        tree: FlyweightCapableTreeStructure<LighterASTNode>,
        astNode: LighterASTNode,
        type: WNodeType,
        isLeaf: Boolean,
        ctx: WContext,
        dispatch: StreamDispatch,
        reporter: WReporter,
        ref: Ref<Array<LighterASTNode?>>,
        pool: ChildArrayPool,
        bufferPool: ChildBufferPool,
        activeNodeRules: ActiveNodeRules,
    ) {
        val ownChildIndex = ctx.childIndex

        ctx.type = type
        ctx.startOffset = astNode.startOffset
        ctx.endOffset = astNode.endOffset
        if (isLeaf) ctx.enterLeaf() else ctx.leafText = null

        if (isLeaf) {
            if (dispatch.hasLeafRules) {
                val leafRules = dispatch.leafRulesForType(type = type)
                for (i in 0 until leafRules.size) {
                    leafRules[i].visitLeaf(ctx = ctx, reporter = reporter)
                }
            }

            if (dispatch.hasStreamRules) {
                val streamRules = dispatch.streamRules
                for (i in 0 until streamRules.size) {
                    streamRules[i].visitLeaf(ctx = ctx, reporter = reporter)
                }
            }

            for (i in 0 until activeNodeRules.size) {
                activeNodeRules.ruleAt(i).onChildLeaf(ctx = ctx, reporter = reporter)
            }

            trackLastNewline(ctx)

            ctx.prevLeafType = type
            ctx.prevLeafStart = astNode.startOffset
            ctx.prevLeafEnd = astNode.endOffset
        } else {
            if (dispatch.hasStreamRules) {
                val streamRules = dispatch.streamRules
                for (i in 0 until streamRules.size) {
                    streamRules[i].enterNode(ctx = ctx)
                }
            }

            val depth = ctx.ancestors.size
            val nodeRules = if (dispatch.hasNodeRules) dispatch.nodeRulesForType(type) else emptyList()
            val enteredCount = enterNodeRules(
                nodeRules = nodeRules,
                ctx = ctx,
                reporter = reporter,
                activeNodeRules = activeNodeRules,
                bufferPool = bufferPool,
                depth = depth,
            )
            val sharedBuffer = if (enteredCount > 0) activeNodeRules.lastBuffer(enteredCount) else null

            ctx.ancestors.push(type = type, startOffset = astNode.startOffset, endOffset = astNode.endOffset)
            val count = tree.getChildren(astNode, ref)
            val liveChildren = ref.get()
            if (liveChildren != null && count > 0) {
                val children = pool.acquire(depth = depth, minSize = count)
                System.arraycopy(liveChildren, 0, children, 0, count)
                for (i in 0 until count) {
                    val child = children[i] ?: continue
                    ctx.childIndex = i
                    val childType = WNodeTypeMapping.map(elementType = child.tokenType)
                    val childIsLeaf = child is LighterASTTokenNode

                    walkNode(
                        tree = tree,
                        astNode = child,
                        type = childType,
                        isLeaf = childIsLeaf,
                        ctx = ctx,
                        dispatch = dispatch,
                        reporter = reporter,
                        ref = ref,
                        pool = pool,
                        bufferPool = bufferPool,
                        activeNodeRules = activeNodeRules,
                    )

                    if (sharedBuffer != null) {
                        val start = child.startOffset
                        val end = child.endOffset
                        sharedBuffer.add(
                            type = childType,
                            start = start,
                            end = end,
                            text = if (childIsLeaf) StringSlice(ctx.sourceText, start, end) else null,
                        )
                    }
                }
                tree.disposeChildren(liveChildren, count)
            }

            ctx.ancestors.pop()

            ctx.type = type
            ctx.startOffset = astNode.startOffset
            ctx.endOffset = astNode.endOffset
            ctx.leafText = null
            ctx.childIndex = ownChildIndex

            exitNodeRules(
                activeNodeRules = activeNodeRules,
                enteredCount = enteredCount,
                ctx = ctx,
                reporter = reporter,
            )

            if (dispatch.hasStreamRules) {
                val streamRules = dispatch.streamRules
                for (i in 0 until streamRules.size) {
                    streamRules[i].exitNode(ctx = ctx)
                }
            }
        }
    }

    private fun trackLastNewline(ctx: WContext) {
        if (newlineFreeByOrdinal[ctx.type.ordinal]) return
        val source = ctx.sourceText
        val start = ctx.startOffset
        var i = ctx.endOffset - 1
        while (i >= start) {
            if (source[i] == '\n') {
                ctx.lastNewlineOffset = i
                return
            }
            i--
        }
    }

    private fun enterNodeRules(
        nodeRules: List<WNodeRule>,
        ctx: WContext,
        reporter: WReporter,
        activeNodeRules: ActiveNodeRules,
        bufferPool: ChildBufferPool,
        depth: Int,
    ): Int {
        var enteredCount = 0
        var buffer: ChildBuffer? = null
        for (i in 0 until nodeRules.size) {
            val rule = nodeRules[i]
            val wantChildren = rule.enterNode(ctx = ctx, reporter = reporter)
            if (wantChildren) {
                if (rule is WBufferedNodeRule && buffer == null) buffer = bufferPool.acquire(depth)
                activeNodeRules.add(rule = rule, buffer = if (rule is WBufferedNodeRule) buffer else null)
                enteredCount++
            }
        }
        return enteredCount
    }

    private fun exitNodeRules(
        activeNodeRules: ActiveNodeRules,
        enteredCount: Int,
        ctx: WContext,
        reporter: WReporter,
    ) {
        if (enteredCount == 0) {
            return
        }
        val start = activeNodeRules.size - enteredCount
        for (i in start until activeNodeRules.size) {
            val rule = activeNodeRules.ruleAt(i)
            val buffer = activeNodeRules.bufferAt(i)
            if (rule is WBufferedNodeRule && buffer != null) {
                rule.exitNode(ctx = ctx, children = buffer, reporter = reporter)
            } else {
                rule.exitNode(ctx = ctx, reporter = reporter)
            }
        }
        activeNodeRules.removeLast(enteredCount)
    }

    /**
     * The node rules currently between their `enterNode` and `exitNode`, as two parallel arrays
     * (no per-entry object): the rule, and the [ChildBuffer] it will receive on exit — one buffer
     * per node, shared by every buffered rule entered at that node.
     */
    private class ActiveNodeRules {
        private var rules = arrayOfNulls<WNodeRule>(16)
        private var buffers = arrayOfNulls<ChildBuffer>(16)
        var size = 0
            private set

        fun add(rule: WNodeRule, buffer: ChildBuffer?) {
            if (size == rules.size) {
                rules = rules.copyOf(size * 2)
                buffers = buffers.copyOf(size * 2)
            }
            rules[size] = rule
            buffers[size] = buffer
            size++
        }

        fun ruleAt(i: Int): WNodeRule = rules[i]!!

        fun bufferAt(i: Int): ChildBuffer? = buffers[i]

        /** The buffer shared by the last [enteredCount] entries, or null when none of them is buffered. */
        fun lastBuffer(enteredCount: Int): ChildBuffer? {
            for (i in size - enteredCount until size) {
                val buffer = buffers[i]
                if (buffer != null) return buffer
            }
            return null
        }

        fun removeLast(count: Int) {
            for (i in size - count until size) {
                rules[i] = null
                buffers[i] = null
            }
            size -= count
        }
    }

    /**
     * One reusable [ChildBuffer] per tree depth, mirroring [ChildArrayPool]: the buffer for a
     * node at depth `d` is live only between that node's enter and exit, and siblings at the same
     * depth reuse it in turn. Cleared on every [acquire].
     */
    private class ChildBufferPool {
        private var slots: Array<ChildBuffer?> = arrayOfNulls(16)

        fun acquire(depth: Int): ChildBuffer {
            if (depth >= slots.size) {
                slots = slots.copyOf(maxOf(slots.size * 2, depth + 1))
            }
            val existing = slots[depth]
            if (existing != null) {
                existing.clear()
                return existing
            }
            val fresh = ChildBuffer()
            slots[depth] = fresh
            return fresh
        }
    }

    /**
     * Per-walk pool of children arrays, one reusable, geometrically-grown slot per tree
     * depth. Exactly one node at a given depth is ever mid-loop over its own children at
     * a time (the walk is single-threaded and strictly depth-first), so siblings at the
     * same depth safely reuse the same backing array across calls. Owned by a single
     * [walk] invocation — never shared across concurrent walks.
     */
    private class ChildArrayPool {
        private var slots: Array<Array<LighterASTNode?>?> = arrayOfNulls(16)

        fun acquire(depth: Int, minSize: Int): Array<LighterASTNode?> {
            if (depth >= slots.size) {
                slots = slots.copyOf(maxOf(slots.size * 2, depth + 1))
            }
            val existing = slots[depth]
            if (existing != null && existing.size >= minSize) {
                return existing
            }
            val newSize = maxOf(minSize, if (existing == null) 8 else existing.size * 2)
            val grown = arrayOfNulls<LighterASTNode?>(newSize)
            slots[depth] = grown
            return grown
        }
    }
}
