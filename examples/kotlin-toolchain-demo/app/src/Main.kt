package demo

import kotlin.math.max
import java.util.*
import kotlin.collections.List

data class Item(val name: String, val weight: Int, val tags: kotlin.collections.List<String> = emptyList())

class Inventory {
  private val items = mutableListOf<Item>();

  fun add(name: String, weight: Int): Item {
    val item = Item(name, weight, listOf("added"));
    items.add(item)
    return item
  }

  fun heaviest(): Int = items.fold(0) { acc, item -> max(acc, item.weight) }

  fun describe(): String { if (items.isEmpty()) return "empty" else return "${items.size} items, heaviest ${heaviest()}" }
}

fun main() {
    val inventory = Inventory()
    inventory.add("anvil", 90); inventory.add("feather", 1)
    println(inventory.describe())
}
