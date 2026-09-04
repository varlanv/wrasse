package sample

interface Publisher

interface Caller

interface PubSub : Publisher, Caller {
    fun call()

    fun publish()
}