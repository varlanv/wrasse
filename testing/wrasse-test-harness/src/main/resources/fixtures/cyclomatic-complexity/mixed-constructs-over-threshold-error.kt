package sample

fun riskyCall() {
}

fun f(items: List<Int>): Int {
    var total = 0
    for (i in items) {
        if (i > 0 && i < 100) {
            total += i
        } else if (i == -1 || i == -2) {
            continue
        }
        when (i) {
            1 -> total += 1
            2 -> total += 2
            else -> total += 0
        }
        if (total > 1000) break
    }
    try {
        riskyCall()
    } catch (e: IllegalStateException) {
        total = -1
    } catch (e: Exception) {
        total = -2
    } finally {
        total += 0
    }
    val maybeNull: Int? = if (total > 0) total else null
    return maybeNull ?: 0
}

// expect-error 6:5 cyclomatic-complexity "Function 'f' has a cyclomatic complexity of 16; the maximum allowed is 14"
