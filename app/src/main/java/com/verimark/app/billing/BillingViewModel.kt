package com.verimark.app.billing

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** UI-ready product summary, free of billing library types. */
data class ProProduct(
    val productId: String,
    val title: String,
    val price: String
)

/** ViewModel for the Pro screen; the only entry point the UI uses for billing. */
class BillingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BillingRepository.get(application)

    val entitlement: StateFlow<EntitlementState> = repository.entitlement
    val purchasing: StateFlow<Boolean> = repository.purchasing
    val events = repository.events

    val products: StateFlow<List<ProProduct>> = repository.products
        .map { list -> list.map { it.toProProduct() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        repository.start()
    }

    fun refresh() = repository.refresh()

    fun restorePurchases() = repository.restorePurchases()

    fun purchase(activity: Activity, productId: String) =
        repository.launchPurchase(activity, productId)

    private fun ProductDetails.toProProduct(): ProProduct {
        val safeTitle = runCatching { title }.getOrDefault(productId)
        val safePrice = runCatching {
            val isSub = productType == BillingClient.ProductType.SUBS
            if (isSub) {
                subscriptionOfferDetails
                    ?.firstOrNull()
                    ?.pricingPhases
                    ?.pricingPhaseList
                    ?.firstOrNull()
                    ?.formattedPrice
            } else {
                oneTimePurchaseOfferDetails?.formattedPrice
            }.orEmpty()
        }.getOrDefault("")
        return ProProduct(productId = productId, title = safeTitle, price = safePrice)
    }
}
