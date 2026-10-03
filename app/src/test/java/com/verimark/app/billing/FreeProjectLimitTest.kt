package com.verimark.app.billing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeProjectLimitTest {

    @Test
    fun freeUserBelowLimitIsNotBlocked() {
        assertFalse(FreeProjectLimit.isCreationBlocked(isPro = false, usedCreations = 0))
        assertFalse(FreeProjectLimit.isCreationBlocked(isPro = false, usedCreations = 1))
        assertFalse(FreeProjectLimit.isCreationBlocked(isPro = false, usedCreations = 2))
    }

    @Test
    fun freeUserAtLimitIsBlocked() {
        assertTrue(FreeProjectLimit.isCreationBlocked(isPro = false, usedCreations = 3))
        assertTrue(FreeProjectLimit.isCreationBlocked(isPro = false, usedCreations = 4))
    }

    @Test
    fun proUserIsNeverBlockedRegardlessOfUsedCreations() {
        assertFalse(FreeProjectLimit.isCreationBlocked(isPro = true, usedCreations = 0))
        assertFalse(FreeProjectLimit.isCreationBlocked(isPro = true, usedCreations = 3))
        assertFalse(FreeProjectLimit.isCreationBlocked(isPro = true, usedCreations = 99))
    }

    @Test
    fun limitConstantIsThree() {
        assertTrue(FreeProjectLimit.FREE_PROJECT_CREATION_LIMIT == 3)
    }
}
