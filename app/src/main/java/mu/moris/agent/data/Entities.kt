package mu.moris.agent.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val title: String, val content: String, val createdAt: Long = System.currentTimeMillis())

@Entity(tableName = "expenses")
data class ExpenseEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val amount: Double, val category: String, val description: String, val currency: String = "MUR", val createdAt: Long = System.currentTimeMillis())

@Entity(tableName = "tasks")
data class TaskEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val title: String, val completed: Boolean = false, val dueAt: Long? = null, val createdAt: Long = System.currentTimeMillis())

@Entity(tableName = "agent_activity")
data class ActivityEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val action: String, val detail: String, val createdAt: Long = System.currentTimeMillis())
