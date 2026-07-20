package sample

annotation class A19
annotation class B19(val v: Array<String>)
annotation class C19

@A19
@B19(v = [
    "foo",
    "baz",
    "bar"
])
@C19
public suspend fun returnsSomething19() = ""