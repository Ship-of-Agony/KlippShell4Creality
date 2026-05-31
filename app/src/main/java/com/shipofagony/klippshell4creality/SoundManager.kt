package com.shipofagony.klippshell4creality

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

object SoundManager {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var toneGenerator: ToneGenerator? = null
    private var playCount = 0
    private var loopRunnable: Runnable? = null
    private val melodyHandler = Handler(Looper.getMainLooper())

    init {
        try {
            // Initialisiert den Generator auf maximaler System-Lautstärke für Alarme
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        } catch (e: Exception) {
            e.printStackTrace()
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
            triggerThreeTimesLoop(prefKey)
        } else {
            // Normale Meilensteine (First Layer, 50%...) im echten Betrieb auch nur 1x
            executeSingleTone(prefKey)
        }
    }

    /**
     * Erzeugt die spezifischen Töne oder komplexe Melodien.
     */
    private fun executeSingleTone(prefKey: String) {
        try {
            when {
                // Kritische Fehler: Ein schriller, unüberhörbarer Doppel-Frequenz-Alarm (500ms lang)
                prefKey.contains("error") -> {
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 500)
                }

                // Drucker geht offline: Ein tieferer, warnender Dreifach-Impuls (400ms lang)
                prefKey.contains("offline") -> {
                    toneGenerator?.startTone(ToneGenerator.TONE_SUP_DIAL, 400)
                }

                // Druck fertig (100%): Eine fröhliche, ansteigende 3-Ton-Erfolgsmelodie (C-Dur-Akkord)
                prefKey.contains("100") -> {
                    playHappySuccessMelody()
                }

                // Normale Meilensteine (50%, 75%, First Layer): Ein ganz kurzer, dezenter "Pling" (100ms)
                else -> {
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 100)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Komponiert eine fröhliche, ansteigende Tonfolge direkt auf dem Audio-Chip.
     */
    private fun playHappySuccessMelody() {
        try {
            // Ton 1: Grundton C (heller DTMF-Ton) für 120ms
            toneGenerator?.startTone(ToneGenerator.TONE_DTMF_C, 120)

            // Ton 2: Terz E nach 130ms für 120ms
            melodyHandler.postDelayed({
                toneGenerator?.stopTone()
                toneGenerator?.startTone(ToneGenerator.TONE_DTMF_3, 120)
            }, 130)

            // Ton 3: Quinte G nach 260ms für 250ms (länger ausklingend)
            melodyHandler.postDelayed({
                toneGenerator?.stopTone()
                toneGenerator?.startTone(ToneGenerator.TONE_DTMF_9, 250)
            }, 260)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Schleife, die den spezifischen Alarmton im echten Betrieb exakt 3x abfeuert.
     */
    private fun triggerThreeTimesLoop(prefKey: String) {
        if (playCount < 3) {
            executeSingleTone(prefKey)
            playCount++

            // Da die 100%-Melodie insgesamt ca. 500ms dauert, lassen wir 1.2 Sekunden Luft bis zur Wiederholung
            val delay = when {
                prefKey.contains("100") -> 1200L
                else -> 800L
            }

            val runnable = Runnable {
                triggerThreeTimesLoop(prefKey)
            }
            loopRunnable = runnable
            mainHandler.postDelayed(runnable, delay)
        }
    }

    /**
     * Stoppt sofort die Schleife, bricht die Melodie ab und schneidet den Ton ab.
     */
    fun stopAllSounds() {
        loopRunnable?.let { mainHandler.removeCallbacks(it) }
        loopRunnable = null

        // Alle anstehenden Töne der fröhlichen Melodie ebenfalls löschen
        melodyHandler.removeCallbacksAndMessages(null)

        try {
            toneGenerator?.stopTone()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}