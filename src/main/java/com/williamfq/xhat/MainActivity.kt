package com.williamfq.xhat

import android.app.AlertDialog
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.williamfq.domain.location.LocationTracker
import com.williamfq.xhat.ui.Navigation.AppNavigation
import com.williamfq.xhat.ui.Navigation.NavigationState
import com.williamfq.xhat.ui.Navigation.Screen
import com.williamfq.xhat.ui.theme.XhatTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.Date
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var auth: FirebaseAuth
    @Inject lateinit var locationTracker: LocationTracker
    private var permissionsGranted by mutableStateOf(false)
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var navigationState: NavigationState

    companion object {
        private const val PERMISSIONS_GRANTED_TIME = "permissions_granted_time"
        private const val PROFILE_SETUP_COMPLETE = "profile_setup_complete"
    }

    private val requiredPermissions = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.READ_MEDIA_IMAGES,
            android.Manifest.permission.READ_MEDIA_VIDEO,
            android.Manifest.permission.READ_MEDIA_AUDIO,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.POST_NOTIFICATIONS
        )

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> arrayOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )

        else -> arrayOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionsGranted = permissions.values.all { it }
        if (permissionsGranted) {
            onAllPermissionsGranted()
        } else {
            showPermissionRequiredDialog()
        }
    }
    private val photoPickerLauncher = registerForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) {
            Timber.d("Photo selected: $uri")
        } else {
            Timber.d("No photo selected")
        }
    }
    private fun launchPhotoPicker() {
        photoPickerLauncher.launch(PickVisualMediaRequest(PickVisualMedia.ImageAndVideo))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navigationState = NavigationState()
        sharedPreferences = getSharedPreferences("xhat_preferences", MODE_PRIVATE)
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork() // or .detectAll() for all detectable problems
                .penaltyLog()
                .build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build())
        }
        lifecycleScope.launch(Dispatchers.IO) {
            createWebViewCacheDirInBackground()
            withContext(Dispatchers.Main.immediate) {
                initializeApp()
            }
        }
    }
    private suspend fun createWebViewCacheDirInBackground() = withContext(Dispatchers.IO) {
        val cacheDir = File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/js")
        if (!cacheDir.exists()) {
            if (cacheDir.mkdirs()) {
                Timber.d("Directorio de caché WebView creado: ${cacheDir.absolutePath}")
            } else {
                Timber.e("No se pudo crear el directorio de caché WebView: ${cacheDir.absolutePath}")
            }
        }
    }
    private fun initializeApp() {
        val permissionsGrantedTime = sharedPreferences.getLong(PERMISSIONS_GRANTED_TIME, 0)
        if (permissionsGrantedTime == 0L || !areAllPermissionsGranted()) {
            checkAndRequestPermissions()
        } else {
            verifyPermissionsAndProceed()
        }
    }
    private fun areAllPermissionsGranted(): Boolean =
        requiredPermissions.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    private fun checkAndRequestPermissions() {
        val permissionsToRequest = requiredPermissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest)
        } else {
            permissionsGranted = true
            onAllPermissionsGranted()
        }
    }
    private fun onAllPermissionsGranted() {
        Timber.d("Todos los permisos han sido concedidos")
        sharedPreferences.edit().putLong(PERMISSIONS_GRANTED_TIME, Date().time).apply()
        lifecycleScope.launch(Dispatchers.IO) {
            checkAuthenticationStatus()
        }
    }
    private suspend fun checkAuthenticationStatus() {
        try {
            val currentUser = auth.currentUser
            val isProfileSetupComplete = sharedPreferences.getBoolean(PROFILE_SETUP_COMPLETE, false)
            val startDestination = when {
                currentUser == null -> Screen.PhoneNumber.route
                !isProfileSetupComplete -> Screen.ProfileSetup.route
                else -> Screen.Main.route
            }
            withContext(Dispatchers.Main) {
                setContent {
                    XhatTheme {
                        val navController = rememberNavController()
                        AppNavigation(
                            navController = navController,
                            startDestination = startDestination,
                            permissionsGranted = permissionsGranted,
                            onRequestPermissions = { checkAndRequestPermissions() },
                            navigationState = navigationState,
                            locationTracker = locationTracker
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error en checkAuthenticationStatus")
            withContext(Dispatchers.Main) {
                showPermissionRequiredDialog()
            }
        }
    }
    private fun showPermissionRequiredDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.permissions_required))
            .setMessage(getString(R.string.permissions_rationale))
            .setPositiveButton(getString(R.string.retry)) { _, _ -> checkAndRequestPermissions() }
            .setNegativeButton(getString(R.string.exit)) { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }
    override fun onResume() {
        super.onResume()
        if (areAllPermissionsGranted()) {
            verifyPermissionsAndProceed()
        }
    }
    private fun verifyPermissionsAndProceed() {
        if (areAllPermissionsGranted()) {
            permissionsGranted = true
            lifecycleScope.launch(Dispatchers.IO) {
                checkAuthenticationStatus()
            }
        } else {
            checkAndRequestPermissions()
        }
    }
}