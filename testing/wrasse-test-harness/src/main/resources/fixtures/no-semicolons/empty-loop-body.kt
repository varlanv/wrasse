package sample

fun busy() {
    while (System.currentTimeMillis() < 0) {}
}

// expect-clean
