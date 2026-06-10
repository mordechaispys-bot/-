package com.example.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.VaultItem
import com.example.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultAppMain(viewModel: VaultViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSlateBg)
    ) {
        when (state) {
            is VaultUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = TechCyanAccent)
                }
            }
            is VaultUiState.Setup -> {
                SetupScreen(
                    onSetupComplete = { pin, decoy, useCalc ->
                        viewModel.completeSetup(pin, decoy, useCalc)
                    }
                )
            }
            is VaultUiState.PasswordLock -> {
                val stealth = (state as VaultUiState.PasswordLock).stealthMode
                if (stealth) {
                    DisguisedCalculator(
                        expression = viewModel.calcExpression.collectAsStateWithLifecycle().value,
                        result = viewModel.calcResult.collectAsStateWithLifecycle().value,
                        onBtnClick = { viewModel.onCalcBtnClick(it) }
                    )
                } else {
                    ClassicLockScreen(
                        failedAttempts = viewModel.failedAttempts.collectAsStateWithLifecycle().value,
                        onPinSubmit = { pin ->
                            viewModel.authenticatePIN(pin)
                        }
                    )
                }
            }
            is VaultUiState.MainUnlocked -> {
                val items by viewModel.mainItems.collectAsStateWithLifecycle()
                VaultDashboardScreen(
                    titleStr = "הכספת הראשית שלך",
                    isDecoy = false,
                    items = items,
                    viewModel = viewModel,
                    onLockRequested = { viewModel.lockVault() }
                )
            }
            is VaultUiState.DecoyUnlocked -> {
                val items by viewModel.decoyItems.collectAsStateWithLifecycle()
                VaultDashboardScreen(
                    titleStr = "כספת גיבוי מדומה (Decoy)",
                    isDecoy = true,
                    items = items,
                    viewModel = viewModel,
                    onLockRequested = { viewModel.lockVault() }
                )
            }
        }
    }
}

// ==========================================
// 1. SETUP / ONBOARDING SCREEN
// ==========================================
@Composable
fun SetupScreen(onSetupComplete: (String, String?, Boolean) -> Unit) {
    var step by remember { mutableStateOf(1) }
    var primaryPin by remember { mutableStateOf("") }
    var primaryPinConfirm by remember { mutableStateOf("") }
    var decoyPin by remember { mutableStateOf("") }
    var useCalculatorSkin by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // TOP LOGO
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(TechCyanAccent.copy(alpha = 0.3f), Color.Transparent)))
                    .border(2.dp, TechCyanAccent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Security Vault",
                    tint = TechCyanAccent,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "הגדרת כספת סודית",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = "אנחנו בונים עבורך את מרחב ההצפנה הפרטי",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // STEP WORKSPACE
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            colors = CardDefaults.cardColors(containerColor = SecondarySlate),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, BorderSlate)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (step) {
                    1 -> {
                        Text(
                            text = "שלב 1: הזן קוד סודי ראשי (PIN)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "הקוד ישמש לפתיחת הכספת האמיתית המאובטחת שלך. הזן 4 עד 8 ספרות.",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        OutlinedTextField(
                            value = primaryPin,
                            onValueChange = { if (it.all { char -> char.isDigit() }) primaryPin = it },
                            label = { Text("קוד גישה ראשי", color = TextSecondary) },
                            singleLine = true,
                            maxLines = 1,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TechCyanAccent,
                                unfocusedBorderColor = BorderSlate,
                                focusedLabelColor = TechCyanAccent,
                                cursorColor = TechCyanAccent
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("setup_primary_pin_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = primaryPinConfirm,
                            onValueChange = { if (it.all { char -> char.isDigit() }) primaryPinConfirm = it },
                            label = { Text("אמת קוד גישה", color = TextSecondary) },
                            singleLine = true,
                            maxLines = 1,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TechCyanAccent,
                                unfocusedBorderColor = BorderSlate,
                                focusedLabelColor = TechCyanAccent,
                                cursorColor = TechCyanAccent
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("setup_primary_pin_confirm")
                        )
                    }

                    2 -> {
                        Text(
                            text = "שלב 2: קוד מזויף להסוואה (Decoy PIN)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "אופציונלי אך מומלץ ביותר! אם יכריחו אותך לפתוח את האפליקציה, הזן את הקוד הזה. הוא יפתח כספת מדומה וריקה לחלוטין ולעולם לא ידעו על קיום הכספת הראשית שלך!",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        OutlinedTextField(
                            value = decoyPin,
                            onValueChange = { if (it.all { char -> char.isDigit() }) decoyPin = it },
                            label = { Text("קוד מזויף אופציונלי", color = TextSecondary) },
                            singleLine = true,
                            maxLines = 1,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AlertWarningOrange,
                                unfocusedBorderColor = BorderSlate,
                                focusedLabelColor = AlertWarningOrange,
                                cursorColor = AlertWarningOrange
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("setup_decoy_pin_input")
                        )
                    }

                    3 -> {
                        Text(
                            text = "שלב 3: הפעלת רמת הסוואה מטורפת",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "בחר כיצד האפליקציה תתנהג ותיראה בטלפון שלך בקליק:",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (useCalculatorSkin) CardSurface else Color.Transparent)
                                .clickable { useCalculatorSkin = true }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("מצב מחשבון סודי (מומלץ!)", color = TextPrimary, fontWeight = FontWeight.Bold)
                                Text("האפליקציה תותקן ותעבוד בדיוק כמו מחשבון רגיל לחלוטין. הקשה של הקוד וכפתור '=' יפתחו את השער.", color = TextSecondary, fontSize = 11.sp, textAlign = TextAlign.Right)
                            }
                            RadioButton(
                                selected = useCalculatorSkin,
                                onClick = { useCalculatorSkin = true },
                                colors = RadioButtonDefaults.colors(selectedColor = TechCyanAccent)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (!useCalculatorSkin) CardSurface else Color.Transparent)
                                .clickable { useCalculatorSkin = false }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("מסך נעילה קלאסי", color = TextPrimary, fontWeight = FontWeight.Bold)
                                Text("מסך נעילה מוצפן בעיצוב קלאסי ומודרני של קוד סודי בכל הפעלה.", color = TextSecondary, fontSize = 11.sp, textAlign = TextAlign.Right)
                            }
                            RadioButton(
                                selected = !useCalculatorSkin,
                                onClick = { useCalculatorSkin = false },
                                colors = RadioButtonDefaults.colors(selectedColor = TechCyanAccent)
                            )
                        }
                    }
                }

                if (errorMsg != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = errorMsg ?: "",
                        color = Color.Red,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // CONTROL BUTTONS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (step > 1) {
                OutlinedButton(
                    onClick = {
                        errorMsg = null
                        step--
                    },
                    border = BorderStroke(1.dp, BorderSlate),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("חזור")
                }
            } else {
                Spacer(modifier = Modifier.width(80.dp))
            }

            Button(
                onClick = {
                    errorMsg = null
                    when (step) {
                        1 -> {
                            if (primaryPin.length < 4) {
                                errorMsg = "הקוד חייב להכיל לפחות 4 ספרות!"
                            } else if (primaryPin != primaryPinConfirm) {
                                errorMsg = "קוד המקור אינו תואם לאימות!"
                            } else {
                                step = 2
                            }
                        }
                        2 -> {
                            if (decoyPin == primaryPin && decoyPin.isNotEmpty()) {
                                errorMsg = "הקוד המזויף חייב להיות שונה לחלוטין מהקוד הראשי!"
                            } else {
                                step = 3
                            }
                        }
                        3 -> {
                            onSetupComplete(primaryPin, decoyPin.ifEmpty { null }, useCalculatorSkin)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = TechCyanAccent),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("setup_next_button")
            ) {
                Text(
                    text = if (step == 3) "הפעל כספת סודית" else "המשך",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ==========================================
// 2. DISGUISED CALCULATOR SKIN
// ==========================================
@Composable
fun DisguisedCalculator(
    expression: String,
    result: String,
    onBtnClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // TOP LOGO INDICATING SECURE CALCULATOR (Very subtle/stealthy)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "מחשבון",
                fontSize = 14.sp,
                color = TextSecondary.copy(alpha = 0.4f),
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = Icons.Default.Adjust,
                contentDescription = "Decor",
                tint = TextSecondary.copy(alpha = 0.1f),
                modifier = Modifier.size(16.dp)
            )
        }

        // CALCULATOR DISPLAY
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 24.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = expression.ifEmpty { "0" },
                fontSize = 42.sp,
                fontWeight = FontWeight.Light,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )

            if (result.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "= $result",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = ActiveSecurityGreen,
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // KEYPAD BUTTONS
        val buttons = listOf(
            listOf("C", "÷", "×", "DEL"),
            listOf("7", "8", "9", "-"),
            listOf("4", "5", "6", "+"),
            listOf("1", "2", "3", "="),
            listOf("0", ".")
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            buttons.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { char ->
                        val isSpecial = char == "="
                        val isOp = char in listOf("C", "÷", "×", "DEL", "-", "+")
                        val weight = if (char == "0") 2f else 1f

                        Box(
                            modifier = Modifier
                                .weight(weight)
                                .height(72.dp)
                                .clip(RoundedCornerShape(36.dp))
                                .background(
                                    when {
                                        isSpecial -> KeypadSpecialBtnBg
                                        isOp -> BorderSlate
                                        else -> KeypadBtnBg
                                    }
                                )
                                .clickable { onBtnClick(char) }
                                .testTag("btn_$char"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = char,
                                fontSize = if (isOp) 26.sp else 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSpecial) Color.Black else TextPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. CLASSIC LOCK SCREEN
// ==========================================
@Composable
fun ClassicLockScreen(failedAttempts: Int, onPinSubmit: (String) -> Boolean) {
    var enteredPin by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Locked",
                tint = TechCyanAccent,
                modifier = Modifier.size(54.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "כספת נעולה",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "הזן קוד PIN מוצפן כדי להיכנס",
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )

            if (failedAttempts > 0) {
                Text(
                    text = "נסיונות כושלים: $failedAttempts (סלפי ומיקום מתועדים)",
                    fontSize = 12.sp,
                    color = AlertWarningOrange,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        // INDICATOR DOTS
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(vertical = 20.dp)
        ) {
            repeat(4) { idx ->
                val filled = enteredPin.length > idx
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            if (filled) TechCyanAccent else BorderSlate
                        )
                        .border(1.dp, if (filled) Color.White else Color.Transparent, CircleShape)
                )
            }
        }

        // NUMPAD 3x4
        val numpad = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("C", "0", "OK")
        )

        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            numpad.forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {
                    row.forEach { digit ->
                        val isAction = digit == "C" || digit == "OK"
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1.2f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isAction) BorderSlate else KeypadBtnBg)
                                .clickable {
                                    when (digit) {
                                        "C" -> if (enteredPin.isNotEmpty()) enteredPin = ""
                                        "OK" -> {
                                            if (enteredPin.isNotEmpty()) {
                                                onPinSubmit(enteredPin)
                                                enteredPin = ""
                                            }
                                        }
                                        else -> {
                                            if (enteredPin.length < 8) {
                                                enteredPin += digit
                                            }
                                        }
                                    }
                                }
                                .testTag("pinpad_$digit"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = digit,
                                fontSize = if (isAction) 16.sp else 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (digit == "OK") TechCyanAccent else TextPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. MAIN VAULT DASHBOARD SCREEN
// ==========================================
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VaultDashboardScreen(
    titleStr: String,
    isDecoy: Boolean,
    items: List<VaultItem>,
    viewModel: VaultViewModel,
    onLockRequested: () -> Unit
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf("מדיה") } // "מדיה" / "צילום ישיר" / "יומן פריצות" / "הגדרות"
    var selectedItemForZoom by remember { mutableStateOf<VaultItem?>(null) }
    var showImportConfirmGuideline by remember { mutableStateOf(false) }

    // Multi-select picker integration
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importSelectedMedia(uris, isDecoy) { count, _ ->
                if (count > 0) {
                    showImportConfirmGuideline = true
                }
            }
        }
    }

    // Direct private camera capture integration
    val tempFile = remember { File(context.cacheDir, "camera_capture_vault.jpg") }
    val tempUri = remember {
        FileProvider.getUriForFile(
            context,
            "com.example.fileprovider",
            tempFile
        )
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            val bitmap = BitmapFactory.decodeFile(tempFile.absolutePath)
            if (bitmap != null) {
                viewModel.importCameraCapture(bitmap, isDecoy)
                tempFile.delete()
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DeepSlateBg,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isDecoy) AlertWarningOrange else ActiveSecurityGreen)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = titleStr,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onLockRequested,
                        modifier = Modifier.testTag("force_lock_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "התנתק ונעל",
                            tint = Color.Red
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SecondarySlate)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = SecondarySlate,
                tonalElevation = 8.dp,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                val tabs = listOf(
                    Triple("מדיה", Icons.Default.PhotoLibrary, "מדיה"),
                    Triple("צילום ישיר", Icons.Default.CameraAlt, "מצלמה"),
                    Triple("יומן פריצות", Icons.Default.History, "סייבר"),
                    Triple("הגדרות", Icons.Default.Settings, "הגדרות")
                )

                tabs.forEach { (tag, icon, label) ->
                    val isSelected = activeTab == tag
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { activeTab = tag },
                        icon = { Icon(imageVector = icon, contentDescription = label) },
                        label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = TechCyanAccent,
                            selectedTextColor = TechCyanAccent,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = BorderSlate
                        ),
                        modifier = Modifier.testTag("tab_$tag")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (activeTab) {
                "מדיה" -> {
                    if (items.isEmpty()) {
                        EmptyVaultState(onAddContent = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        })
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Category chips
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Card(
                                    modifier = Modifier.clickable { },
                                    colors = CardDefaults.cardColors(containerColor = BorderSlate),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        "הכל (${items.size})",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            // Media grid selection
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(items, key = { it.id }) { item ->
                                    val itemFile = File(context.filesDir, item.internalPath)

                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(SecondarySlate)
                                            .combinedClickable(
                                                onClick = { selectedItemForZoom = item },
                                                onLongClick = { viewModel.deleteMediaItem(item) }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (itemFile.exists()) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(LocalContext.current)
                                                    .data(itemFile)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = item.fileName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.BrokenImage,
                                                contentDescription = "שגיאה",
                                                tint = Color.Red
                                            )
                                        }

                                        // Badge tag for Videos
                                        if (item.fileType == "VIDEO") {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(6.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color.Black.copy(alpha = 0.7f))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = "וידאו",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Floating Import Button for fast additions
                    FloatingActionButton(
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                        containerColor = TechCyanAccent,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(20.dp)
                            .testTag("floating_import_button")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "ייבא קבצים", tint = Color.Black)
                    }
                }

                "צילום ישיר" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = "Direct Camera",
                            tint = TechCyanAccent,
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "צילום סודי ישירות לכספת",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "כל תמונה שתצלם מכאן תישמר בתיקייה המוצפנת של האפליקציה באופן מיידי.\nהתמונה לא תעבור דרך גלריית המכשיר ולא תעלה לענן לשום מקום ומוגנת לגמרי!",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                        )

                        Button(
                            onClick = {
                                try {
                                    cameraLauncher.launch(tempUri)
                                } catch (e: Exception) {
                                    Log.e("VaultScreens", "Error triggering camera: ${e.message}")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TechCyanAccent),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Camera, contentDescription = "Shoot", tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("פתח מצלמה מאובטחת", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                "יומן פריצות" -> {
                    val logsList by viewModel.logs.collectAsStateWithLifecycle()

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "נסיונות פריצה ואירועים",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = TextPrimary
                            )

                            if (logsList.isNotEmpty()) {
                                TextButton(onClick = { viewModel.clearAllLogs() }) {
                                    Text("נקה הכל", color = Color.Red)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (logsList.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "אין התראות פריצה אקטיביות. הכספת בטוחה!",
                                    color = TextSecondary,
                                    fontSize = 14.sp
                                )
                            }
                        } else {
                            // Unified Scrollable Column layout avoiding grid name clashes
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                logsList.forEach { log ->
                                    val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))

                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = SecondarySlate),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, AlertWarningOrange.copy(alpha = 0.5f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.ReportProblem,
                                                    contentDescription = "פרצה חסומה",
                                                    tint = AlertWarningOrange,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column {
                                                    Text(
                                                        "הוקלד קוד שגוי: ${log.enteredPin}",
                                                        color = TextPrimary,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp
                                                    )
                                                    Text(
                                                        dateStr,
                                                        color = TextSecondary,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }

                                            Badge(
                                                containerColor = AlertWarningOrange,
                                                modifier = Modifier.padding(horizontal = 4.dp)
                                            ) {
                                                Text("ניסיון כושל", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                "הגדרות" -> {
                    val configStealth by viewModel.isStealthActive.collectAsStateWithLifecycle()

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "הגדרות אבטחה וכספת",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = TextPrimary
                        )

                        // 1. SKIN DISGUISE TOGGLE
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = SecondarySlate),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, BorderSlate)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "הסוואה כמחשבון פעיל",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        "האפליקציה תיפתח כמחשבון. לחיצה על '=' עם הקוד שלך תחשוף את הכספת.",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Right
                                    )
                                }
                                Switch(
                                    checked = configStealth,
                                    onCheckedChange = { viewModel.toggleStealthMode() },
                                    colors = SwitchDefaults.colors(checkedThumbColor = TechCyanAccent)
                                )
                            }
                        }

                        // 2. EXPLANATION CRITICAL DELETE INFO
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = BorderSlate.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "⚠️ אזהרת פרטיות ומחיקת יישום",
                                    color = AlertWarningOrange,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "כל התמונות והסרטונים רשומים ומאוחסנים בתיקייה הפנימית המבודדת של האפליקציה בלבד.\nבמידה ותמחוק את האפליקציה מהמכשיר, כל החומרים בתוכה יימחקו לצמיתות ולא ניתנים לשחזור מאחר ואין שום עותק חיצוני, מטעמי הגנה מקסימלית!",
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Right
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // GALLERY IMPORT GUIDELINE ALERT DIALOG
    if (showImportConfirmGuideline) {
        AlertDialog(
            onDismissRequest = { showImportConfirmGuideline = false },
            title = {
                Text("הקבצים יובאו בהצלחה!", fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Text("התמונות/סרטונים הועתקו לתיקייה המאובטחת.\nכעת כדי להעלים אותם לגמרי מהגלריה הציבורית הכללית של המכשיר, נא מחק את קובצי המקור מהגלריה.", color = TextSecondary, fontSize = 13.sp)
            },
            confirmButton = {
                TextButton(
                    onClick = { showImportConfirmGuideline = false }
                ) {
                    Text("הבנתי, אמחק כעת", color = TechCyanAccent, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = SecondarySlate
        )
    }

    // ZOOM IN MEDIA VIEWER OVERLAY DIALOG (Custom Fullscreen Dialog)
    selectedItemForZoom?.let { item ->
        Dialog(
            onDismissRequest = { selectedItemForZoom = null }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                val file = File(context.filesDir, item.internalPath)

                if (file.exists()) {
                    if (item.fileType == "VIDEO") {
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    setVideoPath(file.absolutePath)
                                    setOnPreparedListener { mp ->
                                        mp.isLooping = true
                                        start()
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.Center)
                        )
                    } else {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(file)
                                .build(),
                            contentDescription = item.fileName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .align(Alignment.Center)
                        )
                    }
                } else {
                    Text(
                        "הקובץ אינו נגיש",
                        color = Color.Red,
                        fontSize = 16.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // HEADER CONTROLS
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { selectedItemForZoom = null },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "סגור", tint = Color.White)
                    }

                    Text(
                        text = item.fileName,
                        color = Color.White,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    )

                    IconButton(
                        onClick = {
                            viewModel.deleteMediaItem(item)
                            selectedItemForZoom = null
                        },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "מחק מהכספת", tint = Color.Red)
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyVaultState(onAddContent: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(BorderSlate.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PhotoLibrary,
                contentDescription = "Empty",
                tint = TextSecondary,
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "הכספת סגורה וריקה לחלוטין",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = "לחץ כעת כדי להצפין ולנעול את התמונות הראשונות שלך בסביבה מחוסנת מאינטרנט.",
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )

        Button(
            onClick = onAddContent,
            colors = ButtonDefaults.buttonColors(containerColor = TechCyanAccent),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Add", tint = Color.Black)
            Spacer(modifier = Modifier.width(8.dp))
            Text("ייבא תמונות/סרטונים מהגלריה", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}
