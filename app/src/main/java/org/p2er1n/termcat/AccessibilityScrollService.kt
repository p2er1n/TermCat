package org.p2er1n.termcat

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AccessibilityScrollService : AccessibilityService() {
    data class ScrollState(
        val scrollY: Int,
        val maxScrollY: Int,
        val deltaY: Int
    )

    @Volatile
    private var lastScrollEventTime = 0L
    @Volatile
    private var lastScrollY = -1
    @Volatile
    private var lastMaxScrollY = -1
    @Volatile
    private var lastScrollDeltaY = 0
    @Volatile
    private var lastRequestedScrollPx = -1

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onInterrupt() {
        instance = null
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
    }

    private fun resetInternal() {
        lastScrollEventTime = 0L
        lastScrollY = -1
        lastMaxScrollY = -1
        lastScrollDeltaY = 0
        lastRequestedScrollPx = -1
    }

    private fun waitForUiToSettleInternal() {
        SystemClock.sleep(250)
    }

    private fun waitForScrollStateInternal(sinceTime: Long, timeoutMs: Long): ScrollState? {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (lastScrollEventTime > sinceTime) {
                return ScrollState(lastScrollY, lastMaxScrollY, lastScrollDeltaY)
            }
            SystemClock.sleep(40)
        }
        return null
    }

    private fun performScrollDownInternal(): Boolean {
        val node = findScrollableNode() ?: return performGestureScroll()
        lastRequestedScrollPx = -1
        return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    private fun performGestureScroll(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false
        }
        val metrics = resources.displayMetrics
        val x = metrics.widthPixels / 2f
        val startY = metrics.heightPixels * 0.72f
        val endY = metrics.heightPixels * 0.28f
        lastRequestedScrollPx = (startY - endY).toInt()

        val path = Path().apply {
            moveTo(x, startY)
            lineTo(x, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 250)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val latch = CountDownLatch(1)
        var success = false
        dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    success = true
                    latch.countDown()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    latch.countDown()
                }
            },
            null
        )
        latch.await(600, TimeUnit.MILLISECONDS)
        return success
    }

    private fun findScrollableNode(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isScrollable && node.actionList.any {
                    it.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                }) {
                return node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun isAtScrollEndInternal(): Boolean {
        val node = findScrollableNode()
        val range = node?.rangeInfo
        if (range != null && range.max > 0) {
            return range.current >= range.max
        }
        return lastMaxScrollY > 0 && lastScrollY >= lastMaxScrollY
    }

    companion object {
        @Volatile
        private var instance: AccessibilityScrollService? = null

        fun resetScrollState() {
            instance?.resetInternal()
        }

        fun getLastScrollEventTime(): Long {
            return instance?.lastScrollEventTime ?: 0L
        }

        fun waitForUiToSettle() {
            instance?.waitForUiToSettleInternal()
        }

        fun waitForScrollState(sinceTime: Long, timeoutMs: Long): ScrollState? {
            return instance?.waitForScrollStateInternal(sinceTime, timeoutMs)
        }

        fun performScrollDown(): Boolean {
            return instance?.performScrollDownInternal() ?: false
        }

        fun isAtScrollEnd(): Boolean {
            return instance?.isAtScrollEndInternal() ?: false
        }

        fun getLastRequestedScrollPx(): Int {
            return instance?.lastRequestedScrollPx ?: -1
        }
    }
}
