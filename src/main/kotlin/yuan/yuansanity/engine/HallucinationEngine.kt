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
import org.bukkit.entity.Zombie
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import yuan.yuansanity.YuanSanity
import java.util.UUID
import kotlin.random.Random

class HallucinationEngine(private val plugin: YuanSanity) {

    private val fakeDropKey = NamespacedKey(plugin, "fake_drop")
    private val doppelgangerKey = NamespacedKey(plugin, "doppelganger")
    private val herobrineKey = NamespacedKey(plugin, "herobrine_stalker")
    private val nightmareCooldowns = mutableMapOf<UUID, Long>()
    private val herobrineCooldowns = mutableMapOf<UUID, Long>()
    private val occultCooldowns = mutableMapOf<UUID, Long>()
    private val herobrineActionCooldowns = mutableMapOf<UUID, Long>()
    private val panicChatCooldowns = mutableMapOf<UUID, Long>()
    private val nightmares = mutableMapOf<UUID, MutableList<Phantom>>()
    private val doppelgangers = mutableMapOf<UUID, MutableList<ArmorStand>>()
    private val herobrines = mutableMapOf<UUID, Zombie>()
    private val ritualItems = mutableMapOf<UUID, MutableList<Item>>()

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

    private val panicMessages = listOf(
        "sau lung",
        "dung quay lai",
        "no dang dung ngay do",
        "ban vua thay no dung khong",
        "tat den di",
        "khong phai ao giac dau",
        "no biet ten ban",
        "chay",
        "chay nhanh hon",
        "Herobrine da thay ban"
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
        showSanityActionBar(player, sanity)

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

            val panicChatChance = cfg.getDouble("hallucination.panic-chat-chance", 0.08)
            if (Random.nextDouble() < panicChatChance) {
                triggerPanicChatBurst(player, 2)
            }
        }

        if (sanity < 15.0) {
            val occultChance = cfg.getDouble("hallucination.occult-scene-chance", 0.04)
            if (Random.nextDouble() < occultChance) {
                triggerOccultScene(player)
            }
        }

        if (sanity <= 0.0) {
            val currentTime = System.currentTimeMillis()
            val lastSpawn = nightmareCooldowns[player.uniqueId] ?: 0L
            val cooldown = cfg.getLong("hallucination.nightmare-cooldown", 60) * 1000L
            val lastHerobrine = herobrineCooldowns[player.uniqueId] ?: 0L
            val herobrineInterval = cfg.getLong("hallucination.herobrine-jumpscare-interval", 15) * 1000L

            if (currentTime - lastSpawn > cooldown) {
                spawnNightmare(player)
                nightmareCooldowns[player.uniqueId] = currentTime
            }

            if (currentTime - lastHerobrine > herobrineInterval) {
                triggerHerobrineJumpscare(player)
                triggerOccultScene(player, force = true)
                herobrineCooldowns[player.uniqueId] = currentTime
            }
            if (cfg.getBoolean("hallucination.herobrine-stalker-enabled", true)) {
                ensureHerobrineStalker(player)
                tickHerobrineStalker(player)
            }
        } else {
            nightmareCooldowns.remove(player.uniqueId)
            herobrineCooldowns.remove(player.uniqueId)
            herobrineActionCooldowns.remove(player.uniqueId)
            herobrines.remove(player.uniqueId)?.let { removeEntity(it) }
        }
    }

    fun cleanup(player: Player) {
        nightmareCooldowns.remove(player.uniqueId)
        herobrineCooldowns.remove(player.uniqueId)
        occultCooldowns.remove(player.uniqueId)
        herobrineActionCooldowns.remove(player.uniqueId)
        panicChatCooldowns.remove(player.uniqueId)
        removeEntities(nightmares.remove(player.uniqueId).orEmpty())
        removeEntities(doppelgangers.remove(player.uniqueId).orEmpty())
        herobrines.remove(player.uniqueId)?.let { removeEntity(it) }
        removeEntities(ritualItems.remove(player.uniqueId).orEmpty())
    }

    fun cleanupAll() {
        nightmareCooldowns.clear()
        herobrineCooldowns.clear()
        occultCooldowns.clear()
        herobrineActionCooldowns.clear()
        panicChatCooldowns.clear()
        nightmares.values.flatten().forEach { removeEntity(it) }
        doppelgangers.values.flatten().forEach { removeEntity(it) }
        herobrines.values.forEach { removeEntity(it) }
        ritualItems.values.flatten().forEach { removeEntity(it) }
        nightmares.clear()
        doppelgangers.clear()
        herobrines.clear()
        ritualItems.clear()
    }

    fun cleanupAllOnDisable() {
        nightmareCooldowns.clear()
        herobrineCooldowns.clear()
        occultCooldowns.clear()
        herobrineActionCooldowns.clear()
        panicChatCooldowns.clear()
        nightmares.values.flatten().forEach { removeEntityNow(it) }
        doppelgangers.values.flatten().forEach { removeEntityNow(it) }
        herobrines.values.forEach { removeEntityNow(it) }
        ritualItems.values.flatten().forEach { removeEntityNow(it) }
        nightmares.clear()
        doppelgangers.clear()
        herobrines.clear()
        ritualItems.clear()
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

    fun isHerobrine(entity: Entity): Boolean {
        return entity.persistentDataContainer.has(herobrineKey, PersistentDataType.BYTE)
    }

    fun handleHerobrineDamage(player: Player, herobrine: Entity) {
        val direction = player.location.toVector().subtract(herobrine.location.toVector()).normalize()
        direction.y = 0.35
        player.velocity = direction.multiply(1.15)
        player.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 45, 0, false, false, true))
        player.playSound(player.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.8f, 0.55f)
        playScareCombo(player, "hit")
        triggerPanicChatBurst(player, 3)
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

    private fun ensureHerobrineStalker(player: Player) {
        val current = herobrines[player.uniqueId]
        if (current != null && current.isValid && !current.isDead) return

        val spawnLoc = player.location.clone().subtract(player.location.direction.normalize().multiply(7.0))
        plugin.scheduler.runRegion(spawnLoc) {
            if (!player.isOnline) return@runRegion
            if (herobrines[player.uniqueId]?.isValid == true) return@runRegion

            val zombie = spawnLoc.world.spawnEntity(spawnLoc, EntityType.ZOMBIE) as Zombie
            zombie.customName(Component.text("\u00a74${nameOfHerobrine()}"))
            zombie.isCustomNameVisible = true
            zombie.setAdult()
            zombie.setAI(true)
            zombie.isSilent = true
            zombie.isPersistent = false
            zombie.removeWhenFarAway = true
            zombie.isVisibleByDefault = false
            zombie.persistentDataContainer.set(herobrineKey, PersistentDataType.BYTE, 1.toByte())
            zombie.equipment.setItemInMainHand(ItemStack(Material.NETHERITE_SWORD))
            zombie.equipment.helmet = ItemStack(Material.PLAYER_HEAD)
            zombie.equipment.chestplate = ItemStack(Material.NETHERITE_CHESTPLATE)
            zombie.equipment.leggings = ItemStack(Material.NETHERITE_LEGGINGS)
            zombie.equipment.boots = ItemStack(Material.NETHERITE_BOOTS)
            zombie.target = player
            player.showEntity(plugin, zombie)
            herobrines[player.uniqueId] = zombie
            playScareCombo(player, "stalker-spawn")
        }
    }

    private fun tickHerobrineStalker(player: Player) {
        val herobrine = herobrines[player.uniqueId] ?: return
        if (!herobrine.isValid || herobrine.isDead) {
            herobrines.remove(player.uniqueId)
            return
        }

        plugin.scheduler.runEntityTask(herobrine) {
            if (!player.isOnline || !herobrine.isValid) return@runEntityTask
            herobrine.target = player
        }

        val now = System.currentTimeMillis()
        val last = herobrineActionCooldowns[player.uniqueId] ?: 0L
        val actionCooldown = plugin.config.getLong("hallucination.herobrine-stalker-action-cooldown", 6) * 1000L
        if (now - last < actionCooldown) return
        herobrineActionCooldowns[player.uniqueId] = now

        val distanceSquared = runCatching { herobrine.location.distanceSquared(player.location) }.getOrDefault(9999.0)
        if (distanceSquared <= 16.0) {
            when (Random.nextInt(4)) {
                0 -> dragPlayerLeg(player, herobrine.location)
                1 -> liftAndThrowPlayer(player)
                2 -> crawlScare(player)
                else -> handleHerobrineDamage(player, herobrine)
            }
        } else if (distanceSquared > 900.0) {
            herobrines.remove(player.uniqueId)?.let { removeEntity(it) }
        }
    }

    private fun dragPlayerLeg(player: Player, source: Location) {
        val pull = source.toVector().subtract(player.location.toVector()).normalize()
        pull.y = -0.25
        player.velocity = pull.multiply(0.95)
        player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 70, 3, false, false, true))
        player.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 25, 0, false, false, true))
        player.sendTitle("\u00a74${nameOfHerobrine()}", "\u00a7ckeo chan...", 0, 25, 10)
        player.playSound(player.location, Sound.BLOCK_CHAIN_PLACE, 1.0f, 0.45f)
        player.playSound(player.location, Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.55f)
        playScareCombo(player, "drag")
        triggerPanicChatBurst(player, 4)
    }

    private fun liftAndThrowPlayer(player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.LEVITATION, 25, 2, false, false, true))
        player.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 70, 0, false, false, true))
        player.sendTitle("\u00a74${nameOfHerobrine()}", "\u00a7cnhac bong", 0, 25, 10)
        player.playSound(player.location, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.55f)
        playScareCombo(player, "lift")
        plugin.scheduler.runPlayerLater(player, 24L) {
            if (!player.isOnline) return@runPlayerLater
            val side = player.location.direction.clone().crossProduct(org.bukkit.util.Vector(0.0, 1.0, 0.0)).normalize()
            player.velocity = side.multiply(Random.nextDouble(0.9, 1.4)).setY(0.65)
            player.playSound(player.location, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.6f)
            playScareCombo(player, "throw")
        }
    }

    private fun crawlScare(player: Player) {
        val loc = player.location.clone().add(player.location.direction.normalize().multiply(1.4))
        loc.y = player.location.y - 1.0
        player.sendTitle("\u00a78...", "\u00a74no dang bo toi", 0, 25, 8)
        player.playSound(player.location, Sound.ENTITY_SPIDER_AMBIENT, 0.9f, 0.45f)
        player.playSound(player.location, Sound.BLOCK_SCULK_SENSOR_CLICKING, 1.0f, 0.6f)
        playScareCombo(player, "crawl")
        plugin.protocolScareEngine?.triggerOccultPacketBurst(player, loc)
        triggerPanicChatBurst(player, 3)
    }

    private fun triggerHerobrineJumpscare(player: Player) {
        val name = plugin.config.getString("hallucination.herobrine-name", "Herobrine") ?: "Herobrine"
        val duration = plugin.config.getLong("hallucination.herobrine-duration", 120L)
        val base = player.location.clone()
        val direction = base.direction.normalize()
        val loc = if (Random.nextBoolean()) {
            base.add(direction.multiply(2.2))
        } else {
            base.subtract(direction.multiply(1.8))
        }
        loc.y = player.location.y
        loc.setDirection(player.location.toVector().subtract(loc.toVector()))

        plugin.scheduler.runRegion(loc) {
            if (!player.isOnline) return@runRegion

            val stand = loc.world.spawnEntity(loc, EntityType.ARMOR_STAND) as ArmorStand
            stand.customName(Component.text("\u00a74$name"))
            stand.isCustomNameVisible = true
            stand.isVisible = true
            stand.setGravity(false)
            stand.isInvulnerable = true
            stand.isVisibleByDefault = false
            stand.persistentDataContainer.set(doppelgangerKey, PersistentDataType.BYTE, 1.toByte())
            stand.equipment?.setItemInMainHand(ItemStack(Material.NETHERITE_SWORD))
            stand.equipment?.helmet = ItemStack(Material.PLAYER_HEAD)
            stand.equipment?.chestplate = ItemStack(Material.LEATHER_CHESTPLATE)
            player.showEntity(plugin, stand)
            doppelgangers.computeIfAbsent(player.uniqueId) { mutableListOf() }.add(stand)

            player.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 80, 0, false, false, true))
            player.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 35, 0, false, false, true))
            player.sendTitle("\u00a74$name", "\u00a7cDUNG QUAY LAI", 0, 35, 10)
            player.playSound(player.location, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 0.55f)
            player.playSound(player.location, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 1.0f, 0.7f)
            player.playSound(player.location, Sound.ENTITY_ENDERMAN_SCREAM, 0.9f, 0.6f)
            playScareCombo(player, "jumpscare")
            plugin.protocolScareEngine?.triggerHerobrinePacketBurst(player, loc)
            triggerPanicChatBurst(player, 5)
            triggerInventoryGlitch(player)

            plugin.scheduler.runEntityLater(stand, duration) {
                if (stand.isValid) {
                    removeDoppelganger(stand)
                }
            }
        }
    }

    private fun triggerOccultScene(player: Player, force: Boolean = false) {
        val now = System.currentTimeMillis()
        val cooldown = plugin.config.getLong("hallucination.occult-scene-cooldown", 12) * 1000L
        val last = occultCooldowns[player.uniqueId] ?: 0L
        if (!force && now - last < cooldown) return
        occultCooldowns[player.uniqueId] = now

        when (Random.nextInt(3)) {
            0 -> triggerNetherCross(player)
            1 -> triggerSummoningCircle(player)
            else -> triggerRedstoneRitual(player)
        }
    }

    private fun triggerNetherCross(player: Player) {
        val base = getScareBaseLocation(player, Random.nextDouble(4.0, 7.0))
        val blocks = mutableListOf<Pair<Location, Material>>()

        for (y in 0..4) {
            blocks.add(base.clone().add(0.0, y.toDouble(), 0.0) to Material.NETHER_BRICKS)
        }
        blocks.add(base.clone().add(-1.0, 2.0, 0.0) to Material.NETHER_BRICKS)
        blocks.add(base.clone().add(1.0, 2.0, 0.0) to Material.NETHER_BRICKS)
        blocks.add(base.clone().add(0.0, 1.0, 0.0) to Material.REDSTONE_BLOCK)
        blocks.add(base.clone().add(0.0, 0.0, 1.0) to Material.SOUL_SAND)
        blocks.add(base.clone().add(0.0, 0.0, -1.0) to Material.SOUL_SAND)

        showFakeBlocks(player, blocks, plugin.config.getLong("hallucination.occult-scene-duration", 120L))
        plugin.protocolScareEngine?.triggerOccultPacketBurst(player, base)
        player.sendTitle("\u00a74+", "\u00a78mot dau hieu vua xuat hien", 0, 30, 10)
        player.playSound(player.location, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.0f, 0.55f)
        player.playSound(player.location, Sound.ENTITY_WITHER_SPAWN, 0.35f, 0.7f)
        playScareCombo(player, "cross")
        triggerPanicChatBurst(player, 4)
    }

    private fun triggerSummoningCircle(player: Player) {
        val base = getScareBaseLocation(player, Random.nextDouble(3.5, 6.5))
        val blocks = mutableListOf<Pair<Location, Material>>()
        val ring = listOf(
            -2 to 0, -1 to -1, 0 to -2, 1 to -1, 2 to 0,
            1 to 1, 0 to 2, -1 to 1
        )

        for ((x, z) in ring) {
            blocks.add(base.clone().add(x.toDouble(), 0.0, z.toDouble()) to Material.REDSTONE_BLOCK)
        }
        blocks.add(base.clone() to Material.CRYING_OBSIDIAN)
        blocks.add(base.clone().add(1.0, 0.0, 0.0) to Material.NETHER_BRICKS)
        blocks.add(base.clone().add(-1.0, 0.0, 0.0) to Material.NETHER_BRICKS)
        blocks.add(base.clone().add(0.0, 0.0, 1.0) to Material.NETHER_BRICKS)
        blocks.add(base.clone().add(0.0, 0.0, -1.0) to Material.NETHER_BRICKS)

        showFakeBlocks(player, blocks, plugin.config.getLong("hallucination.occult-scene-duration", 120L))
        plugin.protocolScareEngine?.triggerOccultPacketBurst(player, base)
        spawnRitualItems(player, base.clone().add(0.0, 1.0, 0.0))
        player.sendTitle("\u00a74${nameOfHerobrine()}", "\u00a7cnghi le da bat dau", 0, 35, 10)
        player.playSound(player.location, Sound.BLOCK_END_PORTAL_SPAWN, 0.8f, 0.55f)
        player.playSound(player.location, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.6f)
        playScareCombo(player, "summon")
        triggerPanicChatBurst(player, 5)
    }

    private fun triggerRedstoneRitual(player: Player) {
        val base = getScareBaseLocation(player, Random.nextDouble(2.5, 5.0))
        val blocks = mutableListOf<Pair<Location, Material>>()

        for (i in -4..4) {
            blocks.add(base.clone().add(i.toDouble(), 0.0, 0.0) to Material.REDSTONE_BLOCK)
        }
        for (i in -3..3) {
            blocks.add(base.clone().add(0.0, 0.0, i.toDouble()) to Material.REDSTONE_BLOCK)
        }
        blocks.add(base.clone().add(0.0, 1.0, 0.0) to Material.SOUL_LANTERN)
        blocks.add(base.clone().add(2.0, 1.0, 2.0) to Material.SKELETON_SKULL)
        blocks.add(base.clone().add(-2.0, 1.0, -2.0) to Material.SKELETON_SKULL)

        showFakeBlocks(player, blocks, plugin.config.getLong("hallucination.occult-scene-duration", 120L))
        plugin.protocolScareEngine?.triggerOccultPacketBurst(player, base)
        player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 1, false, false, true))
        player.playSound(player.location, Sound.BLOCK_REDSTONE_TORCH_BURNOUT, 1.0f, 0.45f)
        player.playSound(player.location, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.7f, 0.65f)
        playScareCombo(player, "redstone")
        triggerPanicChatBurst(player, 6)
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

    private fun spawnRitualItems(player: Player, center: Location) {
        val materials = listOf(
            Material.REDSTONE,
            Material.ENDER_EYE,
            Material.BLAZE_POWDER,
            Material.NETHER_STAR,
            Material.TOTEM_OF_UNDYING,
            Material.PLAYER_HEAD
        )
        val spawned = mutableListOf<Item>()

        plugin.scheduler.runRegion(center) {
            if (!player.isOnline) return@runRegion

            for ((index, material) in materials.withIndex()) {
                val angle = (Math.PI * 2.0 / materials.size) * index
                val loc = center.clone().add(Math.cos(angle) * 1.6, 0.0, Math.sin(angle) * 1.6)
                val item = loc.world.dropItem(loc, ItemStack(material))
                item.pickupDelay = Int.MAX_VALUE
                item.isVisibleByDefault = false
                item.persistentDataContainer.set(fakeDropKey, PersistentDataType.BYTE, 1.toByte())
                player.showEntity(plugin, item)
                spawned.add(item)
            }

            ritualItems.computeIfAbsent(player.uniqueId) { mutableListOf() }.addAll(spawned)
            plugin.scheduler.runPlayerLater(player, plugin.config.getLong("hallucination.occult-scene-duration", 120L)) {
                spawned.forEach { item ->
                    plugin.scheduler.runEntityTask(item) {
                        if (item.isValid) item.remove()
                    }
                }
                ritualItems[player.uniqueId]?.removeAll(spawned.toSet())
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

    private fun triggerPanicChatBurst(player: Player, amount: Int) {
        val now = System.currentTimeMillis()
        val last = panicChatCooldowns[player.uniqueId] ?: 0L
        if (now - last < 3500L) return
        panicChatCooldowns[player.uniqueId] = now

        val herobrineName = plugin.config.getString("hallucination.herobrine-name", "Herobrine") ?: "Herobrine"
        for (i in 0 until amount.coerceIn(1, 8)) {
            plugin.scheduler.runPlayerLater(player, ((i + 1) * Random.nextLong(4L, 9L))) {
                if (!player.isOnline) return@runPlayerLater
                val sender = if (Random.nextBoolean()) herobrineName else "???"
                val msg = panicMessages[Random.nextInt(panicMessages.size)]
                val formatted = "\u00a78<$sender> \u00a74\u00a7o$msg"
                player.sendMessage(LegacyComponentSerializer.legacySection().deserialize(formatted))
            }
        }
    }

    private fun triggerAmbientSound(player: Player) {
        val loc = player.location.clone().add(
            Random.nextDouble(-15.0, 15.0),
            Random.nextDouble(-5.0, 5.0),
            Random.nextDouble(-15.0, 15.0)
        )
        val sound = creepySounds[Random.nextInt(creepySounds.size)]
        player.playSound(loc, sound, 0.5f, Random.nextFloat() * 0.5f + 0.5f)
        if (Random.nextDouble() < 0.35) {
            playScareCombo(player, "ambient")
        }
    }

    private fun playScareCombo(player: Player, type: String) {
        if (!plugin.config.getBoolean("hallucination.extra-scare-sounds-enabled", true)) return

        when (type) {
            "ambient" -> {
                playNamedSound(player, "ENTITY_WITCH_AMBIENT", 0.45f, 0.75f)
                playNamedSound(player, "ENTITY_GHAST_AMBIENT", 0.35f, 0.55f)
            }
            "stalker-spawn" -> {
                playNamedSound(player, "ENTITY_WITCH_AMBIENT", 0.9f, 0.55f)
                playNamedSound(player, "ENTITY_ENDERMAN_STARE", 0.8f, 0.45f)
                playNamedSound(player, "BLOCK_SCULK_SENSOR_CLICKING", 0.8f, 0.7f)
            }
            "jumpscare" -> {
                playNamedSound(player, "ENTITY_WITCH_CELEBRATE", 1.0f, 0.55f)
                playNamedSound(player, "ENTITY_GHAST_SCREAM", 0.9f, 0.7f)
                playNamedSound(player, "ENTITY_VEX_CHARGE", 0.7f, 0.55f)
            }
            "drag" -> {
                playNamedSound(player, "ENTITY_WITCH_AMBIENT", 0.95f, 0.45f)
                playNamedSound(player, "BLOCK_CHAIN_PLACE", 1.0f, 0.45f)
                playNamedSound(player, "BLOCK_GRAVEL_BREAK", 0.8f, 0.55f)
            }
            "lift" -> {
                playNamedSound(player, "ENTITY_GHAST_WARN", 0.9f, 0.55f)
                playNamedSound(player, "ENTITY_ILLUSIONER_PREPARE_BLINDNESS", 0.7f, 0.7f)
                playNamedSound(player, "ENTITY_WITCH_CELEBRATE", 0.7f, 0.65f)
            }
            "throw" -> {
                playNamedSound(player, "ENTITY_GHAST_SHOOT", 0.8f, 0.55f)
                playNamedSound(player, "ENTITY_PLAYER_HURT", 0.55f, 0.7f)
            }
            "crawl" -> {
                playNamedSound(player, "ENTITY_SPIDER_AMBIENT", 0.9f, 0.4f)
                playNamedSound(player, "ENTITY_WITCH_AMBIENT", 0.75f, 0.5f)
                playNamedSound(player, "BLOCK_SCULK_CATALYST_BLOOM", 0.8f, 0.55f)
            }
            "hit" -> {
                playNamedSound(player, "ENTITY_WITCH_CELEBRATE", 0.8f, 0.6f)
                playNamedSound(player, "ENTITY_ZOMBIE_ATTACK_IRON_DOOR", 0.85f, 0.5f)
            }
            "cross" -> {
                playNamedSound(player, "ENTITY_WITCH_AMBIENT", 0.8f, 0.4f)
                playNamedSound(player, "ENTITY_EVOKER_PREPARE_SUMMON", 0.8f, 0.65f)
                playNamedSound(player, "ENTITY_GHAST_AMBIENT", 0.45f, 0.45f)
            }
            "summon" -> {
                playNamedSound(player, "ENTITY_WITCH_CELEBRATE", 1.0f, 0.6f)
                playNamedSound(player, "ENTITY_EVOKER_CAST_SPELL", 0.9f, 0.65f)
                playNamedSound(player, "ENTITY_WITHER_SPAWN", 0.25f, 0.75f)
            }
            "redstone" -> {
                playNamedSound(player, "ENTITY_WITCH_AMBIENT", 0.9f, 0.5f)
                playNamedSound(player, "ENTITY_ELDER_GUARDIAN_CURSE", 0.8f, 0.55f)
                playNamedSound(player, "BLOCK_REDSTONE_TORCH_BURNOUT", 1.0f, 0.45f)
            }
        }
    }

    private fun playNamedSound(player: Player, soundName: String, volume: Float, pitch: Float) {
        runCatching {
            val sound = Sound.valueOf(soundName)
            player.playSound(player.location, sound, volume, pitch)
        }
    }

    private fun showFakeBlocks(player: Player, blocks: List<Pair<Location, Material>>, durationTicks: Long) {
        if (blocks.isEmpty()) return

        for ((location, material) in blocks) {
            player.sendBlockChange(location, material.createBlockData())
        }

        plugin.scheduler.runPlayerLater(player, durationTicks) {
            if (!player.isOnline) return@runPlayerLater
            for ((location, _) in blocks) {
                val realBlock = location.world.getBlockAt(location)
                player.sendBlockChange(location, realBlock.blockData)
            }
        }
    }

    private fun getScareBaseLocation(player: Player, distance: Double): Location {
        val origin = player.location.clone()
        val angle = if (Random.nextBoolean()) {
            Math.atan2(origin.direction.z, origin.direction.x)
        } else {
            Random.nextDouble() * Math.PI * 2.0
        }
        val x = Math.cos(angle) * distance
        val z = Math.sin(angle) * distance
        return origin.add(x, -1.0, z).block.location
    }

    private fun nameOfHerobrine(): String {
        return plugin.config.getString("hallucination.herobrine-name", "Herobrine") ?: "Herobrine"
    }

    private fun showSanityActionBar(player: Player, sanity: Double) {
        if (!plugin.config.getBoolean("settings.actionbar-enabled", true)) return

        val max = plugin.sanityManager.getMaxSanity()
        val percent = if (max <= 0.0) 0.0 else ((sanity / max) * 100.0).coerceIn(0.0, 100.0)
        val format = plugin.config.getString("settings.actionbar-format")
            ?: "&8[&4Sanity&8] &c%percent%% &7- &f%status%"
        val message = format
            .replace("%sanity%", String.format("%.1f", sanity))
            .replace("%max%", String.format("%.1f", max))
            .replace("%percent%", String.format("%.0f", percent))
            .replace("%status%", getStatus(sanity))
        player.sendActionBar(LegacyComponentSerializer.legacyAmpersand().deserialize(message))
    }

    private fun getStatus(sanity: Double): String {
        return when {
            sanity <= 0.0 -> "INSANE"
            sanity < 25.0 -> "PANIC"
            sanity < 50.0 -> "UNSTABLE"
            sanity < 80.0 -> "UNEASY"
            else -> "STABLE"
        }
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

    private fun removeEntityNow(entity: Entity) {
        runCatching {
            if (entity.isValid) {
                entity.remove()
            }
        }
    }
}
