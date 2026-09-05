package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class StringTemplateTextSpec : BaseSpec({

    should("not find interpolation in a plain string") {
        StringTemplateText.hasInterpolation("\"hello\"") shouldBe false
    }

    should("find a simple name interpolation") {
        StringTemplateText.hasInterpolation("\"hello \$name\"") shouldBe true
    }

    should("find a block interpolation") {
        StringTemplateText.hasInterpolation("\"hello \${name}\"") shouldBe true
    }

    should("not find interpolation when the dollar is escaped") {
        StringTemplateText.hasInterpolation("\"hello \\\$name\"") shouldBe false
    }

    should("find interpolation after an unrelated escape sequence") {
        StringTemplateText.hasInterpolation("\"a\\tb\${c}\"") shouldBe true
    }

    should("not find interpolation in a lone trailing dollar") {
        StringTemplateText.hasInterpolation("\"\$\"") shouldBe false
    }

    should("not find interpolation when the dollar is followed by a space") {
        StringTemplateText.hasInterpolation("\"\$ \"") shouldBe false
    }

    should("not find interpolation when the dollar is followed by punctuation") {
        StringTemplateText.hasInterpolation("\"cost:\$\"") shouldBe false
    }

    should("find interpolation when the dollar is followed by an underscore identifier") {
        StringTemplateText.hasInterpolation("\"\$_y\"") shouldBe true
    }
})
