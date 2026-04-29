package com.techadvantage.budgetrak.data.ocr

data class OcrCategoryAmount(
    val categoryId: Int,
    val amount: Double
)

data class OcrResult(
    val merchant: String,
    val merchantLegalName: String? = null,
    val date: String,
    val amount: Double,
    val categoryAmounts: List<OcrCategoryAmount>? = null,
    val lineItems: List<String>? = null,
    val notes: String? = null
)

sealed class OcrState {
    object Idle : OcrState()
    object Loading : OcrState()
    data class Success(val result: OcrResult) : OcrState()
    data class Failed(val message: String) : OcrState()
    /** Distinguished from Failed so the UI picks an offline-specific toast
     *  without string-matching the message field. */
    object Offline : OcrState()
}
