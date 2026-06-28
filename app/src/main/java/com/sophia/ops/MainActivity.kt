package com.sophia.ops

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.sophia.ops.ai.CyberDefenseAnalyst
import androidx.compose.material3.Surface
import com.sophia.ops.navigation.AppNavigation
import com.sophia.ops.data.utils.OuiLookupEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.sophia.ops.ui.theme.SophiaOpsTheme
import com.sophia.ops.viewmodel.DashboardViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: DashboardViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        
        val nearbyWifiGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.NEARBY_WIFI_DEVICES] == true
        } else {
            true
        }

        // Trigger scan if any core connectivity permission is granted
        if (locationGranted || nearbyWifiGranted) {
            Log.i("MainActivity", "Core permissions granted, starting scan")
            viewModel.scan()
        } else {
            Log.e("MainActivity", "Permissions NOT granted")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Warm up the OUI database cache
        OuiLookupEngine.initialize(applicationContext)

        // Initialize local AI Analyst
        lifecycleScope.launch(Dispatchers.IO) {
            CyberDefenseAnalyst.initialize(applicationContext)
        }

        val permissionList = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionList.add(Manifest.permission.BLUETOOTH_SCAN)
            permissionList.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionList.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        permissionLauncher.launch(permissionList.toTypedArray())

        setContent {
            SophiaOpsTheme {
                Surface {
                    AppNavigation(viewModel = viewModel)
                }
            }
        }
    }
}
