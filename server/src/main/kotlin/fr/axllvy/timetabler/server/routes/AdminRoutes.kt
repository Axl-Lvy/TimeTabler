package fr.axllvy.timetabler.server.routes

import fr.axllvy.timetabler.server.auth.adminRoutes
import fr.axllvy.timetabler.server.auth.caller
import fr.axllvy.timetabler.wire.AdminIdentity
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/** Admin-only routes: `GET /v1/admin/me` answers the signed-in admin's subject. */
fun Route.adminApiRoutes() {
  adminRoutes { get("/v1/admin/me") { call.respond(AdminIdentity(call.caller.subject)) } }
}
