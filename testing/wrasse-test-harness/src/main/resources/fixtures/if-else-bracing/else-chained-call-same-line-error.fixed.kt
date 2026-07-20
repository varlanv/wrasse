package sample

val foo = if (isEven()) {
    0
} else {
    System.currentTimeMillis().toInt()
}

fun isEven(): Boolean = System.currentTimeMillis() % 2 == 0L