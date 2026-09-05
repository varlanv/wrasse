package sample

class Key(val asset: String, val exchange: String)

class Item(val asset: String, val exchange: String)

fun select(items: List<Item>, rows: Map<Key, Int>): Set<Item> =
    items
        .filter {
            !rows.containsKey(
                Key(it.asset, it.exchange),
            )
        }
        .mapTo(mutableSetOf()) { Item(it.asset, it.exchange) }

// expect-error 1:1 format "File is not wrasse-formatted"
// expect-error 10:30 named-arguments "Positional arguments should be named"
// expect-error 11:20 named-arguments "Positional arguments should be named"
