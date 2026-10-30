package fr.axllvy.timetabler.wire

import kotlinx.serialization.Serializable

/** The admin a request was authenticated as, by the `sub` of their access token. */
@Serializable data class AdminIdentity(val subject: String)
