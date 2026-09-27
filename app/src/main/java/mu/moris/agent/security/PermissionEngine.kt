package mu.moris.agent.security

enum class AccessMode { ALLOW, ASK, CONFIRM, BLOCK }
enum class AgentCapability { NOTES, EXPENSES, TASKS, REMINDERS, CALENDAR, NOTIFICATIONS, CONTACTS, CAMERA, GALLERY, FILES, PHONE, APPS, LOCATION }

class PermissionEngine {
    private val policy = mutableMapOf(
        AgentCapability.NOTES to AccessMode.ALLOW,
        AgentCapability.EXPENSES to AccessMode.ALLOW,
        AgentCapability.TASKS to AccessMode.ALLOW,
        AgentCapability.REMINDERS to AccessMode.ALLOW,
        AgentCapability.CALENDAR to AccessMode.ASK,
        AgentCapability.NOTIFICATIONS to AccessMode.ASK,
        AgentCapability.CONTACTS to AccessMode.BLOCK,
        AgentCapability.CAMERA to AccessMode.ASK,
        AgentCapability.GALLERY to AccessMode.ASK,
        AgentCapability.FILES to AccessMode.ASK,
        AgentCapability.PHONE to AccessMode.CONFIRM,
        AgentCapability.APPS to AccessMode.ALLOW,
        AgentCapability.LOCATION to AccessMode.BLOCK
    )
    fun mode(capability: AgentCapability): AccessMode = policy[capability] ?: AccessMode.BLOCK
    fun set(capability: AgentCapability, mode: AccessMode) { policy[capability] = mode }
    fun allowed(capability: AgentCapability): Boolean = mode(capability) == AccessMode.ALLOW
}
