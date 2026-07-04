package yuan.yuansanity.hook

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer
import yuan.yuansanity.YuanSanity

class SanityExpansion(
    private val plugin: YuanSanity,
    private val identifier: String = "yuansanity"
) : PlaceholderExpansion() {

    override fun getIdentifier(): String = identifier

    override fun getAuthor(): String = "Yuan"

    override fun getVersion(): String = "1.0.0"

    override fun persist(): Boolean = true

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        if (player == null || !player.isOnline) return null
        val onlinePlayer = player.player ?: return null

        return when (params.lowercase()) {
            "level" -> String.format("%.1f", plugin.sanityManager.getSanity(onlinePlayer))
            "max" -> String.format("%.1f", plugin.sanityManager.getMaxSanity())
            "percent" -> {
                val current = plugin.sanityManager.getSanity(onlinePlayer)
                val max = plugin.sanityManager.getMaxSanity()
                String.format("%.0f%%", (current / max) * 100)
            }
            "stats" -> {
                val current = plugin.sanityManager.getSanity(onlinePlayer)
                val max = plugin.sanityManager.getMaxSanity()
                val percent = if (max <= 0.0) 0.0 else (current / max) * 100.0
                String.format("%.1f/%.1f (%.0f%%)", current, max, percent)
            }
            "status" -> {
                val current = plugin.sanityManager.getSanity(onlinePlayer)
                when {
                    current <= 0.0 -> "INSANE"
                    current < 25.0 -> "PANIC"
                    current < 50.0 -> "UNSTABLE"
                    current < 80.0 -> "UNEASY"
                    else -> "STABLE"
                }
            }
            else -> null
        }
    }
}
