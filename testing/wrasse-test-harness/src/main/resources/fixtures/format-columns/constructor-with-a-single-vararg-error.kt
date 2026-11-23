package sample

class Bag(vararg val items: String)

// wrasse columns:2
val bag = Bag("a", "bb", "ccc", "d")

// expect-error 1:1 format "File is not wrasse-formatted"
