package sample

const val SPECIAL = 99

fun foo(v: Int) {
    when (v) {
        SPECIAL -> println("special")
        in 1..5 -> println("range")
    }
}

// expect-clean
