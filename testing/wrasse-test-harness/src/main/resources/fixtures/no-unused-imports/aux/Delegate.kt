package sample.auxdelegate

import kotlin.reflect.KProperty

class Box

operator fun <T> Box.provideDelegate(thisRef: T, prop: KProperty<*>) = lazy { 1 }
