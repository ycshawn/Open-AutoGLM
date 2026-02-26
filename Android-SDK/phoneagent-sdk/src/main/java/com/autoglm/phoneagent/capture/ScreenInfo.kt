package com.autoglm.phoneagent.capture

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Information about the current screen state.
 * Used to provide additional context to the model about the UI hierarchy.
 */
data class ScreenInfo(
    val screenName: String,
    val screenWidth: Int,
    val screenHeight: Int,
    val visibleElements: List<UIElement> = emptyList(),
    val extraInfo: Map<String, Any> = emptyMap()
) {
    /**
     * Represents a UI element on the screen.
     *
     * @property id View ID (or generated ID if none)
     * @property type View class name
     * @property text Displayed text (if any)
     * @property bounds View bounds in screen coordinates
     * @property clickable Whether the view is clickable
     * @property enabled Whether the view is enabled
     * @property focusable Whether the view can take focus
     * @property focused Whether the view is currently focused
     */
    data class UIElement(
        val id: String,
        val type: String,
        val text: String?,
        val bounds: Rect,
        val clickable: Boolean,
        val enabled: Boolean,
        val focusable: Boolean,
        val focused: Boolean
    ) {
        fun toJson(): JSONObject {
            return JSONObject().apply {
                put("id", id)
                put("type", type)
                put("text", text ?: "")
                put("bounds", JSONObject().apply {
                    put("left", bounds.left)
                    put("top", bounds.top)
                    put("right", bounds.right)
                    put("bottom", bounds.bottom)
                })
                put("clickable", clickable)
                put("enabled", enabled)
                put("focusable", focusable)
                put("focused", focused)
            }
        }
    }

    /**
     * Convert screen info to JSON for API requests.
     */
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("current_screen", screenName)
            put("screen_width", screenWidth)
            put("screen_height", screenHeight)

            if (visibleElements.isNotEmpty()) {
                put("ui_elements", JSONArray().apply {
                    visibleElements.forEach { element ->
                        put(element.toJson())
                    }
                })
            }

            // Add extra info
            extraInfo.forEach { (key, value) ->
                when (value) {
                    is String -> put(key, value)
                    is Number -> put(key, value)
                    is Boolean -> put(key, value)
                    else -> put(key, value.toString())
                }
            }
        }
    }

    /**
     * Convert screen info to JSON string.
     */
    fun toJsonString(): String {
        return toJson().toString(2) // Pretty print with 2 spaces
    }

    companion object {
        /**
         * Create screen info from an activity.
         * This will scan the view hierarchy to collect UI element information.
         *
         * @param activity The activity to scan
         * @param includeElements Whether to include UI element details (default: false for performance)
         * @return ScreenInfo object with screen details
         */
        suspend fun fromActivity(
            activity: Activity,
            includeElements: Boolean = false
        ): ScreenInfo = withContext(Dispatchers.Main) {
            val decorView = activity.window.decorView
            val screenWidth = decorView.width
            val screenHeight = decorView.height
            val screenName = activity.javaClass.simpleName

            val elements = if (includeElements) {
                collectUIElements(decorView)
            } else {
                emptyList()
            }

            ScreenInfo(
                screenName = screenName,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                visibleElements = elements
            )
        }

        /**
         * Recursively collect UI elements from the view hierarchy.
         * This is an expensive operation, use sparingly.
         */
        private fun collectUIElements(rootView: View): List<UIElement> {
            val elements = mutableListOf<UIElement>()
            val screenBounds = Rect(0, 0, rootView.width, rootView.height)

            fun traverseView(view: View, depth: Int) {
                // Limit depth to avoid infinite loops
                if (depth > 20) return

                // Skip invisible views
                if (view.visibility != View.VISIBLE) return

                // Get view bounds
                val bounds = Rect()
                view.getGlobalVisibleRect(bounds)

                // Check if view is actually on screen
                if (!bounds.intersect(screenBounds)) return

                // Skip very small views (likely decorative)
                if (bounds.width() < 10 || bounds.height() < 10) return

                // Get view ID
                val viewId = if (view.id != View.NO_ID) {
                    try {
                        view.resources?.getResourceEntryName(view.id) ?: "id_${view.id}"
                    } catch (e: Exception) {
                        "id_${view.id}"
                    }
                } else {
                    view.javaClass.simpleName + "_" + System.identityHashCode(view)
                }

                // Get text content
                val text = when (view) {
                    is TextView -> view.text?.toString()
                    is EditText -> view.text?.toString()
                    else -> null
                }

                // Determine if this is an interactive element worth including
                val isInteractive = view.isClickable ||
                    view.isFocusable ||
                    view is EditText ||
                    (view as? TextView)?.isFocusable == true

                if (isInteractive || text != null) {
                    elements.add(
                        UIElement(
                            id = viewId,
                            type = view.javaClass.simpleName,
                            text = text,
                            bounds = bounds,
                            clickable = view.isClickable,
                            enabled = view.isEnabled,
                            focusable = view.isFocusable,
                            focused = view.isFocused
                        )
                    )
                }

                // Recursively traverse children
                if (view is ViewGroup) {
                    for (i in 0 until view.childCount) {
                        traverseView(view.getChildAt(i), depth + 1)
                    }
                }
            }

            traverseView(rootView, 0)
            return elements
        }
    }
}
