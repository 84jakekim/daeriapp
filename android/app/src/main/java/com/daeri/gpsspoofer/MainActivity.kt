package com.daeri.gpsspoofer

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.daeri.gpsspoofer.databinding.ActivityMainBinding
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var kakaoMap: KakaoMap? = null
    private var labelId: String? = null
    private var selected: LatLng? = null
    private lateinit var searchClient: KakaoSearchClient

    private val locationPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> /* user choice */ }

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        searchClient = KakaoSearchClient(BuildConfig.KAKAO_REST_API_KEY)

        requestPermissionsIfNeeded()
        setupMap()
        setupUi()
        refreshStatus()
    }

    private fun requestPermissionsIfNeeded() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) locationPermLauncher.launch(missing.toTypedArray())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun setupMap() {
        binding.mapView.start(
            object : MapLifeCycleCallback() {
                override fun onMapDestroy() {}
                override fun onMapError(error: Exception?) {
                    Log.e("MainActivity", "KakaoMap error", error)
                    Toast.makeText(this@MainActivity,
                        "지도 초기화 실패: 네이티브 앱 키 / 키 해시 확인",
                        Toast.LENGTH_LONG).show()
                }
            },
            object : KakaoMapReadyCallback() {
                override fun getPosition(): LatLng = LatLng.from(37.5665, 126.9780)
                override fun getZoomLevel(): Int = 15

                override fun onMapReady(map: KakaoMap) {
                    kakaoMap = map
                    map.setOnMapClickListener { _, latLng, _, _ ->
                        setSelected(latLng)
                    }
                    setSelected(LatLng.from(37.5665, 126.9780))
                }
            }
        )
    }

    private fun setupUi() {
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearch(); true
            } else false
        }
        binding.searchBtn.setOnClickListener { doSearch() }

        binding.startBtn.setOnClickListener {
            val pos = selected
            if (pos == null) { toast("위치를 먼저 선택하세요"); return@setOnClickListener }
            if (!isMockLocationEnabled()) {
                showMockLocationGuide()
                return@setOnClickListener
            }
            val i = Intent(this, MockLocationService::class.java).apply {
                action = MockLocationService.ACTION_START
                putExtra(MockLocationService.EXTRA_LAT, pos.latitude)
                putExtra(MockLocationService.EXTRA_LNG, pos.longitude)
                putExtra(MockLocationService.EXTRA_ACCURACY, 3.0f)
            }
            ContextCompat.startForegroundService(this, i)
            refreshStatus(delayMs = 400)
        }

        binding.stopBtn.setOnClickListener {
            val i = Intent(this, MockLocationService::class.java).apply {
                action = MockLocationService.ACTION_STOP
            }
            startService(i)
            refreshStatus(delayMs = 400)
        }

        binding.openDevSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        }
    }

    private fun setSelected(latLng: LatLng) {
        selected = latLng
        binding.coordText.text = "위도 %.7f\n경도 %.7f".format(latLng.latitude, latLng.longitude)

        val map = kakaoMap ?: return
        val layer = map.labelManager?.layer ?: return
        val style = LabelStyles.from(LabelStyle.from(android.R.drawable.ic_menu_mylocation))

        labelId?.let { runCatching { layer.remove(layer.getLabel(it)) } }
        val label = layer.addLabel(LabelOptions.from(latLng).setStyles(style))
        labelId = label.labelId
        map.moveCamera(CameraUpdateFactory.newCenterPosition(latLng))

        if (MockLocationService.running) {
            val i = Intent(this, MockLocationService::class.java).apply {
                action = MockLocationService.ACTION_UPDATE
                putExtra(MockLocationService.EXTRA_LAT, latLng.latitude)
                putExtra(MockLocationService.EXTRA_LNG, latLng.longitude)
            }
            startService(i)
        }
    }

    private fun doSearch() {
        val q = binding.searchInput.text.toString().trim()
        if (q.isEmpty()) return
        hideKeyboard()

        if (BuildConfig.KAKAO_REST_API_KEY == "PUT_KAKAO_REST_API_KEY_HERE") {
            toast("local.properties 에 KAKAO_REST_API_KEY 를 설정하세요")
            return
        }

        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val results = runCatching { searchClient.searchKeyword(q) }.getOrDefault(emptyList())
            val fallback = if (results.isEmpty())
                runCatching { searchClient.searchAddress(q) }.getOrNull()?.let { listOf(it) }
                    ?: emptyList()
            else results

            binding.progress.visibility = View.GONE
            if (fallback.isEmpty()) { toast("검색 결과가 없습니다"); return@launch }
            if (fallback.size == 1) {
                val r = fallback[0]
                setSelected(LatLng.from(r.lat, r.lng))
            } else {
                showResultDialog(fallback)
            }
        }
    }

    private fun showResultDialog(results: List<PlaceResult>) {
        val items = results.map { "${it.name}\n${it.address}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("검색 결과")
            .setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_2, items)) { _, which ->
                val r = results[which]
                setSelected(LatLng.from(r.lat, r.lng))
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun isMockLocationEnabled(): Boolean {
        return try {
            val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_MOCK_LOCATION,
                    Process.myUid(),
                    packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_MOCK_LOCATION,
                    Process.myUid(),
                    packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) { false }
    }

    private fun showMockLocationGuide() {
        AlertDialog.Builder(this)
            .setTitle("모의 위치 앱 설정 필요")
            .setMessage(
                "이 앱이 시스템 GPS를 변경하려면 다음 설정이 필요합니다:\n\n" +
                "1. 설정 → 휴대전화 정보 → 빌드번호 7회 탭 (개발자 모드 활성화)\n" +
                "2. 설정 → 시스템 → 개발자 옵션\n" +
                "3. \"모의 위치 앱 선택\" → 이 앱(GPS 시뮬레이터) 선택\n\n" +
                "설정 후 다시 시작 버튼을 눌러주세요."
            )
            .setPositiveButton("개발자 설정 열기") { _, _ ->
                runCatching {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                }.onFailure {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun refreshStatus(delayMs: Long = 0) {
        binding.root.postDelayed({
            val running = MockLocationService.running
            binding.statusText.text = if (running) {
                "● 모의 위치 송출 중 — %.6f, %.6f".format(
                    MockLocationService.currentLat, MockLocationService.currentLng
                )
            } else {
                "○ 정지됨"
            }
            binding.startBtn.isEnabled = !running
            binding.stopBtn.isEnabled = running
        }, delayMs)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.searchInput.windowToken, 0)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onResume() {
        super.onResume()
        binding.mapView.resume()
        refreshStatus()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.mapView.finish()
    }
}
