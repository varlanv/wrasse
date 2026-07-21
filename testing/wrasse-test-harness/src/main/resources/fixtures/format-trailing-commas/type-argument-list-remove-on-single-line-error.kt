package sample

class Pair2<A, B>

fun <A, B> makePair(): Pair2<A, B> = Pair2()

fun demo() {
    makePair<String, Int,>()
}

// expect-error 1:1 format "File is not wrasse-formatted"
