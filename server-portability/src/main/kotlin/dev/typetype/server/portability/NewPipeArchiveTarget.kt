package dev.typetype.server.portability

enum class NewPipeArchiveTarget(
    val databaseVersion: Int,
    val identityHash: String,
    val pipePipe: Boolean,
) {
    NEW_PIPE(9, "7591e8039faa74d8c0517dc867af9d3e", false),
    PIPE_PIPE(901, "d505dd6c0be6a80da07aa980bd361064", true),
}
