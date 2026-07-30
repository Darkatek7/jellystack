package dev.jellystack.core.jellyseerr

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JellyseerrRequestCapabilitiesTest {
    @Test
    fun standardRequestPermissionAllowsMoviesAndShowsOnly() {
        val capabilities =
            JellyseerrRequestCapabilities.fromPermissions(JellyseerrPermission.REQUEST)

        assertTrue(capabilities.canRequestMovie)
        assertTrue(capabilities.canRequestTv)
        assertFalse(capabilities.canRequest4kMovie)
        assertFalse(capabilities.canRequest4kTv)
        assertFalse(capabilities.canUseAdvancedRequests)
        assertFalse(capabilities.canManageRequests)
    }

    @Test
    fun mediaSpecificAnd4kPermissionsRemainIndependent() {
        val capabilities =
            JellyseerrRequestCapabilities.fromPermissions(
                JellyseerrPermission.REQUEST_MOVIE or
                    JellyseerrPermission.REQUEST_4K_TV,
            )

        assertTrue(capabilities.canRequestMovie)
        assertFalse(capabilities.canRequestTv)
        assertFalse(capabilities.canRequest4kMovie)
        assertTrue(capabilities.canRequest4kTv)
        assertTrue(capabilities.canRequest(JellyseerrMediaType.MOVIE, JellyseerrRequestVariant.STANDARD))
        assertTrue(capabilities.canRequest(JellyseerrMediaType.TV, JellyseerrRequestVariant.FOUR_K))
    }

    @Test
    fun manageRequestsEnablesAdvancedManagementButNotMediaRequestTypes() {
        val capabilities =
            JellyseerrRequestCapabilities.fromPermissions(JellyseerrPermission.MANAGE_REQUESTS)

        assertTrue(capabilities.canUseAdvancedRequests)
        assertTrue(capabilities.canManageRequests)
        assertFalse(capabilities.canRequestMovie)
        assertFalse(capabilities.canRequestTv)
    }

    @Test
    fun adminCanUseEveryRequestCapability() {
        val capabilities =
            JellyseerrRequestCapabilities.fromPermissions(JellyseerrPermission.ADMIN)

        assertTrue(capabilities.canRequestMovie)
        assertTrue(capabilities.canRequestTv)
        assertTrue(capabilities.canRequest4kMovie)
        assertTrue(capabilities.canRequest4kTv)
        assertTrue(capabilities.canUseAdvancedRequests)
        assertTrue(capabilities.canManageRequests)
    }
}
