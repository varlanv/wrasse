package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class SuppressionIndexSpec : BaseSpec({

    should("suppress a report inside a region matching the exact rule id") {
        val index = SuppressionIndex()
        index.addRegion("no-semicolons", 10, 20)

        index.isSuppressed("no-semicolons", 12, 15) shouldBe true
    }

    should("not suppress a report outside the region's span") {
        val index = SuppressionIndex()
        index.addRegion("no-semicolons", 10, 20)

        index.isSuppressed("no-semicolons", 20, 25) shouldBe false
    }

    should("not suppress a report for a different rule id in the same region") {
        val index = SuppressionIndex()
        index.addRegion("no-semicolons", 10, 20)

        index.isSuppressed("no-wildcard-imports", 12, 15) shouldBe false
    }

    should("suppress a report exactly matching the region's boundaries") {
        val index = SuppressionIndex()
        index.addRegion("no-semicolons", 10, 20)

        index.isSuppressed("no-semicolons", 10, 20) shouldBe true
    }

    should("suppress every rule id when the region carries the 'all' wildcard") {
        val index = SuppressionIndex()
        index.addRegion("all", 10, 20)

        index.isSuppressed("no-semicolons", 12, 15) shouldBe true
        index.isSuppressed("no-wildcard-imports", 12, 15) shouldBe true
    }

    should("match the 'all' wildcard case-insensitively") {
        val index = SuppressionIndex()
        index.addRegion("ALL", 10, 20)

        index.isSuppressed("no-semicolons", 12, 15) shouldBe true
    }

    should("suppress every rule id when the region carries the 'wrasse' wildcard, case-insensitively") {
        val index = SuppressionIndex()
        index.addRegion("Wrasse", 10, 20)

        index.isSuppressed("no-semicolons", 12, 15) shouldBe true
    }

    should("suppress every rule id anywhere in the file when marked at file scope") {
        val index = SuppressionIndex()
        index.markFile("no-semicolons")

        index.isSuppressed("no-semicolons", 1_000, 1_005) shouldBe true
        index.isSuppressed("no-wildcard-imports", 1_000, 1_005) shouldBe false
    }

    should("suppress every rule id anywhere in the file when marked with a file-scoped wildcard") {
        val index = SuppressionIndex()
        index.markFile("all")

        index.isSuppressed("no-semicolons", 1_000, 1_005) shouldBe true
        index.isSuppressed("no-wildcard-imports", 1_000, 1_005) shouldBe true
    }

    should("not suppress an unrelated rule id when only a specific rule id is region-suppressed") {
        val index = SuppressionIndex()
        index.addRegion("no-semicolons", 10, 20)
        index.addRegion("no-wildcard-imports", 10, 20)

        index.isSuppressed("no-semicolons", 12, 15) shouldBe true
        index.isSuppressed("no-wildcard-imports", 12, 15) shouldBe true
        index.isSuppressed("import-ordering", 12, 15) shouldBe false
    }

    should("recognize wildcard identifiers via isWildcard") {
        SuppressionIndex.isWildcard("all") shouldBe true
        SuppressionIndex.isWildcard("ALL") shouldBe true
        SuppressionIndex.isWildcard("wrasse") shouldBe true
        SuppressionIndex.isWildcard("Wrasse") shouldBe true
        SuppressionIndex.isWildcard("no-semicolons") shouldBe false
        SuppressionIndex.isWildcard("unused") shouldBe false
    }
})
