package sample

interface Api

class Impl(a: Api) : Api by a

// expect-clean
