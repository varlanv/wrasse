package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WCallableUsage
import com.varlanv.wrasse.model.WResolvedImport
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

private fun explicitImport(fqn: String, aliasName: String? = null): ImportRecord =
ImportRecord(fqn = fqn, simpleName = fqn.substringAfterLast('.'), aliasName = aliasName, startOffset = 0, endOffset = fqn.length)

private fun star(packageFqName: String, startOffset: Int, endOffset: Int): StarImportRecord =
StarImportRecord(packageFqName, startOffset, endOffset)

private fun topLevelCallable(packageFqName: String, name: String): WCallableUsage =
WCallableUsage(packageFqName = packageFqName, classFqName = null, name = name)

private fun memberCallable(packageFqName: String, classFqName: String, name: String, isStatic: Boolean = false): WCallableUsage =
WCallableUsage(packageFqName = packageFqName, classFqName = classFqName, name = name, isStatic = isStatic)

private fun packageStar(fqn: String): List<WResolvedImport> =
listOf(WResolvedImport(fqn = fqn, isStarImport = true, resolvedParentClassFqName = null, resolved = true))

private fun memberStar(fqn: String): List<WResolvedImport> =
listOf(WResolvedImport(fqn = fqn, isStarImport = true, resolvedParentClassFqName = fqn, resolved = true))

private fun packageStars(vararg fqns: String): List<WResolvedImport> =
fqns.map { WResolvedImport(fqn = it, isStarImport = true, resolvedParentClassFqName = null, resolved = true) }

class UnusedStarDecisionSpec :
    BaseSpec(
        {

            should("report unused with a whole-line removal edit when the star attributes nothing") {
                val sourceText = "package sample\n\nimport p.aux.*\n\nval x = 1\n"
                val starImport = star("p.aux", startOffset = 16, endOffset = 30)
                val verdict = UnusedStarDecision
                    .decide(
                        star = starImport,
                        allStars = listOf(starImport),
                        explicitImports = emptyList(),
                        filePackageFqName = "sample",
                        classifiers = emptySet(),
                        callables = emptySet(),
                        writtenIdentifiers = emptySet(),
                        kdocSpans = emptyList(),
                        sourceText = sourceText,
                        resolvedImports = packageStar("p.aux"),
                    )
                verdict.shouldBeInstanceOfUnused().edit.shouldNotBeNull().let {
                    it.startOffset shouldBe 16
                    it.endOffset shouldBe 31
                    it.replacement shouldBe ""
                }
            }

            should("stay out of scope when a classifier attributes the star") {
                val starImport = star("p.aux", startOffset = 0, endOffset = 14)
                val verdict = UnusedStarDecision
                    .decide(
                        star = starImport,
                        allStars = listOf(starImport),
                        explicitImports = emptyList(),
                        filePackageFqName = "sample",
                        classifiers = setOf("p.aux.Widget"),
                        callables = emptySet(),
                        writtenIdentifiers = setOf("Widget"),
                        kdocSpans = emptyList(),
                        sourceText = "import p.aux.*",
                        resolvedImports = packageStar("p.aux"),
                    )
                verdict shouldBe UnusedStarVerdict.OutOfScope
            }

            should("stay out of scope when only an ungated top-level callable (operator convention) attributes the star") {
                val starImport = star("p.auxop", startOffset = 0, endOffset = 16)
                val verdict = UnusedStarDecision
                    .decide(
                        star = starImport,
                        allStars = listOf(starImport),
                        explicitImports = emptyList(),
                        filePackageFqName = "sample",
                        classifiers = emptySet(),
                        callables = setOf(topLevelCallable("p.auxop", "plus")),
                        writtenIdentifiers = emptySet(),
                        kdocSpans = emptyList(),
                        sourceText = "import p.auxop.*",
                        resolvedImports = packageStar("p.auxop"),
                    )
                verdict shouldBe UnusedStarVerdict.OutOfScope
            }

            should("report unused when the only attributed symbol is already covered by a non-aliased explicit import") {
                val starImport = star("p.aux", startOffset = 0, endOffset = 14)
                val verdict = UnusedStarDecision
                    .decide(
                        star = starImport,
                        allStars = listOf(starImport),
                        explicitImports = listOf(explicitImport("p.aux.Widget")),
                        filePackageFqName = "sample",
                        classifiers = setOf("p.aux.Widget"),
                        callables = emptySet(),
                        writtenIdentifiers = setOf("Widget"),
                        kdocSpans = emptyList(),
                        sourceText = "import p.aux.*",
                        resolvedImports = packageStar("p.aux"),
                    )
                verdict.shouldBeInstanceOfUnused()
            }

            should("stay out of scope when the star's own package equals the file's package") {
                val starImport = star("sample", startOffset = 0, endOffset = 15)
                val verdict = UnusedStarDecision
                    .decide(
                        star = starImport,
                        allStars = listOf(starImport),
                        explicitImports = emptyList(),
                        filePackageFqName = "sample",
                        classifiers = emptySet(),
                        callables = emptySet(),
                        writtenIdentifiers = emptySet(),
                        kdocSpans = emptyList(),
                        sourceText = "import sample.*",
                        resolvedImports = packageStar("sample"),
                    )
                verdict shouldBe UnusedStarVerdict.OutOfScope
            }

            should("stay out of scope when the star has no matching resolved import (unresolved or ambiguous)") {
                val starImport = star("p.aux", startOffset = 0, endOffset = 14)
                val verdict = UnusedStarDecision
                    .decide(
                        star = starImport,
                        allStars = listOf(starImport),
                        explicitImports = emptyList(),
                        filePackageFqName = "sample",
                        classifiers = emptySet(),
                        callables = emptySet(),
                        writtenIdentifiers = emptySet(),
                        kdocSpans = emptyList(),
                        sourceText = "import p.aux.*",
                        resolvedImports = emptyList(),
                    )
                verdict shouldBe UnusedStarVerdict.OutOfScope
            }

            should("stay out of scope when a KDoc bracket reference's leading segment is uncovered by every other source") {
                val sourceText = "/**\n * See [Gizmo] for details.\n */\nimport p.aux.*"
                val starImport = star("p.aux", startOffset = 36, endOffset = 50)
                val verdict = UnusedStarDecision
                    .decide(
                        star = starImport,
                        allStars = listOf(starImport),
                        explicitImports = emptyList(),
                        filePackageFqName = "sample",
                        classifiers = emptySet(),
                        callables = emptySet(),
                        writtenIdentifiers = emptySet(),
                        kdocSpans = listOf(0 until 35),
                        sourceText = sourceText,
                        resolvedImports = packageStar("p.aux"),
                    )
                verdict shouldBe UnusedStarVerdict.OutOfScope
            }

            should("report unused when a KDoc bracket reference's leading segment is covered by an explicit import") {
                val sourceText = "/**\n * See [Widget] for details.\n */\nimport p.aux.*"
                val starImport = star("p.aux", startOffset = 37, endOffset = 51)
                val verdict = UnusedStarDecision
                    .decide(
                        star = starImport,
                        allStars = listOf(starImport),
                        explicitImports = listOf(explicitImport("p.aux.Widget")),
                        filePackageFqName = "sample",
                        classifiers = emptySet(),
                        callables = emptySet(),
                        writtenIdentifiers = emptySet(),
                        kdocSpans = listOf(0 until 36),
                        sourceText = sourceText,
                        resolvedImports = packageStar("p.aux"),
                    )
                verdict.shouldBeInstanceOfUnused()
            }

            should("report unused when a KDoc bracket reference's leading segment is covered by another star's own attribution") {
                val sourceText = "import p.aux2.*\nimport p.aux.*\n/**\n * See [Widget] for details.\n */\nval x = 1"
                val zeroAttributionStar = star("p.aux", startOffset = 16, endOffset = 30)
                val otherStar = star("p.aux2", startOffset = 0, endOffset = 15)
                val verdict = UnusedStarDecision
                    .decide(
                        star = zeroAttributionStar,
                        allStars = listOf(otherStar, zeroAttributionStar),
                        explicitImports = emptyList(),
                        filePackageFqName = "sample",
                        classifiers = setOf("p.aux2.Widget"),
                        callables = emptySet(),
                        writtenIdentifiers = setOf("Widget"),
                        kdocSpans = listOf(31 until 67),
                        sourceText = sourceText,
                        resolvedImports = packageStars("p.aux", "p.aux2"),
                    )
                verdict.shouldBeInstanceOfUnused()
            }

            should("report unused with no edit when the directive shares its line with something else") {
                val sourceText = "import p.aux.Widget; import p.aux.*\nval w = Widget()"
                val starImport = star("p.aux", startOffset = 21, endOffset = 35)
                val verdict = UnusedStarDecision
                    .decide(
                        star = starImport,
                        allStars = listOf(starImport),
                        explicitImports = listOf(explicitImport("p.aux.Widget")),
                        filePackageFqName = "sample",
                        classifiers = setOf("p.aux.Widget"),
                        callables = emptySet(),
                        writtenIdentifiers = setOf("Widget"),
                        kdocSpans = emptyList(),
                        sourceText = sourceText,
                        resolvedImports = packageStar("p.aux"),
                    )
                verdict.shouldBeInstanceOfUnused().edit.shouldBeNull()
            }

            should("report unused independently for two duplicate zero-attribution stars, each with its own disjoint edit") {
                val sourceText = "package sample\n\nimport p.aux.*\nimport p.aux.*\n\nval x = 1\n"
                val first = star("p.aux", startOffset = 16, endOffset = 30)
                val second = star("p.aux", startOffset = 31, endOffset = 45)
                val resolvedImports = packageStars("p.aux", "p.aux")
                val firstVerdict = UnusedStarDecision
                    .decide(
                        star = first,
                        allStars = listOf(first, second),
                        explicitImports = emptyList(),
                        filePackageFqName = "sample",
                        classifiers = emptySet(),
                        callables = emptySet(),
                        writtenIdentifiers = emptySet(),
                        kdocSpans = emptyList(),
                        sourceText = sourceText,
                        resolvedImports = resolvedImports,
                    )
                val secondVerdict = UnusedStarDecision
                    .decide(
                        star = second,
                        allStars = listOf(first, second),
                        explicitImports = emptyList(),
                        filePackageFqName = "sample",
                        classifiers = emptySet(),
                        callables = emptySet(),
                        writtenIdentifiers = emptySet(),
                        kdocSpans = emptyList(),
                        sourceText = sourceText,
                        resolvedImports = resolvedImports,
                    )
                val firstEdit = firstVerdict.shouldBeInstanceOfUnused().edit.shouldNotBeNull()
                val secondEdit = secondVerdict.shouldBeInstanceOfUnused().edit.shouldNotBeNull()
                firstEdit.startOffset shouldBe 16
                firstEdit.endOffset shouldBe 31
                secondEdit.startOffset shouldBe 31
                secondEdit.endOffset shouldBe 46
            }

            should("MEMBER-STAR: stay out of scope when a used enum entry attributes the star") {
                val starImport = star("p.aux.Status", startOffset = 0, endOffset = 21)
                val verdict = UnusedStarDecision
                    .decide(
                        star = starImport,
                        allStars = listOf(starImport),
                        explicitImports = emptyList(),
                        filePackageFqName = "sample",
                        classifiers = emptySet(),
                        callables = setOf(memberCallable("p.aux", "p.aux.Status", "ACTIVE", isStatic = true)),
                        writtenIdentifiers = setOf("ACTIVE"),
                        kdocSpans = emptyList(),
                        sourceText = "import p.aux.Status.*",
                        resolvedImports = memberStar("p.aux.Status"),
                    )
                verdict shouldBe UnusedStarVerdict.OutOfScope
            }

            should(
                "MEMBER-STAR: report unused when the only associated usage is a non-static instance member (never legally reachable through the star)",
            ) {
                val sourceText = "package sample\n\nimport p.aux.MyClass.*\n\nval v = instanceVal\n"
                val starImport = star("p.aux.MyClass", startOffset = 16, endOffset = 38)
                val verdict = UnusedStarDecision
                    .decide(
                        star = starImport,
                        allStars = listOf(starImport),
                        explicitImports = emptyList(),
                        filePackageFqName = "sample",
                        classifiers = emptySet(),
                        callables = setOf(memberCallable("p.aux", "p.aux.MyClass", "instanceVal", isStatic = false)),
                        writtenIdentifiers = setOf("instanceVal"),
                        kdocSpans = emptyList(),
                        sourceText = sourceText,
                        resolvedImports = memberStar("p.aux.MyClass"),
                    )
                verdict.shouldBeInstanceOfUnused().edit.shouldNotBeNull().let {
                    it.startOffset shouldBe 16
                    it.endOffset shouldBe 39
                    it.replacement shouldBe ""
                }
            }

            should("MEMBER-STAR: report unused with a whole-line removal edit when the member-star attributes nothing") {
                val sourceText = "package sample\n\nimport p.aux.Status.*\n\nval x = 1\n"
                val starImport = star("p.aux.Status", startOffset = 16, endOffset = 38)
                val verdict = UnusedStarDecision
                    .decide(
                        star = starImport,
                        allStars = listOf(starImport),
                        explicitImports = emptyList(),
                        filePackageFqName = "sample",
                        classifiers = emptySet(),
                        callables = emptySet(),
                        writtenIdentifiers = emptySet(),
                        kdocSpans = emptyList(),
                        sourceText = sourceText,
                        resolvedImports = memberStar("p.aux.Status"),
                    )
                verdict.shouldBeInstanceOfUnused().edit.shouldNotBeNull().let {
                    it.startOffset shouldBe 16
                    it.endOffset shouldBe 39
                    it.replacement shouldBe ""
                }
            }
        },
    )

private fun UnusedStarVerdict.shouldBeInstanceOfUnused(): UnusedStarVerdict.Unused {
    val unused = this as? UnusedStarVerdict.Unused
    unused.shouldNotBeNull()
    return unused
}
