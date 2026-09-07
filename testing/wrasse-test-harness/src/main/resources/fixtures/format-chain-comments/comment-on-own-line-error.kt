package sample

class Node {
    val next: Node? = null

    val name: String = ""
}

fun first(node: Node): String? = node
        // pick the next one
        .next
        ?.name

// expect-error 1:1 format "File is not wrasse-formatted"
