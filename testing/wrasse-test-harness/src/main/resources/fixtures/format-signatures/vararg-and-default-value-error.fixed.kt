package sample

fun build(
    pre: String,
    suf: String = "!",
    vararg names: String,
): String {
    return pre + suf + names.size
}