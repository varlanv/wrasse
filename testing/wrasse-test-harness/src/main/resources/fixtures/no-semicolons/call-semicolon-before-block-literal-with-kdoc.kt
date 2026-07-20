package sample

val f: () -> String = run {
    listOf(1).map {
        it + it
    };
    /**
     * kdoc
     */
    { "" }
}

// expect-clean
