package sample

class Foo14 {
    protected tailrec fun bar(s: String): String = bar(s.substringBeforeLast("\n"))
}