package com.verimark.app.billing

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails

/**
 * Selects the subscription offer to purchase: the first offer whose first
 * pricing phase has a non-blank formatted price (i.e. the paid base plan),
 * falling back to the first offer if none has a visible price.
 */
fun ProductDetails.selectSubscriptionOffer(): ProductDetails.SubscriptionOfferDetails? =
    subscriptionOfferDetails?.firstOrNull { offer ->
        offer.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice?.isNotBlank() == true
    } ?: subscriptionOfferDetails?.firstOrNull()

/** Localized price string for display, or empty if unavailable. */
fun ProductDetails.priceString(): String = runCatching {
    if (productType == BillingClient.ProductType.SUBS) {
        selectSubscriptionOffer()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
    } else {
        oneTimePurchaseOfferDetails?.formattedPrice
    }.orEmpty()
}.getOrDefault("")
