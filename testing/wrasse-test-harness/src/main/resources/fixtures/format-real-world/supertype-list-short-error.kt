package sample

interface Publisher
interface Caller

interface PubSub :
    Publisher,
    Caller {

    fun call()
    fun publish()
}

// expect-error 1:1 format "File is not wrasse-formatted"
