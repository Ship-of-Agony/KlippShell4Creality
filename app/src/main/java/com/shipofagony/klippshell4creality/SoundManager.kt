package com.shipofagony.klippshell4creality

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.util.Log

object SoundManager {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val melodyHandler = Handler(Looper.getMainLooper())
    private var toneGenerator: ToneGenerator? = null

    private var playCount = 0
    private var currentPrefKey: String = ""

    // Dedizierte Runnables statt anonymer Blöcke für garantierte Löschbarkeit
    private val loopRunnable = object : Runnable {
        override fun run() {
            triggerThreeTimesLoop()
        }
    }

    private val toneStep1Runnable = Runnable {
        try {
            toneGenerator?.stopTone()
            toneGenerator?.startTone(ToneGenerator.TONE_DTMF_3, 120)
        } catch (_: Exception) {}
    }

    private val toneStep2Runnable = Runnable {
        try {
            toneGenerator?.stopTone()
            toneGenerator?.startTone(ToneGenerator.TONE_DTMF_9, 250)
        } catch (_: Exception) {}
    }

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        } catch (e: Exception) {
            Log.e("KlippShell", "ToneGenerator konnte nicht initialisiert werden", e)
            toneGenerator = null
        }
    }

    /**
     * FALL 1: Die Vorschau in den Einstellungen (Spielt IMMER nur exakt 1x kurz ab).
     */
    fun playPreview(prefKey: String) {
        stopAllSounds()
        executeSingleTone(prefKey)
    }

    /**
     * FALL 2: Der echte Druckbetrieb (Meilenstein = 1x, Kritisch/Fehler = 3x).
     */
    fun playLiveNotification(prefKey: String) {
        stopAllSounds()
        val isCriticalEvent = prefKey.contains("error") || prefKey.contains("offline") || prefKey.contains("100")

        if (isCriticalEvent) {
            playCount = 0
            currentPrefKey = prefKey
            triggerThreeTimesLoop()
        } else {
            executeSingleTone(prefKey)
        }
    }

    private fun executeSingleTone(prefKey: String) {
        val generator = toneGenerator ?: return
        try {
            when {
                prefKey.contains("error") -> {
                    generator.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 500)
                }
                prefKey.contains("offline") -> {
                    generator.startTone(ToneGenerator.TONE_SUP_DIAL, 400)
                }
                prefKey.contains("100") -> {
                    playHappySuccessMelody()
                }
                else -> {
                    generator.startTone(ToneGenerator.TONE_PROP_ACK, 100)
                }
            }
        } catch (e: Exception) {
            Log.e("KlippShell", "Fehler bei der Tonausgabe", e)
        }
    }

    private fun playHappySuccessMelody() {
        val generator = toneGenerator ?: return
        try {
            generator.startTone(ToneGenerator.TONE_DTMF_C, 120)
            melodyHandler.postDelayed(toneStep1Runnable, 130)
            melodyHandler.postDelayed(toneStep2Runnable, 260)
        } catch (e: Exception) {
            Log.e("KlippShell", "Fehler in der Erfolgsmelodie", e)
        }
    }

    private fun triggerThreeTimesLoop() {
        if (playCount < 3) {
            executeSingleTone(currentPrefKey)
            playCount++

            val delay = if (currentPrefKey.contains("100")) 1200L else 800L
            mainHandler.postDelayed(loopRunnable, delay)
        }
    }

    /**
     * Stoppt sofort alle Schleifen, bricht Melodien ab und schneidet den Ton hart ab.
     */
    fun stopAllSounds() {
        mainHandler.removeCallbacks(loopRunnable)
        melodyHandler.removeCallbacks(toneStep1Runnable)
        melodyHandler.removeCallbacks(toneStep2Runnable)
        melodyHandler.removeCallbacksAndMessages(null)

        try {
            toneGenerator?.stopTone()
        } catch (e: Exception) {
            Log.e("KlippShell", "Fehler beim Stoppen des Tones", e)
        }
    }
}