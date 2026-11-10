package test.probe

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import ai.pleos.playground.vehicle.Vehicle
import android.app.Activity
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.property.CarPropertyManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView

// Logs one PROBE line per SDK result so the workflow can read what works on the emulator.
class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var vehicle: Vehicle
    private lateinit var tts: TextToSpeech
    private var car: Car? = null

    private fun log(text: String) { Log.i("PROBE", text) }

    private fun probe(name: String, block: () -> Unit) {
        try { block() } catch (t: Throwable) { log("$name THROW ${t.javaClass.simpleName}: ${t.message}") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "Probe running"; textSize = 48f })
        log("app started")

        probe("vehicle.init") { vehicle = Vehicle(this); vehicle.initialize() }
        probe("car.init") { car = Car.createCar(this); log("car connected=${car?.isConnected}") }
        probe("tts.init") { startTts() }
        probe("navi") { startNavi() }
        probe("fused") { startFused() }
        probe("service") { startForegroundService(android.content.Intent(this, ProbeService::class.java)) }
        handler.postDelayed(::readSignals, 5000)
    }

    private fun readSignals() {
        probe("gear") {
            vehicle.getGear().getCurrentGearSelectionState({ v -> log("gear=$v") }, { e -> log("gear FAIL $e") })
        }
        probe("turn") {
            vehicle.getTurnSignal().getCurrentTurnSignalState({ v -> log("turn=$v") }, { e -> log("turn FAIL $e") })
        }
        probe("brake") {
            vehicle.getBrake().getCurrentBrakePedalStatus({ v -> log("brake=$v") }, { e -> log("brake FAIL $e") })
        }
        probe("speed") {
            val manager = car?.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            log("speed=${manager.getFloatProperty(VehiclePropertyIds.PERF_VEHICLE_SPEED, 0)}")
        }
        probe("ux") {
            val manager = car?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            val r = manager.currentCarUxRestrictions
            log("ux requiresDO=${r.isRequiresDistractionOptimization} active=${r.activeRestrictions}")
        }
        handler.postDelayed(::readSignals, 10000)
    }

    private fun startTts() {
        tts = TextToSpeech(applicationContext, Mode.ON_DEVICE)
        tts.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                log("tts init ok")
                tts.addEventListener(object : EventListener {
                    override fun onReady() = log("tts ready")
                    override fun onStart() = log("tts start")
                    override fun onUpdatedRms(rms: Double) = Unit
                    override fun onDone() = log("tts done")
                    override fun onStop() = log("tts stop")
                    override fun onError(errorType: ErrorType) = log("tts ERROR $errorType")
                })
                tts.speak("시험 음성입니다.", true)
            }
            override fun onFailure(errorType: ErrorType) = log("tts init FAIL $errorType")
        })
    }

    private fun startNavi() {
        val navi = NaviHelper(applicationContext)
        navi.initialize(
            onConnected = {
                log("navi connected")
                navi.addListener(object : NaviHelperEventListener {
                    override fun onCurrentLocationInfo(info: CurrentLocationInfo) = log("navi location=$info")
                    override fun onTBTInfo(info: List<TBTInfo>) = log("navi tbt=$info")
                    override fun onError(errorCode: NaviErrorCode) = log("navi ERROR $errorCode")
                    override fun onRouteStarted(routeStartInfo: RouteStartInfo) = log("navi routeStarted=$routeStartInfo")
                    override fun onRoutingCompleted() {
                        log("navi routingCompleted")
                        navi.getTBTInfo()
                        navi.getDestinationInfo()
                        navi.requestTrip(TripRequest(latitude = 37.48544722, longitude = 127.03636666, poiName = "probe", poiId = "", poiSubId = ""))
                    }
                    override fun onDestinationInfo(destinationInfo: DestinationInfo) = log("navi destination=$destinationInfo")
                    override fun onTripInfo(tripInfo: TripInfo) = log("navi trip=$tripInfo")
                })
                navi.getCurrentLocationInfo()
                navi.getTBTInfo()
                handler.postDelayed({
                    probe("navi.route") {
                        navi.requestRoute(RouteInfo(latitude = 37.48544722, longitude = 127.03636666, displayLongitude = null, displayLatitude = null, poiName = "probe", poiId = "36khp37hh8gqcm5nqhe5", poiSubId = "0", address = "probe"))
                        log("navi route requested")
                    }
                }, 100000)
            },
            onFailed = { t -> log("navi connect FAIL $t") },
        )
    }

    private fun startFused() {
        val fused = FusedLocation(applicationContext)
        val supported = fused.isSupported()
        log("fused supported=$supported")
        if (!supported) return
        fused.initialize()
        val listener = object : FusedLocationListener {
            override fun onChanged(location: LocationData?) = log("fused location=$location")
            override fun onError(e: Exception) = log("fused ERROR $e")
        }
        handler.postDelayed({ probe("fused.last") { fused.getLastKnownLocation(listener) } }, 3000)
    }
}
