package dev.typetype.server.services

sealed interface SubscriptionSelection {
    val cursorKey: String

    data object All : SubscriptionSelection {
        override val cursorKey: String = "all"
    }

    data object Ungrouped : SubscriptionSelection {
        override val cursorKey: String = "ungrouped"
    }

    data class Group(val id: String) : SubscriptionSelection {
        override val cursorKey: String = "group:$id"
    }
}
