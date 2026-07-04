package yuan.yuansanity.engine

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Item
import org.bukkit.entity.Phantom
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import yuan.yuansanity.YuanSanity
import java.util.UUID
import kotlin.random.Random

class HallucinationEngine(private val plugin: YuanSanity) {

    private val fakeDropKey = NamespacedKey(plugin, "fake_drop")
    private val doppelgangerKey = NamespacedKey(plugin, "doppelganger")
    private val nightmareCooldowns = mutableMapOf<UUID, Long>()
    private val nightmares = mutableMapOf<UUID, MutableList<Phantom>>()
    private val doppelgangers = mutableMapOf<UUID, MutableList<ArmorStand>>()

    private val whisperMessages = listOf(
        "Di theo toi...",
        "Dung nhin lai phia sau",
        "Ban co nghe thay khong?",
        "Toi o ngay day...",
        "Chay di... nhanh len...",
        "Khong ai cuu duoc ban dau",
        "Nhin xuong chan ban...",
        "Dung lai. Dung di tiep.",
        "Toi da thay ban roi",
        "Ban khong mot minh dau..."
    )

    private val creepySounds = listOf(
        Sound.AMBIENT_CAVE,
        Sound.ENTITY_ENDERMAN_STARE,
        Sound.ENTITY_GHAST_AMBIENT,
        Sound.ENTITY_VEX_AMBIENT,
        Sound.ENTITY_PHANTOM_AMBIENT,
        Sound.ENTITY_WARDEN_HEARTBEAT,
        Sound.BLOCK_SCULK_SHRIEKER_SHRIEK
    )

    private val fakeBlockTypes = listOf(
        Material.DIAMOND_ORE,
        Material.EMERALD_ORE,
        Material.LAVA,
        Material.REDSTONE_ORE,
        Material.ANCIENT_DEBRIS
    )

    private val fakeDropTypes = listOf(
        Material.DIAMOND,
        Material.EMERALD,
        Material.NETHERITE_SCRAP,
        Material.GOLD_INGOT
    )

    fun processOutboundPacket(player: Player, packetName: String, packet: Any): Any? {
        if (!player.isOnline || player.hasPermission("yuansanity.bypass")) return packet
        return packet
    }

    fun tick(player: Player, sanity: Double) {
        if (player.hasPermission("yuansanity.bypass")) return

        val cfg = plugin.config

        if (sanity < 80.0) {
            val chance = cfg.getDouble("hallucination.whisper-chance", 0.02)
            if (Random.nextDouble() < chance * 2) {
                triggerAmbientSound(player)
            }
        }

        if (sanity < 50.0) {
            val whisperChance = cfg.getDouble("hallucination.whisper-chance", 0.02)
            if (Random.nextDouble() < whisperChance) {
                triggerWhisper(player)
            }

            val fakeBlockChance = cfg.getDouble("hallucination.fake-block-chance", 0.05)
            if (Random.nextDouble() < fakeBlockChance) {
                triggerFakeBlocks(player)
            }

            val fakeDropChance = cfg.getDouble("hallucination.fake-drop-chance", 0.04)
            if (Random.nextDouble() < fakeDropChance) {
                triggerFakeDrop(player)
            }
        }

        if (sanity < 25.0) {
            val doppelgangerChance = cfg.getDouble("hallucination.doppelganger-chance", 0.01)
            if (Random.nextDouble() < doppelgangerChance) {
                spawnDoppelganger(player)
            }

            val inventoryChance = cfg.getDouble("hallucination.inventory-glitch-chance", 0.03)
            if (Random.nextDouble() < inventoryChance) {
                triggerInventoryGlitch(player)
            }
        }

        if (sanity <= 0.0) {
            val currentTime = System.currentTimeMillis()
            val lastSpawn = nightmareCooldowns[player.uniqueId] ?: 0L
            val cooldown = cfg.getLong("hallucination.nightmare-cooldown", 60) * 1000L

            if (currentTime - lastSpawn > cooldown) {
                spawnNightmare(player)
                nightmareCooldowns[player.uniqueId] = currentTime
            }
        } else {
            nightmareCooldowns.remove(player.uniqueId)
        }
    }

    fun cleanup(player: Player) {
        nightmareCooldowns.remove(player.uniqueId)
        removeEntities(nightmares.remove(player.uniqueId).orEmpty())
        removeEntities(doppelgangers.remove(player.uniqueId).orEmpty())
    }

    fun cleanupAll() {
        nightmareCooldowns.clear()
        nightmares.values.flatten().forEach { removeEntity(it) }
        doppelgangers.values.flatten().forEach { removeEntity(it) }
        nightmares.clear()
        doppelgangers.clear()
    }

    fun isFakeDrop(item: Item): Boolean {
        return item.persistentDataContainer.has(fakeDropKey, PersistentDataType.BYTE)
    }

    fun vanishFakeDrop(player: Player, item: Item) {
        player.playSound(player.location, Sound.ENTITY_ITEM_PICKUP, 0.5f, 0.4f)
        plugin.scheduler.runEntityTask(item) {
            if (!item.isValid) return@runEntityTask
            item.remove()
        }
    }

    fun isDoppelganger(stand: ArmorStand): Boolean {
        return stand.persistentDataContainer.has(doppelgangerKey, PersistentDataType.BYTE)
    }

    fun removeDoppelganger(stand: ArmorStand) {
        doppelgangers.values.forEach { list -> list.removeIf { it.uniqueId == stand.uniqueId } }
        plugin.scheduler.runEntityTask(stand) {
            if (!stand.isValid) return@runEntityTask
            stand.remove()
        }
    }

    private fun spawnNightmare(player: Player) {
        val loc = player.location.clone().add(0.0, 15.0, 0.0)
        val cfg = plugin.config
        val health = cfg.getDouble("hallucination.nightmare-health", 100.0)
        val damage = cfg.getDouble("hallucination.nightmare-damage", 15.0)

        plugin.scheduler.runRegion(loc) {
            if (!player.isOnline) return@runRegion

            val phantom = loc.world.spawnEntity(loc, EntityType.PHANTOM) as Phantom
            phantom.customName(Component.text("\u00a74The Nightmare"))
            phantom.isCustomNameVisible = true
            phantom.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.baseValue = health
            phantom.health = health
            phantom.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE)?.baseValue = damage
            phantom.isVisibleByDefault = false
            player.showEntity(plugin, phantom)
            phantom.target = player
            nightmares.computeIfAbsent(player.uniqueId) { mutableListOf() }.add(phantom)
            player.sendMessage(plugin.languageManager.getMessage("nightmare-spawn"))
        }
    }

    private fun spawnDoppelganger(player: Player) {
        val angle = Random.nextDouble() * Math.PI * 2
        val distance = Random.nextDouble(15.0, 30.0)
        val offsetX = Math.cos(angle) * distance
        val offsetZ = Math.sin(angle) * distance
        val loc = player.location.clone().add(offsetX, 0.0, offsetZ)

        plugin.scheduler.runRegion(loc) {
            if (!player.isOnline) return@runRegion

            loc.y = loc.world.getHighestBlockYAt(loc).toDouble() + 1.0
            val stand = loc.world.spawnEntity(loc, EntityType.ARMOR_STAND) as ArmorStand
            stand.customName(Component.text(player.name))
            stand.isCustomNameVisible = true
            stand.isVisible = true
            stand.setGravity(false)
            stand.isInvulnerable = true
            stand.isVisibleByDefault = false
            stand.persistentDataContainer.set(doppelgangerKey, PersistentDataType.BYTE, 1.toByte())
            stand.equipment?.setItemInMainHand(player.inventory.itemInMainHand.clone())
            stand.equipment?.helmet = player.inventory.helmet?.clone()
            player.showEntity(plugin, stand)

            doppelgangers.computeIfAbsent(player.uniqueId) { mutableListOf() }.add(stand)
            plugin.scheduler.runEntityLater(stand, 100L) {
                if (stand.isValid) {
                    removeDoppelganger(stand)
                }
            }
        }
    }

    private fun triggerInventoryGlitch(player: Player) {
        val glitchMaterials = listOf(
            Material.DIRT,
            Material.ROTTEN_FLESH,
            Material.COBBLESTONE,
            Material.POISONOUS_POTATO,
            Material.DEAD_BUSH,
            Material.BONE
        )

        var changed = false
        for (i in 0 until 9) {
            if (!Random.nextBoolean()) continue

            val fakeMat = glitchMaterials[Random.nextInt(glitchMaterials.size)]
            val protocolSlot = 36 + i
            if (plugin.packetInjector.sendFakeContainerSlot(player, protocolSlot, ItemStack(fakeMat))) {
                changed = true
            }
        }

        if (!changed) return

        plugin.scheduler.runPlayerLater(player, 30L) {
            if (player.isOnline) {
                player.updateInventory()
            }
        }
    }

    private fun triggerFakeBlocks(player: Player) {
        val loc = player.location
        val world = loc.world
        val fakeLocations = mutableListOf<Location>()

        for (i in 0 until Random.nextInt(3, 8)) {
            val x = loc.blockX + Random.nextInt(-10, 11)
            val y = loc.blockY + Random.nextInt(-3, 4)
            val z = loc.blockZ + Random.nextInt(-10, 11)
            val blockLoc = Location(world, x.toDouble(), y.toDouble(), z.toDouble())
            val block = world.getBlockAt(blockLoc)

            if (block.type == Material.STONE || block.type == Material.DEEPSLATE || block.type == Material.COBBLESTONE) {
                val fakeMat = fakeBlockTypes[Random.nextInt(fakeBlockTypes.size)]
                player.sendBlockChange(blockLoc, fakeMat.createBlockData())
                fakeLocations.add(blockLoc)
            }
        }

        if (fakeLocations.isEmpty()) return

        val delay = Random.nextLong(60L, 200L)
        plugin.scheduler.runPlayerLater(player, delay) {
            if (!player.isOnline) return@runPlayerLater
            for (fakeLoc in fakeLocations) {
                val realBlock = world.getBlockAt(fakeLoc)
                player.sendBlockChange(fakeLoc, realBlock.blockData)
            }
        }
    }

    private fun triggerFakeDrop(player: Player) {
        val angle = Random.nextDouble() * Math.PI * 2
        val distance = Random.nextDouble(2.5, 5.0)
        val loc = player.location.clone().add(Math.cos(angle) * distance, 0.4, Math.sin(angle) * distance)
        val itemStack = ItemStack(fakeDropTypes[Random.nextInt(fakeDropTypes.size)])

        plugin.scheduler.runRegion(loc) {
            if (!player.isOnline) return@runRegion

            val item = loc.world.dropItem(loc, itemStack)
            item.pickupDelay = 0
            item.isVisibleByDefault = false
            item.persistentDataContainer.set(fakeDropKey, PersistentDataType.BYTE, 1.toByte())
            player.showEntity(plugin, item)

            plugin.scheduler.runEntityLater(item, 100L) {
                if (item.isValid) {
                    item.remove()
                }
            }
        }
    }

    private fun triggerWhisper(player: Player) {
        val onlinePlayers = plugin.server.onlinePlayers.filter { it.uniqueId != player.uniqueId }
        val senderName = if (onlinePlayers.isNotEmpty()) {
            onlinePlayers.random().name
        } else {
            "???"
        }
        val message = whisperMessages[Random.nextInt(whisperMessages.size)]
        val formatted = "\u00a77<$senderName> \u00a77\u00a7o$message"
        player.sendMessage(LegacyComponentSerializer.legacySection().deserialize(formatted))
    }

    private fun triggerAmbientSound(player: Player) {
        val loc = player.location.clone().add(
            Random.nextDouble(-15.0, 15.0),
            Random.nextDouble(-5.0, 5.0),
            Random.nextDouble(-15.0, 15.0)
        )
        val sound = creepySounds[Random.nextInt(creepySounds.size)]
        player.playSound(loc, sound, 0.5f, Random.nextFloat() * 0.5f + 0.5f)
    }

    private fun removeEntities(entities: List<Entity>) {
        entities.forEach { removeEntity(it) }
    }

    private fun removeEntity(entity: Entity) {
        plugin.scheduler.runEntityTask(entity) {
            if (entity.isValid) {
                entity.remove()
            }
        }
    }
}
