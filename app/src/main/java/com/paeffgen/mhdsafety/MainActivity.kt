package com.paeffgen.mhdsafety

import android.Manifest
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.paeffgen.mhdsafety.data.ThemeMode
import com.paeffgen.mhdsafety.domain.ParsedDate
import com.paeffgen.mhdsafety.reminder.Notifications
import com.paeffgen.mhdsafety.ui.EditFoodScreen
import com.paeffgen.mhdsafety.ui.FoodListScreen
import com.paeffgen.mhdsafety.ui.FoodViewModel
import com.paeffgen.mhdsafety.ui.ScanMode
import com.paeffgen.mhdsafety.ui.ScanScreen
import com.paeffgen.mhdsafety.ui.SettingsScreen
import com.paeffgen.mhdsafety.ui.UpdateDialog
import com.paeffgen.mhdsafety.ui.theme.MHDSafetyTheme
import java.time.LocalDate

// Schlüssel, über die der Scan-Bildschirm sein Ergebnis an die Erfassen-Seite zurückgibt.
private const val KEY_BARCODE = "scan_barcode"
private const val KEY_DATE = "scan_date"
private const val KEY_USE_BY = "scan_use_by"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: FoodViewModel = viewModel()
            val themeMode by vm.themeMode.collectAsStateWithLifecycle()
            val dark = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            // Statusleisten-Symbole passend zum App-Design (nicht zum System) einfärben.
            LaunchedEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            MHDSafetyTheme(darkTheme = dark) {
                // Ab Android 13 muss man für Benachrichtigungen um Erlaubnis fragen.
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { }
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !Notifications.canNotify(this@MainActivity)
                    ) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                val updateDialogVisible by vm.updateDialogVisible.collectAsStateWithLifecycle()
                val updateState by vm.updateState.collectAsStateWithLifecycle()
                if (updateDialogVisible) {
                    UpdateDialog(
                        state = updateState,
                        currentVersion = vm.currentVersion,
                        onInstall = vm::installUpdate,
                        onDismiss = vm::dismissUpdateDialog,
                    )
                }

                val navController = rememberNavController()
                NavHost(navController, startDestination = "list") {
                    composable("list") {
                        FoodListScreen(
                            viewModel = vm,
                            onAdd = { navController.navigate("edit") },
                            onEdit = { id -> navController.navigate("edit?id=$id") },
                            onSettings = { navController.navigate("settings") },
                        )
                    }
                    composable(
                        "edit?id={id}",
                        arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
                    ) { entry ->
                        val id = entry.arguments?.getLong("id")?.takeIf { it >= 0 }
                        val handle = entry.savedStateHandle
                        val scannedBarcode by handle.getStateFlow<String?>(KEY_BARCODE, null)
                            .collectAsStateWithLifecycle()
                        val scannedDate by handle.getStateFlow<Long?>(KEY_DATE, null)
                            .collectAsStateWithLifecycle()
                        EditFoodScreen(
                            viewModel = vm,
                            itemId = id,
                            scannedBarcode = scannedBarcode,
                            scannedDate = scannedDate?.let {
                                ParsedDate(LocalDate.ofEpochDay(it), handle.get<Boolean>(KEY_USE_BY) == true)
                            },
                            onScanConsumed = {
                                handle[KEY_BARCODE] = null
                                handle[KEY_DATE] = null
                                handle.remove<Boolean>(KEY_USE_BY)
                            },
                            onScanBarcode = { navController.navigate("scan/${ScanMode.BARCODE}") },
                            onScanDate = { navController.navigate("scan/${ScanMode.DATE}") },
                            onDone = { navController.popBackStack() },
                        )
                    }
                    composable("scan/{mode}") { entry ->
                        val mode = ScanMode.valueOf(entry.arguments?.getString("mode") ?: ScanMode.BARCODE.name)
                        val result = navController.previousBackStackEntry?.savedStateHandle
                        ScanScreen(
                            mode = mode,
                            onBarcode = { code ->
                                result?.set(KEY_BARCODE, code)
                                navController.popBackStack()
                            },
                            onDate = { parsed ->
                                result?.set(KEY_USE_BY, parsed.useBy)
                                result?.set(KEY_DATE, parsed.date.toEpochDay())
                                navController.popBackStack()
                            },
                            onCancel = { navController.popBackStack() },
                        )
                    }
                    composable("settings") {
                        SettingsScreen(viewModel = vm, onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
