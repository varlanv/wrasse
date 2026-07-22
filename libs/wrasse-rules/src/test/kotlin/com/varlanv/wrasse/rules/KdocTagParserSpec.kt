package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class KdocTagParserSpec :
    BaseSpec(
        {

            should("return an empty list for a KDoc with no tags") {
                KdocTagParser.parseTags("/** Does a thing. */") shouldBe emptyList()
            }

            should("parse a single param tag") {
                val tags = KdocTagParser.parseTags("/** @param foo the foo */")
                tags.map { it.name to it.kind } shouldBe listOf("foo" to KdocDeclarationKind.PARAM)
            }

            should("parse a single property tag") {
                val tags = KdocTagParser.parseTags("/** @property bar the bar */")
                tags.map { it.name to it.kind } shouldBe listOf("bar" to KdocDeclarationKind.PROPERTY)
            }

            should("parse several tags in source order") {
                val text = """
                    /**
                     * @param first
                     * @property second
                     * @param third
                     */
                    """
                    .trimIndent()
                KdocTagParser.parseTags(text).map { it.name } shouldBe listOf("first", "second", "third")
            }

            should("unquote a backtick-wrapped subject name") {
                val tags = KdocTagParser.parseTags("/** @param `my name` description */")
                tags.single().name shouldBe "my name"
            }

            should("ignore unrelated tags such as return and throws") {
                val text = "/** @return the result @throws Exception on failure */"
                KdocTagParser.parseTags(text) shouldBe emptyList()
            }
        },
    )
