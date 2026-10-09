package app.monolauncher.session

/** In-memory Settings.Secure that records successful writes in order. */
class FakeSecureSettings(
    vararg initial: Pair<String, Int>,
    var canWrite: Boolean = true,
) : SecureSettings {
    val values = mutableMapOf(*initial)
    val writes = mutableListOf<Pair<String, Int>>()
    val failingKeys = mutableSetOf<String>()

    override fun getInt(name: String): Int? = values[name]

    override fun putInt(name: String, value: Int): Boolean {
        if (!canWrite || name in failingKeys) return false
        values[name] = value
        writes += name to value
        return true
    }

    override fun canWrite(): Boolean = canWrite

    /** Simulates a change from outside the app (quick settings), not recorded in [writes]. */
    fun externalSet(name: String, value: Int) {
        values[name] = value
    }
}
