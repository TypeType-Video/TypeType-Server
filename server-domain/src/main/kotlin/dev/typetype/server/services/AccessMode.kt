package dev.typetype.server.services

const val ACCESS_MODE_UNRESTRICTED = "unrestricted"
const val ACCESS_MODE_ALLOW_LIST = "allow_list"
const val ALLOW_SCOPE_USER = "user"
const val ALLOW_SCOPE_GLOBAL = "global"

fun String.toAccessMode(): String = when (this) {
    ACCESS_MODE_ALLOW_LIST -> ACCESS_MODE_ALLOW_LIST
    else -> ACCESS_MODE_UNRESTRICTED
}
