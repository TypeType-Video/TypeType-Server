package dev.typetype.server.services

internal const val ERROR_RETRY_MS = 1_000L
internal const val FETCH_RETRY_DELAY_MS = 100L
internal const val FETCH_ATTEMPTS = 2
internal const val SEQUENTIAL_PUMPS = 6
internal const val SEQUENTIAL_FILL_AHEAD_MS = 8_000L
internal const val INITIAL_SEQUENTIAL_LIMIT_MS = 10_000L
internal const val PLAYER_TIME_OFFSET_MS = 5L
internal const val REWIND_GAP_MS = 2_000L
internal const val DEMAND_FORWARD_JUMP_MS = 30_000L
internal const val READAHEAD_CUSHION_MS = 60_000L
internal const val BACK_BUFFER_MS = 30_000L
internal const val MAX_AHEAD_BYTES = 100L * 1024 * 1024
