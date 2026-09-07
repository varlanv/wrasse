package sample

class Node {
    val next: Node? = null

    val name: String = ""
}

fun third(node: Node): String? = node
    .next
    // .parent
    ?.name

// expect-error 1:1 format "File is not wrasse-formatted"
