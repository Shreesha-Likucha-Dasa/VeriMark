package com.verimark.app.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** One-shot UI messages produced by billing operations. */
sealed class BillingEvent {
    data class Message(val text: String) : BillingEvent()
}

/**
 * The single billing manager for VeriMark.
 *
 * It owns the [BillingClient] lifecycle, product queries, purchase queries,
 * purchase launch, acknowledgement, restoration, and entitlement state. No other
 * component may create its own [BillingClient].
 */
class BillingRepository private constructor(private val context: Context) {

    companion object {
        const val PROJECT_LIMIT = 3
        const val MARKER_LIMIT = 20

        private const val PREFS = "verimark_billing"
        private const val KEY_ENTITLEMENT = "cached_entitlement"

        @Volatile
        private var INSTANCE: BillingRepository? = null

        fun get(context: Context): BillingRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: BillingRepository(context.applicationContext).also { INSTANCE = it }
            }
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _entitlement =
        MutableStateFlow(readCachedEntitlement() ?: EntitlementState.Loading)
    val entitlement: StateFlow<EntitlementState> = _entitlement.asStateFlow()

    private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())
    val products: StateFlow<List<ProductDetails>> = _products.asStateFlow()

    private val _purchasing = MutableStateFlow(false)
    val purchasing: StateFlow<Boolean> = _purchasing.asStateFlow()

    private val _events = MutableSharedFlow<BillingEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<BillingEvent> = _events.asSharedFlow()

    private var client: BillingClient? = null
    private var connected = false
    private var connecting = false

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        try {
            handlePurchasesUpdated(result, purchases)
        } catch (_: Exception) {
            _purchasing.value = false
        }
    }

    /** Connects once, then queries products and entitlement. Safe to call repeatedly. */
    fun start() {
        if (connected || connecting) return
        try {
            val c = client ?: createClient().also { client = it }
            connecting = true
            c.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    connecting = false
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        connected = true
                        queryProducts()
                        refreshEntitlement()
                    } else {
                        connected = false
                        if (_entitlement.value == EntitlementState.Loading) {
                            _entitlement.value = EntitlementState.BillingUnavailable
                        }
                    }
                }

                override fun onBillingServiceDisconnected() {
                    connecting = false
                    connected = false
                    // Keep the last known entitlement so offline Pro users stay Pro.
                }
            })
        } catch (_: Exception) {
            connecting = false
            connected = false
            if (_entitlement.value == EntitlementState.Loading) {
                _entitlement.value = EntitlementState.BillingUnavailable
            }
        }
    }

    /** Re-queries products and entitlement; connects first if needed. */
    fun refresh() {
        if (client?.isReady == true) {
            queryProducts()
            refreshEntitlement()
        } else {
            start()
        }
    }

    /** Launches the Google Play purchase flow for [productId]. */
    fun launchPurchase(activity: Activity, productId: String) {
        try {
            val c = client
            if (c == null || !c.isReady) {
                start()
                emit("Google Play isn't available right now. Please try again.")
                return
            }
            val details = _products.value.firstOrNull { it.productId == productId }
            if (details == null) {
                emit("This item isn't available right now. Please try again.")
                return
            }
            _purchasing.value = true
            val params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(details)
                            .build()
                    )
                )
                .build()
            val result = c.launchBillingFlow(activity, params)
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                _purchasing.value = false
                emitLaunchError(result.responseCode)
            }
        } catch (_: Exception) {
            _purchasing.value = false
            emit("Google Play isn't available right now. Please try again.")
        }
    }

    /** Queries existing purchases and reports the resulting entitlement. */
    fun restorePurchases() {
        val c = client
        if (c == null || !c.isReady) {
            start()
            emit("Google Play isn't available right now. Please try again.")
            return
        }
        refreshEntitlement { state ->
            if (state.isPro) emit("Purchases restored") else emit("No active purchase found")
        }
    }

    private fun createClient(): BillingClient =
        BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()

    private fun queryProducts() {
        val c = client ?: return
        if (!c.isReady) return
        // Billing requires one query per product type: subscriptions and
        // one-time (INAPP) products must be queried separately.
        val subsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    product(BillingProducts.PRO_MONTHLY, BillingClient.ProductType.SUBS),
                    product(BillingProducts.PRO_YEARLY, BillingClient.ProductType.SUBS)
                )
            )
            .build()
        val inAppParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    product(BillingProducts.PRO_LIFETIME, BillingClient.ProductType.INAPP)
                )
            )
            .build()
        try {
            c.queryProductDetailsAsync(subsParams) { subsResult, subs ->
                val subList = try {
                    if (subsResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        subs.orEmpty()
                    } else {
                        emptyList()
                    }
                } catch (_: Exception) {
                    emptyList()
                }
                try {
                    c.queryProductDetailsAsync(inAppParams) { inAppResult, inApps ->
                        val inAppList = try {
                            if (inAppResult.responseCode == BillingClient.BillingResponseCode.OK) {
                                inApps.orEmpty()
                            } else {
                                emptyList()
                            }
                        } catch (_: Exception) {
                            emptyList()
                        }
                        _products.value = subList + inAppList
                    }
                } catch (_: Exception) {
                    _products.value = subList
                }
            }
        } catch (_: Exception) {
            _products.value = emptyList()
        }
    }

    private fun product(id: String, type: String): QueryProductDetailsParams.Product =
        QueryProductDetailsParams.Product.newBuilder()
            .setProductId(id)
            .setProductType(type)
            .build()

    private fun refreshEntitlement(onDone: ((EntitlementState) -> Unit)? = null) {
        val c = client ?: return
        if (!c.isReady) {
            onDone?.invoke(_entitlement.value)
            return
        }
        val subsParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        try {
            c.queryPurchasesAsync(subsParams) { subsResult, subs ->
                try {
                    if (subsResult.responseCode != BillingClient.BillingResponseCode.OK) {
                        onDone?.invoke(_entitlement.value)
                        return@queryPurchasesAsync
                    }
                    val inAppParams = QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                    c.queryPurchasesAsync(inAppParams) { inAppResult, inApps ->
                        try {
                            if (inAppResult.responseCode != BillingClient.BillingResponseCode.OK) {
                                onDone?.invoke(_entitlement.value)
                                return@queryPurchasesAsync
                            }
                            val subList = subs.orEmpty()
                            val inAppList = inApps.orEmpty()
                            acknowledgeIfNeeded(subList + inAppList)
                            val state = EntitlementResolver.resolve(
                                subscriptions = subList.map { it.toOwnedPurchase() },
                                inApp = inAppList.map { it.toOwnedPurchase() }
                            )
                            setEntitlement(state)
                            onDone?.invoke(state)
                        } catch (_: Exception) {
                            onDone?.invoke(_entitlement.value)
                        }
                    }
                } catch (_: Exception) {
                    onDone?.invoke(_entitlement.value)
                }
            }
        } catch (_: Exception) {
            onDone?.invoke(_entitlement.value)
        }
    }

    /** Acknowledges eligible purchases exactly once (PURCHASED and not yet acknowledged). */
    private fun acknowledgeIfNeeded(purchases: List<Purchase>) {
        val c = client ?: return
        if (!c.isReady) return
        purchases.forEach { purchase ->
            try {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
                    val params = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    c.acknowledgePurchase(params) { /* acknowledgement is best-effort */ }
                }
            } catch (_: Exception) {
                // Ignore; entitlement remains consistent with Google Play state.
            }
        }
    }

    private fun handlePurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        _purchasing.value = false
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val owned = purchases.orEmpty()
                if (owned.isNotEmpty()) {
                    acknowledgeIfNeeded(owned)
                    refreshEntitlement()
                    when {
                        owned.any { it.purchaseState == Purchase.PurchaseState.PURCHASED } ->
                            emit("Welcome to VeriMark Pro!")
                        owned.any { it.purchaseState == Purchase.PurchaseState.PENDING } ->
                            emit("Your purchase is pending and may take a moment to activate.")
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> emit("Purchase cancelled.")
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                refreshEntitlement()
                emit("You already own this item.")
            }
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ->
                emit("Google Play isn't available right now. Please try again.")
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE ->
                emit("This item isn't available.")
            BillingClient.BillingResponseCode.DEVELOPER_ERROR ->
                emit("Something went wrong with billing. Please try again later.")
            BillingClient.BillingResponseCode.ERROR ->
                emit("Something went wrong. Please try again.")
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED ->
                emit("This device doesn't support Google Play billing.")
            BillingClient.BillingResponseCode.NETWORK_ERROR ->
                emit("Check your connection and try again.")
            else -> emit("Something went wrong. Please try again.")
        }
    }

    private fun emitLaunchError(responseCode: Int) {
        when (responseCode) {
            BillingClient.BillingResponseCode.USER_CANCELED -> emit("Purchase cancelled.")
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                refreshEntitlement()
                emit("You already own this item.")
            }
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE ->
                emit("This item isn't available.")
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ->
                emit("Google Play isn't available right now. Please try again.")
            BillingClient.BillingResponseCode.DEVELOPER_ERROR ->
                emit("Something went wrong with billing. Please try again later.")
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED ->
                emit("This device doesn't support Google Play billing.")
            BillingClient.BillingResponseCode.NETWORK_ERROR ->
                emit("Check your connection and try again.")
            else -> emit("Something went wrong. Please try again.")
        }
    }

    private fun setEntitlement(state: EntitlementState) {
        _entitlement.value = state
        cacheEntitlement(state)
    }

    private fun cacheEntitlement(state: EntitlementState) {
        val value = when (state) {
            is EntitlementState.Pro -> "PRO_${state.tier.name}"
            EntitlementState.Free -> "FREE"
            else -> return
        }
        prefs.edit().putString(KEY_ENTITLEMENT, value).apply()
    }

    private fun readCachedEntitlement(): EntitlementState? =
        when (prefs.getString(KEY_ENTITLEMENT, null)) {
            "FREE" -> EntitlementState.Free
            "PRO_MONTHLY" -> EntitlementState.Pro(ProTier.MONTHLY)
            "PRO_YEARLY" -> EntitlementState.Pro(ProTier.YEARLY)
            "PRO_LIFETIME" -> EntitlementState.Pro(ProTier.LIFETIME)
            else -> null
        }

    private fun emit(text: String) {
        _events.tryEmit(BillingEvent.Message(text))
    }

    private fun Purchase.toOwnedPurchase() = OwnedPurchase(
        productId = products.firstOrNull().orEmpty(),
        purchaseState = when (purchaseState) {
            Purchase.PurchaseState.PURCHASED -> PurchaseState.PURCHASED
            Purchase.PurchaseState.PENDING -> PurchaseState.PENDING
            else -> PurchaseState.UNSPECIFIED
        },
        isAcknowledged = isAcknowledged
    )
}
