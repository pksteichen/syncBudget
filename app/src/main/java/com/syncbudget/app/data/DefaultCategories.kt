package com.syncbudget.app.data

import com.syncbudget.app.ui.strings.AppStrings
import com.syncbudget.app.ui.strings.EnglishStrings
import com.syncbudget.app.ui.strings.SpanishStrings

data class DefaultCategoryDef(val tag: String, val iconName: String)

val DEFAULT_CATEGORY_DEFS = listOf(
    DefaultCategoryDef("other", "CreditCard"),
    DefaultCategoryDef("recurring", "Sync"),
    DefaultCategoryDef("amortization", "Schedule"),
    DefaultCategoryDef("recurring_income", "Payments"),
    DefaultCategoryDef("transportation", "DirectionsCar"),
    DefaultCategoryDef("groceries", "LocalGroceryStore"),
    DefaultCategoryDef("entertainment", "SportsEsports"),
    DefaultCategoryDef("home_supplies", "Home"),
    DefaultCategoryDef("restaurants", "Restaurant"),
    DefaultCategoryDef("charity", "VolunteerActivism"),
    DefaultCategoryDef("clothes", "Checkroom")
)

fun getDefaultCategoryName(tag: String, strings: AppStrings): String? {
    val names = strings.defaultCategoryNames
    return when (tag) {
        "other" -> names.other
        "recurring" -> names.recurring
        "amortization" -> names.amortization
        "recurring_income" -> names.recurringIncome
        "transportation" -> names.transportation
        "groceries" -> names.groceries
        "entertainment" -> names.entertainment
        "home_supplies" -> names.homeSupplies
        "restaurants" -> names.restaurants
        "charity" -> names.charity
        "clothes" -> names.clothes
        else -> null
    }
}

fun getAllKnownNamesForTag(tag: String): Set<String> {
    val result = mutableSetOf<String>()
    for (strings in listOf<AppStrings>(EnglishStrings, SpanishStrings)) {
        getDefaultCategoryName(tag, strings)?.let { result.add(it) }
    }
    return result
}
