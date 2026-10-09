package app.monolauncher.routine

import android.content.ContextWrapper
import app.monolauncher.apps.AppEntry
import app.monolauncher.apps.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidStepExecutorLaunchTest {
    /** Launches only [installed] packages and records every attempt. */
    private class FakeApps(private val installed: Set<String>) : AppRepository {
        val attempts = mutableListOf<String>()
        override val apps: StateFlow<List<AppEntry>> = MutableStateFlow(emptyList())
        override fun refresh() = Unit
        override fun launch(entry: AppEntry) = false
        override fun launchPackage(packageName: String): Boolean {
            attempts += packageName
            return packageName in installed
        }
    }

    // Launch steps never touch the Context, so an empty wrapper is enough.
    private fun executor(apps: AppRepository) = AndroidStepExecutor(ContextWrapper(null), apps)

    @Test
    fun launchTriesCandidatesInOrderAndStopsAtFirstThatOpens() = runTest {
        val apps = FakeApps(installed = setOf("c", "d"))

        val result = executor(apps).execute(Step.Launch("a", listOf("b", "c", "d")))

        assertEquals(StepResult.Success, result)
        assertEquals(listOf("a", "b", "c"), apps.attempts)
    }

    @Test
    fun launchFailsWhenNoCandidateOpens() = runTest {
        val apps = FakeApps(installed = emptySet())

        val result = executor(apps).execute(Step.Launch("a", listOf("b")))

        assertEquals(StepResult.Failed("앱을 열 수 없습니다: a, b"), result)
        assertEquals(listOf("a", "b"), apps.attempts)
    }

    @Test
    fun launchWithoutAlternativesTriesOnlyItsPackage() = runTest {
        val apps = FakeApps(installed = setOf("a"))

        assertEquals(StepResult.Success, executor(apps).execute(Step.Launch("a")))
        assertEquals(StepResult.Failed("앱을 열 수 없습니다: b"), executor(apps).execute(Step.Launch("b")))
        assertEquals(listOf("a", "b"), apps.attempts)
    }
}
