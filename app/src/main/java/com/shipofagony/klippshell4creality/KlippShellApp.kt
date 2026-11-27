package com.shipofagony.klippshell4creality

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.json.JSONObject
import java.lang.ref.WeakReference

class KlippShellApp : Application(), Application.ActivityLifecycleCallbacks {

    interface CompanionCommandHandler {
        fun onCompanionCommand(command: String): Boolean
        fun getCompanionLiveState(): JSONObject
        fun getCompanionPrinterIp(): String
        fun getCompanionActiveUrl(): String
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentActivityRef: WeakReference<Activity>? = null
    private var activeDialogRef: WeakReference<Dialog>? = null

    private var dialogCursorRef: WeakReference<View>? = null
    private var dialogCursorX = -1f
    private var dialogCursorY = -1f

    private var activityCursorRef: WeakReference<View>? = null
    private var activityCursorX = -1f
    private var activityCursorY = -1f

    lateinit var companionServerManager: CompanionServerManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        registerActivityLifecycleCallbacks(this)

        val prefs = getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        companionServerManager = CompanionServerManager(
            context = this,
            prefs = prefs,
            scope = appScope,
            onCommandReceived = { command -> dispatchRemoteCommand(command) },
            getCurrentPrinterIp = {
                (currentActivityRef?.get() as? CompanionCommandHandler)?.getCompanionPrinterIp() ?: ""
            },
            getCurrentActiveUrl = {
                (currentActivityRef?.get() as? CompanionCommandHandler)?.getCompanionActiveUrl() ?: ""
            },
            getLiveState = {
                (currentActivityRef?.get() as? CompanionCommandHandler)?.getCompanionLiveState() ?: JSONObject()
            }
        )
        companionServerManager.startServers()
    }

    fun restartCompanionServer() {
        companionServerManager.stopServers()
        companionServerManager.startServers()
    }

    fun registerActiveDialog(dialog: Dialog?) {
        activeDialogRef = if (dialog != null) WeakReference(dialog) else null
        dialogCursorRef = null
        dialogCursorX = -1f
        dialogCursorY = -1f
        if (dialog != null) {
            activityCursorRef?.get()?.visibility = View.GONE
        }
    }

    private fun toPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun createCursorView(context: Context): View {
        val sizePx = toPx(22)
        return View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E64CAF50"))
                setStroke(toPx(3), Color.WHITE)
            }
            elevation = 200f
            translationZ = 200f
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = FrameLayout.LayoutParams(sizePx, sizePx, Gravity.TOP or Gravity.START)
            visibility = View.GONE
        }
    }

    private fun findFirstVisibleScrollView(view: View): ScrollView? {
        if (view.visibility != View.VISIBLE) return null
        if (view is ScrollView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findFirstVisibleScrollView(view.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    private fun getOrCreateDialogCursor(dialog: Dialog): View? {
        val container = dialog.window?.decorView as? ViewGroup ?: return null
        dialogCursorRef?.get()?.let { existing ->
            if (existing.parent === container) return existing
        }
        val cursor = createCursorView(dialog.context)
        container.addView(cursor)
        dialogCursorRef = WeakReference(cursor)
        return cursor
    }

    private fun handleDialogMouseMove(dialog: Dialog, command: String) {
        val parts = command.split(";")
        if (parts.size < 3) return
        val dx = (parts[1].toFloatOrNull() ?: return) * 1.8f
        val dy = (parts[2].toFloatOrNull() ?: return) * 1.8f

        val container = dialog.window?.decorView as? ViewGroup ?: return
        val cursor = getOrCreateDialogCursor(dialog) ?: return

        val cursorSize = toPx(22)
        val maxW = (container.width - cursorSize).toFloat().coerceAtLeast(0f)
        val maxH = (container.height - cursorSize).toFloat().coerceAtLeast(0f)

        if (dialogCursorX < 0f || dialogCursorY < 0f) {
            dialogCursorX = container.width / 2f
            dialogCursorY = container.height / 2f
        }

        dialogCursorX = (dialogCursorX + dx).coerceIn(0f, maxW)
        dialogCursorY = (dialogCursorY + dy).coerceIn(0f, maxH)

        cursor.visibility = View.VISIBLE
        cursor.bringToFront()
        cursor.x = dialogCursorX
        cursor.y = dialogCursorY
    }

    private fun handleDialogMouseClick(dialog: Dialog): Boolean {
        val cursor = dialogCursorRef?.get()
        if (cursor == null || cursor.visibility != View.VISIBLE) return false

        val decorView = dialog.window?.decorView ?: return false
        val half = toPx(11).toFloat()
        val clickX = dialogCursorX + half
        val clickY = dialogCursorY + half

        val downTime = SystemClock.uptimeMillis()
        val downEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, clickX, clickY, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        val upEvent = MotionEvent.obtain(downTime, downTime + 45, MotionEvent.ACTION_UP, clickX, clickY, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        try {
            decorView.dispatchTouchEvent(downEvent)
            decorView.dispatchTouchEvent(upEvent)
        } finally {
            downEvent.recycle()
            upEvent.recycle()
        }
        return true
    }

    private fun getActivityCursorContainer(activity: Activity): ViewGroup? {
        return activity.findViewById<ViewGroup>(android.R.id.content)
            ?: activity.window?.decorView as? ViewGroup
    }

    private fun getOrCreateActivityCursor(activity: Activity): View? {
        val container = getActivityCursorContainer(activity) ?: return null
        activityCursorRef?.get()?.let { existing ->
            if (existing.parent === container) return existing
        }
        val cursor = createCursorView(activity)
        container.addView(cursor)
        activityCursorRef = WeakReference(cursor)
        return cursor
    }

    private fun handleActivityMouseMove(activity: Activity, command: String) {
        val parts = command.split(";")
        if (parts.size < 3) return
        val dx = (parts[1].toFloatOrNull() ?: return) * 1.8f
        val dy = (parts[2].toFloatOrNull() ?: return) * 1.8f

        val container = getActivityCursorContainer(activity) ?: return
        val cursor = getOrCreateActivityCursor(activity) ?: return

        val cursorSize = toPx(22)
        val maxW = (container.width - cursorSize).toFloat().coerceAtLeast(0f)
        val maxH = (container.height - cursorSize).toFloat().coerceAtLeast(0f)

        if (activityCursorX < 0f || activityCursorY < 0f) {
            activityCursorX = container.width / 2f
            activityCursorY = container.height / 2f
        }

        activityCursorX = (activityCursorX + dx).coerceIn(0f, maxW)
        activityCursorY = (activityCursorY + dy).coerceIn(0f, maxH)

        cursor.visibility = View.VISIBLE
        cursor.bringToFront()
        cursor.x = activityCursorX
        cursor.y = activityCursorY
    }

    private fun handleActivityMouseClick(activity: Activity): Boolean {
        val cursor = activityCursorRef?.get()
        if (cursor == null || cursor.visibility != View.VISIBLE) return false

        val decorView = activity.window?.decorView ?: return false
        val location = IntArray(2)
        cursor.getLocationInWindow(location)
        val half = toPx(11).toFloat()
        val clickX = location[0].toFloat() + half
        val clickY = location[1].toFloat() + half

        val downTime = SystemClock.uptimeMillis()
        val downEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, clickX, clickY, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        val upEvent = MotionEvent.obtain(downTime, downTime + 45, MotionEvent.ACTION_UP, clickX, clickY, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        try {
            decorView.dispatchTouchEvent(downEvent)
            decorView.dispatchTouchEvent(upEvent)
        } finally {
            downEvent.recycle()
            upEvent.recycle()
        }
        return true
    }

    private fun dispatchDirectionKeyWithFallback(
        keyCode: Int,
        getCurrentFocus: () -> View?,
        dispatchKey: (KeyEvent) -> Boolean
    ) {
        val prevFocus = getCurrentFocus()
        dispatchKey(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        dispatchKey(KeyEvent(KeyEvent.ACTION_UP, keyCode))

        val direction = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT
            else -> return
        }

        if (getCurrentFocus() === prevFocus && prevFocus != null) {
            prevFocus.focusSearch(direction)?.requestFocus()
        }
    }

    private fun dispatchRemoteCommand(command: String) {
        mainHandler.post {
            val activity = currentActivityRef?.get() ?: return@post
            if (activity.isFinishing || activity.isDestroyed) return@post

            val openDialog = activeDialogRef?.get()?.takeIf { it.isShowing }

            // 1. Wenn ein Dialog/Menü offen ist: Scrollen, Maus & D-Pad direkt auf dem Dialog-Fenster ausführen
            if (openDialog != null) {
                if (command.startsWith("SCROLL;")) {
                    val dy = command.substringAfter("SCROLL;").toIntOrNull()
                    if (dy != null) {
                        openDialog.window?.decorView?.let { decor ->
                            findFirstVisibleScrollView(decor)?.smoothScrollBy(0, dy)
                        }
                    }
                    return@post
                }
                if (command.startsWith("MOUSE_MOVE;")) {
                    handleDialogMouseMove(openDialog, command)
                    return@post
                }
                if (command == "HIDE_CURSOR") {
                    dialogCursorRef?.get()?.visibility = View.GONE
                    return@post
                }
                if (command == "MOUSE_CLICK" || command == "DPAD_OK" || command == "DPAD_CENTER") {
                    if (handleDialogMouseClick(openDialog)) return@post
                }

                val keyCode = when (command) {
                    "DPAD_UP" -> KeyEvent.KEYCODE_DPAD_UP
                    "DPAD_DOWN" -> KeyEvent.KEYCODE_DPAD_DOWN
                    "DPAD_LEFT" -> KeyEvent.KEYCODE_DPAD_LEFT
                    "DPAD_RIGHT" -> KeyEvent.KEYCODE_DPAD_RIGHT
                    "DPAD_OK", "DPAD_CENTER", "MOUSE_CLICK" -> KeyEvent.KEYCODE_DPAD_CENTER
                    "BACK" -> KeyEvent.KEYCODE_BACK
                    else -> null
                }
                if (keyCode != null) {
                    if (keyCode in listOf(
                            KeyEvent.KEYCODE_DPAD_UP,
                            KeyEvent.KEYCODE_DPAD_DOWN,
                            KeyEvent.KEYCODE_DPAD_LEFT,
                            KeyEvent.KEYCODE_DPAD_RIGHT
                        )
                    ) {
                        dialogCursorRef?.get()?.visibility = View.GONE
                    }
                    dispatchDirectionKeyWithFallback(
                        keyCode = keyCode,
                        getCurrentFocus = { openDialog.currentFocus },
                        dispatchKey = { ev -> openDialog.dispatchKeyEvent(ev) }
                    )
                    return@post
                }
            }

            // 2. Aktivität-spezifische Befehle (z. B. WebViewActivity) prüfen
            if (activity is CompanionCommandHandler && activity.onCompanionCommand(command)) {
                return@post
            }

            // 3. Generische Maus- & Touchpad-Steuerung für MainActivity, SettingsActivity etc.
            if (command.startsWith("MOUSE_MOVE;")) {
                handleActivityMouseMove(activity, command)
                return@post
            }
            if (command == "HIDE_CURSOR") {
                activityCursorRef?.get()?.visibility = View.GONE
                return@post
            }
            if (command == "MOUSE_CLICK" || command == "DPAD_OK" || command == "DPAD_CENTER") {
                if (handleActivityMouseClick(activity)) return@post
            }

            // 4. Generisches 2-Finger-Scrollen für MainActivity, SettingsActivity etc.
            if (command.startsWith("SCROLL;")) {
                val dy = command.substringAfter("SCROLL;").toIntOrNull()
                if (dy != null) {
                    activity.window?.decorView?.let { decor ->
                        findFirstVisibleScrollView(decor)?.smoothScrollBy(0, dy)
                    }
                }
                return@post
            }

            // 5. Generische D-Pad- & Back-Steuerung für MainActivity, SettingsActivity etc.
            val fallbackKeyCode = when (command) {
                "DPAD_UP" -> KeyEvent.KEYCODE_DPAD_UP
                "DPAD_DOWN" -> KeyEvent.KEYCODE_DPAD_DOWN
                "DPAD_LEFT" -> KeyEvent.KEYCODE_DPAD_LEFT
                "DPAD_RIGHT" -> KeyEvent.KEYCODE_DPAD_RIGHT
                "DPAD_OK", "DPAD_CENTER", "MOUSE_CLICK" -> KeyEvent.KEYCODE_DPAD_CENTER
                "BACK" -> KeyEvent.KEYCODE_BACK
                else -> null
            }
            if (fallbackKeyCode != null) {
                if (fallbackKeyCode in listOf(
                        KeyEvent.KEYCODE_DPAD_UP,
                        KeyEvent.KEYCODE_DPAD_DOWN,
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_DPAD_RIGHT
                    )
                ) {
                    activityCursorRef?.get()?.visibility = View.GONE
                }
                dispatchDirectionKeyWithFallback(
                    keyCode = fallbackKeyCode,
                    getCurrentFocus = { activity.currentFocus },
                    dispatchKey = { ev -> activity.dispatchKeyEvent(ev) }
                )
            }
        }
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivityRef = WeakReference(activity)
        activityCursorRef = null
        activityCursorX = -1f
        activityCursorY = -1f
    }

    override fun onActivityPaused(activity: Activity) {
        if (currentActivityRef?.get() === activity) {
            activityCursorRef?.get()?.visibility = View.GONE
            currentActivityRef = null
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}

    companion object {
        @Volatile
        var instance: KlippShellApp? = null
            private set
    }
}