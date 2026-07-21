package sample

annotation class Id

class Item(
    a: Int,
    @Id id: Int,
    b: Int,
)