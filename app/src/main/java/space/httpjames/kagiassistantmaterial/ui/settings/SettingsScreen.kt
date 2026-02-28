package space.httpjames.kagiassistantmaterial.ui.settings

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.service.voice.VoiceInteractionService
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.HomeMini
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import space.httpjames.kagiassistantmaterial.AssistantClient
import space.httpjames.kagiassistantmaterial.R
import space.httpjames.kagiassistantmaterial.Screens
import space.httpjames.kagiassistantmaterial.ui.viewmodel.AssistantViewModelFactory
import space.httpjames.kagiassistantmaterial.ui.viewmodel.SettingsViewModel
import space.httpjames.kagiassistantmaterial.utils.DataFetchingState
import space.httpjames.kagiassistantmaterial.utils.TtsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    assistantClient: AssistantClient,
    navController: NavController,
) {
    val context = LocalContext.current
    val prefs =
        context.getSharedPreferences("assistant_prefs", android.content.Context.MODE_PRIVATE)
    val cacheDir = context.cacheDir.absolutePath

    val viewModel: SettingsViewModel = viewModel(
        factory = AssistantViewModelFactory(assistantClient, prefs, cacheDir)
    )
    val uiState by viewModel.uiState.collectAsState()

    var ttsManager by remember { mutableStateOf<TtsManager?>(null) }

    DisposableEffect(Unit) {
        var manager: TtsManager? = null
        manager = TtsManager(
            context = context,
            prefs = prefs,
            onStart = {},
            onDone = {},
            onReady = {
                manager?.let { viewModel.setAvailableVoices(it.getAvailableVoices()) }
            }
        )
        ttsManager = manager

        onDispose {
            manager?.release()
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val uriHandler = LocalUriHandler.current
    val packageInfo = context.packageManager.getPackageInfo(
        context.packageName,
        0
    )

    BackHandler(enabled = true) {
        navController.popBackStack()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {},
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        navController.popBackStack()
                    }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                when (uiState.emailAddressCallState) {
                    DataFetchingState.FETCHING -> CircularProgressIndicator(
                        modifier = Modifier.size(48.dp)
                    )

                    DataFetchingState.OK -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            InitialsAvatar(char = uiState.emailAddress.firstOrNull() ?: 'K')
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = uiState.emailAddress,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }

                    else -> Unit
                }
            }


            Spacer(modifier = Modifier.height(32.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                SettingsItem(
                    icon = Icons.Default.Stars,
                    title = "Set default assistant",
                    subtitle = "Open app on assistant invocation",
                    pos = SettingsItemPosition.TOP,
                    iconBackgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = {
                        val intent = Intent(VoiceInteractionService.SERVICE_INTERFACE)
                        val pm = context.packageManager
                        val ri = pm.queryIntentServices(intent, PackageManager.GET_META_DATA)
                            .firstOrNull { it.serviceInfo.packageName == context.packageName }

                        if (ri != null) {
                            val serviceName = ComponentName(context, ri.serviceInfo.name)
                            val setDefaultIntent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
                                .putExtra(
                                    ":settings:fragment_args_key",
                                    serviceName.flattenToString()
                                )
                                .putExtra(":settings:show_fragment_args_key", true)
                            context.startActivity(setDefaultIntent)
                        }

                    }
                )
                SettingsItem(
                    icon = Icons.Default.HomeMini,
                    title = "Use mini overlay",
                    subtitle = "Show assist overlay instead of app",
                    pos = SettingsItemPosition.MIDDLE,
                    iconBackgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    rightSide = {
                        Switch(checked = uiState.useMiniOverlay, onCheckedChange = {
                            viewModel.toggleUseMiniOverlay()
                        })
                    }
                )
                SettingsItem(
                    icon = Icons.Default.Assistant,
                    title = "Assistant model",
                    subtitle = "Using ${uiState.selectedAssistantModelName ?: "..."}",
                    pos = SettingsItemPosition.MIDDLE,
                    iconBackgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = {
                        viewModel.showAssistantModelChooser()
                    }
                )
                SettingsItem(
                    icon = Icons.Default.PhoneInTalk,
                    title = "Speak replies",
                    subtitle = "Read aloud assistant replies",
                    pos = SettingsItemPosition.MIDDLE,
                    iconBackgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    rightSide = {
                        Switch(checked = uiState.autoSpeakReplies, onCheckedChange = {
                            viewModel.toggleAutoSpeakReplies()
                        })
                    }
                )
                SettingsItem(
                    icon = Icons.Default.RecordVoiceOver,
                    title = "Voice",
                    subtitle = uiState.selectedTtsVoiceDisplayName
                        ?: stringResource(R.string.default_tts_voice),
                    pos = SettingsItemPosition.BOTTOM,
                    iconBackgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = {
                        viewModel.showVoiceChooser()
                    }
                )

            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                SettingsItem(
                    icon = Icons.Default.Pets,
                    title = "Choose companion",
                    subtitle = "Use a Kagi companion across the app",
                    pos = SettingsItemPosition.SINGLE,
                    iconBackgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = {
                        navController.navigate(Screens.COMPANIONS.route)
                    }
                )
            }


            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                SettingsItem(
                    icon = Icons.Default.VerticalAlignBottom,
                    title = "Sticky scroll",
                    subtitle = "Auto-scroll during generation",
                    pos = SettingsItemPosition.TOP,
                    iconBackgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    rightSide = {
                        Switch(checked = uiState.stickyScrollEnabled, onCheckedChange = {
                            viewModel.toggleStickyScroll()
                        })
                    }
                )
                SettingsItem(
                    icon = Icons.Default.Keyboard,
                    title = "Auto keyboard",
                    subtitle = "Always focus the message bar",
                    pos = SettingsItemPosition.MIDDLE,
                    iconBackgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    rightSide = {
                        Switch(checked = uiState.openKeyboardAutomatically, onCheckedChange = {
                            viewModel.toggleOpenKeyboardAutomatically()
                        })
                    }
                )
                SettingsItem(
                    icon = Icons.Default.Language,
                    title = "Auto Internet",
                    subtitle = "Toggle Internet based on model",
                    pos = SettingsItemPosition.BOTTOM,
                    iconBackgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    rightSide = {
                        Switch(checked = uiState.autoToggleInternet, onCheckedChange = {
                            viewModel.toggleAutoToggleInternet()
                        })
                    }
                )

            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                SettingsItem(
                    icon = Icons.Default.Public,
                    title = "Cloud settings",
                    subtitle = "Manage instructions, assistants, and more",
                    pos = SettingsItemPosition.SINGLE,
                    iconBackgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = {
                        navController.navigate(Screens.ASSISTANT_SETTINGS_WEBVIEW.route)
                    }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                SettingsItem(
                    icon = Icons.Default.Refresh,
                    title = "Check for updates",
                    subtitle = "Version v${packageInfo.versionName}",
                    pos = SettingsItemPosition.TOP,
                    iconBackgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = {
                        uriHandler.openUri("https://github.com/httpjamesm/KagiAssistant/releases")
                    }
                )

                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "About",
                    subtitle = "Miscellaneous information",
                    pos = SettingsItemPosition.BOTTOM,
                    iconBackgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = {
                        navController.navigate(Screens.ABOUT.route)
                    }
                )
            }


            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                SettingsItem(
                    icon = Icons.Default.Logout,
                    title = "Sign out",
                    subtitle = "Sign out on this device only",
                    pos = SettingsItemPosition.SINGLE,
                    iconBackgroundColor = MaterialTheme.colorScheme.errorContainer,
                    iconTint = MaterialTheme.colorScheme.onErrorContainer,
                    onClick = {
                        coroutineScope.launch {
                            viewModel.logout()
                            navController.navigate(Screens.LANDING.route)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

        }
    }

    if (uiState.showAssistantModelChooserModal) {
        AssistantModelChooserModal(
            profiles = uiState.profiles,
            onDismiss = { viewModel.hideAssistantModelChooser() },
            onModelSelected = {
                viewModel.saveAssistantModel(it)
            },
            selectedKey = uiState.selectedAssistantModel
        )
    }

    if (uiState.showVoiceChooserModal) {
        VoiceChooserModal(
            voices = uiState.availableVoices,
            onDismiss = {
                ttsManager?.stop()
                if (uiState.selectedTtsVoice.isNotEmpty()) {
                    ttsManager?.setVoice(uiState.selectedTtsVoice)
                } else {
                    ttsManager?.resetToDefaultVoice()
                }
                viewModel.hideVoiceChooser()
            },
            onVoiceSelected = { voiceName ->
                ttsManager?.stop()
                viewModel.saveTtsVoice(voiceName)
                if (voiceName.isNotEmpty()) {
                    ttsManager?.setVoice(voiceName)
                } else {
                    ttsManager?.resetToDefaultVoice()
                }
                viewModel.hideVoiceChooser()
            },
            onPreviewVoice = { voiceName ->
                ttsManager?.previewVoice(voiceName)
            },
            selectedVoiceName = uiState.selectedTtsVoice
        )
    }
}