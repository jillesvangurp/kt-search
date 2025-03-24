package com.jillesvangurp.searchdsls

enum class SearchEngineVariant { ES7, ES8, ES9, OS1, OS2, OS3 }
annotation class VariantRestriction(vararg val variant: SearchEngineVariant)
