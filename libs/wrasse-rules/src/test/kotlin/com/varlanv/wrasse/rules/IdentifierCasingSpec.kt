package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class IdentifierCasingSpec : BaseSpec({
    should("unquote a backtick-wrapped identifier") {
        IdentifierCasing.unquote("`foo bar`") shouldBe "foo bar"
    }

    should("leave a plain identifier untouched by unquote") {
        IdentifierCasing.unquote("foo") shouldBe "foo"
    }

    should("recognize a backtick-wrapped keyword") {
        IdentifierCasing.isBacktickKeyword("`class`") shouldBe true
    }

    should("not recognize a backtick-wrapped non-keyword") {
        IdentifierCasing.isBacktickKeyword("`className`") shouldBe false
    }

    should("classify PascalCase") {
        IdentifierCasing.isPascalCase("FooBar2") shouldBe true
        IdentifierCasing.isPascalCase("fooBar") shouldBe false
        IdentifierCasing.isPascalCase("") shouldBe false
    }

    should("classify lowerCamelCase") {
        IdentifierCasing.isLowerCamelCase("fooBar2") shouldBe true
        IdentifierCasing.isLowerCamelCase("FooBar") shouldBe false
        IdentifierCasing.isLowerCamelCase("_foo") shouldBe false
    }

    should("classify SCREAMING_SNAKE_CASE") {
        IdentifierCasing.isScreamingSnakeCase("FOO_BAR2") shouldBe true
        IdentifierCasing.isScreamingSnakeCase("Foo_Bar") shouldBe false
        IdentifierCasing.isScreamingSnakeCase("fooBar") shouldBe false
    }

    should("classify a lowercase dotted package segment") {
        IdentifierCasing.isLowerDottedSegment("foo2") shouldBe true
        IdentifierCasing.isLowerDottedSegment("Foo") shouldBe false
        IdentifierCasing.isLowerDottedSegment("foo_bar") shouldBe false
    }
})
