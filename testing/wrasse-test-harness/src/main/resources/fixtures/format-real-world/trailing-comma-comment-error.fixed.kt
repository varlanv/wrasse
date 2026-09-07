package sample

class Config {
    companion object {
        fun custom(): Config = Config()
    }

    fun setResponseTimeout(t: Int): Config = this

    fun build(): Config = this
}

class Builder {
    fun setDefaultRequestConfig(c: Config): Builder = this

    fun disableRetries(): Builder = this

    fun build(): Builder = this
}

val client = Builder()
    .setDefaultRequestConfig(
        Config.custom().setResponseTimeout(5).build(),
        // Retries belong to the limiters.
    )
    .disableRetries()
    .build()