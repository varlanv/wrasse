package sample

fun drain(queue: MutableList<String>, limit: Int): Int {
    var taken = 0
    while (queue.isNotEmpty() && taken < limit) {
        queue.removeAt(0)
        taken++
    }
    return taken
}