package com.varlanv.wrasse.format

import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WFormatConfig
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WStreamRule
import com.varlanv.wrasse.model.WrasseRuleConfig

private val INDENTING_TYPES = setOf(WNodeType.BLOCK, WNodeType.CLASS_BODY, WNodeType.WHEN, WNodeType.FUNCTION_LITERAL)

/**
 * The privileged stream consumer that turns the SAX walk into a [Doc] tree, one `when (ctx.type)`
 * decision at a time (§5.3) — here, the only decision this Phase C.1 foundation slice makes is
 * indentation. Registered alongside ordinary rules (see `WrassePlugin`'s `alwaysOn` list) so it
 * rides the same single walk; never user-configurable via `wrasse.json`'s `rules` map (`format` is
 * its own on/off key, see [WFormatConfig]).
 *
 * Every leaf becomes [Doc.Text] verbatim, **except** [WNodeType.WHITE_SPACE] tokens that contain a
 * newline, which become a `HARD` [Doc.Break]: the original text up to and including its final
 * `\n` is kept exactly (so blank lines and any trailing whitespace on them survive byte-for-byte),
 * and [Layout] synthesizes the indent for the line that follows from the ambient [Doc.Indent]
 * depth instead of copying the original run of spaces/tabs. Comment and string-literal leaves
 * (`KDOC`, `BLOCK_COMMENT`, string-template entries) are never [WNodeType.WHITE_SPACE] — their
 * entire text, embedded newlines included, is carried by one `Doc.Text` and is therefore never
 * touched, by construction, satisfying the "don't corrupt an interior" requirement without any
 * special-casing.
 *
 * A node's direct children are buffered until [exitNode], because the whitespace immediately
 * before a node's own closing delimiter must render one level shallower than the rest of the
 * node's interior — the same "dedent before the closer" placement every Wadler-style printer uses
 * for a closing brace's own line. Whether a node opens an indent scope at all is decided once its
 * children are complete, in [resolveFrame]: a node in [INDENTING_TYPES] whose own last child is
 * not literally `RBRACE` owns no closing delimiter of its own and is a transparent pass-through
 * instead — the case that distinguishes a real, braced `BLOCK` (a function/if/loop body, which
 * owns its own `{`/`}`) from a lambda body's `BLOCK` (whose surrounding `{`, whitespace, and `}`
 * belong to the enclosing `FUNCTION_LITERAL`, not to the statement-sequence `BLOCK` nested inside
 * it — confirmed empirically off a real LightTree dump, not assumed). Without this check, a
 * multi-statement lambda body would be indented twice (once by `FUNCTION_LITERAL`, again by its
 * interior `BLOCK`) while a single-statement one would accidentally look right, masking the bug.
 */
class DocBuilder(
    formatConfig: WFormatConfig,
) : WStreamRule {

    override val id: String = "format"
    override val config: WrasseRuleConfig = formatConfig.ruleConfig

    private val style = formatConfig.style
    private val frames = ArrayDeque<Frame>()
    private var rootDoc: Doc = Doc.Concat.EMPTY

    override fun visitLeaf(ctx: WContext, reporter: WReporter) {
        val text = ctx.leafText?.toString() ?: ""
        val entry = if (ctx.type == WNodeType.WHITE_SPACE && text.contains('\n')) {
            ChildEntry.Ws(text)
        } else {
            ChildEntry.Resolved(ctx.type, Doc.Text(text))
        }
        frames.last().children.add(entry)
    }

    override fun enterNode(ctx: WContext) {
        frames.addLast(Frame(ctx.type))
    }

    override fun exitNode(ctx: WContext) {
        val frame = frames.removeLast()
        val doc = resolveFrame(frame)
        val parent = frames.lastOrNull()
        if (parent == null) {
            rootDoc = doc
        } else {
            parent.children.add(ChildEntry.Resolved(ctx.type, doc))
        }
    }

    override fun afterFile(ctx: WContext, reporter: WReporter) {
        val original = ctx.sourceText.toString()
        val rendered = Layout.render(rootDoc, style)
        if (rendered == original) return
        reporter.report(
            id,
            "File is not wrasse-formatted",
            0,
            original.length,
            this,
            edits = listOf(WEdit(0, original.length, rendered)),
        )
    }

    private fun resolveFrame(frame: Frame): Doc {
        val children = frame.children
        if (children.isEmpty()) return Doc.Concat.EMPTY

        val lastIndex = children.size - 1
        val opensIndentScope = frame.type in INDENTING_TYPES && children[lastIndex].type == WNodeType.RBRACE
        if (!opensIndentScope) {
            return Doc.Concat(children.map { resolveEntry(it) })
        }

        val dedentIndex = lastIndex - 1
        val hasDedent = dedentIndex >= 0 && children[dedentIndex] is ChildEntry.Ws

        val innerCount = if (hasDedent) dedentIndex else lastIndex
        val innerParts = ArrayList<Doc>(innerCount)
        for (i in 0 until innerCount) {
            innerParts.add(resolveEntry(children[i]))
        }

        val parts = mutableListOf<Doc>(Doc.Indent(Doc.Concat(innerParts)))
        if (hasDedent) {
            parts.add(resolveEntry(children[dedentIndex]))
        }
        parts.add(resolveEntry(children[lastIndex]))
        return Doc.Concat(parts)
    }

    private fun resolveEntry(entry: ChildEntry): Doc = when (entry) {
        is ChildEntry.Resolved -> entry.doc
        is ChildEntry.Ws -> Doc.Break(BreakKind.HARD, literal = entry.rawText.substring(0, entry.rawText.lastIndexOf('\n') + 1))
    }

    private class Frame(val type: WNodeType) {
        val children = mutableListOf<ChildEntry>()
    }

    private sealed interface ChildEntry {
        val type: WNodeType

        class Resolved(override val type: WNodeType, val doc: Doc) : ChildEntry
        class Ws(val rawText: String) : ChildEntry {
            override val type: WNodeType = WNodeType.WHITE_SPACE
        }
    }
}
