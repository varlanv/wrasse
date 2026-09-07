package sample

fun probe(first: Int, second: Int): Int = first + second

fun spread(vararg values: Int): Int = values.size

fun run(): Int {
    probe(/* first */ 1, 2)
    probe(1 /* first */, 2)
    probe(1, /* second */ 2)
    spread(*intArrayOf(1) /* tail */)
    return probe(
        /* wide
         note */ 1,
        2,
    )
}

// expect-clean
