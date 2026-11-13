package es.calendario.personal

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val TASKS_API_BASE = "https://tasks.googleapis.com/tasks/v1"
private const val DEFAULT_TASK_LIST = "@default"

data class GoogleTask(
    val id: String,
    val title: String,
    val notes: String,
    val dueDate: LocalDate?,
    val completed: Boolean
)

class GoogleTasksRepository {
    suspend fun loadTasks(accessToken: String): List<GoogleTask> = withContext(Dispatchers.IO) {
        val listId = findDefaultListId(accessToken)
        val json = request(
            "GET",
            "/lists/${path(listId)}/tasks?showCompleted=true&showHidden=true&maxResults=100",
            accessToken
        )
        val items = json.optJSONArray("items") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                if (item.optString("status") == "deleted") continue
                add(item.toGoogleTask())
            }
        }.sortedWith(compareBy<GoogleTask>({ it.completed }, { it.dueDate ?: LocalDate.MAX }, { it.title.lowercase() }))
    }

    suspend fun createTask(accessToken: String, title: String, dueDate: LocalDate?) = withContext(Dispatchers.IO) {
        val listId = findDefaultListId(accessToken)
        request("POST", "/lists/${path(listId)}/tasks", accessToken, taskBody(title, dueDate))
    }

    suspend fun updateTask(accessToken: String, task: GoogleTask, title: String, dueDate: LocalDate?) = withContext(Dispatchers.IO) {
        val listId = findDefaultListId(accessToken)
        val body = taskBody(title, dueDate).put("status", if (task.completed) "completed" else "needsAction")
        if (task.completed) body.put("completed", java.time.Instant.now().toString())
        request("PATCH", "/lists/${path(listId)}/tasks/${path(task.id)}", accessToken, body)
    }

    suspend fun setCompleted(accessToken: String, task: GoogleTask, completed: Boolean) = withContext(Dispatchers.IO) {
        val listId = findDefaultListId(accessToken)
        val body = JSONObject().put("status", if (completed) "completed" else "needsAction")
        if (completed) body.put("completed", java.time.Instant.now().toString()) else body.put("completed", JSONObject.NULL)
        request("PATCH", "/lists/${path(listId)}/tasks/${path(task.id)}", accessToken, body)
    }

    suspend fun deleteTask(accessToken: String, taskId: String) = withContext(Dispatchers.IO) {
        val listId = findDefaultListId(accessToken)
        request("DELETE", "/lists/${path(listId)}/tasks/${path(taskId)}", accessToken)
    }

    private fun findDefaultListId(accessToken: String): String {
        val json = request("GET", "/users/@me/lists?maxResults=100", accessToken)
        val items = json.optJSONArray("items")
        return if (items != null && items.length() > 0) {
            items.optJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() } ?: DEFAULT_TASK_LIST
        } else DEFAULT_TASK_LIST
    }

    private fun taskBody(title: String, dueDate: LocalDate?): JSONObject {
        val body = JSONObject().put("title", title.trim())
        if (dueDate != null) body.put("due", dueDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toString())
        else body.put("due", JSONObject.NULL)
        return body
    }

    private fun JSONObject.toGoogleTask(): GoogleTask {
        val due = optString("due").takeIf { it.isNotBlank() && it != "null" }?.take(10)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return GoogleTask(
            id = optString("id"),
            title = optString("title", "Tarea sin título"),
            notes = optString("notes"),
            dueDate = due,
            completed = optString("status") == "completed"
        )
    }

    private fun path(value: String): String = Uri.encode(value)

    private fun request(method: String, path: String, token: String, body: JSONObject? = null): JSONObject {
        val connection = (URL(TASKS_API_BASE + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val message = runCatching { JSONObject(detail).optJSONObject("error")?.optString("message") }.getOrNull()
                throw IllegalStateException(message?.takeIf { it.isNotBlank() } ?: "Google Tasks ha respondido con el error HTTP $code.")
            }
            if (method == "DELETE") return JSONObject()
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            return if (response.isBlank()) JSONObject() else JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }
}

@Composable
fun GoogleTasksScreen(accessToken: String?, onConnect: () -> Unit) {
    val repository = remember { GoogleTasksRepository() }
    val scope = rememberCoroutineScope()
    var tasks by remember { mutableStateOf(emptyList<GoogleTask>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var editorTask by remember { mutableStateOf<GoogleTask?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    fun reload() {
        val token = accessToken ?: return
        scope.launch {
            loading = true
            error = null
            try { tasks = repository.loadTasks(token) }
            catch (e: Exception) { error = e.message ?: "No se han podido cargar las tareas." }
            finally { loading = false }
        }
    }

    LaunchedEffect(accessToken) {
        if (accessToken != null) reload() else tasks = emptyList()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("TAREAS", color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp)
                Text(
                    if (accessToken == null) "Conecta tu cuenta de Google"
                    else "Google Tasks · Lista predeterminada",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (accessToken != null) {
                IconButton(onClick = { reload() }, enabled = !loading) {
                    Icon(Icons.Default.Refresh, contentDescription = "Actualizar tareas")
                }
                IconButton(onClick = { editorTask = null; showEditor = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Nueva tarea")
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (accessToken == null) {
            Spacer(Modifier.height(28.dp))
            Text("Conecta Google Tasks para consultar y gestionar tus tareas desde Calendario.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
                Text("Conectar con Google")
            }
            Text("Solo se solicitará acceso a tus tareas de Google. Los eventos y contactos seguirán funcionando por separado.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp))
        } else {
            if (loading && tasks.isEmpty()) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp))
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 12.dp)) }
            val pending = tasks.filterNot { it.completed }
            val completed = tasks.filter { it.completed }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
            ) {
                item {
                    Text("PENDIENTES", color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp, modifier = Modifier.padding(vertical = 10.dp))
                }
                if (pending.isEmpty()) item {
                    Text("No tienes tareas pendientes.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp))
                }
                items(pending, key = { it.id }) { task ->
                    GoogleTaskRow(task,
                        onToggle = { checked ->
                            scope.launch {
                                try {
                                    repository.setCompleted(accessToken, task, checked)
                                    reload()
                                } catch (e: Exception) { error = e.message }
                            }
                        },
                        onEdit = { editorTask = task; showEditor = true },
                        onDelete = {
                            scope.launch {
                                try { repository.deleteTask(accessToken, task.id); reload() }
                                catch (e: Exception) { error = e.message }
                            }
                        }
                    )
                }
                if (completed.isNotEmpty()) {
                    item {
                        Text("COMPLETADAS", color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp, modifier = Modifier.padding(top = 22.dp, bottom = 10.dp))
                    }
                    items(completed, key = { it.id }) { task ->
                        GoogleTaskRow(task,
                            onToggle = { checked ->
                                scope.launch {
                                    try {
                                        repository.setCompleted(accessToken, task, checked)
                                        reload()
                                    } catch (e: Exception) { error = e.message }
                                }
                            },
                            onEdit = { editorTask = task; showEditor = true },
                            onDelete = {
                                scope.launch {
                                    try { repository.deleteTask(accessToken, task.id); reload() }
                                    catch (e: Exception) { error = e.message }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showEditor && accessToken != null) {
        GoogleTaskEditorDialog(
            task = editorTask,
            onDismiss = { showEditor = false },
            onSave = { title, dueDate ->
                scope.launch {
                    try {
                        if (editorTask == null) repository.createTask(accessToken, title, dueDate)
                        else repository.updateTask(accessToken, editorTask!!, title, dueDate)
                        showEditor = false
                        reload()
                    } catch (e: Exception) { error = e.message ?: "No se ha podido guardar la tarea." }
                }
            }
        )
    }
}

@Composable
private fun GoogleTaskRow(
    task: GoogleTask,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = task.completed, onCheckedChange = onToggle)
        Column(Modifier.weight(1f).clickable(onClick = onEdit).padding(vertical = 8.dp)) {
            Text(task.title, fontWeight = FontWeight.Medium,
                color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            task.dueDate?.let {
                Text("Vence el " + it.format(DateTimeFormatter.ofPattern("d 'de' MMMM", java.util.Locale("es", "ES"))),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            if (task.notes.isNotBlank()) Text(task.notes, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall, maxLines = 2)
        }
        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Editar tarea") }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Eliminar tarea") }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun GoogleTaskEditorDialog(
    task: GoogleTask?,
    onDismiss: () -> Unit,
    onSave: suspend (String, LocalDate?) -> Unit
) {
    val scope = rememberCoroutineScope()
    var title by remember(task?.id) { mutableStateOf(task?.title.orEmpty()) }
    var dueText by remember(task?.id) { mutableStateOf(task?.dueDate?.toString().orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (task == null) "Nueva tarea" else "Editar tarea") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Tarea") }, singleLine = true)
                OutlinedTextField(dueText, { dueText = it }, label = { Text("Fecha límite (AAAA-MM-DD, opcional)") }, singleLine = true)
                Text("La fecha límite no reserva una hora en tu calendario.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = {
                try {
                    val date = dueText.trim().takeIf { it.isNotEmpty() }?.let { LocalDate.parse(it) }
                    scope.launch { onSave(title, date) }
                } catch (_: Exception) { error = "La fecha debe tener el formato AAAA-MM-DD." }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
