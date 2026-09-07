package com.varlanv.wrasse.plugin

import org.jetbrains.kotlin.config.CompilerConfigurationKey

const val KEY_ENABLED_STR = "enabled"
const val KEY_WARN_ONLY_STR = "warnOnly"
const val KEY_FIX_OUTPUT_DIR_STR = "fixOutputDir"
const val KEY_DUMP_RESOLVED_USAGE_STR = "dumpResolvedUsage"
const val KEY_EXCLUDED_ROOT_STR = "excludedRoot"
const val KEY_PROJECT_DIR_STR = "projectDir"
const val PLUGIN_ID: String = "com.varlanv.wrasse"
val KEY_ENABLED = CompilerConfigurationKey<Boolean>(KEY_ENABLED_STR)
val KEY_WARN_ONLY = CompilerConfigurationKey<Boolean>(KEY_WARN_ONLY_STR)
val KEY_FIX_OUTPUT_DIR = CompilerConfigurationKey<String>(KEY_FIX_OUTPUT_DIR_STR)
val KEY_DUMP_RESOLVED_USAGE = CompilerConfigurationKey<Boolean>(KEY_DUMP_RESOLVED_USAGE_STR)
val KEY_EXCLUDED_ROOT = CompilerConfigurationKey<List<String>>(KEY_EXCLUDED_ROOT_STR)
val KEY_PROJECT_DIR = CompilerConfigurationKey<String>(KEY_PROJECT_DIR_STR)
