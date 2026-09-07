package sample

fun probe(value: Int) {}

fun run() {
    probe(
        // keep this
        1,
    )
}

// expect-clean
