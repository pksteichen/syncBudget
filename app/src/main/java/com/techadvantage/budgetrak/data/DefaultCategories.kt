package com.techadvantage.budgetrak.data

import com.techadvantage.budgetrak.ui.strings.AppStrings
import com.techadvantage.budgetrak.ui.strings.EnglishStrings
import com.techadvantage.budgetrak.ui.strings.SpanishStrings

data class DefaultCategoryDef(
    val tag: String,
    val iconName: String,
    val charted: Boolean = true,
    val widgetVisible: Boolean = true
)

val DEFAULT_CATEGORY_DEFS = listOf(
    DefaultCategoryDef("other", "CreditCard"),
    DefaultCategoryDef("recurring_income", "Payments"),
    DefaultCategoryDef("supercharge", "Bolt", charted = true, widgetVisible = false),
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
        "recurring_income" -> names.recurringIncome
        "supercharge" -> names.supercharge
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
