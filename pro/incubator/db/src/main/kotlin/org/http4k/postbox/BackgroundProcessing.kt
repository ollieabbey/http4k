package org.http4k.postbox

import dev.forkhandles.result4k.Failure
import dev.forkhandles.result4k.Result
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.db.Transactor
import org.http4k.events.Event
import org.http4k.events.Events
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime


class PostboxProcessing(
    private val transactor: Transactor<Postbox>,
    private val target: HttpHandler,
    private val batchSize: Int = 10,
    private val maxPollingTime: Duration = 5.seconds,
    private val events: Events = { },
    private val executorService: ExecutorService = Executors.newVirtualThreadPerTaskExecutor(),
) : Runnable {
    private var running = true

    override fun run() {
        while (running) {
            val elapsedTime = measureTime {
                processPendingRequests()
            }
            events(ProcessingEvent.BatchProcessed(batchSize, elapsedTime))
            val remainingTime = maxPollingTime - elapsedTime
            if (remainingTime > Duration.ZERO) {
                events(ProcessingEvent.PollWait(remainingTime))
                Thread.sleep(remainingTime.inWholeMilliseconds)
            }
        }
    }

    fun stop() {
        running = false
    }

    fun start() {
        executorService.execute(this)
    }

    fun processPendingRequests(): Unit = transactor.perform { postbox ->
        // TODO: define a limit for the number of requests to process
        // TODO: exclude requests marked as permanent failures
        // TODO: mark requests as "processing" to allow for multiple instances of this function to run concurrently?
        for (pending in postbox.pendingRequests(batchSize)) {
            processPendingRequest(postbox, pending)
        }
    }

    private fun processPendingRequest(postbox: Postbox, pending: Postbox.PendingRequest,
                                      successCriteria: (Response) -> Boolean = { it.status.successful }
    ): Result<Unit, PostboxError> = target(pending.request).let { response ->
        if (successCriteria(response)) {
            postbox.markProcessed(pending.requestId, response)
        } else {
            Failure(PostboxError.RequestProcessingFailure("response was not successful"))
        }
    }
}

sealed class ProcessingEvent : Event {
    data class BatchProcessed(val batchSize: Int, val duration: Duration) : ProcessingEvent()
    data class PollWait(val duration: Duration) : ProcessingEvent()
}
