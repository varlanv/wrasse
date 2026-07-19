package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WCallableUsage
import com.varlanv.wrasse.model.WQualifiedUsage
import com.varlanv.wrasse.model.WQualifiedUsageKind

/**
 * One written `IDENTIFIER` leaf outside import directives and the package directive, recorded by
 * [ImportEngine]'s walk only when `no-unnecessary-fqn` is enabled — the position-aware sibling
 * of the flat `writtenIdentifiers` set the other import ids already collect (that set has no
 * offsets, so it can't tell "this name is written only as the trailing segment of the very chain
 * being judged" from "this name is also written somewhere else").
 */
class IdentifierOccurrence(val text: String, val startOffset: Int)

/**
 * A single, provably-rewritable `no-unnecessary-fqn` finding: [dropStart]/[dropEnd] is the
 * package-prefix span to delete (the `a.b.` part of a written `a.b.C`), never the whole chain.
 */
class UnnecessaryFqnReport(val dropStart: Int, val dropEnd: Int)

/**
 * Pure decision logic for `no-unnecessary-fqn` (D.2, report-only — design.md §8), compiler-free
 * so it is unit-testable without kotlinc. Precision over coverage: every ambiguity resolves to a
 * silent skip (no report at all), never a guess — a false report here would become a wrong fix
 * once D.3 attaches an edit to the same decision.
 *
 * **Syntactic proof (per usage, before any grouping).** A [WQualifiedUsage] is only "provably
 * rewritable" if the walk-collected source text at its own span literally spells the target FQN:
 * a `QUALIFIER` usage's span must equal [WQualifiedUsage.targetFqName] exactly (FIR's qualifier
 * always stops at the class — design.md §8, D.1 — so the written chain and the target agree
 * character for character when this holds); a `TYPE_REF` usage's span must *start with* the
 * target FQN followed by end-of-span, `<` (generic args), or `?` (nullability) — dogfooding
 * during D.1 showed TYPE_REF spans include both. Any mismatch — a backtick-quoted segment, an
 * alias spelled differently, whitespace or a comment inside the chain, partial qualification —
 * fails this literal comparison and the usage is dropped before it can even reach grouping.
 *
 * **Package boundary — from FIR, never guessed from the string.** [WQualifiedUsage.packageFqName]
 * (D.2 facade addition) gives the real package/class split directly from `ClassId`, which a flat
 * dotted FQN string cannot: `a.b.C.Nested` and a flat `a.b.c.D` are indistinguishable by dots
 * alone. The class actually worth importing — [WQualifiedUsage.packageFqName] plus the first
 * segment of the FQN's relative-class part — is always the *outermost* class of the chain, so a
 * nested reference like `a.b.C.Nested` proposes `import a.b.C` and leaves `C.Nested` as the kept
 * remainder, never proposing to import the nested class itself (one conservative canonical
 * proposal per target). The dropped span is always exactly `packageFqName + "."`.
 *
 * **Deduplication.** Every usage sharing the same candidate import FQN is one "target": the
 * import-viability decision below runs once per target, but every surviving usage still gets its
 * own report at its own span (multiple fully-qualified mentions of the same class each get fixed
 * independently once D.3 lands).
 *
 * **Import viability — the inverted collision analysis, in priority order:**
 * 1. **Already imported.** A non-aliased explicit `import a.b.C` already exists for the exact
 *    candidate FQN — the usage is pure redundancy, the cleanest case, report unconditionally
 *    (nothing else to check: the import is already there, so the bare name is already safe by
 *    construction).
 * 2. **Skip (no report) if the candidate's simple name collides with either:**
 *    - another used FQN's own simple name anywhere in the file
 *      ([SimpleNameCollisionIndex], shared with [WildcardExpansionDecision]'s bail 7);
 *    - an explicit import's visible name (alias if present, else simple name) bound to a
 *      *different* FQN.
 *    These two apply **unconditionally**, same-package target or not (see below) — both reflect a
 *    real name-binding fact regardless of whether an import needs to be *added*.
 * 3. **Same package needs no new import** — skip the remaining check (below) and report.
 * 4. **Otherwise (a new import is genuinely needed), also skip if** the candidate's simple name is
 *    a written `IDENTIFIER` occurrence anywhere in the file *outside* every usage span of this
 *    same target (the trailing segment of the chain being judged is itself a written identifier
 *    and must not self-disqualify — [IdentifierOccurrence] carries offsets precisely so this
 *    exclusion can be checked positionally, not by name alone). Otherwise, report.
 *
 * **Why step 4 is scoped away from same package — two fixes found in high-supervision review,
 * neither shipped as first written.** An earlier draft returned "safe" unconditionally for any
 * same-package candidate, reasoning that Kotlin resolves same-package classes unqualified with no
 * import needed, and a package cannot declare two top-level classes with the same simple name.
 * Both true, but incomplete on their own:
 * - **Fix 1 — an explicit import can still shadow a same-package sibling.** Confirmed empirically
 *   (not assumed) via a dedicated real-compile probe: package `p` with a sibling-file class `p.C`,
 *   plus `import q.C` and a bare `C().qOnly()` call in the same file — `dumpResolvedUsage`'s dump
 *   showed `callables=[q.C/C, q.C/qOnly]`, `errors=false`: the bare call resolved to the
 *   **imported** `q.C`, the same-package `p.C` never even considered. So unconditionally trusting
 *   same-package would let `import q.C` elsewhere in the file silently rebind what a shortened `C`
 *   means — exactly the wrong-fix-from-a-right-report outcome this whole track exists to prevent.
 *   Step 2 above catches this (an explicit import of `q.C` collides with candidate `p.C` under the
 *   same simple name `C`) and is never skipped for same-package targets, unlike step 4.
 * - **Fix 2 — step 4 itself is unsound for same-package, the opposite direction.** Once fix 1 made
 *   same-package fall through the *general* checks instead of returning early, a real regression
 *   surfaced immediately: a same-package class declared in the current file (its own declaration,
 *   e.g. `class Helper` in the same package as a separate `sample.aux.Helper` usage) writes its own
 *   simple name as an `IDENTIFIER` at the declaration site — a position no usage span covers — so
 *   step 4's raw text scan flagged the class's *own declaration of itself* as if it were a foreign
 *   collision, wrongly bailing `same-package-redundant-error` (previously reported, silently began
 *   reporting nothing). A same-package target can *only* have its own declaration living in the
 *   current file's own package — a cross-package "new import needed" target never can, since a
 *   class's package is fixed by its own file's package declaration — so this false positive is
 *   structurally impossible for step 4's real purpose (catching an unresolved/unused bare
 *   reference that might collide with a *newly inserted* import) and only ever fires spuriously for
 *   same-package. Fixed by skipping step 4 outright once step 3 already established "no import
 *   needed" — step 2's real name-binding checks remain fully active either way.
 *
 * Locked by `same-package-shadowed-by-import-skip-clean` (fix 1: skip) alongside
 * `same-package-unshadowed-still-reported-error` / `same-package-redundant-error` (fix 2's own
 * regression fixture: report, no conflicting import) as the paired control.
 */
object QualifiedUsageDecision {

    fun decideAll(
        qualifiedUsages: List<WQualifiedUsage>,
        sourceText: CharSequence,
        filePackageFqName: String,
        classifiers: Set<String>,
        callables: Set<WCallableUsage>,
        explicitImports: List<ImportRecord>,
        identifierOccurrences: List<IdentifierOccurrence>,
    ): List<UnnecessaryFqnReport> {
        val proven = qualifiedUsages.mapNotNull { provenUsage(it, sourceText) }
        if (proven.isEmpty()) return emptyList()

        val collisionIndex = SimpleNameCollisionIndex.build(classifiers, callables)
        val reports = mutableListOf<UnnecessaryFqnReport>()

        for ((candidateImportFqn, usages) in proven.groupBy { it.candidateImportFqn }) {
            val topLevelSimpleName = usages.first().topLevelSimpleName
            val packageFqName = usages.first().usage.packageFqName
            val ownSpans = usages.map { it.usage.startOffset until it.usage.endOffset } +
                literalOccurrences(candidateImportFqn, sourceText)
            if (isSafeToDrop(
                    candidateImportFqn = candidateImportFqn,
                    topLevelSimpleName = topLevelSimpleName,
                    isSamePackage = packageFqName == filePackageFqName,
                    explicitImports = explicitImports,
                    collisionIndex = collisionIndex,
                    identifierOccurrences = identifierOccurrences,
                    ownSpans = ownSpans,
                )
            ) {
                for (p in usages) {
                    reports.add(UnnecessaryFqnReport(p.usage.startOffset, p.usage.startOffset + p.dropLength))
                }
            }
        }
        return reports.sortedWith(compareBy({ it.dropStart }, { it.dropEnd }))
    }

    private class ProvenUsage(
        val usage: WQualifiedUsage,
        val candidateImportFqn: String,
        val topLevelSimpleName: String,
        val dropLength: Int,
    )

    private fun provenUsage(usage: WQualifiedUsage, sourceText: CharSequence): ProvenUsage? {
        val packageFqName = usage.packageFqName
        if (packageFqName.isEmpty()) return null
        val prefix = "$packageFqName."
        if (!usage.targetFqName.startsWith(prefix)) return null
        if (usage.startOffset < 0 || usage.endOffset > sourceText.length || usage.endOffset < usage.startOffset) return null

        val written = sourceText.subSequence(usage.startOffset, usage.endOffset).toString()
        val matches = when (usage.kind) {
            WQualifiedUsageKind.QUALIFIER -> written == usage.targetFqName
            WQualifiedUsageKind.TYPE_REF -> {
                written.startsWith(usage.targetFqName) &&
                    (written.length == usage.targetFqName.length || written[usage.targetFqName.length].let { it == '<' || it == '?' })
            }
        }
        if (!matches) return null

        val relativeClassName = usage.targetFqName.removePrefix(prefix)
        val topLevelSimpleName = relativeClassName.substringBefore('.')
        return ProvenUsage(usage, "$packageFqName.$topLevelSimpleName", topLevelSimpleName, prefix.length)
    }

    /**
     * Every literal, word-bounded occurrence of [candidateImportFqn] in [sourceText] — not just
     * the [WQualifiedUsage] spans FIR gave us. A fully-qualified *constructor call* of a plain
     * (non-object) class never gets its own [WQualifiedUsage] entry at all (FIR only records a
     * `QUALIFIER` for object-like references — confirmed empirically while building this rule's
     * fixtures: `sample.aux.Widget()` produces no qualifier usage, only the callable resolution
     * FIR already tracked elsewhere), so its own trailing simple-name segment is a written
     * `IDENTIFIER` at a position no usage span covers. Without this, the extremely common
     * `val x: pkg.Type = pkg.Type()` shape would self-trigger the "written elsewhere" bail
     * against its own constructor call — a real false-negative found while dogfooding this
     * rule's own fixtures, not a hypothetical. Scanning for the literal chain text directly
     * closes that gap honestly (this is still "was it written", not a resolution claim) without
     * reopening the syntactic-proof precision the per-usage check above already established.
     */
    private fun literalOccurrences(candidateImportFqn: String, sourceText: CharSequence): List<IntRange> {
        val pattern = Regex("\\b" + Regex.escape(candidateImportFqn) + "\\b")
        return pattern.findAll(sourceText).map { it.range }.toList()
    }

    private fun isSafeToDrop(
        candidateImportFqn: String,
        topLevelSimpleName: String,
        isSamePackage: Boolean,
        explicitImports: List<ImportRecord>,
        collisionIndex: Map<String, Set<String>>,
        identifierOccurrences: List<IdentifierOccurrence>,
        ownSpans: List<IntRange>,
    ): Boolean {
        val alreadyImported = explicitImports.any { it.aliasName == null && it.fqn == candidateImportFqn }
        if (alreadyImported) return true

        if (SimpleNameCollisionIndex.collidesWithOtherFqn(candidateImportFqn, topLevelSimpleName, collisionIndex)) return false

        val explicitImportCollision = explicitImports.any { (it.aliasName ?: it.simpleName) == topLevelSimpleName && it.fqn != candidateImportFqn }
        if (explicitImportCollision) return false

        if (isSamePackage) return true

        val writtenElsewhere = identifierOccurrences.any { occ ->
            occ.text == topLevelSimpleName && ownSpans.none { span -> occ.startOffset in span }
        }
        if (writtenElsewhere) return false

        return true
    }
}
