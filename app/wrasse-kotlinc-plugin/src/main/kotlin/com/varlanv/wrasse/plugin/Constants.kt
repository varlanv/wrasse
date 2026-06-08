package com.varlanv.wrasse.plugin

import org.jetbrains.kotlin.config.CompilerConfigurationKey

const val KEY_ENABLED_STR = "enabled"
const val KEY_WARN_ONLY_STR = "warnOnly"
const val PLUGIN_ID: String = "com.varlanv.wrasse"
val KEY_ENABLED = CompilerConfigurationKey<Boolean>(KEY_ENABLED_STR)
val KEY_WARN_ONLY = CompilerConfigurationKey<Boolean>(KEY_WARN_ONLY_STR)
