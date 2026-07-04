package yuan.yuansanity.hook

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer
import yuan.yuansanity.YuanSanity

class SanityExpansion(private val plugin: YuanSanity) : PlaceholderExpansion() {

    override fun getIdentifier(): String = "yuansanity"

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
            else -> null
        }
    }
}
