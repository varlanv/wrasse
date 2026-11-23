package sample

fun describe(x: Int): String = when (x) {
    1 ->  "one"
    // wrasse fmt:off
    2   ->   "two"
      3->"three"
    // wrasse fmt:on
    else  ->  "many"
}