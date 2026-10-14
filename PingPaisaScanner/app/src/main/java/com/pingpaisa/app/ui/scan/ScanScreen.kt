package com.pingpaisa.app.ui.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pingpaisa.app.PingPaisaApp
import com.pingpaisa.app.camera.CameraController
import com.pingpaisa.app.camera.awaitCameraProvider
import com.pingpaisa.app.data.ItemState
import com.pingpaisa.app.data.PriceSource
import com.pingpaisa.app.data.Product
import com.pingpaisa.app.recognition.FocusSpec
import com.pingpaisa.app.ui.theme.PP
import com.pingpaisa.app.util.Money
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.pingpaisa.app.recognition.Box as FocusBox

private enum class CamState { CHECKING, NEED_PERMISSION, DENIED_FOREVER, STARTING, LIVE, ERROR, NO_CAMERA }

@Composable
fun ScanScreen(vm: ScanViewModel, onOpenProfile: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val app = context.applicationContext as PingPaisaApp
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }

    val products by vm.products.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val assessment by vm.assessment.collectAsStateWithLifecycle()
    val selectedId by vm.selectedId.collectAsStateWithLifecycle()
    val pinnedAt by vm.pinnedAt.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()

    var camState by remember { mutableStateOf(CamState.CHECKING) }
    var retryTick by remember { mutableIntStateOf(0) }
    var askedOnce by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var helpOpen by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var detailsOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var duplicate by remember { mutableStateOf<UiEvent.AskDuplicate?>(null) }
    var possibleDup by remember { mutableStateOf<UiEvent.AskPossibleDuplicate?>(null) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    val controller = remember { CameraController(context, app.ocr) { vm.live.value = it } }
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        askedOnce = true
        camState = when {
            granted -> CamState.STARTING
            activity != null && !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA) -> CamState.DENIED_FOREVER
            else -> CamState.NEED_PERMISSION
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) vm.addGalleryImages(uris)
    }
    fun openGallery() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    // Permission: ask once on first show, re-check whenever we come back (e.g. from system settings).
    LaunchedEffect(Unit) {
        if (hasPermission()) camState = CamState.STARTING
        else if (!askedOnce) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME && hasPermission() &&
                camState in listOf(CamState.NEED_PERMISSION, CamState.DENIED_FOREVER, CamState.CHECKING)
            ) camState = CamState.STARTING
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // Bind the camera whenever we are asked to start (or retry).
    LaunchedEffect(camState, retryTick) {
        if (camState != CamState.STARTING) return@LaunchedEffect
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            camState = CamState.NO_CAMERA; return@LaunchedEffect
        }
        try {
            val provider = context.awaitCameraProvider()
            controller.bind(provider, lifecycleOwner, previewView)
            camState = CamState.LIVE
        } catch (e: Exception) {
            camState = CamState.ERROR
        }
    }
    DisposableEffect(Unit) { onDispose { controller.unbind(); vm.live.value = null } }

    // Keep the screen on while scanning.
    val view = LocalView.current
    DisposableEffect(view) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }

    // Tick so a pinned price falls back to the live price after a few seconds.
    LaunchedEffect(pinnedAt) {
        while (true) { now = System.currentTimeMillis(); delay(500) }
    }

    // One-shot events from the view model.
    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is UiEvent.Message -> { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(e.text) }
                is UiEvent.AskDuplicate -> duplicate = e
                is UiEvent.AskPossibleDuplicate -> possibleDup = e
            }
        }
    }

    val ui = VerdictPresenter.present(products, assessment, profile)
    val selected = products.firstOrNull { it.id == selectedId }
    val pinned = selected != null && now - pinnedAt < 5000
    val anyProcessing = products.any { it.state == ItemState.PROCESSING }

    // Chip content
    val chipLabel: String
    val chipValue: String
    val chipDim: Boolean
    when {
        anyProcessing -> { chipLabel = "ANALYSING"; chipValue = "Reading…"; chipDim = true }
        pinned && selected != null -> {
            chipLabel = when {
                selected.price == null -> "PRICE NEEDED"
                selected.lowConfidencePrice -> "CHECK THIS PRICE"
                selected.priceSource == PriceSource.MANUAL -> "YOUR PRICE"
                selected.priceSource == PriceSource.PAGE -> "LISTED PRICE"
                else -> "PRICE DETECTED"
            }
            chipValue = selected.price?.let { Money.format(it) } ?: "Tap to add"
            chipDim = selected.price == null || selected.lowConfidencePrice
        }
        live != null -> { chipLabel = "PRICE DETECTED"; chipValue = Money.format(live!!.value); chipDim = false }
        else -> { chipLabel = "LOOKING FOR A PRICE"; chipValue = "₹ – – –"; chipDim = true }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(PP.Charcoal)) {
        val maxW = maxWidth
        val maxH = maxHeight
        val frameW = maxW * 0.67f
        val frameH = (maxH * 0.31f).coerceIn(190.dp, 270.dp)

        if (camState == CamState.LIVE || camState == CamState.STARTING) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        }
        val scrimAlpha = if (camState == CamState.LIVE) 0.62f else 1f
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    listOf(Color(0xFF3B4A43).copy(alpha = scrimAlpha), Color(0xFF1B2421).copy(alpha = scrimAlpha)),
                    center = Offset.Unspecified, radius = 1400f
                )
            )
        )

        Column(Modifier.fillMaxSize()) {
            // Top bar
            Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 4.dp).height(48.dp)) {
                Row(Modifier.align(Alignment.CenterStart), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        CircleButton(Icons.Rounded.Menu, "Menu") { menuOpen = true }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = PP.Surface) {
                            DropdownMenuItem(text = { Text("Financial profile", color = PP.TextHi) }, onClick = { menuOpen = false; onOpenProfile() })
                            DropdownMenuItem(
                                text = { Text("Clear all scanned items", color = PP.TextHi) },
                                enabled = products.isNotEmpty(),
                                onClick = { menuOpen = false; confirmClear = true }
                            )
                        }
                    }
                    CircleButton(Icons.Rounded.ArrowBack, "Back") { activity?.moveTaskToBack(true) }
                }
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CropFree, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Scan price", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
                Box(Modifier.align(Alignment.CenterEnd)) {
                    CircleButton(null, "Help", label = "?") { helpOpen = true }
                }
            }

            // Scan frame
            Box(Modifier.fillMaxWidth().padding(top = 14.dp), contentAlignment = Alignment.TopCenter) {
                Box(
                    Modifier.width(frameW).height(frameH).onGloballyPositioned { c ->
                        val b = c.boundsInRoot()
                        val rw = view.width; val rh = view.height
                        if (rw > 0 && rh > 0) controller.focus.set(FocusSpec(rw, rh, FocusBox(b.left, b.top, b.right, b.bottom)))
                    },
                    contentAlignment = Alignment.Center
                ) {
                    ScanFrameOverlay(Modifier.fillMaxSize())
                    PriceChip(
                        chipLabel, chipValue, chipDim, anyProcessing,
                        onClick = if (pinned && selected != null) { { editingId = selected.id } } else null
                    )
                }
            }
            Text(
                "Point the camera at a price tag", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )

            // Capture row
            Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(64.dp).padding(horizontal = 20.dp)) {
                Box(Modifier.align(Alignment.CenterStart)) {
                    CircleButton(Icons.Outlined.PhotoLibrary, "Choose photos from gallery", size = 44.dp) { openGallery() }
                }
                Box(
                    Modifier.align(Alignment.Center).size(60.dp).border(3.dp, Color.White, CircleShape).padding(5.dp)
                        .clip(CircleShape).background(Color.White)
                        .clickable(enabled = camState == CamState.LIVE && !capturing, role = Role.Button) {
                            capturing = true
                            scope.launch {
                                try {
                                    val f = controller.capture()
                                    vm.addCapturedImage(f, controller.focus.get())
                                } catch (e: Exception) {
                                    snack.showSnackbar("Couldn't take the photo. Try again.")
                                } finally { capturing = false }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (capturing) CircularProgressIndicator(Modifier.size(24.dp), color = PP.AccentDark, strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.CameraAlt, "Capture", tint = if (camState == CamState.LIVE) PP.Ink else PP.NavIdle)
                }
            }

            // Tray or camera status
            Box(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
                if (products.isNotEmpty()) {
                    ProductTray(
                        products, assessment, selectedId,
                        onSelect = { vm.select(it.id); editingId = it.id },
                        onRemove = { vm.removeProduct(it.id) },
                        onAdd = { openGallery() },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                } else {
                    CameraStatus(camState, onAllow = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        onSettings = {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                        },
                        onRetry = { camState = CamState.STARTING; retryTick++ })
                }
            }
            if (products.isNotEmpty() && camState != CamState.LIVE && camState != CamState.STARTING) {
                CameraStatus(camState, onAllow = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onSettings = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                    },
                    onRetry = { camState = CamState.STARTING; retryTick++ }, compact = true)
            }

            VerdictCard(
                ui, modifier = Modifier.padding(horizontal = 13.dp).padding(bottom = 10.dp),
                onClick = when (ui.action) {
                    VerdictAction.PROFILE -> onOpenProfile
                    VerdictAction.DETAILS -> { { detailsOpen = true } }
                    VerdictAction.NONE -> null
                }
            )
            AppBottomNav(
                onOther = { name -> scope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar("$name lives in the full PingPaisa app. This build only has the scanner.") } },
                onYou = onOpenProfile
            )
        }

        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = 150.dp))
    }

    // Sheets and dialogs
    val editing = products.firstOrNull { it.id == editingId }
    if (editing != null) {
        EditProductSheet(
            editing, app.categories, assessment?.line(editing.id),
            onSave = { n, p, c, s, pr -> vm.updateProduct(editing.id, n, p, c, s, pr); editingId = null },
            onRemove = { vm.removeProduct(editing.id); editingId = null },
            onDismiss = { editingId = null }
        )
    } else if (editingId != null) editingId = null

    val a = assessment
    if (detailsOpen && a != null) VerdictDetailsSheet(a, products, onDismiss = { detailsOpen = false })
    else if (detailsOpen) detailsOpen = false
    if (helpOpen) HelpDialog { helpOpen = false }

    duplicate?.let { d ->
        AlertDialog(
            onDismissRequest = {}, containerColor = PP.Surface, titleContentColor = PP.TextHi, textContentColor = PP.TextLo,
            title = { Text("Already in your list") },
            text = { Text("\"${d.existingName}\" looks like the same photo or link. Add it again as a separate item?") },
            confirmButton = { TextButton(onClick = { vm.resolveDuplicate(d.token, true); duplicate = null }) { Text("Add again", color = PP.Accent) } },
            dismissButton = { TextButton(onClick = { vm.resolveDuplicate(d.token, false); duplicate = null }) { Text("Skip it", color = PP.TextLo) } }
        )
    }
    possibleDup?.let { d ->
        AlertDialog(
            onDismissRequest = {}, containerColor = PP.Surface, titleContentColor = PP.TextHi, textContentColor = PP.TextLo,
            title = { Text("Possible duplicate") },
            text = { Text("This looks the same as \"${d.otherName}\" (same name and price). Keep both?") },
            confirmButton = { TextButton(onClick = { vm.resolvePossibleDuplicate(d.productId, true); possibleDup = null }) { Text("Keep both", color = PP.Accent) } },
            dismissButton = { TextButton(onClick = { vm.resolvePossibleDuplicate(d.productId, false); possibleDup = null }) { Text("Remove new one", color = PP.TextLo) } }
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false }, containerColor = PP.Surface, titleContentColor = PP.TextHi, textContentColor = PP.TextLo,
            title = { Text("Clear all items?") },
            text = { Text("This removes every scanned product and its saved photo from this device.") },
            confirmButton = { TextButton(onClick = { vm.clearAll(); confirmClear = false }) { Text("Clear", color = PP.Error) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel", color = PP.TextLo) } }
        )
    }
}

@Composable
private fun CameraStatus(
    state: CamState, onAllow: () -> Unit, onSettings: () -> Unit, onRetry: () -> Unit, compact: Boolean = false
) {
    val msg: String
    val action: Pair<String, () -> Unit>?
    when (state) {
        CamState.NEED_PERMISSION -> { msg = "Camera access is needed to read price tags. You can still add photos from your gallery."; action = "Allow camera" to onAllow }
        CamState.DENIED_FOREVER -> { msg = "Camera access is turned off. Enable it in Settings, or add photos from your gallery."; action = "Open settings" to onSettings }
        CamState.ERROR -> { msg = "The camera couldn't start. It may be in use by another app."; action = "Try again" to onRetry }
        CamState.NO_CAMERA -> { msg = "This device has no usable camera. Add photos from your gallery instead."; action = null }
        else -> return
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = if (compact) 4.dp else 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(msg, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, textAlign = TextAlign.Center)
        if (action != null) {
            Button(
                onClick = action.second, modifier = Modifier.padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PP.Accent, contentColor = PP.Ink)
            ) { Text(action.first, fontWeight = FontWeight.SemiBold) }
        }
    }
}
