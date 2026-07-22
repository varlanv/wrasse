package sample

object GlobalScope

fun bar(scope: Any) = Unit

fun foo() {
    bar(GlobalScope)
}

// expect-clean
