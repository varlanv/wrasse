package sample

object GlobalScope

fun bar(scope: Any) = Unit

fun foo() {
    val scope = GlobalScope
    bar(scope)
}

// expect-clean
