package com.verimark.app.billing

/**
 * Stable Google Play product IDs for VeriMark Pro.
 *
 * These are the single source of truth for product IDs. Play Console must be
 * configured with the same IDs; the app never hard-codes prices — Google Play
 * provides localized pricing via ProductDetails.
 */
object BillingProducts {

    const val PRO_MONTHLY = "verimark_pro_monthly"
    const val PRO_YEARLY = "verimark_pro_yearly"
    const val PRO_LIFETIME = "verimark_pro_lifetime"

    fun productId(tier: ProTier): String = when (tier) {
        ProTier.MONTHLY -> PRO_MONTHLY
        ProTier.YEARLY -> PRO_YEARLY
        ProTier.LIFETIME -> PRO_LIFETIME
    }
}
