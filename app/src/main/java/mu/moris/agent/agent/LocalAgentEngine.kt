package mu.moris.agent.agent

import mu.moris.agent.data.*
import mu.moris.agent.security.AgentCapability
import mu.moris.agent.security.PermissionEngine

data class AgentReply(val text: String, val success: Boolean = true)

class LocalAgentEngine(private val dao: AgentDao, private val permissions: PermissionEngine) {
    suspend fun handle(raw: String): AgentReply {
        val text = raw.trim()
        if (text.isBlank()) return AgentReply("Type or say a command.", false)
        val lower = text.lowercase()
        return when {
            listOf("spent", "expense", "depans", "depanse", "finn depans").any(lower::contains) -> addExpense(text)
            listOf("task", "to do", "todo", "pou fer").any(lower::contains) -> addTask(text)
            listOf("note", "remember this", "rapel sa", "ekrir").any(lower::contains) -> addNote(text)
            lower.contains("what can you do") || lower.contains("ki to kapav fer") ->
                AgentReply("Mo travay lokal lor telefonn. I can create notes, tasks and expenses without sending your private data to the internet.")
            else -> AgentReply("I understood that locally, but no matching tool is enabled yet. Try: “Mo finn depans 450 roupi lor lunch”, “Create a note …”, or “Create a task …”.", false)
        }
    }

    private suspend fun addExpense(text: String): AgentReply {
        if (!permissions.allowed(AgentCapability.EXPENSES)) return blocked("Expenses")
        val amount = Regex("""\b(\d+(?:[.,]\d{1,2})?)\b""").find(text)?.value?.replace(",", ".")?.toDoubleOrNull()
            ?: return AgentReply("I found an expense command, but not the amount.", false)
        val category = when {
            text.contains("petrol", true) || text.contains("fuel", true) -> "Fuel"
            text.contains("lunch", true) || text.contains("food", true) || text.contains("manze", true) -> "Food"
            text.contains("bus", true) || text.contains("taxi", true) -> "Transport"
            else -> "Other"
        }
        dao.addExpense(ExpenseEntity(amount = amount, category = category, description = text))
        dao.log(ActivityEntity(action = "Expense added", detail = "MUR " + amount + " • " + category))
        return AgentReply("Saved locally: Rs " + amount.toInt() + " • " + category + ".")
    }

    private suspend fun addTask(text: String): AgentReply {
        if (!permissions.allowed(AgentCapability.TASKS)) return blocked("Tasks")
        val cleaned = text.replace(Regex("(?i)create|add|task|todo|to do|pou fer"), "").trim().ifBlank { "New task" }
        dao.addTask(TaskEntity(title = cleaned))
        dao.log(ActivityEntity(action = "Task created", detail = cleaned))
        return AgentReply("Task created locally: " + cleaned)
    }

    private suspend fun addNote(text: String): AgentReply {
        if (!permissions.allowed(AgentCapability.NOTES)) return blocked("Notes")
        val cleaned = text.replace(Regex("(?i)create|add|note|remember this|rapel sa|ekrir"), "").trim().ifBlank { text }
        dao.addNote(NoteEntity(title = cleaned.take(40), content = cleaned))
        dao.log(ActivityEntity(action = "Note created", detail = cleaned.take(80)))
        return AgentReply("Note saved locally.")
    }

    private fun blocked(name: String) = AgentReply(name + " access is blocked by your agent permissions.", false)
}
