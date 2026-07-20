package sample

annotation class Ann

val f: () -> String = run {
    listOf(1).map {
        it + it
    };
    @Ann
    { "" }
}

// expect-clean
