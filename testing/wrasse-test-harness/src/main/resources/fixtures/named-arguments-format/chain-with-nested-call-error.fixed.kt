package sample

class Key(val asset: String, val exchange: String)

class Item(val asset: String, val exchange: String)

fun select(items: List<Item>, rows: Map<Key, Int>): Set<Item> = items.filter {
    !rows.containsKey(
        key = Key(
            asset = it.asset,
            exchange = it.exchange,
        ),
    )
}.mapTo(mutableSetOf()) { Item(it.asset, it.exchange) }