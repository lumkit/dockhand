package io.github.dockhand.task

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskManagerTest {
    @Test
    fun cancellingATaskRunsItsRollback() = runBlocking {
        val manager = TaskManager(this)
        var rolledBack = false
        val id = manager.start("copy", background = false) {
            addRollback { rolledBack = true }
            awaitCancellation()
        }

        delay(50)
        manager.cancel(id)
        delay(100)

        assertTrue(rolledBack)
        assertEquals(emptyList(), manager.tasks.value)
    }
}
