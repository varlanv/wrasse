package sample

fun probe() {}

fun run() {
    probe(
            // nothing here
    )
}

// expect-error 1:1 format "File is not wrasse-formatted"
