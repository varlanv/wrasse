package sample

class Property(val text: String)

class Bag {
    fun get(key: String, type: Class<*>): Property = Property(key + type.name)
}

infix fun Property.shouldBe(other: Property): Boolean = text == other.text

fun check(bag: Bag, longerNameForTheExpectedValue: Property): Boolean =
    bag.get("configuration", Property::class.java) shouldBe longerNameForTheExpectedValue

// expect-clean
