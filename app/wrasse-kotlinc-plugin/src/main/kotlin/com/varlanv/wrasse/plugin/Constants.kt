package com.varlanv.wrasse.plugin

import org.jetbrains.kotlin.config.CompilerConfigurationKey

const val KEY_ENABLED_STR = "enabled"
const val PLUGIN_ID: String = "com.varlanv.wrasse"
val KEY_ENABLED = CompilerConfigurationKey<Boolean>(KEY_ENABLED_STR)
