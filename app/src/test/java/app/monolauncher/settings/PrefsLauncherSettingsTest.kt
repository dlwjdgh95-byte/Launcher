package app.monolauncher.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PrefsLauncherSettingsTest {

    private val defaultRoutines = """{"version":1,"routines":[{"id":"home","label":"집 가기","steps":[]}]}"""

    private fun settings(prefs: FakeSharedPreferences) = PrefsLauncherSettings(prefs) { defaultRoutines }

    @Test
    fun `defaults apply until something is stored`() {
        val settings = settings(FakeSharedPreferences())

        assertEquals(Defaults.REGISTERED_PACKAGES.toSet(), settings.registeredPackages.value)
        assertEquals(emptyMap<String, List<String>>(), settings.aliases.value)
        assertEquals(Defaults.VARIABLES, settings.variables.value)
        assertEquals(defaultRoutines, settings.routinesJson.value)
    }

    @Test
    fun `registered packages are persisted, including an empty set`() {
        val prefs = FakeSharedPreferences()
        val settings = settings(prefs)

        settings.setRegisteredPackages(setOf("a", "b"))
        assertEquals(setOf("a", "b"), settings.registeredPackages.value)
        assertEquals(setOf("a", "b"), settings(prefs).registeredPackages.value)

        settings.setRegisteredPackages(emptySet())
        assertEquals(emptySet<String>(), settings(prefs).registeredPackages.value)
    }

    @Test
    fun `aliases are cleaned, persisted and removed when empty`() {
        val prefs = FakeSharedPreferences()
        val settings = settings(prefs)

        settings.setAliases("net.daum.android.map", listOf(" 카맵 ", "지도", "", "지도"))
        settings.setAliases("com.google.android.apps.youtube.music", listOf("유튜브뮤직"))
        assertEquals(
            mapOf("net.daum.android.map" to listOf("카맵", "지도"), "com.google.android.apps.youtube.music" to listOf("유튜브뮤직")),
            settings(prefs).aliases.value,
        )

        settings.setAliases("net.daum.android.map", listOf(" "))
        assertEquals(mapOf("com.google.android.apps.youtube.music" to listOf("유튜브뮤직")), settings.aliases.value)
        assertEquals(settings.aliases.value, settings(prefs).aliases.value)
    }

    @Test
    fun `variables are persisted`() {
        val prefs = FakeSharedPreferences()
        val settings = settings(prefs)

        settings.setVariable("home_lat", "37.5")
        settings.setVariable("home_lng", "127.0")
        settings.setVariable("home_lat", "37.6")

        assertEquals(Defaults.VARIABLES + mapOf("home_lat" to "37.6", "home_lng" to "127.0"), settings(prefs).variables.value)
    }

    @Test
    fun `user values override default variables`() {
        val prefs = FakeSharedPreferences()
        settings(prefs).setVariable("home_by", "car")

        assertEquals("car", settings(prefs).variables.value["home_by"])
    }

    @Test
    fun `corrupt stored json falls back to defaults`() {
        val prefs = FakeSharedPreferences().apply {
            values[PrefsLauncherSettings.KEY_ALIASES] = "{not json"
            values[PrefsLauncherSettings.KEY_VARIABLES] = "[1,2]"
        }

        val settings = settings(prefs)

        assertEquals(emptyMap<String, List<String>>(), settings.aliases.value)
        assertEquals(Defaults.VARIABLES, settings.variables.value)
    }

    @Test
    fun `routines are persisted and reset to the default`() {
        val prefs = FakeSharedPreferences()
        val settings = settings(prefs)
        val custom = """{"version":1,"routines":[]}"""

        settings.setRoutinesJson(custom)
        assertEquals(custom, settings.routinesJson.value)
        assertEquals(custom, settings(prefs).routinesJson.value)

        settings.resetRoutinesToDefault()
        assertEquals(defaultRoutines, settings.routinesJson.value)
        assertFalse(prefs.contains(PrefsLauncherSettings.KEY_ROUTINES))
    }
}
