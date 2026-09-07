package sample

class Users(val name: String) {
    operator fun get(key: String): String = key
}

class JoinType {
    companion object {
        val INNER = JoinType()
    }
}

class Join(val users: Users) {
    fun join(
        alias: Users,
        type: JoinType,
        left: String,
        right: String,
    ): Join = this

    fun selectAll(): Join = this

    fun toList(): List<String> = listOf(users.name)
}

class Query(val users: Users, val usersAlias: Users) {
    fun run(expAlias: String): List<String> {
        val resultRows = Join(
            users,
        ).join(usersAlias, JoinType.INNER, usersAlias[expAlias], users.name).selectAll().toList()
        return resultRows
    }
}

// expect-error 1:1 format "File is not wrasse-formatted"
