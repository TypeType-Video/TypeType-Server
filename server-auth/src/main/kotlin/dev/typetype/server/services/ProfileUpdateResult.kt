package dev.typetype.server.services

sealed class ProfileUpdateResult {
    data object Updated : ProfileUpdateResult()
    data object UsernameInvalidLength : ProfileUpdateResult()
    data object UsernameInvalidFormat : ProfileUpdateResult()
    data object UsernameTaken : ProfileUpdateResult()
    data object BioTooLong : ProfileUpdateResult()
    data object UserNotFound : ProfileUpdateResult()
}
