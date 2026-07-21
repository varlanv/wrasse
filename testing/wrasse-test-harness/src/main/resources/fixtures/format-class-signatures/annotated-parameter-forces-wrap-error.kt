package sample

annotation class Id

class Item(a: Int, @Id id: Int, b: Int)

// expect-error 1:1 format "File is not wrasse-formatted"
