package sample

fun probe(first: Int, second: Int): Int = first + second

fun run(): Int {
    probe(/* first */ 1, 2)
    probe(1 /* first */, 2)
    probe(1, /* second */ 2)
    return probe(
        /* wide
         note */ 1,
        2,
    )
}