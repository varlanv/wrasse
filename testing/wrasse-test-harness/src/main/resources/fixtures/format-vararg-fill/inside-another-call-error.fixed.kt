package sample

fun use(
    label: String,
    values: IntArray,
): Int = values.size

val size = use(
    "table",
    intArrayOf(
        10, 20, 30, 40, 50, 60, 70, 80,
        90, 100,
    ),
)