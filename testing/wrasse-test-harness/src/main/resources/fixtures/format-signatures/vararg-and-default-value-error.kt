package sample

fun build(pre: String, suf: String = "!", vararg names: String): String {
    return pre + suf + names.size
}

// expect-error 1:1 format "File is not wrasse-formatted"
