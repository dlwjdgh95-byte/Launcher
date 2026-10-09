package app.monolauncher.routine

import kotlinx.serialization.json.Json

/**
 * JSON format of [RoutineConfig]. Each step carries a `"type"` tag, e.g.
 * `{"type":"launch","packageName":"net.daum.android.map"}`, `{"type":"delay","ms":2500}`,
 * `{"type":"home"}`. Unknown keys are ignored; optional fields that are null are omitted.
 * [parse] throws [kotlinx.serialization.SerializationException] (an IllegalArgumentException) on invalid input.
 */
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
