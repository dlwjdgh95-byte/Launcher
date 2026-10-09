package app.monolauncher.routine

private val PLACEHOLDER = Regex("""\{([A-Za-z_][A-Za-z0-9_]*)\}""")

/** Names of `{variable}` placeholders in this step that have no non-blank value in [variables]. */
internal fun Step.missingVariables(variables: Map<String, String>): List<String> =
    strings()
        .flatMap { s -> PLACEHOLDER.findAll(s).map { it.groupValues[1] } }
        .filter { variables[it].isNullOrBlank() }
        .distinct()

/** This step with every known placeholder replaced by its value. */
internal fun Step.resolveVariables(variables: Map<String, String>): Step =
    mapStrings { s -> PLACEHOLDER.replace(s) { m -> variables[m.groupValues[1]] ?: m.value } }

// Walks the same fields as mapStrings so the two can never disagree.
private fun Step.strings(): List<String> = buildList { mapStrings { add(it); it } }

/** Applies [transform] to every String field of the step. */
private fun Step.mapStrings(transform: (String) -> String): Step = when (this) {
    is Step.Launch -> copy(packageName = transform(packageName))
    is Step.DeepLink -> copy(uri = transform(uri), packageName = packageName?.let(transform))
    is Step.Shortcut -> copy(packageName = transform(packageName), shortcutId = transform(shortcutId))
    is Step.Timer -> copy(message = message?.let(transform))
    is Step.Alarm -> copy(message = message?.let(transform))
    is Step.Delay, is Step.Media, is Step.Volume, is Step.Dnd, is Step.Torch, Step.GoHome -> this
}
