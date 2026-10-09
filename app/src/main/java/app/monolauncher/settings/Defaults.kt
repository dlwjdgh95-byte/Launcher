package app.monolauncher.settings

object Defaults {
    /**
     * Initial registered apps (docs/PLAN.md §14). Galaxy Store and Play builds use different
     * package names, so both are listed; whichever is installed shows up.
     */
    val REGISTERED_PACKAGES: List<String> = listOf(
        "com.kyobo.ebook.samsung",                  // 교보eBook for 삼성 (Galaxy Store)
        "com.kyobo.ebook.common.b2c",               // 교보eBook (Play)
        "kr.co.millie.millieshelf",                 // 밀리의서재 (Play)
        "kr.co.millie.millieshelf.samsung",         // 밀리의서재 (Galaxy Store)
        "com.google.android.apps.youtube.music",    // YouTube Music
        "net.daum.android.map",                     // 카카오맵
        "com.google.android.apps.chromecast.app",   // Google Home
    )

    /** Routine variables with a sensible default; user values override these. */
    val VARIABLES: Map<String, String> = mapOf(
        "home_by" to "publictransit",               // 카카오맵 이동수단: car, publictransit, foot, bicycle
    )
}
