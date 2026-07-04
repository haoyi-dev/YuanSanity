package yuan.yuansanity.manager

import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import yuan.yuansanity.YuanSanity
import kotlin.math.max
import kotlin.math.min

class SanityManager(private val plugin: YuanSanity) {

    private val sanityKey = NamespacedKey(plugin, "sanity")

    fun getMaxSanity(): Double {
        return plugin.config.getDouble("settings.max-sanity", 100.0)
    }

    fun getSanity(player: Player): Double {
        return player.persistentDataContainer
            .getOrDefault(sanityKey, PersistentDataType.DOUBLE, getMaxSanity())
    }

    fun setSanity(player: Player, amount: Double) {
        val clamped = max(0.0, min(amount, getMaxSanity()))
        player.persistentDataContainer.set(sanityKey, PersistentDataType.DOUBLE, clamped)
    }

    fun modifySanity(player: Player, amount: Double) {
        setSanity(player, getSanity(player) + amount)
    }

    fun isInsane(player: Player): Boolean {
        return getSanity(player) <= 0.0
    }
}
