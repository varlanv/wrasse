package com.varlanv.wrasse.sample

import com.varlanv.wrasse.sample.service.UserService

fun main() {
    val service = UserService()

    // These should trigger wrasse errors:
    println("Starting app")
    print("Loading...")

    // This is fine — it's our own function, not kotlin.io.println:
    service.log("App started")
}
