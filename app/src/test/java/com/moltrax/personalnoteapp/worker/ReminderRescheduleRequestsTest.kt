package com.moltrax.personalnoteapp.worker

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import org.junit.Assert.*
import org.junit.Test

class ReminderRescheduleRequestsTest {
    private data class Enqueued(val name: String, val policy: ExistingWorkPolicy, val request: OneTimeWorkRequest)

    @Test fun keepIsForwardedWithTheExistingUniqueNameAndWorker() {
        val calls = mutableListOf<Enqueued>()
        enqueueReminderReschedule(ExistingWorkPolicy.KEEP) { name, policy, request ->
            calls += Enqueued(name, policy, request)
        }
        assertEquals(1, calls.size)
        assertEquals("reschedule_notifications", calls.single().name)
        assertEquals(ExistingWorkPolicy.KEEP, calls.single().policy)
        assertTrue(calls.single().request.tags.contains(RescheduleNotificationsWorker::class.java.name))
        assertEquals(RescheduleNotificationsWorker::class.java.name, calls.single().request.workSpec.workerClassName)
    }

    @Test fun replaceIsForwardedAndEachInvocationBuildsANewRequest() {
        val calls = mutableListOf<Enqueued>()
        repeat(2) {
            enqueueReminderReschedule(ExistingWorkPolicy.REPLACE) { name, policy, request ->
                calls += Enqueued(name, policy, request)
            }
        }
        assertEquals(2, calls.size)
        assertTrue(calls.all { it.name == RescheduleNotificationsWorker.WORK_NAME && it.policy == ExistingWorkPolicy.REPLACE })
        calls.forEach { call ->
            assertEquals("reschedule_notifications", call.name)
            assertEquals(ExistingWorkPolicy.REPLACE, call.policy)
            assertEquals(RescheduleNotificationsWorker::class.java.name, call.request.workSpec.workerClassName)
        }
        assertNotEquals(calls[0].request.id, calls[1].request.id)
    }

    @Test fun enqueueFailuresAreNotSwallowedOrRetried() {
        val failure = IllegalStateException("enqueue failed")
        var calls = 0
        try {
            enqueueReminderReschedule(ExistingWorkPolicy.KEEP) { _, _, _ -> calls++; throw failure }
            fail("The enqueue failure must propagate")
        } catch (e: IllegalStateException) {
            assertSame(failure, e)
        }
        assertEquals(1, calls)
    }
}
