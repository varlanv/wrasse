package sample

import sample.Utils.`when`
import sample.Utils.verify

object Utils {
    fun `when`() {}

    fun verify() {}
}

fun use() {
    `when`()
    verify()
}

// expect-error 3:1 import-ordering "Imports are not sorted"
