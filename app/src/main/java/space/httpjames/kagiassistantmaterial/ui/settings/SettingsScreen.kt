package space.httpjames.kagiassistantmaterial.ui.settings

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.service.voice.VoiceInteractionService
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stars
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import space.httpjames.kagiassistantmaterial.AssistantClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    assistantClient: AssistantClient,
    navController: NavController,
) {
    val state = rememberSettingsScreenState(assistantClient)
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val packageInfo = context.packageManager.getPackageInfo(
        context.packageName,
        0
    )


    LaunchedEffect(Unit) {
        state.runInit()
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
                .fillMaxWidth()
                .scrollable(scrollState, Orientation.Vertical),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            if (!state.emailAddressLoading) {
                InitialsAvatar(char = state.emailAddress.firstOrNull() ?: 'K')
                Text(
                    state.emailAddress,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            } else {
                Row(
                    modifier = Modifier
                        .size(128.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator()
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
                    subtitle = "Open Kagi Assistant on device invocation",
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
                    icon = Icons.Default.Keyboard,
                    title = "Open Keyboard automatically",
                    subtitle = "Focus the message bar on app open",
                    pos = SettingsItemPosition.BOTTOM,
                    iconBackgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    rightSide = {
                        Switch(checked = state.openKeyboardAutomatically, onCheckedChange = {
                            state.toggleOpenKeyboardAutomatically()
                        })
                    }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                SettingsItem(
                    icon = Icons.Default.Refresh,
                    title = "Check for updates",
                    subtitle = "Version v${packageInfo.versionName}",
                    pos = SettingsItemPosition.SINGLE,
                    iconBackgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = {
                        uriHandler.openUri("https://github.com/httpjamesm/KagiAssistant/releases")
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
                    subtitle = "Sign out of your Kagi account on this device only",
                    pos = SettingsItemPosition.SINGLE,
                    iconBackgroundColor = MaterialTheme.colorScheme.errorContainer,
                    iconTint = MaterialTheme.colorScheme.onErrorContainer,
                    onClick = {
                        coroutineScope.launch {
                            val loggedOut = assistantClient.deleteSession()
                            if (loggedOut) {
                                state.clearAllPrefs()
                                navController.navigate("landing")
                            }
                        }
                    }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Source Code",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://github.com/httpjamesm/KagiAssistant")
                    },
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "© http.james",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://httpjames.space")
                    },
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Not officially endorsed by Kagi Inc.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}