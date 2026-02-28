package com.autoglm.phoneagent.action

import android.app.Activity
import android.graphics.Rect
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.EditText
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.resume
import kotlin.math.abs

/**
 * Executes actions on the Android UI.
 * Operates within the same app, so direct view access is available.
 *
 * @param activityProvider A function that returns the current activity.
 *                         This allows the executor to work with activity transitions.
 */
class ActionExecutor(private val activityProvider: () -> Activity) {

    /**
     * Get the current root view dynamically.
     * This ensures we use the correct view even after activity transitions.
     */
    private val rootView: ViewGroup
        get() = activityProvider().window.decorView as ViewGroup

    // Helper method for conditional logging
    private fun logDebug(message: String) {
        println("[ActionExecutor] $message")
    }

    private fun logError(message: String) {
        println("[ActionExecutor] ERROR: $message")
    }

    /**
     * Execute an action and return the result.
     *
     * @param action The action to execute
     * @return ExecutionResult indicating success/failure
     */
    suspend fun execute(action: AgentAction): ExecutionResult {
        return try {
            when (action.type) {
                ActionType.TAP -> executeTap(action)
                ActionType.TAP_WITH_MESSAGE -> executeTapWithMessage(action)
                ActionType.TYPE -> executeType(action)
                ActionType.SWIPE -> executeSwipe(action)
                ActionType.LONG_PRESS -> executeLongPress(action)
                ActionType.DOUBLE_TAP -> executeDoubleTap(action)
                ActionType.BACK -> executeBack()
                ActionType.WAIT -> executeWait(action)
                ActionType.TAKE_OVER -> executeTakeOver(action)
                ActionType.FINISH -> ExecutionResult.Finished
                ActionType.UNKNOWN -> ExecutionResult.Unknown(action.rawAction)
            }
        } catch (e: Exception) {
            ExecutionResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Execute a tap action at the given coordinates.
     */
    private suspend fun executeTap(action: AgentAction): ExecutionResult {
        val x = action.coordinates[0]
        val y = action.coordinates[1]

        // Map normalized coordinates (0-999) to actual screen coordinates
        val actualX = (x.toFloat() / 999f * rootView.width).toInt()
        val actualY = (y.toFloat() / 999f * rootView.height).toInt()

        // Find the view at the coordinates
        val view = findViewAt(actualX, actualY)

        return if (view != null) {
            // Perform click on the view
            val performed = performClick(view)
            if (performed) {
                ExecutionResult.Success("点击了 ${getViewDescription(view)}")
            } else {
                // Fallback to simulated touch event
                simulateTap(actualX, actualY)
                ExecutionResult.Success("点击坐标 ($actualX, $actualY)")
            }
        } else {
            // No view found, simulate touch event
            simulateTap(actualX, actualY)
            ExecutionResult.Success("点击坐标 ($actualX, $actualY)")
        }
    }

    /**
     * Execute a tap with confirmation message (for sensitive actions).
     */
    private suspend fun executeTapWithMessage(action: AgentAction): ExecutionResult {
        // For now, just execute as a normal tap
        // The confirmation should be handled by the AgentEngine
        return executeTap(action.copy(type = ActionType.TAP))
    }

    /**
     * Execute a type action to input text.
     */
    private suspend fun executeType(action: AgentAction): ExecutionResult {
        val text = action.text ?: return ExecutionResult.Error("缺少文本内容")

        // Try to find currently focused EditText
        val focusedView = rootView.findFocus()

        val editText = when (focusedView) {
            is EditText -> focusedView
            else -> {
                // Try to find EditText at coordinates
                val x = action.coordinates.getOrNull(0)?.let { (it.toFloat() / 999f * rootView.width).toInt() } ?: return ExecutionResult.Error("无效的坐标")
                val y = action.coordinates.getOrNull(1)?.let { (it.toFloat() / 999f * rootView.height).toInt() } ?: return ExecutionResult.Error("无效的坐标")
                findViewAt(x, y) as? EditText
            }
        }

        return if (editText != null) {
            editText.setText(text)
            ExecutionResult.Success("输入文本: $text")
        } else {
            ExecutionResult.Error("未找到输入框")
        }
    }

    /**
     * Execute a swipe action.
     * Amplifies swipe distance by 1.8x for more noticeable scrolling.
     */
    private suspend fun executeSwipe(action: AgentAction): ExecutionResult {
        val start = action.start ?: return ExecutionResult.Error("缺少起始坐标")
        val end = action.end ?: return ExecutionResult.Error("缺少结束坐标")

        // Amplify swipe distance for more noticeable scrolling
        val swipeAmplification = 1.8f

        // Calculate center point of swipe
        val centerX = (start[0] + end[0]) / 2f
        val centerY = (start[1] + end[1]) / 2f

        // Calculate amplified delta for end position
        val amplifiedDeltaX = (end[0] - start[0]) * swipeAmplification
        val amplifiedDeltaY = (end[1] - start[1]) * swipeAmplification

        // Calculate new amplified end coordinates
        val amplifiedEndX = (centerX + amplifiedDeltaX / 2).coerceIn(0f, 999f).toInt()
        val amplifiedEndY = (centerY + amplifiedDeltaY / 2).coerceIn(0f, 999f).toInt()

        val startX = (start[0].toFloat() / 999f * rootView.width).toInt()
        val startY = (start[1].toFloat() / 999f * rootView.height).toInt()
        val endX = (amplifiedEndX.toFloat() / 999f * rootView.width).toInt()
        val endY = (amplifiedEndY.toFloat() / 999f * rootView.height).toInt()

        // Try to find a scrollable view at the start position
        val scrollable = try {
            findScrollableAt(startX, startY)
        } catch (e: Exception) {
            logError("查找可滚动视图异常: ${e.message}")
            null
        }

        if (scrollable != null) {
            // Use scrollBy for smoother scrolling
            val deltaX = endX - startX
            val deltaY = endY - startY

            // Must run scroll operations on main thread
            withContext(Dispatchers.Main) {
                try {
                    if (scrollable is RecyclerView) {
                        scrollable.scrollBy(deltaX, deltaY)
                    } else if (scrollable is NestedScrollView) {
                        // Check if NestedScrollView can scroll
                        val child = scrollable.getChildAt(0)

                        if (child != null && child is ViewGroup) {
                            // Check for RecyclerView inside NestedScrollView
                            for (i in 0 until child.childCount) {
                                val grandChild = child.getChildAt(i)
                                if (grandChild is RecyclerView) {
                                    grandChild.scrollBy(deltaX, deltaY)
                                    delay(100)
                                    return@withContext
                                }
                            }

                            // Calculate actual content height by summing all children
                            var totalContentHeight = 0
                            for (i in 0 until child.childCount) {
                                val grandChild = child.getChildAt(i)
                                totalContentHeight = maxOf(totalContentHeight, grandChild.bottom)
                            }

                            // Calculate max scroll based on content height
                            val maxScrollY = (totalContentHeight - scrollable.height + scrollable.paddingTop + scrollable.paddingBottom).coerceAtLeast(0)

                            if (maxScrollY > 0) {
                                // Use simulateSwipe to send real touch events
                                simulateSwipe(startX, startY, endX, endY)
                                delay(50)
                                return@withContext
                            }
                        }

                        simulateSwipe(startX, startY, endX, endY)
                    } else if (scrollable is ScrollView) {
                        val child = scrollable.getChildAt(0)
                        val childHeight = child?.height ?: 0
                        val maxScrollY = (childHeight - scrollable.height).coerceAtLeast(0)
                        val currentScrollY = scrollable.scrollY
                        val newScrollY = (currentScrollY - deltaY).coerceIn(0, maxScrollY)
                        scrollable.scrollTo(0, newScrollY)
                        delay(100)
                    } else if (scrollable is WebView) {
                        scrollable.scrollBy(deltaX, deltaY)
                        delay(100)
                    } else {
                        // Fallback to simulated swipe
                        simulateSwipe(startX, startY, endX, endY)
                    }
                } catch (e: Exception) {
                    logError("滚动异常: ${e.message}")
                    simulateSwipe(startX, startY, endX, endY)
                }
            }

            val direction = when {
                abs(deltaY) > abs(deltaX) -> if (deltaY < 0) "向上滑动" else "向下滑动"
                deltaX < 0 -> "向左滑动"
                else -> "向右滑动"
            }

            return ExecutionResult.Success("$direction")
        } else {
            // No scrollable view found, simulate swipe gesture
            simulateSwipe(startX, startY, endX, endY)
            return ExecutionResult.Success("滑动从 ($startX, $startY) 到 ($endX, $endY)")
        }
    }

    /**
     * Execute a long press action.
     */
    private suspend fun executeLongPress(action: AgentAction): ExecutionResult {
        val x = (action.coordinates[0].toFloat() / 999f * rootView.width).toInt()
        val y = (action.coordinates[1].toFloat() / 999f * rootView.height).toInt()

        simulateLongPress(x, y)
        return ExecutionResult.Success("长按坐标 ($x, $y)")
    }

    /**
     * Execute a double tap action.
     */
    private suspend fun executeDoubleTap(action: AgentAction): ExecutionResult {
        val x = (action.coordinates[0].toFloat() / 999f * rootView.width).toInt()
        val y = (action.coordinates[1].toFloat() / 999f * rootView.height).toInt()

        simulateDoubleTap(x, y)
        return ExecutionResult.Success("双击坐标 ($x, $y)")
    }

    /**
     * Execute a back action.
     */
    private suspend fun executeBack(): ExecutionResult {
        return suspendCancellableCoroutine { continuation ->
            val currentActivity = activityProvider()
            currentActivity.runOnUiThread {
                if (currentActivity is AppCompatActivity) {
                    currentActivity.onBackPressedDispatcher.onBackPressed()
                } else {
                    currentActivity.finish()
                }
                continuation.resume(ExecutionResult.Success("导航返回"))
            }
        }
    }

    /**
     * Execute a wait action.
     */
    private suspend fun executeWait(action: AgentAction): ExecutionResult {
        val duration = action.duration ?: 1000
        delay(duration)
        return ExecutionResult.Success("等待 ${duration}ms")
    }

    /**
     * Execute a takeover action (request user assistance).
     */
    private suspend fun executeTakeOver(action: AgentAction): ExecutionResult {
        val message = action.message ?: "需要用户协助"
        return ExecutionResult.TakeOver(message)
    }

    /**
     * Find the view at the given coordinates.
     */
    private fun findViewAt(x: Int, y: Int): View? {
        val views = rootView.touchables
        println("   findViewAt($x, $y): touchables.size=${views.size}")

        // Find the topmost clickable view at the position
        val result = views
            .filter { it.isEnabled && it.isClickable }
            .firstOrNull { view ->
                val rect = Rect()
                view.getGlobalVisibleRect(rect)
                val contained = rect.contains(x, y)
                if (contained) {
                    println("      - ${getViewDescription(view)}: rect=$rect")
                }
                contained
            }

        println("   findViewAt result: ${result?.let { getViewDescription(it) } ?: "null"}")
        return result
    }

    /**
     * Find a scrollable view at the given coordinates.
     */
    private fun findScrollableAt(x: Int, y: Int): View? {
        return try {
            println("   findScrollableAt($x, $y): start")

            // Try to find common scrollable container views
            // android.R.id.content is the content container, need to search its children
            val currentActivity = activityProvider()
            val contentView = currentActivity.findViewById<View>(android.R.id.content)
            if (contentView != null && contentView is ViewGroup) {
                println("   found android.R.id.content: ${contentView.javaClass.simpleName}")

                // Search for scrollable views in the content view
                val scrollableChildren = findScrollableChildren(contentView)
                println("   found ${scrollableChildren.size} scrollable children in content")

                val result = scrollableChildren.firstOrNull { view ->
                    try {
                        val rect = Rect()
                        view.getGlobalVisibleRect(rect)
                        val contained = rect.contains(x, y)
                        if (contained) {
                            println("      - ${view.javaClass.simpleName}: rect=$rect, contains=true")
                        }
                        contained
                    } catch (e: Exception) {
                        println("      - ${view.javaClass.simpleName}: ERROR - ${e.message}")
                        false
                    }
                }

                if (result != null) {
                    println("   found scrollable at position: ${result.javaClass.simpleName}")
                    return result
                }
            } else {
                println("   android.R.id.content not found or not a ViewGroup")
            }

            // Try to find RecyclerView
            println("   findScrollableAt: trying to find RecyclerView...")
            val recyclerView = findViewOfTypeInHierarchy(RecyclerView::class.java, x, y)
            if (recyclerView != null) {
                println("   found RecyclerView at ($x, $y)")
                return recyclerView
            }

            // Try to find ScrollView
            println("   findScrollableAt: trying to find ScrollView...")
            val scrollView = findViewOfTypeInHierarchy(ScrollView::class.java, x, y)
            if (scrollView != null) {
                println("   found ScrollView at ($x, $y)")
                return scrollView
            }

            // Try to find WebView
            println("   findScrollableAt: trying to find WebView...")
            val webView = findViewOfTypeInHierarchy(WebView::class.java, x, y)
            if (webView != null) {
                println("   found WebView at ($x, $y)")
                return webView
            }

            println("   no scrollable found at ($x, $y)")
            null
        } catch (e: Exception) {
            println("   findScrollableAt ERROR: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Find all scrollable children in a ViewGroup.
     */
    private fun findScrollableChildren(parent: ViewGroup): List<View> {
        val scrollables = mutableListOf<View>()

        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val childName = child.javaClass.simpleName
            println("      child[$i]: $childName")

            when {
                child is RecyclerView || child is ScrollView || child is WebView || child is NestedScrollView -> {
                    println("         -> is scrollable!")
                    scrollables.add(child)
                }
                child is ViewGroup -> {
                    scrollables.addAll(findScrollableChildren(child))
                }
            }
        }

        return scrollables
    }

    /**
     * Find a view of specific type at coordinates by searching entire hierarchy.
     */
    private fun <T : View> findViewOfTypeInHierarchy(clazz: Class<T>, x: Int, y: Int): T? {
        return findViewOfTypeInHierarchy(rootView, clazz, x, y)
    }

    /**
     * Recursively find a view of specific type at coordinates.
     */
    private fun <T : View> findViewOfTypeInHierarchy(parent: ViewGroup, clazz: Class<T>, x: Int, y: Int, depth: Int = 0): T? {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)

            if (clazz.isInstance(child)) {
                val rect = Rect()
                child.getGlobalVisibleRect(rect)
                if (rect.contains(x, y)) {
                    println("      found ${clazz.simpleName} at depth=$depth, rect=$rect")
                    @Suppress("UNCHECKED_CAST")
                    return child as T
                } else {
                    println("      found ${clazz.simpleName} but rect=$rect doesn't contain ($x, $y)")
                }
            }

            if (child is ViewGroup) {
                val found = findViewOfTypeInHierarchy(child, clazz, x, y, depth + 1)
                if (found != null) {
                    return found
                }
            }
        }
        return null
    }

    /**
     * Find a view of specific type at coordinates.
     */
    private inline fun <reified T : View> findViewOfType(x: Int, y: Int): T? {
        return try {
            println("      findViewOfType<${T::class.simpleName}>($x, $y)")
            val views = rootView.touchables
            println("      found ${views.size} touchables")

            val result = views
                .filterIsInstance<T>()
                .firstOrNull { view ->
                    try {
                        val rect = Rect()
                        view.getGlobalVisibleRect(rect)
                        val contains = rect.contains(x, y)
                        if (contains) {
                            println("         found ${T::class.simpleName} at rect=$rect")
                        }
                        contains
                    } catch (e: Exception) {
                        println("         ERROR checking view: ${e.message}")
                        false
                    }
                }

            if (result == null) {
                println("      no ${T::class.simpleName} found at ($x, $y)")
            }
            result
        } catch (e: Exception) {
            println("      findViewOfType ERROR: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Perform click on a view.
     */
    private suspend fun performClick(view: View): Boolean {
        return withContext(Dispatchers.Main) {
            if (view.isClickable) {
                view.performClick()
            } else {
                false
            }
        }
    }

    /**
     * Simulate a tap event at the given coordinates.
     */
    private suspend fun simulateTap(x: Int, y: Int) {
        withContext(Dispatchers.Main) {
            val downTime = System.currentTimeMillis()

            val downEvent = MotionEvent.obtain(
                downTime,
                downTime,
                MotionEvent.ACTION_DOWN,
                x.toFloat(),
                y.toFloat(),
                0
            )

            val upEvent = MotionEvent.obtain(
                downTime,
                downTime + 100,
                MotionEvent.ACTION_UP,
                x.toFloat(),
                y.toFloat(),
                0
            )

            println("   simulateTap: dispatching events to rootView")
            rootView.dispatchTouchEvent(downEvent)
            rootView.dispatchTouchEvent(upEvent)

            downEvent.recycle()
            upEvent.recycle()
        }
    }

    /**
     * Simulate a swipe gesture.
     * Uses 200ms duration and 20 steps for faster, more responsive swipes.
     */
    private suspend fun simulateSwipe(x1: Int, y1: Int, x2: Int, y2: Int) {
        withContext(Dispatchers.Main) {
            // Faster swipe parameters for better responsiveness
            val actualDuration = 200L
            val steps = 20
            val stepDelay = actualDuration / steps
            val downTime = System.currentTimeMillis()

            println("   simulateSwipe: from=($x1,$y1) to=($x2,$y2), actualDuration=${actualDuration}ms, steps=$steps")
            println("   Thread: ${Thread.currentThread().name}, isMainThread: ${Looper.myLooper() == Looper.getMainLooper()}")

            // Try to find RecyclerView first (most common scrollable in modern apps)
            val recyclerView = findViewOfTypeInHierarchy(RecyclerView::class.java, x1, y1)
            val nestedScrollView = if (recyclerView == null) {
                findViewOfTypeInHierarchy(NestedScrollView::class.java, x1, y1)
            } else null

            val targetView = recyclerView ?: nestedScrollView ?: rootView

            if (recyclerView != null) {
                println("   Found RecyclerView, will dispatch events directly to it")
                println("   RecyclerView.canScrollVertically(-1): ${recyclerView.canScrollVertically(-1)}")
            } else if (nestedScrollView != null) {
                println("   Found NestedScrollView, will dispatch events directly to it")
                println("   NestedScrollView.canScrollVertically(-1): ${nestedScrollView.canScrollVertically(-1)}")
            } else {
                println("   No scrollable found at position, using rootView")
            }

            for (i in 0..steps) {
                val eventTime = downTime + (actualDuration * i / steps)
                val progress = i.toFloat() / steps
                val x = x1 + (x2 - x1) * progress
                val y = y1 + (y2 - y1) * progress

                val event = MotionEvent.obtain(
                    downTime,  // Down time - when the gesture started
                    eventTime, // Event time - when this specific event occurred
                    when (i) {
                        0 -> MotionEvent.ACTION_DOWN
                        steps -> MotionEvent.ACTION_UP
                        else -> MotionEvent.ACTION_MOVE
                    },
                    x,
                    y,
                    0
                )

                val handled = targetView.dispatchTouchEvent(event)
                if (i == 0 || i == steps || i == steps / 2) {
                    println("   Event[$i]: ${event.actionName()}, handled=$handled, target=${targetView.javaClass.simpleName}, x=${x.toInt()}, y=${y.toInt()}")
                }
                event.recycle()

                if (i < steps) {
                    delay(stepDelay.toLong())
                }
            }

            // Minimal wait to ensure UI has time to process the scroll
            delay(50)

            println("   simulateSwipe: dispatched ${steps + 1} events")
        }
    }

    /**
     * Get the name of a MotionEvent action.
     */
    private fun MotionEvent.actionName(): String {
        return when (action) {
            MotionEvent.ACTION_DOWN -> "ACTION_DOWN"
            MotionEvent.ACTION_UP -> "ACTION_UP"
            MotionEvent.ACTION_MOVE -> "ACTION_MOVE"
            MotionEvent.ACTION_CANCEL -> "ACTION_CANCEL"
            else -> "ACTION_${action}"
        }
    }

    /**
     * Simulate a long press event.
     */
    private suspend fun simulateLongPress(x: Int, y: Int) {
        val downTime = System.currentTimeMillis()
        val longPressTimeout = getLongPressTimeout()

        val downEvent = MotionEvent.obtain(
            downTime,
            downTime,
            MotionEvent.ACTION_DOWN,
            x.toFloat(),
            y.toFloat(),
            0
        )

        rootView.dispatchTouchEvent(downEvent)

        // Wait for long press timeout
        delay(longPressTimeout)

        val upEvent = MotionEvent.obtain(
            downTime,
            downTime + longPressTimeout,
            MotionEvent.ACTION_UP,
            x.toFloat(),
            y.toFloat(),
            0
        )

        rootView.dispatchTouchEvent(upEvent)

        downEvent.recycle()
        upEvent.recycle()
    }

    /**
     * Simulate a double tap event.
     */
    private suspend fun simulateDoubleTap(x: Int, y: Int) {
        simulateTap(x, y)
        delay(100)
        simulateTap(x, y)
    }

    /**
     * Get a description of the view for logging.
     */
    private fun getViewDescription(view: View): String {
        return buildString {
            if (view.id != View.NO_ID) {
                try {
                    append(view.resources?.getResourceEntryName(view.id))
                } catch (e: Exception) {
                    append("id_${view.id}")
                }
            }
            append("(${view.javaClass.simpleName})")
        }
    }

    companion object {
        /**
         * Get the long press timeout from ViewConfiguration.
         */
        private fun getLongPressTimeout(): Long {
            return try {
                android.view.ViewConfiguration.getLongPressTimeout().toLong()
            } catch (e: Exception) {
                500L // Default fallback
            }
        }
    }
}

/**
 * Result of executing an action.
 */
sealed class ExecutionResult {
    /** Action executed successfully */
    data class Success(val msg: String) : ExecutionResult()

    /** Action execution failed */
    data class Error(val msg: String) : ExecutionResult()

    /** Task finished */
    data object Finished : ExecutionResult()

    /** User takeover requested */
    data class TakeOver(val msg: String) : ExecutionResult()

    /** Unknown action */
    data class Unknown(val rawAction: String) : ExecutionResult()

    /**
     * Check if the execution was successful.
     */
    val isSuccess: Boolean
        get() = this is Success

    /**
     * Check if the task should finish.
     */
    val shouldFinish: Boolean
        get() = this is Finished

    /**
     * Check if user takeover is requested.
     */
    val requiresTakeover: Boolean
        get() = this is TakeOver

    /**
     * Get the message for this result.
     */
    fun getMessage(): String = when (this) {
        is Success -> msg
        is Error -> msg
        is Finished -> "任务完成"
        is TakeOver -> msg
        is Unknown -> "未知操作: $rawAction"
    }
}
