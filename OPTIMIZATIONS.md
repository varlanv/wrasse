# Pending optimizations

## `WNode.descendants()` — recursive `yieldAll` overhead

`descendants()` creates a chain of coroutine generators proportional to tree depth. Each `next()` call traverses the full chain. For depth ~15, every node pays ~15 suspend/resume hops.

Replace with an explicit stack-based iterator:

```kotlin
fun descendants(): Sequence<WNode> = Sequence {
    iterator {
        val stack = ArrayDeque<WNode>()
        for (i in children.indices.reversed()) stack.addLast(children[i])
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            yield(node)
            for (i in node.children.indices.reversed()) stack.addLast(node.children[i])
        }
    }
}
```

O(1) per node regardless of depth.

## `WNode.siblingAt()` — `indexOf(this)` linear scan

`nextSibling`/`prevSibling` use `siblings.indexOf(this)` which is an identity scan over the parent's children list. Cached via `lazy`, so paid once per node that needs it, but still O(children) per first access.

Fix: store the child index during tree construction in `LightTreeAdapter`, then `siblingAt` becomes a direct index lookup.
