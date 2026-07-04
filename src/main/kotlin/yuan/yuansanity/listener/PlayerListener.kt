package yuan.yuansanity.listener

import org.bukkit.Material
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Enderman
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.player.PlayerBedEnterEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import yuan.yuansanity.compat.SchedulerAdapter
import yuan.yuansanity.YuanSanity
import java.util.UUID

class PlayerListener(private val plugin: YuanSanity) : Listener {

    private val sanityTasks = mutableMapOf<UUID, SchedulerAdapter.TaskHandle>()

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        plugin.packetInjector.inject(player)
        sanityTasks.remove(player.uniqueId)?.cancel()

        val cfg = plugin.config
        val darknessThreshold = cfg.getInt("settings.darkness-threshold", 5)
        val darknessDrain = cfg.getDouble("settings.darkness-drain", 1.0)
        val lightRestore = cfg.getDouble("settings.light-restore-rate", 0.5)
        val depthThreshold = cfg.getInt("settings.depth-threshold", 0)
        val depthDrain = cfg.getDouble("settings.depth-drain", 0.5)
        val isolationRange = cfg.getInt("settings.isolation-range", 500)
        val isolationDrain = cfg.getDouble("settings.isolation-drain", 0.3)

        val tickTask = label@{
            if (!player.isOnline || player.isDead) return@label
            if (player.hasPermission("yuansanity.bypass")) return@label

            val location = player.location
            val lightLevel = location.block.lightLevel
            val maxSanity = plugin.sanityManager.getMaxSanity()

            if (lightLevel <= darknessThreshold) {
                plugin.sanityManager.modifySanity(player, -darknessDrain)
            }

            if (location.blockY < depthThreshold) {
                plugin.sanityManager.modifySanity(player, -depthDrain)
            }

            if (!hasNearbyPlayers(player, isolationRange) && plugin.server.onlinePlayers.size > 1) {
                plugin.sanityManager.modifySanity(player, -isolationDrain)
            }

            if (lightLevel >= 12) {
                val current = plugin.sanityManager.getSanity(player)
                if (current < maxSanity) {
                    plugin.sanityManager.modifySanity(player, lightRestore)
                }
            }

            plugin.hallucinationEngine.tick(player, plugin.sanityManager.getSanity(player))
        }

        sanityTasks[player.uniqueId] = plugin.scheduler.runPlayerTimer(player, 20L, 20L, tickTask)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        sanityTasks.remove(event.player.uniqueId)?.cancel()
        plugin.packetInjector.eject(event.player)
        plugin.hallucinationEngine.cleanup(event.player)
    }

    @EventHandler
    fun onBedEnter(event: PlayerBedEnterEvent) {
        if (event.bedEnterResult != PlayerBedEnterEvent.BedEnterResult.OK) return
        val player = event.player
        val percent = plugin.config.getInt("settings.sleep-restore-percent", 100)
        val maxSanity = plugin.sanityManager.getMaxSanity()
        val restoreAmount = maxSanity * (percent / 100.0)
        plugin.sanityManager.setSanity(player, plugin.sanityManager.getSanity(player) + restoreAmount)
        player.sendMessage(plugin.languageManager.getMessage("restored"))
    }

    @EventHandler
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        val doppelganger = event.entity as? ArmorStand
        if (doppelganger != null && plugin.hallucinationEngine.isDoppelganger(doppelganger)) {
            event.isCancelled = true
            plugin.hallucinationEngine.removeDoppelganger(doppelganger)
            return
        }

        if (plugin.hallucinationEngine.isHerobrine(event.entity)) {
            event.isCancelled = true
            return
        }

        val player = event.entity as? Player ?: return
        if (plugin.hallucinationEngine.isHerobrine(event.damager)) {
            event.isCancelled = true
            plugin.hallucinationEngine.handleHerobrineDamage(player, event.damager)
            return
        }

        if (event.damager is Enderman) {
            plugin.sanityManager.modifySanity(player, -10.0)
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onEntityPickupItem(event: EntityPickupItemEvent) {
        val player = event.entity as? Player ?: return
        if (!plugin.hallucinationEngine.isFakeDrop(event.item)) return

        event.isCancelled = true
        plugin.hallucinationEngine.vanishFakeDrop(player, event.item)
    }

    @EventHandler
    fun onItemConsume(event: PlayerItemConsumeEvent) {
        val player = event.player
        when (event.item.type) {
            Material.ROTTEN_FLESH -> plugin.sanityManager.modifySanity(player, -5.0)
            Material.SPIDER_EYE -> plugin.sanityManager.modifySanity(player, -8.0)
            Material.PUFFERFISH -> plugin.sanityManager.modifySanity(player, -15.0)
            Material.GOLDEN_APPLE -> plugin.sanityManager.modifySanity(player, 20.0)
            Material.ENCHANTED_GOLDEN_APPLE -> plugin.sanityManager.modifySanity(player, 50.0)
            else -> {}
        }
    }

    fun shutdown() {
        sanityTasks.values.forEach { it.cancel() }
        sanityTasks.clear()
    }

    private fun hasNearbyPlayers(player: Player, configuredRange: Int): Boolean {
        if (configuredRange <= 0) return false

        val range = if (plugin.foliaSupport) {
            configuredRange.coerceAtMost(64).toDouble()
        } else {
            configuredRange.toDouble()
        }
        val rangeSquared = range * range
        val origin = player.location

        if (plugin.foliaSupport) {
            return player.getNearbyEntities(range, range, range).any {
                it is Player && it.uniqueId != player.uniqueId && it.location.distanceSquared(origin) <= rangeSquared
            }
        }

        return player.world.players.any {
            it.uniqueId != player.uniqueId && it.location.distanceSquared(origin) <= rangeSquared
        }
    }
}
