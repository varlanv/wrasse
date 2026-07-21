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

private fun unresolvedStar(fqn: String): List<WResolvedImport> =
listOf(WResolvedImport(fqn = fqn, isStarImport = true, resolvedParentClassFqName = null, resolved = false))

class WildcardExpansionDecisionSpec :
    BaseSpec(
        {

            should("expand a star to the ASCII-sorted explicit imports of every attributed symbol") {
                val sourceText = "          import p.aux.*\n"
                val edit = WildcardExpansionDecision
                    .decide(
                        star = star("p.aux", startOffset = 10, endOffset = 24),
                        allStars = listOf(star("p.aux", startOffset = 10, endOffset = 24)),
                        duplicatePackages = emptySet(),
                        explicitImports = emptyList(),
                        filePackageFqName = "sample",
                        classifiers = setOf("p.aux.Widget"),
                        callables = setOf(topLevelCallable("p.aux", "auxFun")),
                        writtenIdentifiers = setOf("Widget"),
                        kdocSpans = emptyList(),
                        sourceText = sourceText,
                        resolvedImports = packageStar("p.aux"),
                    )
                edit.shouldNotBeNull()
                edit.startOffset shouldBe 10
                edit.endOffset shouldBe 24
                edit.replacement shouldBe "import p.aux.Widget\nimport p.aux.auxFun"
            }

            should("attribute a nested classifier to its top-level owner only") {
                val edit = WildcardExpansionDecision
                    .decide(
                        star = star("p.aux", startOffset = 0, endOffset = 14),
                        allStars = listOf(star("p.aux", startOffset = 0, endOffset = 14)),
                        duplicatePackages = emptySet(),
                        explicitImports = emptyList(),
                        filePackageFqName = "sample",
                        classifiers = setOf("p.aux.Outer.Nested"),
                        callables = emptySet(),
                        writtenIdentifiers = setOf("Outer"),
                        kdocSpans = emptyList(),
                        sourceText = "import p.aux.*",
                        resolvedImports = packageStar("p.aux"),
                    )
                edit.shouldNotBeNull()
                edit.replacement shouldBe "import p.aux.Outer"
            }

            should("attribute a constructor call whose classFqName/name is the class's own FQN/simple name") {
                val edit = WildcardExpansionDecision
                    .decide(
                        star = star("p.aux", startOffset = 0, endOffset = 14),
                        allStars = listOf(star("p.aux", startOffset = 0, endOffset = 14)),
                        duplicatePackages = emptySet(),
                        explicitImports = emptyList(),
                        filePackageFqName = "sample",
                        classifiers = emptySet(),
                        callables = setOf(memberCallable("p.aux", "p.aux.Widget", "Widget")),
                        writtenIdentifiers = setOf("Widget"),
                        kdocSpans = emptyList(),
                        sourceText = "import p.aux.*",
                        resolvedImports = packageStar("p.aux"),
                    )
                edit.shouldNotBeNull()
                edit.replacement shouldBe "import p.aux.Widget"
            }

            should("exclude a symbol already covered by an explicit import of the same FQN") {
                val edit = WildcardExpansionDecision
                    .decide(
                        star = star("p.aux", startOffset = 0, endOffset = 14),
                        allStars = listOf(star("p.aux", startOffset = 0, endOffset = 14)),
                        duplicatePackages = emptySet(),
                        explicitImports = listOf(explicitImport("p.aux.Widget")),
                        filePackageFqName = "sample",
                        classifiers = setOf("p.aux.Widget", "p.aux.Gadget"),
                        callables = emptySet(),
                        writtenIdentifiers = setOf("Widget", "Gadget"),
                        kdocSpans = emptyList(),
                        sourceText = "import p.aux.*",
                        resolvedImports = packageStar("p.aux"),
                    )
                edit.shouldNotBeNull()
                edit.replacement shouldBe "import p.aux.Gadget"
            }

            should("NOT exclude a symbol covered only by an ALIASED explicit import of the same FQN, when its plain name is also written") {
                val edit = WildcardExpansionDecision
                    .decide(
                        star = star("p.aux", startOffset = 0, endOffset = 14),
                        allStars = listOf(star("p.aux", startOffset = 0, endOffset = 14)),
                        duplicatePackages = emptySet(),
                        explicitImports = listOf(explicitImport("p.aux.Outer", aliasName = "O")),
                        filePackageFqName = "sample",
                        classifiers = setOf("p.aux.Outer", "p.aux.Gadget"),
                        callables = emptySet(),
                        writtenIdentifiers = setOf("Outer", "Gadget"),
                        kdocSpans = emptyList(),
                        sourceText = "import p.aux.*",
                        resolvedImports = packageStar("p.aux"),
                    )
                edit.shouldNotBeNull()
                edit.replacement shouldBe "import p.aux.Gadget\nimport p.aux.Outer"
            }

            should("bail with no edit when an attributed symbol's simple name is also used under a different FQN (conflict shape)") {
                val edit = WildcardExpansionDecision
                    .decide(
                        star = star("p.auxc1", startOffset = 0, endOffset = 16),
                        allStars = listOf(star("p.auxc1", startOffset = 0, endOffset = 16)),
                        duplicatePackages = emptySet(),
                        explicitImports = listOf(explicitImport("p.auxc2.Item")),
                        filePackageFqName = "sample",
                        classifiers = setOf("p.auxc1.Item", "p.auxc2.Item"),
                        callables = emptySet(),
                        writtenIdentifiers = setOf("Item"),
                        kdocSpans = emptyList(),
                        sourceText = "import p.auxc1.*",
                        resolvedImports = packageStar("p.auxc1"),
                    )
                edit.shouldBeNull()
            }

            should("bail with no edit when an attributed symbol's simple name collides with a default-resolved symbol (flip shape)") {
                val edit = WildcardExpansionDecision
                    .decide(
                        star = star("p.auxflip", startOffset = 0, endOffset = 18),
                        allStars = listOf(star("p.auxflip", startOffset = 0, endOffset = 18)),
                        duplicatePackages = emptySet(),
                        explicitImports = emptyList(),
                        filePackageFqName = "sample",
                        classifiers = setOf("p.auxflip.List", "kotlin.collections.List", "kotlin.Int"),
                        callables = setOf(topLevelCallable("kotlin.collections", "listOf")),
                        writtenIdentifiers = setOf("List"),
                        kdocSpans = emptyList(),
                        sourceText = "import p.auxflip.*",
                        resolvedImports = packageStar("p.auxflip"),
                    )
                edit.shouldBeNull()
            }

            should("not bail when two stars attribute distinct simple names from different packages (no collision)") {
                val edit = WildcardExpansionDecision
                    .decide(
                        star = star("p.aux", startOffset = 0, endOffset = 14),
                        allStars = listOf(star("p.aux", startOffset = 0, endOffset = 14)),
                        duplicatePackages = emptySet(),
                        explicitImports = emptyList(),
                        filePackageFqName = "sample",
                        classifiers = setOf("p.aux.Widget", "p.aux2.Sensor"),
                        callables = emptySet(),
                        writtenIdentifiers = setOf("Widget", "Sensor"),
                        kdocSpans = emptyList(),
                        sourceText = "import p.aux.*",
                        resolvedImports = packageStar("p.aux"),
                    )
                edit.shouldNotBeNull()
                edit.replacement shouldBe "import p.aux.Widget"
            }

            should("bail with no edit when a cross-star collision puts the same simple name under two different attributed FQNs") {
                val editForFirstStar = WildcardExpansionDecision
                    .decide(
                        star = star("p.aux", startOffset = 0, endOffset = 14),
                        allStars = listOf(star("p.aux", startOffset = 0, endOffset = 14)),
                        duplicatePackages = emptySet(),
                        explicitImports = emptyList(),
                        filePackageFqName = "sample",
                        classifiers = setOf("p.aux.Item", "p.aux2.Item"),
                        callables = emptySet(),
                        writtenIdentifiers = setOf("Item"),
                        kdocSpans = emptyList(),
                        sourceText = "import p.aux.*",
                        resolvedImports = packageStar("p.aux"),
                    )
                val editForSecondStar = WildcardExpansionDecision
                    .decide(
                        star = star("p.aux2", startOffset = 15, endOffset = 30),
                        allStars = listOf(star("p.aux2", startOffset = 15, endOffset = 30)),
                        duplicatePackages = emptySet(),
                        explicitImports = emptyList(),
                        filePackageFqName = "sample",
                        classifiers = setOf("p.aux.Item", "p.aux2.Item"),
                        callables = emptySet(),
                        writtenIdentifiers = setOf("Item"),
                        kdocSpans = emptyList(),
                        sourceText = "import p.aux.*\nimport p.aux2.*",
                        resolvedImports = packageStar("p.aux2"),
                    )
                editForFirstStar.shouldBeNull()
                editForSecondStar.shouldBeNull()
            }

            should("bail with no edit on zero attribution") {
                val edit = WildcardExpansionDecision
                    .decide(
                        star = star("p.aux", startOffset = 0, endOffset = 14),
                        allStars = listOf(star("p.aux", startOffset = 0, endOffset = 14)),
                        duplicatePackages = emptySet(),
                        explicitImports = emptyList(),
                        filePackageFqName = "sample",
                        classifiers = emptySet(),
                        callables = emptySet(),
                        writtenIdentifiers = emptySet(),
                        kdocSpans = emptyList(),
                        sourceText = "import p.aux.*",
                        resolvedImports = packageStar("p.aux"),
                    )
                edit.shouldBeNull()
            }

            should("bail with no edit when the star's own package equals the file's package") {
                val edit = WildcardExpansionDecision
                    .decide(
                        star = star("sample", startOffset = 0, endOffset = 15),
                        allStars = listOf(star("sample", startOffset = 0, endOffset = 15)),
                        duplicatePackages = emptySet(),
                        explicitImports = emptyList(),
                        filePackageFqName = "sample",
                        classifiers = setOf("sample.Widget"),
                        callables = emptySet(),
                        writtenIdentifiers = setOf("Widget"),
                        kdocSpans = emptyList(),
                        sourceText = "import sample.*",
                        resolvedImports = packageStar("sample"),
                    )
                edit.shouldBeNull()
            }

            should("bail with no edit when the star's package is in the caller-supplied duplicate set") {
                val edit = WildcardExpansionDecision
                    .decide(
                        star = star("p.aux", startOffset = 0, endOffset = 14),
                        allStars = listOf(star("p.aux", startOffset = 0, endOffset = 14)),
                        duplicatePackages = setOf("p.aux"),
                        explicitImports = emptyList(),
                        filePackageFqName = "sample",
                        classifiers = setOf("p.aux.Widget"),
                        callables = emptySet(),
                        writtenIdentifiers = setOf("Widget"),
                        kdocSpans = emptyList(),
                        sourceText = "import p.aux.*",
                        resolvedImports = packageStar("p.aux"),
                    )
                edit.shouldBeNull()
            }

            should("bail with no edit when the star has no matching resolved import (unresolved)") {
                val edit = WildcardExpansionDecision
                    .decide(
                        star = star("p.aux", startOffset = 0, endOffset = 14),
                        allStars = listOf(star("p.aux", startOffset = 0, endOffset = 14)),
                        duplicatePackages = emptySet(),
                        explicitImports = emptyList(),
                        filePackageFqName = "sample",
                        classifiers = setOf("p.aux.Widget"),
                        callables = emptySet(),
                        writtenIdentifiers = setOf("Widget"),
                        kdocSpans = emptyList(),
                        sourceText = "import p.aux.*",
                        resolvedImports = unresolvedStar("p.aux"),
                    )
                edit.shouldBeNull()
            }

            should("bail with no edit when the star has no matching resolved import at all") {
                val edit = WildcardExpansionDecision
                    .decide(
                        star = star("p.aux", startOffset = 0, endOffset = 14),
                        allStars = listOf(star("p.aux", startOffset = 0, endOffset = 14)),
                        duplicatePackages = emptySet(),
                        explicitImports = emptyList(),
                        filePackageFqName = "sample",
                        classifiers = setOf("p.aux.Widget"),
                        callables = emptySet(),
                        writtenIdentifiers = setOf("Widget"),
                        kdocSpans = emptyList(),
                        sourceText = "import p.aux.*",
                        resolvedImports = emptyList(),
                    )
                edit.shouldBeNull()
            }

            should("bail with no edit when the authoritative classification disagrees with the usage-based cross-check") {
                val edit = WildcardExpansionDecision
                    .decide(
                        star = star("p.aux", startOffset = 0, endOffset = 14),
                        allStars = listOf(star("p.aux", startOffset = 0, endOffset = 14)),
                        duplicatePackages = emptySet(),
                        explicitImports = emptyList(),
                        filePackageFqName = "sample",
                        classifiers = emptySet(),
                        callables = setOf(memberCallable("p.aux", "p.aux", "member", isStatic = true)),
                        writtenIdentifiers = setOf("member"),
                        kdocSpans = emptyList(),
                        sourceText = "import p.aux.*",
                        resolvedImports = packageStar("p.aux"),
                    )
                edit.shouldBeNull()
            }

            should("bail with no edit when the directive shares its line with something else") {
                val sourceText = "import p.aux.Widget; import p.aux.*\nval w = Widget()"
                val edit = WildcardExpansionDecision
                    .decide(
                        star = star("p.aux", startOffset = 21, endOffset = 35),
                        allStars = listOf(star("p.aux", startOffset = 21, endOffset = 35)),
                        duplicatePackages = emptySet(),
                        explicitImports = listOf(explicitImport("p.aux.Widget")),
                        filePackageFqName = "sample",
                        classifiers = setOf("p.aux.Widget"),
                        callables = emptySet(),
                        writtenIdentifiers = setOf("Widget"),
                        kdocSpans = emptyList(),
                        sourceText = sourceText,
                        resolvedImports = packageStar("p.aux"),
                    )
                edit.shouldBeNull()
            }

            should("bail with no edit when a KDoc bracket reference's leading segment is not covered") {
                val sourceText = "/**\n * See [Gizmo] for details.\n */\nimport p.aux.*"
                val edit = WildcardExpansionDecision
                    .decide(
                        star = star("p.aux", startOffset = 36, endOffset = 50),
                        allStars = listOf(star("p.aux", startOffset = 36, endOffset = 50)),
                        duplicatePackages = emptySet(),
                        explicitImports = emptyList(),
                        filePackageFqName = "sample",
                        classifiers = setOf("p.aux.Widget"),
                        callables = emptySet(),
                        writtenIdentifiers = setOf("Widget"),
                        kdocSpans = listOf(0 until 35),
                        sourceText = sourceText,
                        resolvedImports = packageStar("p.aux"),
                    )
                edit.shouldBeNull()
            }

            should("not bail when a KDoc bracket reference's leading segment is covered by an attributed symbol") {
                val sourceText = "/**\n * See [Widget] for details.\n */\nimport p.aux.*"
                val edit = WildcardExpansionDecision
                    .decide(
                        star = star("p.aux", startOffset = 37, endOffset = 51),
                        allStars = listOf(star("p.aux", startOffset = 37, endOffset = 51)),
                        duplicatePackages = emptySet(),
                        explicitImports = emptyList(),
                        filePackageFqName = "sample",
                        classifiers = setOf("p.aux.Widget"),
                        callables = emptySet(),
                        writtenIdentifiers = setOf("Widget"),
                        kdocSpans = listOf(0 until 36),
                        sourceText = sourceText,
                        resolvedImports = packageStar("p.aux"),
                    )
                edit.shouldNotBeNull()
                edit.replacement shouldBe "import p.aux.Widget"
            }

            should("not bail when a KDoc bracket reference's leading segment is covered only by a sibling star's own attribution") {
                val sourceText = "/**\n * See [Sensor] for details.\n */\nimport p.aux.*\nimport p.aux2.*"
                val starAux = star("p.aux", startOffset = 37, endOffset = 51)
                val starAux2 = star("p.aux2", startOffset = 52, endOffset = 67)
                val edit = WildcardExpansionDecision
                    .decide(
                        star = starAux,
                        allStars = listOf(starAux, starAux2),
                        duplicatePackages = emptySet(),
                        explicitImports = emptyList(),
                        filePackageFqName = "sample",
                        classifiers = setOf("p.aux.Widget", "p.aux2.Sensor"),
                        callables = emptySet(),
                        writtenIdentifiers = setOf("Widget", "Sensor"),
                        kdocSpans = listOf(0 until 36),
                        sourceText = sourceText,
                        resolvedImports = packageStar("p.aux") + packageStar("p.aux2"),
                    )
                edit.shouldNotBeNull()
                edit.replacement shouldBe "import p.aux.Widget"
            }

            should("not bail when a qualified KDoc bracket reference's leading segment is covered by an explicit import") {
                val sourceText = "/**\n * See [Widget.Nested] for details.\n */\nimport p.aux.*"
                val edit = WildcardExpansionDecision
                    .decide(
                        star = star("p.aux", startOffset = 44, endOffset = 58),
                        allStars = listOf(star("p.aux", startOffset = 44, endOffset = 58)),
                        duplicatePackages = emptySet(),
                        explicitImports = listOf(explicitImport("p.aux.Widget")),
                        filePackageFqName = "sample",
                        classifiers = setOf("p.aux.Gadget"),
                        callables = emptySet(),
                        writtenIdentifiers = setOf("Gadget"),
                        kdocSpans = listOf(0 until 43),
                        sourceText = sourceText,
                        resolvedImports = packageStar("p.aux"),
                    )
                edit.shouldNotBeNull()
                edit.replacement shouldBe "import p.aux.Gadget"
            }

            should("sort ASCII: uppercase-leading symbols before lowercase-leading ones") {
                val edit = WildcardExpansionDecision
                    .decide(
                        star = star("kotlin.math", startOffset = 0, endOffset = 21),
                        allStars = listOf(star("kotlin.math", startOffset = 0, endOffset = 21)),
                        duplicatePackages = emptySet(),
                        explicitImports = emptyList(),
                        filePackageFqName = "sample",
                        classifiers = emptySet(),
                        callables = setOf(topLevelCallable("kotlin.math", "abs"), topLevelCallable("kotlin.math", "PI")),
                        writtenIdentifiers = emptySet(),
                        kdocSpans = emptyList(),
                        sourceText = "import kotlin.math.*",
                        resolvedImports = packageStar("kotlin.math"),
                    )
                edit.shouldNotBeNull()
                edit.replacement shouldBe "import kotlin.math.PI\nimport kotlin.math.abs"
            }

            should("GATE: keep a classifier attribution whose simple name is written somewhere in the file") {
                val edit = WildcardExpansionDecision
                    .decide(
                        star = star("p.aux", startOffset = 0, endOffset = 14),
                        allStars = listOf(star("p.aux", startOffset = 0, endOffset = 14)),
                        duplicatePackages = emptySet(),
                        explicitImports = emptyList(),
                        filePackageFqName = "sample",
                        classifiers = setOf("p.aux.Widget"),
                        callables = emptySet(),
                        writtenIdentifiers = setOf("Widget"),
                        kdocSpans = emptyList(),
                        sourceText = "import p.aux.*",
                        resolvedImports = packageStar("p.aux"),
                    )
                edit.shouldNotBeNull()
                edit.replacement shouldBe "import p.aux.Widget"
            }

            should("GATE: drop a classifier attribution whose simple name is never written (inference-only usage)") {
                val edit = WildcardExpansionDecision
                    .decide(
                        star = star("p.aux", startOffset = 0, endOffset = 14),
                        allStars = listOf(star("p.aux", startOffset = 0, endOffset = 14)),
                        duplicatePackages = emptySet(),
                        explicitImports = emptyList(),
                        filePackageFqName = "sample",
                        classifiers = setOf("p.aux.Widget"),
                        callables = emptySet(),
                        writtenIdentifiers = emptySet(),
                        kdocSpans = emptyList(),
                        sourceText = "import p.aux.*",
                        resolvedImports = packageStar("p.aux"),
                    )
                edit.shouldBeNull()
            }

            should("GATE: drop a member-callable attribution whose owner's simple name is never written (instance-member access)") {
                val edit = WildcardExpansionDecision
                    .decide(
                        star = star("p.aux", startOffset = 0, endOffset = 14),
                        allStars = listOf(star("p.aux", startOffset = 0, endOffset = 14)),
                        duplicatePackages = emptySet(),
                        explicitImports = emptyList(),
                        filePackageFqName = "sample",
                        classifiers = emptySet(),
                        callables = setOf(memberCallable("p.aux", "p.aux.Widget", "member")),
                        writtenIdentifiers = setOf("member"),
                        kdocSpans = emptyList(),
                        sourceText = "import p.aux.*",
                        resolvedImports = packageStar("p.aux"),
                    )
                edit.shouldBeNull()
            }

            should(
                "GATE: keep a top-level callable attribution even when its name is never written (operator/componentN/invoke conventions)",
            ) {
                val edit = WildcardExpansionDecision
                    .decide(
                        star = star("p.aux", startOffset = 0, endOffset = 14),
                        allStars = listOf(star("p.aux", startOffset = 0, endOffset = 14)),
                        duplicatePackages = emptySet(),
                        explicitImports = emptyList(),
                        filePackageFqName = "sample",
                        classifiers = emptySet(),
                        callables = setOf(topLevelCallable("p.aux", "plus")),
                        writtenIdentifiers = emptySet(),
                        kdocSpans = emptyList(),
                        sourceText = "import p.aux.*",
                        resolvedImports = packageStar("p.aux"),
                    )
                edit.shouldNotBeNull()
                edit.replacement shouldBe "import p.aux.plus"
            }

            should("MEMBER-STAR: expand a used enum entry (isStatic) to an explicit import") {
                val edit = WildcardExpansionDecision
                    .decide(
                        star = star("p.aux.Status", startOffset = 0, endOffset = 21),
                        allStars = listOf(star("p.aux.Status", startOffset = 0, endOffset = 21)),
                        duplicatePackages = emptySet(),
                        explicitImports = emptyList(),
                        filePackageFqName = "sample",
                        classifiers = emptySet(),
                        callables = setOf(memberCallable("p.aux", "p.aux.Status", "ACTIVE", isStatic = true)),
                        writtenIdentifiers = setOf("ACTIVE"),
                        kdocSpans = emptyList(),
                        sourceText = "import p.aux.Status.*",
                        resolvedImports = memberStar("p.aux.Status"),
                    )
                edit.shouldNotBeNull()
                edit.replacement shouldBe "import p.aux.Status.ACTIVE"
            }

            should("MEMBER-STAR: expand only the subset of used enum entries, ASCII-sorted") {
                val edit = WildcardExpansionDecision
                    .decide(
                        star = star("p.aux.Status", startOffset = 0, endOffset = 21),
                        allStars = listOf(star("p.aux.Status", startOffset = 0, endOffset = 21)),
                        duplicatePackages = emptySet(),
                        explicitImports = emptyList(),
                        filePackageFqName = "sample",
                        classifiers = emptySet(),
                        callables = setOf(
                            memberCallable("p.aux", "p.aux.Status", "INACTIVE", isStatic = true),
                            memberCallable("p.aux", "p.aux.Status", "ACTIVE", isStatic = true),
                        ),
                        writtenIdentifiers = setOf("ACTIVE", "INACTIVE"),
                        kdocSpans = emptyList(),
                        sourceText = "import p.aux.Status.*",
                        resolvedImports = memberStar("p.aux.Status"),
                    )
                edit.shouldNotBeNull()
                edit.replacement shouldBe "import p.aux.Status.ACTIVE\nimport p.aux.Status.INACTIVE"
            }

            should("MEMBER-STAR: expand a used nested classifier to an explicit import") {
                val edit = WildcardExpansionDecision
                    .decide(
                        star = star("p.aux.MyClass", startOffset = 0, endOffset = 23),
                        allStars = listOf(star("p.aux.MyClass", startOffset = 0, endOffset = 23)),
                        duplicatePackages = emptySet(),
                        explicitImports = emptyList(),
                        filePackageFqName = "sample",
                        classifiers = setOf("p.aux.MyClass.Nested"),
                        callables = emptySet(),
                        writtenIdentifiers = setOf("Nested"),
                        kdocSpans = emptyList(),
                        sourceText = "import p.aux.MyClass.*",
                        resolvedImports = memberStar("p.aux.MyClass"),
                    )
                edit.shouldNotBeNull()
                edit.replacement shouldBe "import p.aux.MyClass.Nested"
            }

            should("MEMBER-STAR: SKIP a non-static instance member entirely, bailing on the resulting zero attribution") {
                val edit = WildcardExpansionDecision
                    .decide(
                        star = star("p.aux.MyClass", startOffset = 0, endOffset = 23),
                        allStars = listOf(star("p.aux.MyClass", startOffset = 0, endOffset = 23)),
                        duplicatePackages = emptySet(),
                        explicitImports = emptyList(),
                        filePackageFqName = "sample",
                        classifiers = emptySet(),
                        callables = setOf(memberCallable("p.aux", "p.aux.MyClass", "instanceVal", isStatic = false)),
                        writtenIdentifiers = setOf("instanceVal"),
                        kdocSpans = emptyList(),
                        sourceText = "import p.aux.MyClass.*",
                        resolvedImports = memberStar("p.aux.MyClass"),
                    )
                edit.shouldBeNull()
            }

            should("MEMBER-STAR: expand the legal member while SKIPPING a mixed-in non-static instance member") {
                val edit = WildcardExpansionDecision
                    .decide(
                        star = star("p.aux.MyClass", startOffset = 0, endOffset = 23),
                        allStars = listOf(star("p.aux.MyClass", startOffset = 0, endOffset = 23)),
                        duplicatePackages = emptySet(),
                        explicitImports = emptyList(),
                        filePackageFqName = "sample",
                        classifiers = emptySet(),
                        callables = setOf(
                            memberCallable("p.aux", "p.aux.MyClass", "STATIC_ONE", isStatic = true),
                            memberCallable("p.aux", "p.aux.MyClass", "instanceVal", isStatic = false),
                        ),
                        writtenIdentifiers = setOf("STATIC_ONE", "instanceVal"),
                        kdocSpans = emptyList(),
                        sourceText = "import p.aux.MyClass.*",
                        resolvedImports = memberStar("p.aux.MyClass"),
                    )
                edit.shouldNotBeNull()
                edit.replacement shouldBe "import p.aux.MyClass.STATIC_ONE"
            }

            should("MEMBER-STAR: SKIP a same-classFqName constructor call — it never resolves through the star") {
                val edit = WildcardExpansionDecision
                    .decide(
                        star = star("p.aux.MyClass", startOffset = 0, endOffset = 23),
                        allStars = listOf(star("p.aux.MyClass", startOffset = 0, endOffset = 23)),
                        duplicatePackages = emptySet(),
                        explicitImports = emptyList(),
                        filePackageFqName = "sample",
                        classifiers = emptySet(),
                        callables = setOf(
                            memberCallable("p.aux", "p.aux.MyClass", "STATIC_ONE", isStatic = true),
                            memberCallable("p.aux", "p.aux.MyClass", "MyClass", isStatic = false),
                        ),
                        writtenIdentifiers = setOf("STATIC_ONE", "MyClass"),
                        kdocSpans = emptyList(),
                        sourceText = "import p.aux.MyClass.*",
                        resolvedImports = memberStar("p.aux.MyClass"),
                    )
                edit.shouldNotBeNull()
                edit.replacement shouldBe "import p.aux.MyClass.STATIC_ONE"
            }

            should("MEMBER-STAR: bail with no edit on zero attribution (no-unused-imports/engine territory)") {
                val edit = WildcardExpansionDecision
                    .decide(
                        star = star("p.aux.Status", startOffset = 0, endOffset = 21),
                        allStars = listOf(star("p.aux.Status", startOffset = 0, endOffset = 21)),
                        duplicatePackages = emptySet(),
                        explicitImports = emptyList(),
                        filePackageFqName = "sample",
                        classifiers = emptySet(),
                        callables = emptySet(),
                        writtenIdentifiers = emptySet(),
                        kdocSpans = emptyList(),
                        sourceText = "import p.aux.Status.*",
                        resolvedImports = memberStar("p.aux.Status"),
                    )
                edit.shouldBeNull()
            }

            should("MEMBER-STAR: bail with no edit when an attributed member's simple name collides with a different FQN") {
                val edit = WildcardExpansionDecision
                    .decide(
                        star = star("p.aux.Status", startOffset = 0, endOffset = 21),
                        allStars = listOf(star("p.aux.Status", startOffset = 0, endOffset = 21)),
                        duplicatePackages = emptySet(),
                        explicitImports = listOf(explicitImport("p.other.ACTIVE")),
                        filePackageFqName = "sample",
                        classifiers = emptySet(),
                        callables = setOf(
                            memberCallable("p.aux", "p.aux.Status", "ACTIVE", isStatic = true),
                            topLevelCallable("p.other", "ACTIVE"),
                        ),
                        writtenIdentifiers = setOf("ACTIVE"),
                        kdocSpans = emptyList(),
                        sourceText = "import p.aux.Status.*",
                        resolvedImports = memberStar("p.aux.Status"),
                    )
                edit.shouldBeNull()
            }
        },
    )
