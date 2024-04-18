package com.example.part1.a4thebetterworld

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.telecom.Call
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.part1.a4thebetterworld.databinding.ActivityMainBinding
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader
import java.util.*



private lateinit var speechRecognizer: SpeechRecognizer


// 녹음 중인지에 대한 여부
private var recording = false

// 음성 인식으로 입력받은 문자열
private lateinit var contents: EditText
private lateinit var contentsResult: String

// 서버에 POST 할 승차 희망 & 승차 완료 & 하차 희망 / 오류 방지 상수 메시지
val boardingRequest = "1"
val boardingComplete = "2"
val errorPrevent = "0"
val exitWish = "1"

// 서버에 POST 하기 위한 변수 (API)
private lateinit var serverAE: String
private lateinit var serverMessage: String // POST 할 body


// 상행선, 하행선을 특정하기 위한 리스트
val busStopList = listOf(
    "순천향 대학교",
    "읍내리 까치골",
    "순천향대학교 후문",
    "경희 학성 아파트",
    "행목리",
    "신창역",
    "행목1리 칠목",
    "대주아파트",
    "친오애아파트",
    "부영아파트",
    "득산농공단지",
    "삼정 백조아파트"
)

class MainActivity : AppCompatActivity() {

    private val busanStationUP = "173640301"
    private val busanStationDown = "173640301"

    private val songdoBeachUp = "175880101"
    private val songdoBeachDown = "174680301"

    private val amnamParkUp = "175700301"
    private val amnamParkDown = "212530103"

    private val gamcheonCultureVillageUp = "175570201"
    private val gamcheonCultureVillageDown = "212530102"

    private val dadepoBeachUp = "175540201"
    private val dadepoBeachDown = "175540201"

    private val amisanObservatoryUp = "175530301"
    private val amisanObservatoryDown = "175110101"

    private val bunechiaJangnimPortUp = "175520201"
    private val bunechiaJangnimPortDown = "175520201"

    private val busanMuseumOfArtUp = "174700401"
    private val busanMuseumOfArtDown = "174700401"

    private val nakdongRiverEstuaryEcocenterUp = "175850301"
    private val nakdongRiverEstuaryEcocenterDown = "175850301"

    private val seokdangMuseumUp = "174390202"
    private val seokdangMuseumDown = "174390202"

    private val internationalMarketUp = "175850302"
    private val internationalMarketDown = "174390201"

    private val yongdusanParkUp = "174390204"
    private val yongdusanParkDown = "174390301"

    private lateinit var binding: ActivityMainBinding

    // 타이머를 위한 핸들러
    private val handler = Handler()

    // API 키로 사용될 변수들
    private lateinit var bstopId: String
    private lateinit var lineId: String

    private lateinit var serverCNT: String // API의 bstopId와 매핑되는 cnt 값(No.1 ~ 12) 혹은 하차 cnt(exit)

    companion object {
        private const val REQUEST_RECORD_AUDIO_CODE = 200
    }

    var tts: TextToSpeech? = null
    private lateinit var testTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkPermission() //음성 인식에 대한 권한 확인

        // R 파일로 UI 설정
        val btnRecord = findViewById<Button>(R.id.btnRecord)
        val btnSignal = findViewById<Button>(R.id.btnSignal)
        contents = findViewById(R.id.editTextContent)
        testTextView = findViewById(R.id.testTextView)

        tts = TextToSpeech(applicationContext) { status ->
            if (status != TextToSpeech.ERROR) {
                tts!!.language = Locale.KOREAN
            }
        }

        btnSignal.setOnClickListener {
            // 서버에 승차 완료 메시지 송신
            serverMessage = boardingComplete
            callMainMethod(serverMessage, serverCNT)
        }

        btnSignal.setOnLongClickListener {
            val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP)

            serverCNT = "exit"
            serverMessage = exitWish
            callMainMethod2(serverMessage, serverCNT)

            true // 이벤트를 소비한 경우 true를 반환합니다.
        }

        // 녹음 관련 이벤트 처리
        btnRecord.setOnClickListener {
            if (!recording) {
                StartRecord() // recording = true
                Toast.makeText(applicationContext, "지금부터 음성 녹음을 시작합니다.", Toast.LENGTH_SHORT).show()
            } else {
                StopRecord()
            }
        }

        fun onPause() {
            tts?.stop()
            tts?.shutdown()
            super.onPause()
        }

        // RecognizerIntent 객체 생성
        intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName())
        // 한국어로 입력 받음
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
    }

    fun callMainMethod(message: String, cnt: String) {
        serverMessage = message
        makeCinMessage(serverAE, serverCNT, serverMessage)
    }

    // 시간 간격을 두고 서버와의 통신할 수 있도록 하는 메서드
    fun callErrorPrevent() {
        serverMessage = errorPrevent
        makeCinMessage(serverAE, serverCNT, serverMessage)
    }

    fun callMainMethod2(message: String, cnt: String) {
        serverMessage = message
        makeCinMessage(serverAE, serverCNT, serverMessage)

        // 1초 뒤에 B 메서드(에러 방지)를 호출하는 Runnable 객체 생성
        val runnable = Runnable {
            callErrorPrevent() // B 메서드 호출
        }

        // Handler를 사용하여 1초 뒤에 runnable 실행
        handler.postDelayed(runnable, 3000) // 1000 밀리초 = 1초
    }

    // 녹음 관련 함수들
    // 녹음 시작
    private fun StartRecord() {
        contents.setText("")
        recording = true

        binding.btnRecord.setText("Stop Record") // applicationContext 부분의 오류 가능성
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext)
        speechRecognizer.setRecognitionListener(listener)
        speechRecognizer.startListening(intent)
    }

    // 녹음 종료
    private fun StopRecord() {
        recording = false
        binding.btnRecord.setText("Start Record")
        speechRecognizer.stopListening()
        Toast.makeText(applicationContext, "음성 기록 중지합니다.", Toast.LENGTH_SHORT).show()

    }

    // SpeechRecognizer의 정의 (리스너의 생성)
    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {

        }

        override fun onBeginningOfSpeech() {
            // 사용자가 말하기 시작함.
        }

        override fun onRmsChanged(rmsdB: Float) {

        }

        override fun onBufferReceived(buffer: ByteArray?) {

        }

        override fun onEndOfSpeech() {
            // 사용자가 말을 멈추면 호출된다.
            // 인식 결과에 따라 onError나 onResult가 호출된다.
        }

        override fun onError(error: Int) { //토스트 에러 메시지 출력
            val message: String = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "오디오 에러"
                SpeechRecognizer.ERROR_CLIENT -> return //speechRecognizer.stopListening()을 호출하면 발생하는 에러
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "퍼미션 없음"
                SpeechRecognizer.ERROR_NETWORK -> "네트워크 에러"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "네트웍 타임아웃"
                SpeechRecognizer.ERROR_NO_MATCH -> return //녹음을 오래하거나 speechRecognizer.stopListening()을 호출하면 발생하는 에러
                //speechRecognizer를 다시 생성하여 녹음 재개
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RECOGNIZER가 바쁨"
                SpeechRecognizer.ERROR_SERVER -> "서버가 이상함"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "말하는 시간초과"
                else -> "알 수 없는 오류임"
            }
            Toast.makeText(applicationContext, "에러가 발생하였습니다. : $message", Toast.LENGTH_SHORT).show()
        }

        // 인식 결과 준비 완료 시 호출
        override fun onResults(results: Bundle?) {
            val matches: ArrayList<String>? =
                results!!.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val originText: String = contents.text.toString()

            var newText = ""
            matches?.forEach { newText += it }

            contents.setText("입력하신 내용  " + originText + newText + "  으로 서비스를 진행하겠습니다.     정보가 잘못되었다면 다시 버튼을 눌러주세요")

            // TTS_ 읽어주기
            val toSpeak = contents.text.toString()
            tts!!.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null)

            // 서버로 보낼 메시지 파싱하기
            contentsResult = "$originText" + "$newText"
            Log.d("Check", "${contentsResult} 체크")
            // 출발지, 버스 번호는 각각 departure, destination, busNumber의 변수명을 가진 전역 변수
            assignValuesFromVoiceInput(contentsResult)
            serverMessage = boardingRequest

            // TTS 서비스를 한 후 3초간 대기했다가 msgToServer 메시지 값을 서버로 보낸다.
            val handler = Handler()

            // 일정 시간(예: 3초) 후에 서버에 메시지 보내기
            handler.postDelayed({
                // API를 호출하여, serverAE를 초기화 해준다.
                callMainMethod(serverMessage, serverCNT)
            }, 3000)

        }

        override fun onPartialResults(partialResults: Bundle?) {

        }

        override fun onEvent(eventType: Int, params: Bundle?) {

        }

        override fun onSegmentResults(segmentResults: Bundle) {
            super.onSegmentResults(segmentResults)
        }

        override fun onEndOfSegmentedSession() {
            super.onEndOfSegmentedSession()
        }
    }

    // 음성 메시지 파싱
    fun assignValuesFromVoiceInput(input: String) {
        val (departure, destination, busNumber) = parseVoiceInput(input)
        // API 호출 시 사용되는 노선 ID 매핑
        if (busNumber != "404번") {
            println("현재는 404번에 대한 서비스만을 진행합니다, 추후 지원 노선을 확대할 예정입니다.")
            lineId = "5290205000"
        } else {
            lineId = "5290205000"
        }

        serverCNT = departure?.let { assignBusStopId(it) }.toString()

        val departureIndex = busStopList.indexOf(departure)
        val destinationIndex = busStopList.indexOf(destination)

        val isUp = departureIndex < destinationIndex


        // serverCNT와 상하행선 구분하여 bstopId 초기화
        when {
            serverCNT == "No.1" && isUp -> bstopId = busanStationUP
            serverCNT == "No.1" && !isUp -> bstopId = busanStationDown
            serverCNT == "No.2" && isUp -> bstopId = songdoBeachUp
            serverCNT == "No.2" && !isUp -> bstopId = songdoBeachDown
            serverCNT == "No.3" && isUp -> bstopId = amnamParkUp
            serverCNT == "No.3" && !isUp -> bstopId = amnamParkDown
            serverCNT == "No.4" && isUp -> bstopId = gamcheonCultureVillageUp
            serverCNT == "No.4" && !isUp -> bstopId = gamcheonCultureVillageDown
            serverCNT == "No.5" && isUp -> bstopId = dadepoBeachUp
            serverCNT == "No.5" && !isUp -> bstopId = dadepoBeachDown
            serverCNT == "No.6" && isUp -> bstopId = amisanObservatoryUp
            serverCNT == "No.6" && !isUp -> bstopId = amisanObservatoryDown
            serverCNT == "No.7" && isUp -> bstopId = bunechiaJangnimPortUp
            serverCNT == "No.7" && !isUp -> bstopId = bunechiaJangnimPortDown
            serverCNT == "No.8" && isUp -> bstopId = busanMuseumOfArtUp
            serverCNT == "No.8" && !isUp -> bstopId = busanMuseumOfArtDown
            serverCNT == "No.9" && isUp -> bstopId = nakdongRiverEstuaryEcocenterUp
            serverCNT == "No.9" && !isUp -> bstopId = nakdongRiverEstuaryEcocenterDown
            serverCNT == "No.10" && isUp -> bstopId = seokdangMuseumUp
            serverCNT == "No.10" && !isUp -> bstopId = seokdangMuseumDown
            serverCNT == "No.11" && isUp -> bstopId = internationalMarketUp
            serverCNT == "No.11" && !isUp -> bstopId = internationalMarketDown
            serverCNT == "No.12" && isUp -> bstopId = yongdusanParkUp
            serverCNT == "No.12" && !isUp -> bstopId = yongdusanParkDown

            else -> bstopId = "168470402"
        }

        callAPIService(bstopId, lineId)
    }

    // 서버 CNT 매핑
    fun assignBusStopId(departure: String): String {
        return when (departure) {
            "순천향 대학교" -> "No.1"
            "읍내리 까치골" -> "No.2"
            "순천향대학교 후문" -> "No.3"
            "경희 학성 아파트" -> "No.4"
            "행목리" -> "No.5"
            "신창역" -> "No.6"
            "행목1리 칠목" -> "No.7"
            "대주아파트 칠목" -> "No.8"
            "친오애아파트" -> "No.9"
            "부영아파트" -> "No.10"
            "득산농공단지" -> "No.11"
            "삼정 백조아파트" -> "No.12"
            else -> "Invalid_Input"
        }
    }


    fun parseVoiceInput(input: String): Triple<String?, String?, String?> {
        var input = input // 변수를 var로 변경
        val keywords = listOf("에서", "까지", "번 버스")
        val values = mutableListOf<String?>()

        for (keyword in keywords) {
            val startIndex = input.indexOf(keyword)
            if (startIndex != -1) {
                val endIndex = startIndex + keyword.length
                val value = input.substring(0, startIndex).trim()
                input = input.substring(endIndex).trim()
                values.add(value)
            } else {
                values.add(null)
            }
        }

        return Triple(values[0], values[1], values[2])
    }

    // 서버로의 POST 함수
    // 현재 한 노선에 대한 서비스를 제공하기 때문에, serverAE는 "Doyoung"으로 고정
    private fun makeCinMessage(serverAE: String, serverCNT: String, serverMessage: String) {
        val client = OkHttpClient()
        val mediaType = "application/vnd.onem2m-res+json; ty=4".toMediaType()
        val body =
            "{\n    \"m2m:cin\": {\n        \"con\": \"$serverMessage\"\n    }\n}".toRequestBody(
                mediaType
            )
        val request = Request.Builder()
            .url("http://114.71.220.109:7579/Mobius/$serverAE/$serverCNT")
            .post(body)
            .addHeader("Accept", "application/json")
            .addHeader("X-M2M-RI", "12345")
            .addHeader("X-M2M-Origin", "{{aei}}")
            .addHeader("Content-Type", "application/vnd.onem2m-res+json; ty=4")
            .build()


        // 요청에 대한 콜백
        val callback = object : Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.e("Client", e.toString())
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                if (response.isSuccessful) {
                    Log.e("Client", "${response.body?.string()}")
                }
            }
        }

        // 요청 보내기
        client.newCall(request).enqueue(callback)
    }

    // API 호출
    private fun callAPIService(bstopId: String, lineId: String) {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("http://apis.data.go.kr/6260000/BusanBIMS/busStopArrByBstopidLineid?bstopid=$bstopId& lineid=$lineId&serviceKey=wIijJFJm/2zm0jM/BXz59wtjJQ4oA1unMGiSFPnuoRkE3ADPgNBllYQgFZB4Qn05UO4sEA4404DRtwTh1hVJMA==")
            .addHeader("Content-Type", "application/json")
            .build()


        // 요청에 대한 콜백
        val callback = object : Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.e("Client", e.toString())
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                val responseBody = response.body?.string() // 응답 본문을 변수에 저장

                if (response.isSuccessful && responseBody != null) {
                    try {
                        val factory = XmlPullParserFactory.newInstance()
                        factory.isNamespaceAware = true
                        val parser = factory.newPullParser()
                        parser.setInput(StringReader(responseBody))

                        var carno1Value: String? = null
                        var eventType = parser.eventType

                        while (eventType != XmlPullParser.END_DOCUMENT) {
                            val nodeName = parser.name

                            when (eventType) {
                                XmlPullParser.TEXT -> {
                                    if ("carno1" == nodeName) {
                                        carno1Value = parser.text
                                        break
                                    }
                                }
                            }

                            eventType = parser.next()
                        }

                        // carno1Value 값을 확인하거나 처리합니다.
                        if (carno1Value != null) {
                            Log.d("XML_Parsing", "carno1Value: $carno1Value")
                            // 이 때 carno1Value는 사용자가 탑승을 원하는 노선 중 가장 먼저 도착하는 차량의 번호이다.
                            // 해당 번호를 서버의 AE(serverAE)와 매핑한다.
                            when (carno1Value) {
                                // 실제 운행 중인 시티투어 4번 노선의 차량 뒷자리
                                // 시연을 위해 디폴터 AE와 매핑한다.
                                "2932" -> {
                                    //serverAE = "2932"
                                    serverAE = "test"
                                }
                                "3721" -> {
                                    //serverAE = "3721"
                                    serverAE = "test"
                                }
                                "5713" -> {
                                    //serverAE = "5713"
                                    serverAE = "test"
                                }
                                "9839" -> {
                                    //serverAE = "9839"
                                    serverAE = "test"
                                }
                                else -> {
                                    serverAE = "test"
                                }
                            }
                        } else {
                            Log.d("XML_Parsing", "carno1Value not found in XML")
                            Log.d("XML_Parsing", "오류가 발생했습니다. 디폴트 서버 AE와 매핑합니다.")
                            serverAE = "test"
                        }

                    } catch (e: Exception) {
                        Log.e("XML_Parsing", "XML 파싱 오류: ${e.message}")
                    }
                }
            }

        }

        // 요청 보내기
        client.newCall(request).enqueue(callback)
    }


    // 하위 코드 모두 권한 관련 코드임
    // 권한 확인 (인터넷, 녹음)
    private fun checkPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            // 권한 요청
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.INTERNET
                ) == PackageManager.PERMISSION_DENIED
                || ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_DENIED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        android.Manifest.permission.RECORD_AUDIO,
                        android.Manifest.permission.INTERNET
                    ),
                    REQUEST_RECORD_AUDIO_CODE
                )
            }
        }
    }

    private fun showPermissionRationalDialog() {
        AlertDialog.Builder(this)
            .setMessage("녹음 권한을 켜주셔야지 앱을 정상적으로 사용할 수 있습니다.")
            .setPositiveButton("권한 허용하기") { _, _ ->
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.RECORD_AUDIO),
                    REQUEST_RECORD_AUDIO_CODE
                )
            }.setNegativeButton("취소") { dialogInterface, _ -> dialogInterface.cancel() }
            .show()
    }

    private fun showPermissionSettingDialog() {
        AlertDialog.Builder(this)
            .setMessage("녹음 권한을 켜주셔야지 앱을 정상적으로 사용할 수 있습니다. 앱 설정 화면으로 진입하셔서 권한을 켜주세요.")
            .setPositiveButton("권한 변경하러 가기") { _, _ ->
                navigateToAppSetting()
            }.setNegativeButton("취소") { dialogInterface, _ -> dialogInterface.cancel() }
            .show()
    }

    private fun navigateToAppSetting() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null) // packageName에 해당하는 디테일 세팅으로 가겠다.
        }
        startActivity(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        val audioRecordPermissionGranted = requestCode == REQUEST_RECORD_AUDIO_CODE
                && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED

        if (audioRecordPermissionGranted) {

        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    android.Manifest.permission.RECORD_AUDIO
                )
            ) {
                showPermissionRationalDialog()
            } else {
                showPermissionSettingDialog()
            }
        }
    }
}