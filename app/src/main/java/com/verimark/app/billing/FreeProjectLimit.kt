package com.verimark.app.billing

/**
 * Free-tier project creation limit: 3 lifetime NEW project creations.
 *
 * The limit is based on a persistent lifetime counter, NOT on the number of
 * currently existing projects. Deleting a project never refunds a creation.
 */
object FreeProjectLimit {
    const val FREE_PROJECT_CREATION_LIMIT = 3

    /** True when a FREE user has consumed all lifetime creations. Pro users are never blocked. */
    fun isCreationBlocked(isPro: Boolean, usedCreations: Int): Boolean =
        !isPro && usedCreations >= FREE_PROJECT_CREATION_LIMIT
}
