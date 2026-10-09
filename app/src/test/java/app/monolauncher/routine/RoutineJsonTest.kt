package app.monolauncher.routine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutineJsonTest {
    private val allSteps: List<Step> = listOf(
        Step.Launch("com.example.app"),
        Step.DeepLink("kakaomap://route?ep={home_lat},{home_lng}&by=CAR", "net.daum.android.map"),
        Step.DeepLink("https://example.com"),
        Step.Shortcut("com.kakao.talk", "chat_123"),
        Step.Delay(2500),
        Step.Volume(40),
        Step.Dnd(true),
        Step.Torch(false),
        Step.Timer(300, "라면"),
        Step.Timer(60),
        Step.Alarm(7, 30, "기상"),
        Step.Alarm(6, 0),
        Step.GoHome,
    ) + MediaAction.entries.map { Step.Media(it) }

    private val config = RoutineConfig(routines = listOf(Routine("all", "전부", allSteps)))

    @Test
    fun everyStepTypeRoundTrips() {
        assertEquals(config, RoutineJson.parse(RoutineJson.encode(config)))
    }

    @Test
    fun stepsAreTaggedWithTypeDiscriminator() {
        val steps = Json.parseToJsonElement(RoutineJson.encode(config))
            .jsonObject["routines"]!!.jsonArray[0].jsonObject["steps"]!!.jsonArray
        val types = steps.map { it.jsonObject["type"]!!.jsonPrimitive.content }

        assertEquals(
            listOf(
                "launch", "deeplink", "deeplink", "shortcut", "delay", "volume", "dnd", "torch",
                "timer", "timer", "alarm", "alarm", "home", "media", "media", "media", "media", "media",
            ),
            types,
        )
        assertEquals(JsonObject(mapOf("type" to JsonPrimitive("home"))), steps[12])
        // Optional nulls are left out rather than written as null.
        assertEquals(setOf("type", "uri"), steps[2].jsonObject.keys)
    }

    @Test
    fun launchAlternativesRoundTripAndAreOmittedWhenEmpty() {
        val steps = listOf(Step.Launch("a.b", listOf("a.c", "a.d")), Step.Launch("a.b"))
        val withAlternatives = RoutineConfig(routines = listOf(Routine("r", "R", steps)))
        val text = RoutineJson.encode(withAlternatives)

        assertEquals(withAlternatives, RoutineJson.parse(text))
        val encoded = Json.parseToJsonElement(text)
            .jsonObject["routines"]!!.jsonArray[0].jsonObject["steps"]!!.jsonArray
        assertEquals(listOf("a.c", "a.d"), encoded[0].jsonObject["alternatives"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(setOf("type", "packageName"), encoded[1].jsonObject.keys)
    }

    @Test
    fun parsesHandWrittenStepsAndIgnoresUnknownKeys() {
        val text = """
            {"version": 1, "routines": [{"id": "r", "label": "루틴", "steps": [
              {"type": "launch", "packageName": "a.b", "futureField": 1},
              {"type": "launch", "packageName": "a.b", "alternatives": ["a.c"]},
              {"type": "deeplink", "uri": "x://y"},
              {"type": "shortcut", "packageName": "a.b", "shortcutId": "s"},
              {"type": "delay", "ms": 1000},
              {"type": "media", "action": "toggle"},
              {"type": "media", "action": "previous"},
              {"type": "volume", "percent": 40},
              {"type": "dnd", "on": true},
              {"type": "torch", "on": false},
              {"type": "timer", "seconds": 180, "message": "차"},
              {"type": "alarm", "hour": 7, "minute": 5},
              {"type": "home"}
            ]}]}
        """.trimIndent()

        val steps = RoutineJson.parse(text).routines.single().steps

        assertEquals(
            listOf(
                Step.Launch("a.b"),
                Step.Launch("a.b", listOf("a.c")),
                Step.DeepLink("x://y"),
                Step.Shortcut("a.b", "s"),
                Step.Delay(1000),
                Step.Media(MediaAction.TOGGLE),
                Step.Media(MediaAction.PREVIOUS),
                Step.Volume(40),
                Step.Dnd(true),
                Step.Torch(false),
                Step.Timer(180, "차"),
                Step.Alarm(7, 5),
                Step.GoHome,
            ),
            steps,
        )
    }
}
