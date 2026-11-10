package test.probe

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

// Checks whether location and speech keep working while another app covers this one.
class ProbeService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var fused: FusedLocation
    private lateinit var tts: TextToSpeech
    private var ticks = 0

    private fun log(text: String) { Log.i("PROBE", "svc $text") }

    private val listener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) = log("location=${location?.latitude},${location?.longitude}")
        override fun onError(e: Exception) = log("location ERROR $e")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel("probe", "Probe", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val notification = Notification.Builder(this, "probe")
            .setContentTitle("Probe").setSmallIcon(android.R.drawable.ic_media_play).build()
        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        fused = FusedLocation(applicationContext)
        fused.initialize()
        tts = TextToSpeech(applicationContext, Mode.ON_DEVICE)
        tts.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                tts.addEventListener(object : EventListener {
                    override fun onReady() = Unit
                    override fun onStart() = log("tts start")
                    override fun onUpdatedRms(rms: Double) = Unit
                    override fun onDone() = log("tts done")
                    override fun onStop() = log("tts stop")
                    override fun onError(errorType: ErrorType) = log("tts ERROR $errorType")
                })
            }
            override fun onFailure(errorType: ErrorType) = log("tts init FAIL $errorType")
        })
        handler.post(::tick)
    }

    private fun tick() {
        ticks++
        log("tick $ticks")
        fused.getLastKnownLocation(listener)
        if (ticks == 13) tts.speak("가려진 상태에서 나오는 음성입니다.", true)
        handler.postDelayed(::tick, 10000)
    }
}
