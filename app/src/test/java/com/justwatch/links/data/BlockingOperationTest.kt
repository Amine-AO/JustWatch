package com.justwatch.links.data

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class BlockingOperationTest {
    @Test fun cancellationClosesTransportAndInterruptsWorker() = runBlocking {
        val entered = CountDownLatch(1)
        val closed = AtomicBoolean(false)
        val exited = CountDownLatch(1)
        val task = launch {
            blockingOperation(cancel = { closed.set(true) }) {
                entered.countDown()
                try { CountDownLatch(1).await() }
                finally { exited.countDown() }
            }
            fail("A cancelled operation must not deliver a result")
        }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
        }
        withTimeout(5000) { task.cancelAndJoin() }
        assertTrue(closed.get())
        assertTrue(exited.await(5, TimeUnit.SECONDS))
    }

    @Test fun completedOperationReturnsResultWithoutCancellation() = runBlocking {
        val closed = AtomicBoolean(false)
        assertEquals(42, blockingOperation(cancel = { closed.set(true) }) { 42 })
        assertFalse(closed.get())
    }
}
