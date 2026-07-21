package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class WordBoundaryScanSpec :
    BaseSpec(
        {

            should("find a whole-word match") {
                WordBoundaryScan.containsWord("const val x", "const") shouldBe true
            }

            should("not match a word that is only a substring of a longer identifier") {
                WordBoundaryScan.containsWord("constant val x", "const") shouldBe false
            }

            should("not match when the word is absent") {
                WordBoundaryScan.containsWord("open val x", "const") shouldBe false
            }

            should("find a word at the very start of the text") {
                WordBoundaryScan.containsWord("const", "const") shouldBe true
            }

            should("find the index of a character") {
                WordBoundaryScan.indexOfChar("foo(bar)", '(') shouldBe 3
            }

            should("return -1 when the character is absent") {
                WordBoundaryScan.indexOfChar("foobar", '(') shouldBe -1
            }
        },
    )
