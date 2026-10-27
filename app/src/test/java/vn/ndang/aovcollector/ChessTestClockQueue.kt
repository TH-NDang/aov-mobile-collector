package vn.ndang.aovcollector

import java.util.PriorityQueue

internal class ChessTestClockQueue {
    private data class Event(val time: Long, val order: Long, val action: () -> Unit)

    private val events = PriorityQueue<Event>(compareBy({ it.time }, { it.order }))
    private var order = 0L
    var now = 0L
        private set

    fun add(action: () -> Unit) = later(0L, action)

    fun addLast(action: () -> Unit) = add(action)

    fun later(ms: Long, action: () -> Unit) {
        events.add(Event(now + ms, order++, action))
    }

    fun isNotEmpty() = events.isNotEmpty()

    fun removeFirst(): () -> Unit {
        val event = events.remove()
        return {
            now = event.time
            event.action()
        }
    }

    fun drain() {
        var count = 0
        while (isNotEmpty()) {
            check(++count < 10_000)
            removeFirst().invoke()
        }
    }
}
