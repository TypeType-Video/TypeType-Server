package dev.typetype.server.services

fun SabrSessionHolder.nextReplacementGeneration(): Long =
    activeGeneration().let { if (it == Long.MAX_VALUE) it else it + 1L }
