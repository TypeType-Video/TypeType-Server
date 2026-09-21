package dev.typetype.server.services

class AccessControlService(
    private val settingsService: SettingsService,
    private val allowedChannelsService: AllowedChannelsService,
    private val allowedPlaylistsService: AllowedPlaylistsService,
    private val adminSettingsService: AdminSettingsService,
) {
    suspend fun profileFor(userId: String?, role: String? = null): AccessControlProfile {
        if (role == "admin") return AccessControlProfile.unrestricted
        val globalAllowList = adminSettingsService.get().accessMode.toAccessMode() == ACCESS_MODE_ALLOW_LIST
        if (userId == null) return if (globalAllowList) allowListProfile(null) else AccessControlProfile.unrestricted
        val policy = settingsService.getAccessModePolicy(userId)
        if (globalAllowList) {
            return if (policy.adminManaged && policy.accessMode == ACCESS_MODE_UNRESTRICTED) {
                AccessControlProfile.unrestricted
            } else {
                allowListProfile(userId)
            }
        }
        return if (policy.accessMode == ACCESS_MODE_ALLOW_LIST) allowListProfile(userId) else AccessControlProfile.unrestricted
    }

    private suspend fun allowListProfile(userId: String?): AccessControlProfile = AccessControlProfile(
        enabled = true,
        channels = if (userId == null) {
            allowedChannelsService.getGlobalChannels()
        } else {
            allowedChannelsService.getChannels(userId)
        },
        playlists = if (userId == null) {
            allowedPlaylistsService.getGlobalPlaylists()
        } else {
            allowedPlaylistsService.getPlaylists(userId)
        },
    )
}
