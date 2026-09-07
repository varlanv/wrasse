package sample

class Node {
    val next: Node? = null

    val name: String = ""
}

fun plain(node: Node): String? = node.next?.name

// expect-clean
