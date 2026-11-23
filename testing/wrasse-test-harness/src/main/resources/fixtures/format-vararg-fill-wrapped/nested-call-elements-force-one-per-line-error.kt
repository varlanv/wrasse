package sample

class Item(val a: Int)

val items = listOf(Item(1), Item(2), Item(3), Item(4), Item(5), Item(6))

// expect-error 1:1 format "File is not wrasse-formatted"
