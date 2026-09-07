package sample

class Node {
    val next: Node? = null

    val name: String = ""
}

fun first(node: Node): String? = node
    // pick the next one
    .next
    ?.name