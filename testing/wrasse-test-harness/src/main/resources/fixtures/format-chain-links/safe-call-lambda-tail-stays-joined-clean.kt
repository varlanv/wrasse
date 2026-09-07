package sample

class Frame(var depth: Int, val name: String)

fun close(frames: MutableList<Frame>, name: String) {
    frames.lastOrNull()?.let { outer ->
        outer.depth = outer.depth + 1
        frames.add(Frame(outer.depth, name))
    }
}

// expect-clean
