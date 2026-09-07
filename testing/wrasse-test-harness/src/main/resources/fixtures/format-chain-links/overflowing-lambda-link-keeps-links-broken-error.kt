package sample

class Node(val name: String, val next: Node?)

class Flags(val aVeryLongConditionNameNumberOne: Boolean, val aVeryLongConditionNameNumberTwo: Boolean)

fun pick(node: Node, flags: Flags): String? {
    val n = node
        .takeIf { flags.aVeryLongConditionNameNumberOne || flags.aVeryLongConditionNameNumberTwo || node.name.isNotEmpty() || node.next != null }
        ?.next
        ?.let { it.name }
    return n
}

// expect-error 1:1 format "File is not wrasse-formatted"
