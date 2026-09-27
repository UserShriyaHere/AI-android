package mu.moris.agent

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import mu.moris.agent.agent.LocalAgentEngine
import mu.moris.agent.data.AppDatabase
import mu.moris.agent.security.AccessMode
import mu.moris.agent.security.AgentCapability
import mu.moris.agent.security.PermissionEngine
import mu.moris.agent.voice.LocalWhisperVoiceEngine

class MainActivity : ComponentActivity() {
    private lateinit var voiceEngine: LocalWhisperVoiceEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dao = AppDatabase.get(this).agentDao()
        val permissionEngine = PermissionEngine()
        val agent = LocalAgentEngine(dao, permissionEngine)
        voiceEngine = LocalWhisperVoiceEngine(this)

        setContent {
            MaterialTheme {
                MorisAgentApp(
                    dao = dao,
                    agent = agent,
                    permissionEngine = permissionEngine,
                    startVoice = { onStarted, onText, onError ->
                        if (!voiceEngine.isRecording()) {
                            voiceEngine.start()
                                .onSuccess { onStarted() }
                                .onFailure { onError(it.message ?: "Unable to start microphone.") }
                        } else {
                            lifecycleScope.launch {
                                voiceEngine.stopAndTranscribe()
                                    .onSuccess(onText)
                                    .onFailure { onError(it.message ?: "Unable to transcribe voice locally.") }
                            }
                        }
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        if (::voiceEngine.isInitialized) voiceEngine.release()
        super.onDestroy()
    }
}

private enum class HomeTab(val label: String) {
    AGENT("Agent"), NOTES("Notes"), TASKS("Tasks"), EXPENSES("Expenses"), MORE("More")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MorisAgentApp(
    dao: mu.moris.agent.data.AgentDao,
    agent: LocalAgentEngine,
    permissionEngine: PermissionEngine,
    startVoice: (() -> Unit, (String) -> Unit, (String) -> Unit) -> Unit
) {
    var tab by remember { mutableStateOf(HomeTab.AGENT) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Moris Agent") },
                actions = {
                    AssistChip(onClick = {}, label = { Text("🔒 LOCAL") })
                    Spacer(Modifier.width(8.dp))
                }
            )
        },
        bottomBar = {
            NavigationBar {
                HomeTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = {
                            Icon(
                                imageVector = when (item) {
                                    HomeTab.AGENT -> Icons.Default.SmartToy
                                    HomeTab.NOTES -> Icons.Default.Note
                                    HomeTab.TASKS -> Icons.Default.CheckCircle
                                    HomeTab.EXPENSES -> Icons.Default.AccountBalanceWallet
                                    HomeTab.MORE -> Icons.Default.MoreHoriz
                                },
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                HomeTab.AGENT -> AgentScreen(agent, startVoice)
                HomeTab.NOTES -> NotesScreen(dao)
                HomeTab.TASKS -> TasksScreen(dao)
                HomeTab.EXPENSES -> ExpensesScreen(dao)
                HomeTab.MORE -> PrivacyScreen(permissionEngine)
            }
        }
    }
}

@Composable
private fun AgentScreen(
    agent: LocalAgentEngine,
    startVoice: (() -> Unit, (String) -> Unit, (String) -> Unit) -> Unit
) {
    var input by remember { mutableStateOf("") }
    var reply by remember { mutableStateOf("Bonzur 👋 Mo Moris Agent. Your personal data stays on this phone.") }
    var listening by remember { mutableStateOf(false) }
    var transcribing by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    fun submit(text: String) {
        if (text.isBlank()) return
        scope.launch {
            val result = agent.handle(text)
            reply = result.text
            input = ""
        }
    }

    fun toggleVoice() {
        if (!listening) {
            startVoice(
                {
                    listening = true
                    transcribing = false
                    reply = "Listening locally… Tap the microphone again when you finish."
                },
                { spoken ->
                    listening = false
                    transcribing = false
                    input = spoken
                    reply = "Heard: “$spoken”"
                    submit(spoken)
                },
                { error ->
                    listening = false
                    transcribing = false
                    reply = error
                }
            )
        } else {
            transcribing = true
            reply = "Transcribing locally…"
            startVoice(
                {},
                { spoken ->
                    listening = false
                    transcribing = false
                    input = spoken
                    reply = "Heard: “$spoken”"
                    submit(spoken)
                },
                { error ->
                    listening = false
                    transcribing = false
                    reply = error
                }
            )
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) toggleVoice()
        else reply = "Microphone permission was not granted."
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card {
            Column(Modifier.padding(16.dp)) {
                Text("Private by design", style = MaterialTheme.typography.titleMedium)
                Text("Voice, notes, expenses, tasks and commands are processed locally. The app has no INTERNET permission.")
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(reply)
                if (listening || transcribing) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("English, Français or Kreol Morisien") },
            minLines = 2,
            trailingIcon = {
                IconButton(
                    enabled = !transcribing,
                    onClick = {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            toggleVoice()
                        } else {
                            micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                ) {
                    Icon(
                        if (listening) Icons.Default.StopCircle else Icons.Default.Mic,
                        contentDescription = if (listening) "Stop and transcribe" else "Speak"
                    )
                }
            }
        )

        Button(
            onClick = { submit(input) },
            modifier = Modifier.fillMaxWidth(),
            enabled = input.isNotBlank() && !transcribing
        ) {
            Text("Run locally")
        }

        Text("Voice: tap 🎤 → speak → tap stop.", style = MaterialTheme.typography.titleSmall)
        Text("Try:")
        Text("• Mo finn depans 450 roupi lor lunch\n• Create a note about AWS\n• Add task finish report\n• Ki to kapav fer?")
    }
}

@Composable
private fun NotesScreen(dao: mu.moris.agent.data.AgentDao) {
    val notes by dao.notes().collectAsStateWithLifecycle(initialValue = emptyList())
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Notes", style = MaterialTheme.typography.headlineSmall)
        Text("Stored only in the local Room database.")
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(notes) { note ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(note.title, style = MaterialTheme.typography.titleMedium)
                        Text(note.content)
                    }
                }
            }
        }
    }
}

@Composable
private fun TasksScreen(dao: mu.moris.agent.data.AgentDao) {
    val tasks by dao.tasks().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Tasks", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tasks) { task ->
                Card(
                    Modifier.fillMaxWidth().clickable {
                        scope.launch { dao.setTaskCompleted(task.id, !task.completed) }
                    }
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = task.completed,
                            onCheckedChange = {
                                scope.launch { dao.setTaskCompleted(task.id, it) }
                            }
                        )
                        Text(task.title)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpensesScreen(dao: mu.moris.agent.data.AgentDao) {
    val expenses by dao.expenses().collectAsStateWithLifecycle(initialValue = emptyList())
    val total by dao.totalExpenses().collectAsStateWithLifecycle(initialValue = 0.0)

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Expenses", style = MaterialTheme.typography.headlineSmall)
        Text("Total: Rs " + String.format("%.2f", total), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(expenses) { expense ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(expense.category, style = MaterialTheme.typography.titleMedium)
                            Text(expense.description, maxLines = 2)
                        }
                        Text("Rs " + String.format("%.2f", expense.amount))
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyScreen(permissionEngine: PermissionEngine) {
    var refresh by remember { mutableIntStateOf(0) }
    val order = listOf(AccessMode.ALLOW, AccessMode.ASK, AccessMode.CONFIRM, AccessMode.BLOCK)

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Privacy & Permissions", style = MaterialTheme.typography.headlineSmall)
        Text("Tap a capability to cycle: ALLOW → ASK → CONFIRM → BLOCK")
        Text("Network: BLOCKED at Android manifest level", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(AgentCapability.entries) { capability ->
                val mode = permissionEngine.mode(capability)
                Card(
                    Modifier.fillMaxWidth().clickable {
                        val next = order[(order.indexOf(mode) + 1) % order.size]
                        permissionEngine.set(capability, next)
                        refresh++
                    }
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(capability.name.replace("_", " "))
                        Text(mode.name)
                    }
                }
            }
        }

        @Suppress("UNUSED_EXPRESSION")
        refresh
    }
}
