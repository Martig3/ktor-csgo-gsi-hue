package com.martige

import com.fasterxml.jackson.databind.SerializationFeature
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.martige.service.HueLightService
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.GsonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.features.CORS
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
data class CurrentMap(
    val current_spectators: Int?,
    val mode: String?,
    val name: String?,
    val num_matches_to_win_series: Int?,
    val phase: String?,
    val round: Int?,
    val souvenirs_total: Int?,
    val team_ct: TeamCt?,
    val team_t: TeamT?
)
data class GameStateModel (
    val CurrentMap: CurrentMap,
    val players: ArrayList<Player>
)
data class MatchStats(
    val assists: Int?,
    val deaths: Int?,
    val kills: Int?,
    val mvps: Int?,
    val score: Int?
)
data class Player(
    val forward: String?,
    val match_stats: MatchStats?,
    val name: String?,
    val observer_slot: Int?,
    val position: String?,
    val state: State?,
    val team: String?
)
data class State(
    val armor: Int?,
    val burning: Int?,
    val equip_value: Int?,
    val flashed: Int?,
    val health: Int?,
    val helmet: Boolean?,
    val money: Int?,
    val round_killhs: Int?,
    val round_kills: Int?,
    val round_totaldmg: Int?
)
data class TeamCt(
    val consecutive_round_losses: Int?,
    val matches_won_this_series: Int?,
    val score: Int?,
    val timeouts_remaining: Int?
)
data class TeamT(
    val consecutive_round_losses: Int?,
    val matches_won_this_series: Int?,
    val score: Int?,
    val timeouts_remaining: Int?
)
fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }

    val client = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = GsonSerializer()
        }
    }

    var gameState = ""

    suspend fun gameStateLookup(gameState: String): GameStateModel {

        if (gameState == "") {
            return  GameStateModel(CurrentMap(null, null, null, null, null, null, null, null, null), arrayListOf())
        }
        val jsonParser: JsonElement = JsonParser().parse(gameState)
        val currentMap = GlobalScope.async {
            val map = jsonParser.asJsonObject.get("map") ?: JsonObject()
            Gson().fromJson(map.asJsonObject, CurrentMap::class.java)
        }
        val players = GlobalScope.async {
            val allPlayers = (jsonParser.asJsonObject.get("allplayers") ?: JsonObject())
            val players = ArrayList<Player>()
            allPlayers.asJsonObject.entrySet().mapTo(players) { Gson().fromJson(it.value, Player::class.java) }
        }
        return (GameStateModel(currentMap.await(), players.await()))
    }

    install(CORS) {
        method(HttpMethod.Options)
        method(HttpMethod.Put)
        method(HttpMethod.Post)
        method(HttpMethod.Delete)
        method(HttpMethod.Patch)
        header(HttpHeaders.Authorization)
        header(HttpHeaders.AccessControlRequestHeaders)
        header(HttpHeaders.ContentType)
        header(HttpHeaders.AccessControlAllowOrigin)
        allowCredentials = true
        anyHost() // @TODO: Don't do this in production if possible. Try to limit it.
    }

    routing {
        route("/gsi") {
            post("/endpoint") {
                gameState = call.receive()
                val service = HueLightService()
                service.updateLighting(gameState)
                call.respond(HttpStatusCode.OK)

            }
            get("/scoreboard") {
                call.respond(gameStateLookup(gameState))
            }
            put("/scoreboard") {
                gameState = call.receive()
                call.respond(HttpStatusCode.OK)
            }
            get("/scoreboard-as-string") {
                call.respond(gameState)
            }
        }
    }
}


