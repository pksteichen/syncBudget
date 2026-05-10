package com.techadvantage.budgetrak.data.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val TAG = "BillingService"
private const val CONNECT_TIMEOUT_MS = 10_000L

/**
 * Snapshot of Play Billing state for BudgeTrak's two products. Returned
 * by [BillingService.queryAll]. Null fields mean the product or purchase
 * isn't available (offline, sku catalog still propagating, or the user
 * doesn't own the product).
 */
data class BillingState(
    val paidUpgradeDetails: ProductDetails?,
    val subscriberDetails: ProductDetails?,
    val paidUpgradePurchase: Purchase?,
    val subscriberPurchase: Purchase?,
    val paidUpgradePrice: String?,
    val subscriberPrice: String?,
    val subscriberOfferToken: String?
)

/**
 * Wraps Google Play Billing Library 7+ for BudgeTrak's Layer 1 IAP.
 *
 * The [purchasesUpdated] callback fires when a purchase flow launched via
 * [launchPaidUpgrade] / [launchSubscribe] completes. Callers should also
 * invoke [queryAll] on app start and [Activity.onResume] to detect
 * cross-device entitlements, sub expiry, and refunds — Play does not
 * push us those events.
 *
 * Layer 2 server-side verification (App Check + Cloud Function token check)
 * is deferred to post-launch — see project_prelaunch_todo.md item #3 in the
 * Post-launch section. Layer 1 has a 7-day TTL applied in MainViewModel
 * to bound the buy-then-block-internet-then-refund attack window.
 */
class BillingService(
    context: Context,
    purchasesUpdated: PurchasesUpdatedListener
) {
    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdated)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    private val connectMutex = Mutex()

    /** Blocks until the BillingClient is connected, or 10s timeout. */
    private suspend fun ensureConnected(): Boolean {
        if (client.isReady) return true
        return connectMutex.withLock {
            if (client.isReady) return@withLock true
            withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                suspendCancellableCoroutine<Boolean> { cont ->
                    client.startConnection(object : BillingClientStateListener {
                        override fun onBillingSetupFinished(result: BillingResult) {
                            if (cont.isActive) {
                                cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                            }
                        }
                        override fun onBillingServiceDisconnected() {
                            // Don't resume — only setup-finished should resolve the await.
                            // Auto-reconnect happens on next client method call.
                        }
                    })
                }
            } ?: false
        }
    }

    /** One-call snapshot of both products' details + active purchases. */
    suspend fun queryAll(): BillingState? {
        if (!ensureConnected()) {
            Log.w(TAG, "queryAll: BillingClient not connected")
            return null
        }
        val productDetails = queryProductDetails()
        val (inappPurchases, subPurchases) = queryPurchases()

        val paidDetails = productDetails[BillingProducts.PAID_UPGRADE]
        val subDetails = productDetails[BillingProducts.SUBSCRIBER]
        val firstSubOffer = subDetails?.subscriptionOfferDetails?.firstOrNull()

        return BillingState(
            paidUpgradeDetails = paidDetails,
            subscriberDetails = subDetails,
            paidUpgradePurchase = inappPurchases.firstOrNull {
                BillingProducts.PAID_UPGRADE in it.products &&
                    it.purchaseState == Purchase.PurchaseState.PURCHASED
            },
            subscriberPurchase = subPurchases.firstOrNull {
                BillingProducts.SUBSCRIBER in it.products &&
                    it.purchaseState == Purchase.PurchaseState.PURCHASED
            },
            paidUpgradePrice = paidDetails?.oneTimePurchaseOfferDetails?.formattedPrice,
            subscriberPrice = firstSubOffer?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice,
            subscriberOfferToken = firstSubOffer?.offerToken
        )
    }

    private suspend fun queryProductDetails(): Map<String, ProductDetails> {
        // Billing 7+ requires a single product type per query — INAPP and SUBS
        // can't share one call. Issue them separately and merge.
        val inappParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(BillingProducts.PAID_UPGRADE)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()
        val subsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(BillingProducts.SUBSCRIBER)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()
        val inappList = suspendCancellableCoroutine<List<ProductDetails>> { cont ->
            client.queryProductDetailsAsync(inappParams) { result, list ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(TAG, "queryProductDetails(INAPP): ${result.debugMessage}")
                }
                if (cont.isActive) cont.resume(list)
            }
        }
        val subsList = suspendCancellableCoroutine<List<ProductDetails>> { cont ->
            client.queryProductDetailsAsync(subsParams) { result, list ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(TAG, "queryProductDetails(SUBS): ${result.debugMessage}")
                }
                if (cont.isActive) cont.resume(list)
            }
        }
        return (inappList + subsList).associateBy { it.productId }
    }

    /**
     * Raw unfiltered queryPurchasesAsync results — used by the Restore Purchases
     * diagnostic dump so testers can see purchases in any state (PENDING,
     * UNSPECIFIED_STATE, etc.), not just the PURCHASED-filtered ones [queryAll]
     * exposes. Null if BillingClient is disconnected.
     */
    suspend fun queryRawPurchases(): Pair<List<Purchase>, List<Purchase>>? {
        if (!ensureConnected()) return null
        return queryPurchases()
    }

    private suspend fun queryPurchases(): Pair<List<Purchase>, List<Purchase>> {
        val inappParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP).build()
        val subsParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS).build()
        val inapp = suspendCancellableCoroutine<List<Purchase>> { cont ->
            client.queryPurchasesAsync(inappParams) { _, list ->
                if (cont.isActive) cont.resume(list)
            }
        }
        val subs = suspendCancellableCoroutine<List<Purchase>> { cont ->
            client.queryPurchasesAsync(subsParams) { _, list ->
                if (cont.isActive) cont.resume(list)
            }
        }
        return inapp to subs
    }

    /** Launch the in-app purchase flow for [BillingProducts.PAID_UPGRADE]. */
    suspend fun launchPaidUpgrade(activity: Activity, details: ProductDetails): BillingResult {
        if (!ensureConnected()) return disconnectedResult()
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        return client.launchBillingFlow(activity, flowParams)
    }

    /** Launch the subscription flow. [offerToken] from the first subscriptionOfferDetails. */
    suspend fun launchSubscribe(
        activity: Activity,
        details: ProductDetails,
        offerToken: String
    ): BillingResult {
        if (!ensureConnected()) return disconnectedResult()
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        return client.launchBillingFlow(activity, flowParams)
    }

    /** Acknowledge a purchase. Must run within 3 days or Play auto-refunds. */
    suspend fun acknowledge(purchase: Purchase): BillingResult {
        if (purchase.isAcknowledged) return okResult()
        if (!ensureConnected()) return disconnectedResult()
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        return suspendCancellableCoroutine { cont ->
            client.acknowledgePurchase(params) { result ->
                if (cont.isActive) cont.resume(result)
            }
        }
    }

    private fun disconnectedResult() = BillingResult.newBuilder()
        .setResponseCode(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED).build()

    private fun okResult() = BillingResult.newBuilder()
        .setResponseCode(BillingClient.BillingResponseCode.OK).build()
}
