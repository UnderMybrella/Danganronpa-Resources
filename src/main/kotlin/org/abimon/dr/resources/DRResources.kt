package org.abimon.dr.resources

import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.CookieHandler
import io.vertx.ext.web.handler.CorsHandler
import org.abimon.visi.io.iterate
import org.abimon.visi.lang.setHeadless
import java.io.File

fun main(args: Array<String>) {
    setHeadless()
    System.setProperty("vertx.disableFileCPResolving", "true")
    val vertx = Vertx.vertx()
    val http = vertx.createHttpServer()
    val router = Router.router(vertx)
//    all.iterate(true).forEach { file ->
//        if(file.isDirectory && file.iterate().isEmpty())
//            file.deleteRecursively()
//    }

    router.route().handler(CookieHandler.create())
    router.route().handler(CorsHandler.create("*").allowedMethod(HttpMethod.GET).allowCredentials(false).maxAgeSeconds(0))

    val v3Expired = File("v3/expired.html")
    val v3Unauthorised = File("v3/unauthorised.html")
    val raw = File("all")
    val sorted = File("sorted")
    val taggedDir = File("tagged")

    val rawFiles = raw.iterate().filter { file -> file.exists() && !file.isHidden && !file.name.startsWith(".") && !file.name.startsWith("__") && file.extension == "png" }.toMutableList()
    val sortedTags = sorted.iterate(true).filter { it.isDirectory }.map { tag -> tag.absolutePath.replace(sorted.absolutePath, "").substring(1) }.toMutableList()
    val tags = HashMap<String, JsonArray>()

    rawFiles.filter { it.nameWithoutExtension.toIntOrNull() != null }.map { rawFile -> File(taggedDir, rawFile.nameWithoutExtension + ".json") }.filter { tagFile -> tagFile.exists() }.forEach { tags[it.nameWithoutExtension] = JsonArray(it.readText()) }

    router.get("/").handler(RedirectHandler("/index.html"))
    router.get("/raw*").handler(StaticFileHandler("raw", File("raw")))
    router.get("/sorted*").handler(StaticFileHandler("sorted", File("sorted")))
    router.get("/assets*").handler(StaticFileHandler("assets", File("assets")))
    router.get("/handbooks*").handler(StaticFileHandler("handbooks", File("handbooks")))
    router.get("/images*").handler(StaticFileHandler("images", File("images")))
    router.get("/elements.html").handler(SingleFileHandler(File("elements.html")))
    router.get("/reload").handler { context ->
        rawFiles.clear()
        rawFiles.addAll(raw.iterate().filter { file -> file.exists() && !file.isHidden && !file.name.startsWith(".") && !file.name.startsWith("__") && file.extension == "png" })

        sortedTags.clear()
        sortedTags.addAll(sorted.iterate(true).filter { it.isDirectory }.map { tag -> tag.absolutePath.replace(sorted.absolutePath, "").substring(1) })

        tags.clear()
        rawFiles.filter { it.nameWithoutExtension.toIntOrNull() != null }.map { rawFile -> File(taggedDir, rawFile.nameWithoutExtension + ".json") }.filter { tagFile -> tagFile.exists() }.forEach { tags[it.nameWithoutExtension] = JsonArray(it.readText()) }

        StaticFileHandler.reload()
        context.response().redirect("/index.html")
    }

    for(file in arrayOf(File("index.html"), File("handbook.html")))
        router.get("/${file.name}").handler(SingleFileHandler(file))

    router.get("/api/image/:id").handler { context ->
        val id = context.pathParam("id") ?: return@handler context.response().setStatusCode(404).end()
        val rawImg = File(raw, "$id.png")
        if(rawImg.exists())
            context.response().sendFile(rawImg.absolutePath)
        else
            context.response().setStatusCode(404).end()
    }

    val getTagged: (String) -> List<String> = { tag -> tags.filter { (_, tagset) -> tagset.contains(tag) }.map { (file, _) -> file } }

    router.get("/api/tagged/:tag").handler { context ->
        val tag = context.pathParam("tag") ?: return@handler context.response().setStatusCode(404).end()

        context.response().putHeader("Content-Type", "application/json").end(JsonArray(getTagged(tag).filter { it.toIntOrNull() != null }.sortedWith(Comparator<String> { o1, o2 -> o1.toInt().compareTo(o2.toInt()) })).encode())
    }

    val untagged: () -> List<String> = { rawFiles.map { it.nameWithoutExtension }.filter { !tags.containsKey(it) || tags[it]!!.isEmpty }.filter { it.toIntOrNull() != null }.sortedWith(Comparator<String> { o1, o2 -> o1.toInt().compareTo(o2.toInt()) }) }

    router.get("/api/untagged").handler { context -> context.response().putHeader("Content-Type", "application/json").end(JsonArray(untagged()).encode()) }

    router.get("/api/untagged/next").handler { context ->
        val id = context.request().getParam("id") ?: return@handler context.response().setStatusCode(404).end()
        val tagged = untagged()
        val index = tagged.indexOf(id)
        if(index < 0) { //Simulate the location
            val simulatedTagged = tagged.toMutableList()
            simulatedTagged.add(id)
            val simulatedAndSorted = simulatedTagged.filter { it.toIntOrNull() != null }.sortedWith(Comparator<String> { o1, o2 -> o1.toInt().compareTo(o2.toInt()) })
            val simulatedIndex = simulatedAndSorted.indexOf(id)
            if(simulatedIndex == -1)
                context.response().end(simulatedAndSorted.first())
            else if(simulatedIndex + 1 >= simulatedAndSorted.size) {
                if (simulatedAndSorted.isEmpty())
                    return@handler context.response().setStatusCode(400).putHeader("Content-Type", "application/json").end(JsonObject().put("error_code", 0).put("error", "No untagged objects").encode())
                else
                    return@handler context.response().end(simulatedAndSorted.first())
            }

            return@handler context.response().end(simulatedAndSorted[simulatedIndex + 1])
        }
        else if(index + 1 >= tagged.size) {
            if (tagged.isEmpty())
                return@handler context.response().setStatusCode(400).putHeader("Content-Type", "application/json").end(JsonObject().put("error_code", 0).put("error", "No untagged objects").encode())
            else
                return@handler context.response().end(tagged.first())
        }

        context.response().end(tagged[index + 1])
    }

    router.get("/api/untagged/prev").handler { context ->
        val id = context.request().getParam("id") ?: return@handler context.response().setStatusCode(404).end()
        val tagged = untagged()
        val index = tagged.indexOf(id)
        if(index == -1) {
            val simulatedTagged = tagged.toMutableList()
            simulatedTagged.add(id)
            val simulatedAndSorted = simulatedTagged.filter { it.toIntOrNull() != null }.sortedWith(Comparator<String> { o1, o2 -> o1.toInt().compareTo(o2.toInt()) })
            val simulatedIndex = simulatedAndSorted.indexOf(id)
            if(simulatedIndex == -1)
                return@handler context.response().end(simulatedAndSorted.last())
            else if(simulatedIndex + 1 >= simulatedAndSorted.size) {
                if (simulatedAndSorted.isEmpty())
                    return@handler context.response().setStatusCode(400).putHeader("Content-Type", "application/json").end(JsonObject().put("error_code", 0).put("error", "No untagged objects").encode())
                else
                    return@handler context.response().end(simulatedAndSorted.last())
            }

            return@handler context.response().end(simulatedAndSorted[simulatedIndex - 1])
        }
        else if(index < 0 || index > tagged.size) {
            if (tagged.isEmpty())
                return@handler context.response().setStatusCode(400).putHeader("Content-Type", "application/json").end(JsonObject().put("error_code", 0).put("error", "No untagged objects").encode())
            else
                return@handler context.response().end(tagged.last())
        }

        context.response().end(tagged[index - 1])
    }

    router.get("/api/tags").handler { context ->
        val tagsGrouped = sortedTags.groupBy { tag -> getTagged(tag).count() }.toSortedMap(Comparator<Int> { o1, o2 -> o2.compareTo(o1) })
        val tagArray = JsonArray()

        tagsGrouped.forEach { _, tags -> tags.sortedWith(Comparator<String> { o1, o2 -> o1.compareTo(o2) }).forEach { tagArray.add(it) } }
        context.response().putHeader("Content-Type", "application/json").end(tagArray.encode())
    }
    router.get("/api/tags/:id").handler { context ->
        val id = context.pathParam("id") ?: return@handler context.response().setStatusCode(404).end()

        val tagFile = File(taggedDir, "$id.json")
        if(tagFile.exists())
            context.response().sendFile(tagFile.absolutePath)
        else
            context.response().putHeader("Content-Type", "application/json").end("[]")
    }

    router.post().handler(BodyHandler.create())
    router.post("/api/tag/:id").handler { context ->
        val id = context.pathParam("id") ?: return@handler context.response().setStatusCode(404).end()

        println("Tagging $id as ${context.bodyAsJsonArray}")

        val tagFile = File(taggedDir, "$id.json")
        tagFile.writeText(context.bodyAsJsonArray.encodePrettily())
        tags[id] = context.bodyAsJsonArray

        context.response().end("Nice tags")
    }


//    For when V3 comes out, and after I've played it; still want to avoid spoiling people
//    router.route().handler(CorsHandler.create("").maxAgeSeconds(0))
//    router.get("/v3/*").handler { context ->
//        val cookie = context.getCookie("DRv3")
//        if(cookie == null)
//            context.response().redirect("/v3_authorise?reason=no_cookie&redirect=${context.request().path()}")
//        else if((cookie.value.toLongOrNull() ?: 0) < System.currentTimeMillis())
//            context.response().redirect("/v3_authorise?reason=expired&redirect=${context.request().path()}")
//        else
//            context.next()
//    }
//    router.get("/v3/*").handler(StaticFileHandler("v3", File("v3")))
//    router.get("/v3_authorise").handler { context ->
//        val reason = context.pathParam("reason")?.toLowerCase() ?: "no_cookie"
//        if (reason == "expired")
//            context.response().sendFile(v3Expired.absolutePath)
//        else
//            context.response().sendFile(v3Expired.absolutePath)
//    }


    http.requestHandler(router::accept)
    http.listen(22148)
}

fun HttpServerResponse.redirect(url: String) = putHeader("Location", url).setStatusCode(302).end()

val String.parents: String
   get() = if(this.lastIndexOf('/') == -1) "" else this.substring(0, this.lastIndexOf('/'))

val String.child: String
    get() = if(this.lastIndexOf('/') == -1) this else this.substring(this.lastIndexOf('/') + 1, length)

val String.extension: String
    get() = if(this.lastIndexOf('.') == -1) this else this.substring(this.lastIndexOf('.') + 1, length)