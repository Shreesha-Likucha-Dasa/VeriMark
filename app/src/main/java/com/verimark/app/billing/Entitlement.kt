package com.verimark.app.billing

/** The Pro tier a user is entitled to. */
enum class ProTier { MONTHLY, YEARLY, LIFETIME }

/** Billing-library-independent purchase state snapshot. */
enum class PurchaseState { UNSPECIFIED, PURCHASED, PENDING }

/** The current Pro entitlement, computed from Google Play purchase state. */
sealed class EntitlementState {

    /** Billing has not finished its first query yet. */
    data object Loading : EntitlementState()

    /** Google Play Billing could not be reached; entitlement is unknown. */
    data object BillingUnavailable : EntitlementState()

    /** No active Pro purchase. */
    data object Free : EntitlementState()

    /** An active Pro purchase exists for [tier]. */
    data class Pro(val tier: ProTier) : EntitlementState()

    val isPro: Boolean get() = this is Pro
}

/** A snapshot of an owned purchase, decoupled from the billing library. */
data class OwnedPurchase(
    val productId: String,
    val purchaseState: PurchaseState,
    val isAcknowledged: Boolean
)

/**
 * Pure, testable entitlement logic. Google Play purchase state is the source of
 * truth; only PURCHASED items grant Pro. Expired/cancelled subscriptions are
 * simply absent from the owned set and therefore do not grant Pro.
 */
object EntitlementResolver {

    fun resolve(subscriptions: List<OwnedPurchase>, inApp: List<OwnedPurchase>): EntitlementState {
        val active = { purchase: OwnedPurchase ->
            purchase.purchaseState == PurchaseState.PURCHASED
        }

        if (inApp.any { it.productId == BillingProducts.PRO_LIFETIME && active(it) }) {
            return EntitlementState.Pro(ProTier.LIFETIME)
        }
        if (subscriptions.any { it.productId == BillingProducts.PRO_YEARLY && active(it) }) {
            return EntitlementState.Pro(ProTier.YEARLY)
        }
        if (subscriptions.any { it.productId == BillingProducts.PRO_MONTHLY && active(it) }) {
            return EntitlementState.Pro(ProTier.MONTHLY)
        }
        return EntitlementState.Free
    }
}
