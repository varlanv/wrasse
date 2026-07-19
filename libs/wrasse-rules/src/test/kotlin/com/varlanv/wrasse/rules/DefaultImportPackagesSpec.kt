package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder

class DefaultImportPackagesSpec : BaseSpec({

    should("list exactly the ten Kotlin/JVM compiler-default-imported packages") {
        DefaultImportPackages.ALL shouldContainExactlyInAnyOrder listOf(
            "kotlin",
            "kotlin.annotation",
            "kotlin.collections",
            "kotlin.comparisons",
            "kotlin.io",
            "kotlin.ranges",
            "kotlin.sequences",
            "kotlin.text",
            "kotlin.jvm",
            "java.lang",
        )
    }
})
