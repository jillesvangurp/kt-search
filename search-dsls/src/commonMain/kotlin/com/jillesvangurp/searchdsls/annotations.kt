package com.jillesvangurp.searchdsls

enum class SearchEngineVariant { ES7, ES8, OS1, OS2 }
annotation class VariantRestriction(vararg val variant: SearchEngineVariant)
