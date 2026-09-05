package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class AllowedExceptionNameSpec : BaseSpec({

    should("allow a bare underscore") {
        AllowedExceptionName.isAllowed("_") shouldBe true
    }

    should("allow a name starting with ignore") {
        AllowedExceptionName.isAllowed("ignored") shouldBe true
    }

    should("allow a name starting with expected") {
        AllowedExceptionName.isAllowed("expectedFailure") shouldBe true
    }

    should("not allow a plain exception name") {
        AllowedExceptionName.isAllowed("e") shouldBe false
    }

    should("not allow a name merely containing ignore, not starting with it") {
        AllowedExceptionName.isAllowed("pleaseIgnore") shouldBe false
    }
})
