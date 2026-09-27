package mu.moris.agent.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentDao {
    @Query("SELECT * FROM notes ORDER BY createdAt DESC") fun notes(): Flow<List<NoteEntity>>
    @Insert suspend fun addNote(note: NoteEntity)
    @Query("SELECT * FROM expenses ORDER BY createdAt DESC") fun expenses(): Flow<List<ExpenseEntity>>
    @Insert suspend fun addExpense(expense: ExpenseEntity)
    @Query("SELECT COALESCE(SUM(amount), 0) FROM expenses") fun totalExpenses(): Flow<Double>
    @Query("SELECT * FROM tasks ORDER BY completed ASC, createdAt DESC") fun tasks(): Flow<List<TaskEntity>>
    @Insert suspend fun addTask(task: TaskEntity)
    @Query("UPDATE tasks SET completed = :completed WHERE id = :id") suspend fun setTaskCompleted(id: Long, completed: Boolean)
    @Insert suspend fun log(activity: ActivityEntity)
    @Query("SELECT * FROM agent_activity ORDER BY createdAt DESC LIMIT 50") fun activity(): Flow<List<ActivityEntity>>
}
