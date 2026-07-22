package sample

val q: List<Int> = listOf(1, 2, 3)

fun foo() {
    run qwe@{
        q.forEach {
            return@qwe
        }
    }
    q.forEachIndexed { index, i ->
        return@forEachIndexed
    }
    loop@ for (i in q) {
        println(i)
        break@loop
    }
    qq@ for (i in q) {
        println(i)
        break@qq
    }
}

// expect-error 8:19 custom-label "Custom label @qwe is unnecessary; there is no nested loop or forEach for it to disambiguate"
// expect-error 20:14 custom-label "Custom label @qq is unnecessary; there is no nested loop or forEach for it to disambiguate"
