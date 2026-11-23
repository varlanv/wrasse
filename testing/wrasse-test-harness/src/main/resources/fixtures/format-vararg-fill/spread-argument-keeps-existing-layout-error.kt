package sample

val head = intArrayOf(1, 2, 3)
val all = intArrayOf(*head, 100, 200, 300, 400, 500, 600, 700)
val twice = intArrayOf(*head, *head, *head, *head, *head, *head)

// expect-error 1:1 format "File is not wrasse-formatted"
