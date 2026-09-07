package sample

fun use() {
    for (i in 0..(1000000 - 1)) {
        println(i)
    }
}

// fixture-option: multi-pass-fix
// expect-error 4:15 range-conventional "Replace .. with until"
// expect-error 4:19 long-numerical-values "Long numerical literal without underscore separators"
// expect-error 1:1 format "File is not wrasse-formatted"
