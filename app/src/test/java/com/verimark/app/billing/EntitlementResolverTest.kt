package com.verimark.app.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementResolverTest {

    private fun owned(id: String, state: PurchaseState, acknowledged: Boolean = true) =
        OwnedPurchase(productId = id, purchaseState = state, isAcknowledged = acknowledged)

    @Test
    fun freeWhenNoPurchases() {
        assertEquals(
            EntitlementState.Free,
            EntitlementResolver.resolve(emptyList(), emptyList())
        )
    }

    @Test
    fun freeWhenOnlyPendingPurchases() {
        val subs = listOf(owned(BillingProducts.PRO_MONTHLY, PurchaseState.PENDING, acknowledged = false))
        assertEquals(EntitlementState.Free, EntitlementResolver.resolve(subs, emptyList()))
    }

    @Test
    fun proMonthlyWhenActiveMonthlySubscription() {
        val subs = listOf(owned(BillingProducts.PRO_MONTHLY, PurchaseState.PURCHASED))
        assertEquals(
            EntitlementState.Pro(ProTier.MONTHLY),
            EntitlementResolver.resolve(subs, emptyList())
        )
    }

    @Test
    fun proYearlyWhenActiveYearlySubscription() {
        val subs = listOf(owned(BillingProducts.PRO_YEARLY, PurchaseState.PURCHASED))
        assertEquals(
            EntitlementState.Pro(ProTier.YEARLY),
            EntitlementResolver.resolve(subs, emptyList())
        )
    }

    @Test
    fun proLifetimeWhenOwnedLifetime() {
        val inApp = listOf(owned(BillingProducts.PRO_LIFETIME, PurchaseState.PURCHASED))
        assertEquals(
            EntitlementState.Pro(ProTier.LIFETIME),
            EntitlementResolver.resolve(emptyList(), inApp)
        )
    }

    @Test
    fun lifetimeWinsOverActiveSubscription() {
        val subs = listOf(owned(BillingProducts.PRO_YEARLY, PurchaseState.PURCHASED))
        val inApp = listOf(owned(BillingProducts.PRO_LIFETIME, PurchaseState.PURCHASED))
        assertEquals(
            EntitlementState.Pro(ProTier.LIFETIME),
            EntitlementResolver.resolve(subs, inApp)
        )
    }

    @Test
    fun unspecifiedPurchaseStateDoesNotGrantPro() {
        val inApp = listOf(owned(BillingProducts.PRO_LIFETIME, PurchaseState.UNSPECIFIED))
        assertEquals(EntitlementState.Free, EntitlementResolver.resolve(emptyList(), inApp))
    }

    @Test
    fun isProFlagMatchesState() {
        assertTrue(EntitlementState.Pro(ProTier.LIFETIME).isPro)
        assertFalse(EntitlementState.Free.isPro)
        assertFalse(EntitlementState.BillingUnavailable.isPro)
        assertFalse(EntitlementState.Loading.isPro)
    }
}
