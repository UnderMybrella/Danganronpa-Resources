package org.abimon.dr.resources

import io.vertx.core.Handler
import io.vertx.ext.web.RoutingContext
import java.io.File

class SingleFileHandler(val file: File): Handler<RoutingContext> {
    override fun handle(context: RoutingContext) {
        context.response().putHeader("Strict-Transport-Security", "max-age=${Integer.MAX_VALUE}").sendFile(file.absolutePath)
    }
}