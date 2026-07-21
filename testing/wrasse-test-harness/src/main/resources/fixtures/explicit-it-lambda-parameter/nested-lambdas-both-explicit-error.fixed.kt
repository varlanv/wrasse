package sample

fun use() {
    listOf(listOf(1)).map {
        it.map { it.plus(1) }
    }
}