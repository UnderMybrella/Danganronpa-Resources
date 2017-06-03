package org.abimon.dr.resources

import io.vertx.core.Handler
import io.vertx.ext.web.RoutingContext

class RedirectHandler(val url: String): Handler<RoutingContext> {
    override fun handle(event: RoutingContext) = event.response().redirect(url)
}