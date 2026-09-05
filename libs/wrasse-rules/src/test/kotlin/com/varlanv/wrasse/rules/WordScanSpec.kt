package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class WordScanSpec : BaseSpec({

    should("match a word only at word boundaries") {
        WordScan.containsWord("public override fun", "override") shouldBe true
        WordScan.containsWord("overrides", "override") shouldBe false
        WordScan.containsWord("my_override", "override") shouldBe false
        WordScan.containsWord("@Override override", "override") shouldBe true
        WordScan.containsWord("override", "override") shouldBe true
        WordScan.containsWord("", "override") shouldBe false
    }

    should("treat dots inside a qualified name literally and bound only its ends") {
        val text = "val a = com.acme.Foo(1); com.acme.FooBar(2); xcom.acme.Foo(3)"
        WordScan.wordOccurrences(text, "com.acme.Foo") shouldBe listOf(8 until 20)
        WordScan.indexOfWord(text, "com.acme.Foo", 9) shouldBe -1
    }

    should("find a word followed by a character after optional whitespace") {
        WordScan.containsWordFollowedBy("get () = 1", "get", '(') shouldBe true
        WordScan.containsWordFollowedBy("get\n    () = 1", "get", '(') shouldBe true
        WordScan.containsWordFollowedBy("get = 1", "get", '(') shouldBe false
        WordScan.containsWordFollowedBy("target()", "get", '(') shouldBe false
    }

    should("recognise empty parentheses around whitespace") {
        WordScan.isEmptyParens("()") shouldBe true
        WordScan.isEmptyParens("( \n )") shouldBe true
        WordScan.isEmptyParens("(a)") shouldBe false
        WordScan.isEmptyParens("(") shouldBe false
        WordScan.isEmptyParens("()x") shouldBe false
    }
})
