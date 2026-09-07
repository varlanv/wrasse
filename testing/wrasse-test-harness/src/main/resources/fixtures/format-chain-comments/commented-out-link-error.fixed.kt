package sample

class Node {
    val next: Node? = null

    val name: String = ""
}

fun third(node: Node): String? = node.next
    // .parent
    ?.name