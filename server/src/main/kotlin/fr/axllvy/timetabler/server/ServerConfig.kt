package fr.axllvy.timetabler.server

import java.io.File
import java.nio.file.Path

/** Server settings, read from `TT_*` environment variables. */
data class ServerConfig(
  val dbUrl: String,
  val dbUser: String,
  val dbPassword: String,
  val port: Int,
  val staticDir: File?,
  val posterDir: Path,
) {
  companion object {
    /** Reads the configuration from [env]; throws when a required variable is missing. */
    fun fromEnv(env: Map<String, String> = System.getenv()): ServerConfig {
      fun required(name: String) = checkNotNull(env[name]) { "$name is not set" }
      return ServerConfig(
        dbUrl = required("TT_DB_URL"),
        dbUser = required("TT_DB_USER"),
        dbPassword = required("TT_DB_PASSWORD"),
        port = env["TT_PORT"]?.toInt() ?: 8080,
        staticDir = env["TT_STATIC_DIR"]?.let(::File),
        posterDir = Path.of(env["TT_POSTER_DIR"] ?: "/data/posters"),
      )
    }
  }
}
