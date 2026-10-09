package app.monolauncher.routine

import kotlinx.serialization.json.Json

object RoutineJson {
    val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
        encodeDefaults = false
        prettyPrint = true
    }

    fun parse(text: String): RoutineConfig = json.decodeFromString(RoutineConfig.serializer(), text)

    fun encode(config: RoutineConfig): String = json.encodeToString(RoutineConfig.serializer(), config)
}
