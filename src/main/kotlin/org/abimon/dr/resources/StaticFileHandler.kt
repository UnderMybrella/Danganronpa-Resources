package org.abimon.dr.resources

import io.netty.handler.codec.http.HttpHeaderNames
import io.vertx.core.Handler
import io.vertx.core.json.JsonArray
import io.vertx.ext.web.RoutingContext
import org.abimon.visi.io.iterate
import java.io.File

class StaticFileHandler(val endpoint: String, val root: File) : Handler<RoutingContext> {
    companion object {
        val instances = ArrayList<StaticFileHandler>()

        fun reload() {
            instances.forEach { instance ->
                instance.subfiles.clear()
                instance.subfiles.addAll(instance.root.iterate(true))
            }
        }
    }

    val subfiles = root.iterate(true)

    override fun handle(context: RoutingContext) {
        if (context.request().path().substring(1) == endpoint)
            return context.response().putHeader("Strict-Transport-Security", "max-age=${Integer.MAX_VALUE}").putHeader(HttpHeaderNames.CONTENT_TYPE, "text/html").end("<html><body><h1>Files in ${root.name}</h1><ul>${root.listFiles().filter { subFile -> !subFile.name.startsWith(".") }.joinToString("") { "<a href=\"/$endpoint${it.absolutePath.replace(root.absolutePath, "")}\"><li>${it.name}</li></a>" }}</ul></body></html>")

        val path = context.request().path().toLowerCase()
        val file = File(root, path.replaceFirst("/$endpoint/", ""))
        if (file.exists() && !file.isHidden && subfiles.any { otherFile -> otherFile.absolutePath.equals(file.absolutePath, true) }) {
            if (file.isDirectory) {
                if (File(file, "index.html").exists())
                    return context.response().redirect("/$endpoint${File(file, "index.html").absolutePath.replace(root.absolutePath, "").toLowerCase()}")
                else
                    return context.response().putHeader("Strict-Transport-Security", "max-age=${Integer.MAX_VALUE}").putHeader(HttpHeaderNames.CONTENT_TYPE, "text/html").end("<html><body><h1>Files in ${file.absolutePath.replace(root.absolutePath, "")}</h1><ul>${file.listFiles().filter { subFile -> !subFile.name.startsWith(".") }.joinToString("") { "<a href=\"/$endpoint${it.absolutePath.replace(root.absolutePath, "").toLowerCase()}\"><li>${it.name}</li></a>" }}</ul></body></html>")
            } else {
                context.response().putHeader("Strict-Transport-Security", "max-age=${Integer.MAX_VALUE}").sendFile(file.absolutePath)
                return
            }
        }


        if (path.child == "files.json") {
            val parentFile = File(root, path.parents.replaceFirst("/$endpoint/", ""))
            if (parentFile.exists() && !parentFile.isHidden && subfiles.contains(parentFile))
                return context.response().putHeader("Strict-Transport-Security", "max-age=${Integer.MAX_VALUE}").putHeader(HttpHeaderNames.CONTENT_TYPE, "application/json").end(JsonArray(parentFile.listFiles().filter { subFile -> !subFile.isHidden && !subFile.name.startsWith(".") }.map { "/$endpoint${it.absolutePath.replace(root.absolutePath, "").toLowerCase()}" }).toString())
        }

        context.next()
    }

    init {
        instances.add(this)
    }
}