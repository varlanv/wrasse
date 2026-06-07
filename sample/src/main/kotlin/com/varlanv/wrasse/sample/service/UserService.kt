package com.varlanv.wrasse.sample.service

class UserService {

    fun findUser(id: Int): String {
        // This should trigger a wrasse error:
        println("Finding user $id")
        return "User-$id"
    }

    fun log(message: String) {
        // Imagine this goes to a proper logger
        System.err.println(message)
    }
}
