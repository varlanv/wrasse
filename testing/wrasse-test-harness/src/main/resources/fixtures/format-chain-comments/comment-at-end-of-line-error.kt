package sample

class Node {
    val next: Node? = null
}

fun second(node: Node): Node? = node.next // first
        ?.next

// expect-error 1:1 format "File is not wrasse-formatted"
