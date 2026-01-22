package org.p2er1n.termcat

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AccessibilityScrollService : AccessibilityService() {
    @Volatile
    private var lastScrollEventTime = 0L
    @Volatile
    private var lastScrollY = -1
    @Volatile
    private var lastMaxScrollY = -1
    @Volatile
    private var lastScrollDeltaY = 0
    @Volatile
    private var lastScrollClass: CharSequence? = null
    @Volatile
    private var lastRequestedScrollPx = -1

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            return
        }
        val scrollY = event.scrollY
        val maxScrollY = event.maxScrollY
        val deltaY = if (lastScrollY >= 0 && scrollY >= 0) {
            scrollY - lastScrollY
        } else {
            0
        }
        lastScrollEventTime = event.eventTime
        lastScrollDeltaY = deltaY
        lastScrollY = scrollY
        lastMaxScrollY = maxScrollY
        lastScrollClass = event.className
        Log.d(
            TAG,
            "scrollEvent: class=${event.className} scrollY=$scrollY max=$maxScrollY delta=$deltaY"
        )
    }

    override fun onInterrupt() {
        // No-op.
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
    }

    private fun performNodeScroll(): Boolean {
        val root = rootInActiveWindow ?: return false
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val canScroll = node.isScrollable || node.actionList.any {
                it.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            }
            if (canScroll && node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                Log.d(TAG, "performNodeScroll: scrolled node=${node.className}")
                return true
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return false
    }

    private fun performGestureScroll(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val bounds = findScrollableBounds() ?: return false
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()
        if (width <= 0f || height <= 0f) return false
        val edge = height * PAGE_EDGE_RATIO
        val startX = bounds.left + width * 0.5f
        val startY = bounds.bottom - edge
        val endY = bounds.top + edge
        if (startY <= endY) return false
        lastRequestedScrollPx = (startY - endY).toInt()
        Log.d(TAG, "performGestureScroll: bounds=$bounds startY=$startY endY=$endY")
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(startX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 350))
            .build()
        val latch = CountDownLatch(1)
        var success = false
        val callback = object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                success = true
                latch.countDown()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                latch.countDown()
            }
        }
        val dispatched = dispatchGesture(gesture, callback, null)
        if (!dispatched) return false
        latch.await(700, TimeUnit.MILLISECONDS)
        return success
    }

    private fun performScrollDown(): Boolean {
        if (performGestureScroll()) return true
        return performNodeScroll()
    }

    private fun findScrollableNode(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var best: AccessibilityNodeInfo? = null
        var bestArea = 0
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val actions = node.actionList
            val canScroll = node.isScrollable || actions.any {
                it.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ||
                    it.id == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            }
            if (canScroll) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                val area = rect.width() * rect.height()
                if (area > bestArea) {
                    bestArea = area
                    best = node
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        if (best != null) {
            val rect = Rect()
            best.getBoundsInScreen(rect)
            Log.d(
                TAG,
                "findScrollableNode: class=${best.className} bounds=$rect range=${best.rangeInfo}"
            )
        } else {
            Log.d(TAG, "findScrollableNode: no scrollable node found")
        }
        return best
    }

    private fun findScrollableBounds(): Rect? {
        val node = findScrollableNode() ?: return null
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return if (rect.isEmpty) null else rect
    }

    private fun isAtScrollEnd(): Boolean {
        val node = findScrollableNode() ?: return false
        val range = node.rangeInfo
        if (range == null || range.max <= 0f) {
            Log.d(TAG, "isAtScrollEnd: no range info")
            return false
        }
        val atEnd = range.current >= range.max
        Log.d(TAG, "isAtScrollEnd: current=${range.current} max=${range.max} atEnd=$atEnd")
        return atEnd
    }

    private fun resetScrollState() {
        lastScrollEventTime = 0L
        lastScrollY = -1
        lastMaxScrollY = -1
        lastScrollDeltaY = 0
        lastScrollClass = null
        lastRequestedScrollPx = -1
    }

    companion object {
        @Volatile
        private var instance: AccessibilityScrollService? = null
        private const val TAG = "AccessibilityScroll"
        private const val PAGE_EDGE_RATIO = 0.2f
        private const val SCROLL_EVENT_POLL_MS = 50L

        fun performScrollDown(): Boolean {
            return instance?.performScrollDown() ?: false
        }

        fun isAtScrollEnd(): Boolean {
            return instance?.isAtScrollEnd() ?: false
        }

        fun getLastScrollEventTime(): Long {
            return instance?.lastScrollEventTime ?: 0L
        }

        fun getLastRequestedScrollPx(): Int {
            return instance?.lastRequestedScrollPx ?: -1
        }

        fun waitForScrollState(sinceTime: Long, timeoutMs: Long): ScrollState? {
            val service = instance ?: return null
            val deadline = SystemClock.uptimeMillis() + timeoutMs
            while (SystemClock.uptimeMillis() < deadline) {
                val snapshot = service.snapshotScrollState()
                if (snapshot.eventTime > sinceTime) {
                    return snapshot
                }
                SystemClock.sleep(SCROLL_EVENT_POLL_MS)
            }
            return null
        }

        fun waitForUiToSettle() {
            SystemClock.sleep(350)
        }

        fun resetScrollState() {
            instance?.resetScrollState()
        }
    }

    private fun snapshotScrollState(): ScrollState {
        return ScrollState(
            eventTime = lastScrollEventTime,
            scrollY = lastScrollY,
            maxScrollY = lastMaxScrollY,
            deltaY = lastScrollDeltaY,
            sourceClass = lastScrollClass?.toString().orEmpty()
        )
    }
}

data class ScrollState(
    val eventTime: Long,
    val scrollY: Int,
    val maxScrollY: Int,
    val deltaY: Int,
    val sourceClass: String
)
