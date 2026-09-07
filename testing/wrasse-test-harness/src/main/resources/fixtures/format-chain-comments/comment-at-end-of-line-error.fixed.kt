package sample

class Node {
    val next: Node? = null
}

fun second(node: Node): Node? = node.next // first
    ?.next