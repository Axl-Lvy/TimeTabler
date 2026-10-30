package fr.axllvy.timetabler.auth

import kotlin.jvm.JvmInline

/** Bearer token for the admin routes of the TimeTabler API. Its [toString] never shows it. */
@JvmInline
value class AccessToken(val value: String) {
  override fun toString(): String = "AccessToken(redacted)"
}

/** Token the issuer exchanges for a fresh [AccessToken]. Its [toString] never shows it. */
@JvmInline
value class RefreshToken(val value: String) {
  override fun toString(): String = "RefreshToken(redacted)"
}
