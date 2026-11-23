package sample

enum class Color { RED, GREEN, BLUE, YELLOW, ORANGE, PURPLE, BLACK, WHITE }

val palette = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.ORANGE, Color.PURPLE)

// expect-error 1:1 format "File is not wrasse-formatted"
