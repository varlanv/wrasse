package sample

import sample.auxchain.A

val result = A().b.doSomething()

fun run() {
    for (item in A().items) {
        item.compute()
    }
}