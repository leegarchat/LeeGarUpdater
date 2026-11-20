package org.leegar.updater

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.leegar.updater.controller.UpdaterController
import org.leegar.updater.controller.UpdaterService
import org.leegar.updater.data.ChangelogState
import org.leegar.updater.data.ThemeMode
import org.leegar.updater.data.Update
import org.leegar.updater.data.UpdateStatus
import org.leegar.updater.misc.Utils
import org.leegar.updater.ui.markdown.ImageFlightTarget
import org.leegar.updater.ui.markdown.MarkdownImageFlightOverlay
import androidx.compose.runtime.CompositionLocalProvider
import org.leegar.updater.ui.overlays.ReleaseDetailFlightOverlay
import org.leegar.updater.ui.overlays.ReleaseFlightOrigin
import org.leegar.updater.ui.overlays.ReleaseFlightTarget
import org.leegar.updater.ui.overlays.UpdaterFloatBar
import org.leegar.updater.ui.pep.BlurConfig
import org.leegar.updater.ui.pep.LocalBlurConfig
import org.leegar.updater.ui.pep.SliderMorphRoot
import org.leegar.updater.ui.pep.TopBarBlurOverlay
import org.leegar.updater.ui.pep.TopStatusVeil
import org.leegar.updater.ui.pep.UpdaterPager
import org.leegar.updater.ui.pep.UpdaterTabs
import org.leegar.updater.ui.pep.UpdaterTheme
import org.leegar.updater.ui.pep.recordLayer
import org.leegar.updater.ui.pep.rememberGraphicsLayerRecordingState
import org.leegar.updater.ui.pep.rememberSliderMorphHostState
import org.leegar.updater.ui.pep.rememberUpdaterPagerState
import org.leegar.updater.ui.screens.DownloadedTabContent
import org.leegar.updater.ui.screens.SettingsTabContent
import org.leegar.updater.ui.screens.UpdatesTabContent
import org.leegar.updater.updatescheck.UpdatesCheckState
import kotlin.math.roundToInt

class UpdatesActivity : ComponentActivity(), UpdateImporter.Callbacks {

    private val viewModel by viewModels<UpdatesViewModel>()

    private var updaterService: UpdaterService? = null
    private var activeController by mutableStateOf<UpdaterController?>(null)
    private var controllerTick by mutableIntStateOf(0)
    private var isImportingLocalZip by mutableStateOf(false)
    private var pendingExportUpdate: Update? = null

    private lateinit var updateImporter: UpdateImporter
    private lateinit var statusReceiver: BroadcastReceiver

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as UpdaterService.LocalBinder
            updaterService = binder.service
            activeController = binder.service.updaterController
            syncControllerUpdates(viewModel.uiState.value.updates)
            controllerTick++
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            updaterService = null
            activeController = null
            controllerTick++
        }
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        updateImporter = UpdateImporter(this, this)
        statusReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                controllerTick++
                if (UpdaterController.ACTION_DOWNLOAD_ERROR == intent?.action) {
                    val errMsg = intent.getStringExtra(UpdaterController.EXTRA_ERROR_MESSAGE)
                    if (!errMsg.isNullOrBlank()) {
                        viewModel.reportDownloadError(errMsg)
                    }
                    return
                }
                if (UpdaterController.ACTION_UPDATE_STATUS == intent?.action) {
                    val id = intent.getStringExtra(UpdaterController.EXTRA_DOWNLOAD_ID) ?: return
                    val u = activeController?.getUpdate(id) ?: return
                    if (!u.isLocalUpdate) {
                        when (u.status) {
                            UpdateStatus.PAUSED_ERROR ->
                                Toast.makeText(
                                    this@UpdatesActivity,
                                    u.errorMessage ?: getString(R.string.toast_download_failed),
                                    Toast.LENGTH_LONG,
                                ).show()
                            UpdateStatus.VERIFICATION_FAILED ->
                                Toast.makeText(
                                    this@UpdatesActivity,
                                    u.errorMessage ?: getString(R.string.verification_failed_notification),
                                    Toast.LENGTH_LONG,
                                ).show()
                            UpdateStatus.VERIFIED ->
                                Toast.makeText(
                                    this@UpdatesActivity,
                                    getString(R.string.toast_download_verified),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            else -> Unit
                        }
                    }
                }
            }
        }

        val initialTab = intent.getIntExtra(EXTRA_INITIAL_TAB, TAB_UPDATES)
            .coerceIn(TAB_UPDATES, TAB_SETTINGS)

        setContent {
            val prefsRepo = remember { (applicationContext as UpdaterApplication).userPreferencesRepository }
            val themeMode by prefsRepo.themeModeFlow.collectAsStateWithLifecycle(
                ThemeMode.fromStorageValue(getString(R.string.def_theme_mode)),
            )
            UpdaterTheme(themeMode = themeMode) {
                UpdaterRootScreen(
                    viewModel = viewModel,
                    controller = activeController,
                    controllerTick = controllerTick,
                    initialTab = initialTab,
                    isImportingLocalZip = isImportingLocalZip,
                    onBack = { finish() },
                    onImportZipUri = { uri -> updateImporter.importFromUri(uri) },
                    onStartExport = { update, destUri ->
                        startExportService(update, destUri)
                    },
                    onPrepareExport = { update -> pendingExportUpdate = update },
                    getPendingExport = { pendingExportUpdate },
                    onSyncController = { list -> syncControllerUpdates(list) },
                    onControllerTick = { controllerTick++ },
                    onStartInstallService = { action ->
                        startService(Intent(this, UpdaterService::class.java).setAction(action))
                    },
                    onReboot = {
                        getSystemService(PowerManager::class.java)?.reboot(null)
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val serviceIntent = Intent(this, UpdaterService::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        val filter = IntentFilter().apply {
            addAction(UpdaterController.ACTION_UPDATE_STATUS)
            addAction(UpdaterController.ACTION_DOWNLOAD_PROGRESS)
            addAction(UpdaterController.ACTION_INSTALL_PROGRESS)
            addAction(UpdaterController.ACTION_UPDATE_REMOVED)
            addAction(UpdaterController.ACTION_DOWNLOAD_ERROR)
        }
        registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        if (isImportingLocalZip) {
            isImportingLocalZip = false
            updateImporter.stopImport()
        }
        super.onPause()
    }

    override fun onStop() {
        runCatching { unregisterReceiver(statusReceiver) }
        if (updaterService != null) {
            runCatching { unbindService(serviceConnection) }
        }
        super.onStop()
    }

    override fun onImportStarted() {
        isImportingLocalZip = true
    }

    override fun onImportCompleted(update: Update?) {
        isImportingLocalZip = false
        controllerTick++
        if (update == null) {
            Toast.makeText(
                this,
                getString(R.string.toast_import_failed),
                Toast.LENGTH_LONG,
            ).show()
        } else {
            Toast.makeText(
                this,
                getString(R.string.toast_imported_format, update.name),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun syncControllerUpdates(updates: List<Update>) {
        val controller = activeController ?: return
        val onlineIds = ArrayList<String>()
        for (update in updates) {
            val online = !update.isLocalUpdate && !update.downloadUrl.isNullOrBlank()
            controller.addUpdate(update, online)
            if (online) onlineIds.add(update.downloadId)
        }
        controller.setUpdatesAvailableOnline(onlineIds, true)
        controllerTick++
    }

    private fun startExportService(update: Update, destUri: Uri) {
        val file = update.file ?: return
        val intent = Intent(this, ExportUpdateService::class.java).apply {
            action = ExportUpdateService.ACTION_START_EXPORTING
            putExtra(ExportUpdateService.EXTRA_SOURCE_FILE, file)
            putExtra(ExportUpdateService.EXTRA_DEST_URI, destUri)
        }
        startService(intent)
    }

    companion object {
        const val EXTRA_INITIAL_TAB = "extra_initial_tab"
        const val TAB_UPDATES = 0
        const val TAB_DOWNLOADED = 1
        const val TAB_SETTINGS = 2
    }
}

@Composable
private fun UpdaterRootScreen(
    viewModel: UpdatesViewModel,
    controller: UpdaterController?,
    controllerTick: Int,
    initialTab: Int,
    isImportingLocalZip: Boolean,
    onBack: () -> Unit,
    onImportZipUri: (Uri) -> Unit,
    onStartExport: (Update, Uri) -> Unit,
    onPrepareExport: (Update) -> Unit,
    getPendingExport: () -> Update?,
    onSyncController: (List<Update>) -> Unit,
    onControllerTick: () -> Unit,
    onStartInstallService: (String) -> Unit,
    onReboot: () -> Unit,
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as UpdaterApplication }
    val prefsRepo = app.userPreferencesRepository
    val batteryMonitor = app.batteryMonitor
    val networkMonitor = app.networkMonitor

    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    val uiState by viewModel.uiState.collectAsState()
    val networkState by networkMonitor.networkState.collectAsStateWithLifecycle(
        networkMonitor.currentNetworkState,
    )
    val batteryState by batteryMonitor.batteryState.collectAsStateWithLifecycle(
        batteryMonitor.currentBatteryState,
    )
    val showLogo by prefsRepo.showLogoFlow.collectAsStateWithLifecycle(context.resources.getBoolean(R.bool.def_show_logo))
    val showReleaseBanner by prefsRepo.showReleaseBannerFlow.collectAsStateWithLifecycle(context.resources.getBoolean(R.bool.def_show_release_banner))
    val wavyProgress by prefsRepo.wavyProgressFlow.collectAsStateWithLifecycle(context.resources.getBoolean(R.bool.def_wavy_progress))
    val wavyAmplitude by prefsRepo.wavyAmplitudeFlow.collectAsStateWithLifecycle(context.resources.getInteger(R.integer.def_wavy_amplitude_percent) / 100f)
    val blurRadius by prefsRepo.blurRadiusFlow.collectAsStateWithLifecycle(context.resources.getInteger(R.integer.def_blur_radius_dp).toFloat())
    val blurTint by prefsRepo.blurTintFlow.collectAsStateWithLifecycle(context.resources.getInteger(R.integer.def_blur_tint_percent) / 100f)
    val parallelSplit by prefsRepo.parallelSplitFlow.collectAsStateWithLifecycle(context.resources.getBoolean(R.bool.def_parallel_split_download))
    val unsafeMode by prefsRepo.unsafeInstallFlow.collectAsStateWithLifecycle(context.resources.getBoolean(R.bool.def_unsafe_install))
    val forceRecovery by prefsRepo.forceRecoveryInstallFlow.collectAsStateWithLifecycle(context.resources.getBoolean(R.bool.def_force_recovery_install))
    val streamUpdates by prefsRepo.streamUpdatesFlow.collectAsStateWithLifecycle(context.resources.getBoolean(R.bool.def_stream_updates))
    val meteredWarning by prefsRepo.meteredNetworkWarningFlow.collectAsStateWithLifecycle(context.resources.getBoolean(R.bool.def_metered_network_warning))

    LaunchedEffect(uiState.updates, controller) {
        if (controller != null) {
            onSyncController(uiState.updates)
        }
    }

    // Merge Room persistent list with live controller states (progress, speed, parts, local imports)
    val liveUpdates = remember(uiState.updates, controller, controllerTick) {
        if (controller == null) return@remember uiState.updates
        val merged = LinkedHashMap<String, Update>()
        uiState.updates.forEach { dbUpdate ->
            merged[dbUpdate.downloadId] = controller.getUpdate(dbUpdate.downloadId) ?: dbUpdate
        }
        controller.updates.forEach { ctrlUpdate ->
            if (!merged.containsKey(ctrlUpdate.downloadId)) {
                merged[ctrlUpdate.downloadId] = ctrlUpdate
            }
        }
        merged.values.toList()
    }

    val activeTaskUpdate = remember(liveUpdates) {
        liveUpdates.firstOrNull {
            it.status == UpdateStatus.INSTALLING ||
                it.status == UpdateStatus.INSTALLATION_SUSPENDED ||
                it.status == UpdateStatus.UPDATED_NEED_REBOOT
        } ?: liveUpdates.firstOrNull {
            it.status == UpdateStatus.DOWNLOADING ||
                it.status == UpdateStatus.STARTING ||
                it.status == UpdateStatus.VERIFYING ||
                it.status == UpdateStatus.PAUSED ||
                it.status == UpdateStatus.PAUSED_ERROR
        }
    }

    // SAF launchers
    val importZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) onImportZipUri(uri)
    }

    val exportZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        val pending = getPendingExport()
        if (uri != null && pending != null) {
            onStartExport(pending, uri)
        }
    }

    // 3-Pass Non-Cyclic Blur Stack (PEP architecture)
    val scrollBlur = rememberGraphicsLayerRecordingState()
    val cardModalBlur = rememberGraphicsLayerRecordingState()
    val level2Blur = rememberGraphicsLayerRecordingState()
    val sliderMorphHost = rememberSliderMorphHostState()

    val pagerState = rememberUpdaterPagerState(pageCount = 3, initialPage = initialTab)
    val updatesListState = rememberLazyListState()
    val downloadedListState = rememberLazyListState()
    val settingsListState = rememberLazyListState()

    // Level-1 Release Flight Modal & Level-2 Overlays (Slider / Image)
    var releaseFlight by remember { mutableStateOf<ReleaseFlightTarget?>(null) }
    var releaseFlightT by remember { mutableFloatStateOf(0f) }
    var level1CloseSignal by remember { mutableIntStateOf(0) }

    var imageFlight by remember { mutableStateOf<ImageFlightTarget?>(null) }
    var imageFlightT by remember { mutableFloatStateOf(0f) }
    var floatBarHeightPx by remember { mutableFloatStateOf(0f) }
    var floatBarModalOpen by remember { mutableStateOf(false) }

    val anyLevel1Open = releaseFlight != null
    val anySliderOpen = sliderMorphHost.request != null
    val anyLevel2Open = imageFlight != null || anySliderOpen
    val anyModalOpen = anyLevel1Open || anyLevel2Open || floatBarModalOpen

    // Collapsible Frosted Header geometry
    val statusBarTopDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topBarRowHeightDp = 56.dp
    val statusPlusTopBarPx = with(density) { (statusBarTopDp + topBarRowHeightDp).toPx() }
    var collapsibleMaxHeightPx by remember { mutableFloatStateOf(with(density) { 60.dp.toPx() }) }
    var collapseOffsetPx by remember { mutableFloatStateOf(0f) }
    var headerRevealJob by remember { mutableStateOf<Job?>(null) }

    fun revealHeaderSmoothly() {
        if (collapseOffsetPx > -0.5f || headerRevealJob?.isActive == true) return
        headerRevealJob = scope.launch {
            val anim = Animatable(collapseOffsetPx)
            anim.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = Spring.DampingRatioNoBouncy,
                ),
            ) {
                collapseOffsetPx = value
            }
        }
    }

    LaunchedEffect(pagerState.anchor) {
        revealHeaderSmoothly()
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (releaseFlight != null ||
                    imageFlight != null ||
                    sliderMorphHost.request != null ||
                    floatBarModalOpen
                ) return Offset.Zero
                if (available.y > 0.5f && collapsibleMaxHeightPx > 1f) {
                    headerRevealJob?.cancel()
                    collapseOffsetPx = (collapseOffsetPx + available.y)
                        .coerceIn(-collapsibleMaxHeightPx, 0f)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (releaseFlight != null ||
                    imageFlight != null ||
                    sliderMorphHost.request != null ||
                    floatBarModalOpen
                ) return Offset.Zero
                if (consumed.y < -0.5f && collapsibleMaxHeightPx > 1f) {
                    headerRevealJob?.cancel()
                    collapseOffsetPx = (collapseOffsetPx + consumed.y)
                        .coerceIn(-collapsibleMaxHeightPx, 0f)
                }
                return Offset.Zero
            }
        }
    }

    val visibleCollapsiblePx = (collapsibleMaxHeightPx + collapseOffsetPx).coerceAtLeast(0f)
    val effectiveHeaderHeightPx = statusPlusTopBarPx + visibleCollapsiblePx
    val effectiveHeaderHeightDp = with(density) { effectiveHeaderHeightPx.toDp() }
    val listTopPaddingDp = with(density) { (statusPlusTopBarPx + collapsibleMaxHeightPx).toDp() }
    val floatBarExtraBottom = if (activeTaskUpdate != null || isImportingLocalZip) 92.dp else 0.dp

    val surfaceBg = MaterialTheme.colorScheme.surfaceContainer

    CompositionLocalProvider(
        LocalBlurConfig provides BlurConfig(radiusDp = blurRadius, tintAlpha = blurTint),
    ) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaceBg)
            .nestedScroll(nestedScrollConnection),
    ) {
        SliderMorphRoot(
            host = sliderMorphHost,
            blurState = if (anyLevel1Open) cardModalBlur else scrollBlur,
            morphBlurState = level2Blur,
            morphBackgroundColor = surfaceBg,
        ) {
            // Pass 2 wrapper (records Pass 1 + Level-1 modal for FloatBar / Image / Slider blur)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (anyLevel1Open || anyLevel2Open || activeTaskUpdate != null || isImportingLocalZip) {
                            Modifier.recordLayer(state = cardModalBlur, backgroundColor = surfaceBg)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                // Pass 1: Pager content recorded into scrollBlur
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .recordLayer(state = scrollBlur, backgroundColor = surfaceBg),
                ) {
                    UpdaterPager(
                        state = pagerState,
                        userScrollEnabled = !anyModalOpen,
                        onHorizontalDragDetected = { revealHeaderSmoothly() },
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        val pagePadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = listTopPaddingDp + 12.dp,
                            bottom = navBottomDp + floatBarExtraBottom + 28.dp,
                        )

                        when (page) {
                            UpdatesActivity.TAB_UPDATES -> {
                                UpdatesTabContent(
                                    updates = liveUpdates,
                                    changelogs = uiState.changelogs,
                                    projectInfo = uiState.projectInfo,
                                    checkState = uiState.checkState,
                                    lastError = uiState.lastError,
                                    downloadError = uiState.downloadError,
                                    lastCheckedTimestamp = uiState.lastCheckedTimestamp,
                                    showLogo = showLogo,
                                    showReleaseBanner = showReleaseBanner,
                                    unsafeMode = unsafeMode,
                                    wavyProgress = wavyProgress,
                                    wavyAmplitude = wavyAmplitude,
                                    listState = updatesListState,
                                    pagePadding = pagePadding,
                                    activeFlightUpdateId = releaseFlight?.takeIf { it.origin == ReleaseFlightOrigin.UPDATES }?.updateId,
                                    onRefreshClick = { viewModel.fetchUpdates() },
                                    onDismissError = { viewModel.clearDownloadError() },
                                    onOpenRelease = { update, bounds, provider ->
                                        releaseFlight = ReleaseFlightTarget(
                                            updateId = update.downloadId,
                                            origin = ReleaseFlightOrigin.UPDATES,
                                            source = bounds,
                                            sourceBoundsProvider = provider,
                                        )
                                    },
                                    onProjectImageClick = { url, alt, bounds, provider ->
                                        imageFlight = ImageFlightTarget(
                                            url = url,
                                            alt = alt,
                                            source = bounds,
                                            sourceBoundsProvider = provider,
                                        )
                                    },
                                )
                            }

                            UpdatesActivity.TAB_DOWNLOADED -> {
                                DownloadedTabContent(
                                    updates = liveUpdates,
                                    changelogs = uiState.changelogs,
                                    showReleaseBanner = showReleaseBanner,
                                    isBusy = controller?.isBusy == true,
                                    unsafeMode = unsafeMode,
                                    forceRecovery = forceRecovery,
                                    wavyProgress = wavyProgress,
                                    wavyAmplitude = wavyAmplitude,
                                    listState = downloadedListState,
                                    pagePadding = pagePadding,
                                    activeFlightUpdateId = releaseFlight?.takeIf { it.origin == ReleaseFlightOrigin.SAVED }?.updateId,
                                    onImportLocalZip = {
                                        importZipLauncher.launch(arrayOf("application/zip"))
                                    },
                                    onInstall = { u ->
                                        Utils.triggerUpdate(context, u.downloadId)
                                        onControllerTick()
                                    },
                                    onExport = { u ->
                                        onPrepareExport(u)
                                        exportZipLauncher.launch(u.name.ifBlank { "update.zip" })
                                    },
                                    onDelete = { u ->
                                        controller?.deleteUpdate(u.downloadId)
                                        onControllerTick()
                                    },
                                    onPauseDownload = { u ->
                                        controller?.pauseDownload(u.downloadId)
                                        onControllerTick()
                                    },
                                    onResumeDownload = { u ->
                                        controller?.resumeDownload(u.downloadId)
                                        onControllerTick()
                                    },
                                    onCancelDownload = { u ->
                                        controller?.cancelDownload(u.downloadId)
                                        onControllerTick()
                                    },
                                    onReboot = onReboot,
                                    onOpenDetails = { u, bounds, provider ->
                                        releaseFlight = ReleaseFlightTarget(
                                            updateId = u.downloadId,
                                            origin = ReleaseFlightOrigin.SAVED,
                                            source = bounds,
                                            sourceBoundsProvider = provider,
                                        )
                                    },
                                )
                            }

                            else -> {
                                SettingsTabContent(
                                    repository = prefsRepo,
                                    batteryMonitor = batteryMonitor,
                                    listState = settingsListState,
                                    pagePadding = pagePadding,
                                    onOtaUrlChanged = { viewModel.fetchUpdates() },
                                    onClearCaches = { viewModel.clearChangelogCache() },
                                )
                            }
                        }
                    }
                }

                // Level-1 Flight Pseudo-Modal (reserves bottom space when FloatBar is visible!)
                val flightUpdate = releaseFlight?.let { f ->
                    liveUpdates.firstOrNull { it.downloadId == f.updateId }
                }
                ReleaseDetailFlightOverlay(
                    target = releaseFlight,
                    liveUpdate = flightUpdate,
                    changelogState = flightUpdate?.let { uiState.changelogs[it.downloadId] }
                        ?: ChangelogState.Idle,
                    serverError = uiState.downloadError,
                    onDismissServerError = { viewModel.clearDownloadError() },
                    isOnline = networkState.isOnline,
                    isMetered = networkState.isMetered,
                    meteredWarningEnabled = meteredWarning,
                    isBatteryOk = batteryState.isLevelOk,
                    isBusy = controller?.isBusy == true,
                    showReleaseBanner = showReleaseBanner,
                    unsafeMode = unsafeMode,
                    forceRecovery = forceRecovery,
                    streamUpdatesEnabled = streamUpdates,
                    wavyProgress = wavyProgress,
                    wavyAmplitude = wavyAmplitude,
                    scrollBlur = scrollBlur,
                    topInsetPx = effectiveHeaderHeightPx,
                    bottomReservedPx = if (activeTaskUpdate != null || isImportingLocalZip) floatBarHeightPx else 0f,
                    closeSignal = level1CloseSignal,
                    activeFlightImageUrl = imageFlight?.url,
                    onProgress = { releaseFlightT = it },
                    onRequestChangelog = { u, force -> viewModel.loadChangelog(u, force) },
                    onDownloadOnly = { u ->
                        viewModel.clearDownloadError()
                        controller?.startDownload(u.downloadId, false)
                        onControllerTick()
                    },
                    onDownloadAndInstall = { u ->
                        viewModel.clearDownloadError()
                        controller?.startDownload(u.downloadId, true)
                        onControllerTick()
                    },
                    onInstall = { u ->
                        Utils.triggerUpdate(context, u.downloadId)
                        onControllerTick()
                    },
                    onPauseDownload = { u ->
                        controller?.pauseDownload(u.downloadId)
                        onControllerTick()
                    },
                    onResumeDownload = { u ->
                        controller?.resumeDownload(u.downloadId)
                        onControllerTick()
                    },
                    onCancelDownload = { u ->
                        controller?.cancelDownload(u.downloadId)
                        onControllerTick()
                    },
                    onExport = { u ->
                        onPrepareExport(u)
                        exportZipLauncher.launch(u.name.ifBlank { "update.zip" })
                    },
                    onDelete = { u ->
                        controller?.deleteUpdate(u.downloadId)
                        onControllerTick()
                    },
                    onReboot = onReboot,
                    onImageClick = { url, alt, bounds, provider ->
                        imageFlight = ImageFlightTarget(
                            url = url,
                            alt = alt,
                            source = bounds,
                            sourceBoundsProvider = provider,
                        )
                    },
                    onClose = { releaseFlight = null },
                )
            }

            }

        // Frosted Bottom FloatBar — outside SliderMorphRoot so sliders morph underneath it with 2-pass blur
        UpdaterFloatBar(
            activeUpdate = activeTaskUpdate,
            isImportingLocalZip = isImportingLocalZip,
            isAutoInstallQueued = activeTaskUpdate?.let {
                controller?.isAutoInstallQueued(it.downloadId) == true
            } ?: false,
            isParallelSplitEnabled = parallelSplit,
            wavyProgress = wavyProgress,
            wavyAmplitude = wavyAmplitude,
            blurState = if (anyLevel1Open) cardModalBlur else scrollBlur,
            morphBlurState = level2Blur,
            morphActive = anySliderOpen,
            tintBoost = 0.25f * sliderMorphHost.morphProgress,
            onInterceptAnchorTap = {
                if (anySliderOpen) {
                    sliderMorphHost.requestAnimatedClose()
                    true
                } else {
                    false
                }
            },
            onModalOpenChanged = { floatBarModalOpen = it },
            onAnchorHeightPxChanged = { floatBarHeightPx = it },
            onPauseDownload = { u ->
                controller?.pauseDownload(u.downloadId)
                onControllerTick()
            },
            onResumeDownload = { u ->
                controller?.resumeDownload(u.downloadId)
                onControllerTick()
            },
            onCancelDownload = { u ->
                controller?.cancelDownload(u.downloadId)
                onControllerTick()
            },
            onPauseInstall = { onStartInstallService(UpdaterService.ACTION_INSTALL_SUSPEND) },
            onResumeInstall = { onStartInstallService(UpdaterService.ACTION_INSTALL_RESUME) },
            onCancelInstall = { onStartInstallService(UpdaterService.ACTION_INSTALL_STOP) },
            onReboot = onReboot,
        )

        // Frosted Blur Overlay over Pinned Header (zIndex = 2f)
        TopBarBlurOverlay(
            topBarHeight = effectiveHeaderHeightDp,
            blurState = scrollBlur,
            morphBlurState = if (anySliderOpen) level2Blur else cardModalBlur,
            morphActive = anyModalOpen,
            tintBoost = maxOf(
                0.18f * releaseFlightT,
                0.18f * imageFlightT,
                0.18f * sliderMorphHost.morphProgress,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
        )

        // Top Status Bar Veil (zIndex = 3f)
        TopStatusVeil(
            backgroundColor = surfaceBg,
            veilHeight = statusBarTopDp + 14.dp,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
        )

        // Crisp Pinned Header UI above the frost overlay (zIndex = 4f)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .zIndex(4f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(top = statusBarTopDp),
        ) {
            val isChecking = uiState.checkState is UpdatesCheckState.Checking
            val spinTransition = rememberInfiniteTransition(label = "UpdaterRefreshSpin")
            val spinAngle by spinTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 750, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "UpdaterSpinAngle",
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topBarRowHeightDp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
                Text(
                    text = uiState.projectInfo.name?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.display_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        revealHeaderSmoothly()
                        viewModel.fetchUpdates()
                    },
                    enabled = uiState.canCheckForUpdates,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.check_for_update),
                        tint = if (isChecking) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.rotate(if (isChecking) spinAngle else 0f),
                    )
                }
            }

            // Collapsible 3-tab selector
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val fullH = placeable.height.toFloat()
                        collapsibleMaxHeightPx = fullH
                        val visH = (fullH + collapseOffsetPx).roundToInt().coerceAtLeast(0)
                        val yOff = collapseOffsetPx.roundToInt().coerceIn(-placeable.height, 0)
                        layout(placeable.width, visH) {
                            placeable.placeRelative(0, yOff)
                        }
                    },
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(4.dp))
                    val downloadedCount = remember(liveUpdates) {
                        liveUpdates.count { it.isLocalUpdate || it.hasVerifiedPackage() }
                    }
                    UpdaterTabs(
                        titles = listOf(
                            stringResource(R.string.tab_updates),
                            if (downloadedCount > 0) stringResource(R.string.tab_saved_count, downloadedCount) else stringResource(R.string.tab_saved),
                            stringResource(R.string.tab_settings),
                        ),
                        position = pagerState.position,
                        onTabClick = { idx ->
                            scope.launch { pagerState.animateScrollToPage(idx) }
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }

        // Full-header tap blocker when any Level-1 or Slider modal is open
        if ((anyLevel1Open || anySliderOpen) && imageFlight == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(effectiveHeaderHeightDp)
                    .align(Alignment.TopCenter)
                    .zIndex(5f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (anySliderOpen) {
                                sliderMorphHost.requestAnimatedClose()
                            } else {
                                level1CloseSignal++
                            }
                        },
                    ),
            )
        }

        // Level-2 Flight Image Viewer (zIndex = 10f, blurs Level-1 cleanly via cardModalBlur)
        MarkdownImageFlightOverlay(
            target = imageFlight,
            backdropBlur = if (anyLevel1Open) cardModalBlur else scrollBlur,
            onProgress = { imageFlightT = it },
            onClose = { imageFlight = null },
        )
    }
    }
}
