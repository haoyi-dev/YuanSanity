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
import org.bukkit.util.Vector
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
    private val lastKnownLocations = mutableMapOf<UUID, Location>()
    private val lastKnownYaws = mutableMapOf<UUID, Float>()
    private val nightmares = mutableMapOf<UUID, MutableList<Phantom>>()
    private val doppelgangers = mutableMapOf<UUID, MutableList<ArmorStand>>()
    private val herobrines = mutableMapOf<UUID, Zombie>()
    private val herobrineJumpscares = mutableMapOf<UUID, MutableList<Zombie>>()
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

    private data class PlayerMotion(
        val movedSquared: Double,
        val yawDelta: Float
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
        lastKnownLocations.remove(player.uniqueId)
        lastKnownYaws.remove(player.uniqueId)
        removeEntities(nightmares.remove(player.uniqueId).orEmpty())
        removeEntities(doppelgangers.remove(player.uniqueId).orEmpty())
        herobrines.remove(player.uniqueId)?.let { removeEntity(it) }
        removeEntities(herobrineJumpscares.remove(player.uniqueId).orEmpty())
        removeEntities(ritualItems.remove(player.uniqueId).orEmpty())
    }

    fun cleanupAll() {
        nightmareCooldowns.clear()
        herobrineCooldowns.clear()
        occultCooldowns.clear()
        herobrineActionCooldowns.clear()
        panicChatCooldowns.clear()
        lastKnownLocations.clear()
        lastKnownYaws.clear()
        nightmares.values.flatten().forEach { removeEntity(it) }
        doppelgangers.values.flatten().forEach { removeEntity(it) }
        herobrines.values.forEach { removeEntity(it) }
        herobrineJumpscares.values.flatten().forEach { removeEntity(it) }
        ritualItems.values.flatten().forEach { removeEntity(it) }
        nightmares.clear()
        doppelgangers.clear()
        herobrines.clear()
        herobrineJumpscares.clear()
        ritualItems.clear()
    }

    fun cleanupAllOnDisable() {
        nightmareCooldowns.clear()
        herobrineCooldowns.clear()
        occultCooldowns.clear()
        herobrineActionCooldowns.clear()
        panicChatCooldowns.clear()
        lastKnownLocations.clear()
        lastKnownYaws.clear()
        nightmares.values.flatten().forEach { removeEntityNow(it) }
        doppelgangers.values.flatten().forEach { removeEntityNow(it) }
        herobrines.values.forEach { removeEntityNow(it) }
        herobrineJumpscares.values.flatten().forEach { removeEntityNow(it) }
        ritualItems.values.flatten().forEach { removeEntityNow(it) }
        nightmares.clear()
        doppelgangers.clear()
        herobrines.clear()
        herobrineJumpscares.clear()
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
            val motion = capturePlayerMotion(player)
            if (plugin.config.getBoolean("hallucination.herobrine-director-enabled", true)) {
                runDirectorScare(player, herobrine, motion)
            } else if (plugin.config.getBoolean("hallucination.herobrine-heavy-scares-enabled", true)) {
                runRandomHeavyScare(player, herobrine)
            } else {
                when (Random.nextInt(4)) {
                    0 -> dragPlayerLeg(player, herobrine.location)
                    1 -> liftAndThrowPlayer(player)
                    2 -> crawlScare(player)
                    else -> handleHerobrineDamage(player, herobrine)
                }
            }
        } else if (distanceSquared > 900.0) {
            herobrines.remove(player.uniqueId)?.let { removeEntity(it) }
        } else if (plugin.config.getBoolean("hallucination.herobrine-director-enabled", true)) {
            val motion = capturePlayerMotion(player)
            if (motion.yawDelta > 110f && Random.nextBoolean()) {
                neckBreathScare(player)
            } else if (motion.movedSquared < 1.0 && Random.nextDouble() < 0.45) {
                stalkingFootstepsScare(player)
            }
        }
    }

    private fun capturePlayerMotion(player: Player): PlayerMotion {
        val id = player.uniqueId
        val current = player.location.clone()
        val previous = lastKnownLocations.put(id, current)
        val previousYaw = lastKnownYaws.put(id, current.yaw)
        val movedSquared = if (previous != null && previous.world == current.world) {
            previous.distanceSquared(current)
        } else {
            0.0
        }
        val yawDelta = if (previousYaw != null) {
            angleDistance(previousYaw, current.yaw)
        } else {
            0f
        }
        return PlayerMotion(movedSquared, yawDelta)
    }

    private fun angleDistance(a: Float, b: Float): Float {
        var diff = ((b - a + 540f) % 360f) - 180f
        if (diff < -180f) diff += 360f
        return kotlin.math.abs(diff)
    }

    private fun runDirectorScare(player: Player, herobrine: Entity, motion: PlayerMotion) {
        if (!plugin.config.getBoolean("hallucination.psychological-scares-enabled", true)) {
            runRandomHeavyScare(player, herobrine)
            return
        }

        when {
            motion.movedSquared > 49.0 -> {
                when (Random.nextInt(4)) {
                    0 -> dragPlayerLeg(player, herobrine.location)
                    1 -> yankPlayerBack(player, herobrine.location)
                    2 -> fakeTripScare(player)
                    else -> fakeDisconnectScare(player)
                }
            }
            motion.movedSquared < 0.35 -> {
                when (Random.nextInt(5)) {
                    0 -> neckBreathScare(player)
                    1 -> fakeDeathScreenScare(player)
                    2 -> stalkingFootstepsScare(player)
                    3 -> fakeTabCorruptionScare(player)
                    else -> sanityLieScare(player)
                }
            }
            motion.yawDelta > 95f -> {
                when (Random.nextInt(5)) {
                    0 -> mirrorNameScare(player)
                    1 -> faceFlashScare(player)
                    2 -> falseMemoryPathScare(player)
                    3 -> fakePossessionChat(player)
                    else -> cursedHotbarScare(player)
                }
            }
            player.inventory.itemInMainHand.type != Material.AIR && Random.nextBoolean() -> cursedHotbarScare(player)
            else -> {
                when (Random.nextInt(18)) {
                    0 -> dragPlayerLeg(player, herobrine.location)
                    1 -> liftAndThrowPlayer(player)
                    2 -> crawlScare(player)
                    3 -> yankPlayerBack(player, herobrine.location)
                    4 -> slamPlayerDown(player)
                    5 -> fakeCageScare(player)
                    6 -> ritualPullScare(player)
                    7 -> neckBreathScare(player)
                    8 -> voidDropScare(player)
                    9 -> fakeDeathScreenScare(player)
                    10 -> fakePossessionChat(player)
                    11 -> cursedHotbarScare(player)
                    12 -> falseMemoryPathScare(player)
                    13 -> fakeTabCorruptionScare(player)
                    14 -> stalkingFootstepsScare(player)
                    15 -> fakeDisconnectScare(player)
                    16 -> mirrorNameScare(player)
                    else -> handleHerobrineDamage(player, herobrine)
                }
            }
        }
    }

    private fun runRandomHeavyScare(player: Player, herobrine: Entity) {
        when (Random.nextInt(10)) {
            0 -> dragPlayerLeg(player, herobrine.location)
            1 -> liftAndThrowPlayer(player)
            2 -> crawlScare(player)
            3 -> yankPlayerBack(player, herobrine.location)
            4 -> slamPlayerDown(player)
            5 -> fakeCageScare(player)
            6 -> ritualPullScare(player)
            7 -> neckBreathScare(player)
            8 -> voidDropScare(player)
            else -> handleHerobrineDamage(player, herobrine)
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

    private fun yankPlayerBack(player: Player, source: Location) {
        val pull = source.toVector().subtract(player.location.toVector()).normalize()
        pull.y = 0.05
        player.velocity = pull.multiply(plugin.config.getDouble("hallucination.herobrine-pull-power", 1.25))
        player.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 55, 0, false, false, true))
        player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 50, 2, false, false, true))
        player.sendTitle("\u00a74${nameOfHerobrine()}", "\u00a7cbi keo nguoc lai", 0, 25, 8)
        playScareCombo(player, "yank")
        triggerPanicChatBurst(player, 4)
    }

    private fun slamPlayerDown(player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.LEVITATION, 12, 1, false, false, true))
        player.addPotionEffect(PotionEffect(PotionEffectType.SLOW_FALLING, 80, 0, false, false, true))
        player.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 70, 0, false, false, true))
        player.sendTitle("\u00a78...", "\u00a74dap xuong", 0, 18, 8)
        playScareCombo(player, "slam")

        plugin.scheduler.runPlayerLater(player, 14L) {
            if (!player.isOnline) return@runPlayerLater
            val side = player.location.direction.clone().crossProduct(Vector(0.0, 1.0, 0.0)).normalize()
            player.velocity = side.multiply(Random.nextDouble(0.4, 0.9)).setY(-1.15)
            playNamedSound(player, "ENTITY_GENERIC_EXPLODE", 0.45f, 0.8f)
            triggerPanicChatBurst(player, 3)
        }
    }

    private fun fakeCageScare(player: Player) {
        val base = player.location.block.location
        val blocks = mutableListOf<Pair<Location, Material>>()
        val offsets = listOf(
            1 to 0, -1 to 0, 0 to 1, 0 to -1,
            1 to 1, 1 to -1, -1 to 1, -1 to -1
        )

        for ((x, z) in offsets) {
            blocks.add(base.clone().add(x.toDouble(), 0.0, z.toDouble()) to Material.IRON_BARS)
            blocks.add(base.clone().add(x.toDouble(), 1.0, z.toDouble()) to Material.IRON_BARS)
        }
        blocks.add(base.clone().add(0.0, 2.0, 0.0) to Material.CHAIN)
        blocks.add(base.clone().add(0.0, -1.0, 0.0) to Material.SOUL_SAND)
        blocks.add(base.clone().add(0.0, 0.0, 0.0) to Material.COBWEB)

        showFakeBlocks(player, blocks, plugin.config.getLong("hallucination.herobrine-fake-cage-duration", 70L))
        player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 55, 3, false, false, true))
        player.sendTitle("\u00a74${nameOfHerobrine()}", "\u00a7cbi nhot roi", 0, 25, 8)
        playScareCombo(player, "cage")
        triggerPanicChatBurst(player, 5)
    }

    private fun ritualPullScare(player: Player) {
        val center = getScareBaseLocation(player, Random.nextDouble(3.0, 5.0))
        val blocks = mutableListOf<Pair<Location, Material>>()
        val ring = listOf(
            -2 to 0, -1 to -1, 0 to -2, 1 to -1, 2 to 0,
            1 to 1, 0 to 2, -1 to 1
        )

        for ((x, z) in ring) {
            blocks.add(center.clone().add(x.toDouble(), 0.0, z.toDouble()) to Material.REDSTONE_BLOCK)
        }
        blocks.add(center.clone() to Material.CRYING_OBSIDIAN)
        blocks.add(center.clone().add(0.0, 1.0, 0.0) to Material.SOUL_LANTERN)
        blocks.add(center.clone().add(1.0, 1.0, 1.0) to Material.SKELETON_SKULL)
        blocks.add(center.clone().add(-1.0, 1.0, -1.0) to Material.SKELETON_SKULL)

        showFakeBlocks(player, blocks, plugin.config.getLong("hallucination.occult-scene-duration", 120L))
        spawnRitualItems(player, center.clone().add(0.0, 1.0, 0.0))
        val pull = center.toVector().subtract(player.location.toVector()).normalize()
        pull.y = 0.08
        player.velocity = pull.multiply(plugin.config.getDouble("hallucination.herobrine-pull-power", 1.25))
        player.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 80, 0, false, false, true))
        player.sendTitle("\u00a74${nameOfHerobrine()}", "\u00a7cnghi le can ban", 0, 30, 8)
        plugin.protocolScareEngine?.triggerOccultPacketBurst(player, center)
        playScareCombo(player, "ritual-pull")
        triggerPanicChatBurst(player, 6)
    }

    private fun neckBreathScare(player: Player) {
        val behind = player.location.clone().subtract(player.location.direction.normalize().multiply(1.2))
        behind.y = player.location.y + 0.6
        player.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 18, 0, false, false, true))
        player.sendTitle("\u00a78suyt", "\u00a74sau gay ban", 0, 18, 8)
        player.playSound(behind, Sound.ENTITY_WITCH_AMBIENT, 1.0f, 0.42f)
        player.playSound(behind, Sound.ENTITY_GHAST_AMBIENT, 0.65f, 0.5f)
        plugin.protocolScareEngine?.triggerOccultPacketBurst(player, behind)
        playScareCombo(player, "neck")
        triggerPanicChatBurst(player, 4)
    }

    private fun voidDropScare(player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 90, 0, false, false, true))
        player.addPotionEffect(PotionEffect(PotionEffectType.SLOW_FALLING, 120, 0, false, false, true))
        player.addPotionEffect(PotionEffect(PotionEffectType.LEVITATION, 8, 0, false, false, true))
        player.sendTitle("\u00a70", "\u00a74roi xuong", 0, 20, 8)
        playScareCombo(player, "void")

        plugin.scheduler.runPlayerLater(player, 10L) {
            if (!player.isOnline) return@runPlayerLater
            player.velocity = player.location.direction.multiply(-0.25).setY(-1.35)
            plugin.protocolScareEngine?.triggerHerobrinePacketBurst(player, player.location)
            triggerPanicChatBurst(player, 5)
        }
    }

    private fun fakeDeathScreenScare(player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 35, 0, false, false, true))
        player.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 65, 0, false, false, true))
        player.sendTitle("\u00a74YOU DIED", "\u00a78${nameOfHerobrine()} was watching", 0, 28, 4)
        playScareCombo(player, "fake-death")
        triggerPanicChatBurst(player, 5)

        plugin.scheduler.runPlayerLater(player, 34L) {
            if (!player.isOnline) return@runPlayerLater
            player.sendTitle("\u00a78not yet", "\u00a74he wants you awake", 0, 25, 10)
            playScareCombo(player, "not-yet")
        }
    }

    private fun fakePossessionChat(player: Player) {
        val messages = listOf(
            "I can see my body",
            "he is using my hands",
            "why am I still moving",
            "do not let me turn around",
            "this is not me",
            "I opened the door for him"
        )
        val formatted = "\u00a77<${player.name}> \u00a74\u00a7o${messages[Random.nextInt(messages.size)]}"
        player.sendMessage(LegacyComponentSerializer.legacySection().deserialize(formatted))
        player.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 35, 0, false, false, true))
        playScareCombo(player, "possession")
    }

    private fun cursedHotbarScare(player: Player) {
        val cursed = listOf(
            createNamedFakeItem(Material.PLAYER_HEAD, "\u00a74LOOK UP"),
            createNamedFakeItem(Material.REDSTONE, "\u00a74HEARTBEAT"),
            createNamedFakeItem(Material.BONE, "\u00a78DO NOT RUN"),
            createNamedFakeItem(Material.TOTEM_OF_UNDYING, "\u00a74NOT YOURS"),
            createNamedFakeItem(Material.WRITABLE_BOOK, "\u00a78I WROTE THIS"),
            createNamedFakeItem(Material.CLOCK, "\u00a740:00"),
            createNamedFakeItem(Material.COMPASS, "\u00a74BEHIND"),
            createNamedFakeItem(Material.SOUL_LANTERN, "\u00a78LIGHT LIES"),
            createNamedFakeItem(Material.NETHER_STAR, "\u00a74SUMMON")
        )

        var changed = false
        for (slot in 0 until 9) {
            if (plugin.packetInjector.sendFakeContainerSlot(player, 36 + slot, cursed[slot])) {
                changed = true
            }
        }
        if (!changed) return

        player.sendTitle("\u00a74${nameOfHerobrine()}", "\u00a78check your hand", 0, 20, 8)
        playScareCombo(player, "hotbar-curse")
        plugin.scheduler.runPlayerLater(player, 60L) {
            if (player.isOnline) {
                player.updateInventory()
            }
        }
    }

    private fun createNamedFakeItem(material: Material, name: String): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta
        if (meta != null) {
            meta.setDisplayName(name)
            meta.setLore(listOf("\u00a78This was not here before"))
            item.itemMeta = meta
        }
        return item
    }

    private fun falseMemoryPathScare(player: Player) {
        val forward = player.location.direction.clone().normalize()
        val base = player.location.clone().add(forward.multiply(4.0)).block.location
        val blocks = mutableListOf<Pair<Location, Material>>()

        for (x in -1..1) {
            for (y in 0..2) {
                blocks.add(base.clone().add(x.toDouble(), y.toDouble(), 0.0) to Material.BLACKSTONE)
            }
        }
        blocks.add(base.clone().add(0.0, 1.0, 0.0) to Material.CRYING_OBSIDIAN)
        blocks.add(base.clone().add(0.0, 2.0, 0.0) to Material.REDSTONE_BLOCK)
        blocks.add(base.clone().add(-1.0, 0.0, 0.0) to Material.SOUL_SAND)
        blocks.add(base.clone().add(1.0, 0.0, 0.0) to Material.SOUL_SAND)

        showFakeBlocks(player, blocks, 75L)
        player.sendTitle("\u00a78where was the path", "\u00a74it moved", 0, 28, 8)
        playScareCombo(player, "false-memory")
        plugin.protocolScareEngine?.triggerOccultPacketBurst(player, base)
        triggerPanicChatBurst(player, 4)
    }

    private fun fakeTabCorruptionScare(player: Player) {
        val header = "\u00a74${nameOfHerobrine()} \u00a78is online"
        val footer = "\u00a70${player.name} 0ms | ${nameOfHerobrine()} 0ms | ${player.name} 0ms"
        player.setPlayerListHeaderFooter(header, footer)
        player.sendMessage(LegacyComponentSerializer.legacySection().deserialize("\u00a78[Server] \u00a74${nameOfHerobrine()} joined the game"))
        playScareCombo(player, "tab")

        plugin.scheduler.runPlayerLater(player, 80L) {
            if (!player.isOnline) return@runPlayerLater
            player.setPlayerListHeaderFooter("", "")
            player.sendMessage(LegacyComponentSerializer.legacySection().deserialize("\u00a78[Server] \u00a74${nameOfHerobrine()} left the game"))
        }
    }

    private fun stalkingFootstepsScare(player: Player) {
        val origin = player.location.clone()
        player.sendTitle("", "\u00a78listen", 0, 15, 5)
        for (i in 0 until 6) {
            plugin.scheduler.runPlayerLater(player, (i + 1) * 7L) {
                if (!player.isOnline) return@runPlayerLater
                val angle = (Math.PI * 2.0 / 6.0) * i + Random.nextDouble(-0.25, 0.25)
                val distance = 2.0 + i * 0.35
                val soundLoc = origin.clone().add(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance)
                playNamedSoundAt(player, soundLoc, "ENTITY_ZOMBIE_STEP", 0.8f, 0.55f)
                playNamedSoundAt(player, soundLoc, "BLOCK_GRAVEL_STEP", 0.65f, 0.45f)
                if (i == 5) {
                    playNamedSoundAt(player, player.location, "ENTITY_WITCH_AMBIENT", 1.0f, 0.42f)
                    player.sendTitle("\u00a74${nameOfHerobrine()}", "\u00a78too close", 0, 18, 8)
                }
            }
        }
    }

    private fun fakeDisconnectScare(player: Player) {
        player.sendTitle("\u00a74Connection Lost", "\u00a7cInternal Exception: Connection reset", 0, 32, 5)
        player.sendMessage(LegacyComponentSerializer.legacySection().deserialize("\u00a74Internal Exception: java.net.SocketException: Connection reset"))
        player.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 32, 0, false, false, true))
        playScareCombo(player, "fake-disconnect")

        plugin.scheduler.runPlayerLater(player, 38L) {
            if (!player.isOnline) return@runPlayerLater
            player.sendTitle("\u00a78still here", "\u00a74${nameOfHerobrine()} kept the connection open", 0, 28, 8)
            playScareCombo(player, "not-yet")
        }
    }

    private fun mirrorNameScare(player: Player) {
        val spawnLoc = player.location.clone().add(player.location.direction.normalize().multiply(2.4))
        spawnLoc.y = player.location.y
        spawnLoc.setDirection(player.location.toVector().subtract(spawnLoc.toVector()))

        plugin.scheduler.runRegion(spawnLoc) {
            if (!player.isOnline) return@runRegion

            val mirror = spawnLoc.world.spawnEntity(spawnLoc, EntityType.ZOMBIE) as Zombie
            mirror.customName(Component.text("\u00a77${player.name}"))
            mirror.isCustomNameVisible = true
            mirror.setAdult()
            mirror.setAI(false)
            mirror.isSilent = true
            mirror.isPersistent = false
            mirror.removeWhenFarAway = true
            mirror.isVisibleByDefault = false
            mirror.persistentDataContainer.set(herobrineKey, PersistentDataType.BYTE, 1.toByte())
            mirror.equipment.helmet = player.inventory.helmet?.clone() ?: ItemStack(Material.PLAYER_HEAD)
            mirror.equipment.setItemInMainHand(player.inventory.itemInMainHand.clone())
            player.showEntity(plugin, mirror)
            herobrineJumpscares.computeIfAbsent(player.uniqueId) { mutableListOf() }.add(mirror)
            playScareCombo(player, "mirror")

            plugin.scheduler.runEntityLater(mirror, 28L) {
                if (!mirror.isValid || !player.isOnline) return@runEntityLater
                mirror.customName(Component.text("\u00a74${nameOfHerobrine()}"))
                player.sendTitle("\u00a74${nameOfHerobrine()}", "\u00a78that was never you", 0, 24, 8)
                plugin.protocolScareEngine?.triggerHerobrinePacketBurst(player, mirror.location)
                playScareCombo(player, "jumpscare")
            }

            plugin.scheduler.runEntityLater(mirror, 95L) {
                if (mirror.isValid) {
                    herobrineJumpscares[player.uniqueId]?.removeIf { it.uniqueId == mirror.uniqueId }
                    removeEntity(mirror)
                }
            }
        }
    }

    private fun faceFlashScare(player: Player) {
        val loc = player.location.clone().add(player.location.direction.normalize().multiply(1.15))
        loc.y = player.location.y
        loc.setDirection(player.location.toVector().subtract(loc.toVector()))

        plugin.scheduler.runRegion(loc) {
            if (!player.isOnline) return@runRegion

            val face = loc.world.spawnEntity(loc, EntityType.ZOMBIE) as Zombie
            face.customName(Component.text("\u00a74${nameOfHerobrine()}"))
            face.isCustomNameVisible = true
            face.setAdult()
            face.setAI(false)
            face.isSilent = true
            face.isPersistent = false
            face.removeWhenFarAway = true
            face.isVisibleByDefault = false
            face.persistentDataContainer.set(herobrineKey, PersistentDataType.BYTE, 1.toByte())
            face.equipment.helmet = ItemStack(Material.PLAYER_HEAD)
            player.showEntity(plugin, face)
            herobrineJumpscares.computeIfAbsent(player.uniqueId) { mutableListOf() }.add(face)

            player.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 12, 0, false, false, true))
            playScareCombo(player, "face-flash")
            plugin.protocolScareEngine?.triggerHerobrinePacketBurst(player, loc)

            plugin.scheduler.runEntityLater(face, 12L) {
                if (face.isValid) {
                    herobrineJumpscares[player.uniqueId]?.removeIf { it.uniqueId == face.uniqueId }
                    removeEntity(face)
                }
            }
        }
    }

    private fun fakeTripScare(player: Player) {
        player.velocity = player.location.direction.multiply(0.2).setY(0.05)
        player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 45, 4, false, false, true))
        player.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 15, 0, false, false, true))
        player.sendTitle("\u00a78your legs", "\u00a74are not yours", 0, 22, 8)
        playScareCombo(player, "trip")
        triggerPanicChatBurst(player, 3)
    }

    private fun sanityLieScare(player: Player) {
        val messages = listOf(
            "\u00a78[\u00a74Sanity\u00a78] \u00a7a100% \u00a77- STABLE",
            "\u00a78[\u00a74Sanity\u00a78] \u00a7e73% \u00a77- SAFE",
            "\u00a78[\u00a74Sanity\u00a78] \u00a740% \u00a77- HE LIED"
        )
        for (i in messages.indices) {
            plugin.scheduler.runPlayerLater(player, (i + 1) * 15L) {
                if (!player.isOnline) return@runPlayerLater
                player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(messages[i]))
                if (i == messages.lastIndex) {
                    playScareCombo(player, "sanity-lie")
                }
            }
        }
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

            val herobrine = loc.world.spawnEntity(loc, EntityType.ZOMBIE) as Zombie
            herobrine.customName(Component.text("\u00a74$name"))
            herobrine.isCustomNameVisible = true
            herobrine.setAdult()
            herobrine.setAI(false)
            herobrine.isSilent = true
            herobrine.isPersistent = false
            herobrine.removeWhenFarAway = true
            herobrine.isVisibleByDefault = false
            herobrine.persistentDataContainer.set(herobrineKey, PersistentDataType.BYTE, 1.toByte())
            herobrine.equipment.setItemInMainHand(ItemStack(Material.NETHERITE_SWORD))
            herobrine.equipment.helmet = ItemStack(Material.PLAYER_HEAD)
            herobrine.equipment.chestplate = ItemStack(Material.NETHERITE_CHESTPLATE)
            herobrine.equipment.leggings = ItemStack(Material.NETHERITE_LEGGINGS)
            herobrine.equipment.boots = ItemStack(Material.NETHERITE_BOOTS)
            player.showEntity(plugin, herobrine)
            herobrineJumpscares.computeIfAbsent(player.uniqueId) { mutableListOf() }.add(herobrine)

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

            plugin.scheduler.runEntityLater(herobrine, duration) {
                if (herobrine.isValid) {
                    herobrineJumpscares[player.uniqueId]?.removeIf { it.uniqueId == herobrine.uniqueId }
                    removeEntity(herobrine)
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
            "yank" -> {
                playNamedSound(player, "ENTITY_WITCH_AMBIENT", 1.0f, 0.42f)
                playNamedSound(player, "BLOCK_CHAIN_PLACE", 1.0f, 0.38f)
                playNamedSound(player, "ENTITY_ENDERMAN_TELEPORT", 0.75f, 0.55f)
            }
            "slam" -> {
                playNamedSound(player, "ENTITY_IRON_GOLEM_ATTACK", 0.9f, 0.55f)
                playNamedSound(player, "ENTITY_GENERIC_EXPLODE", 0.55f, 0.8f)
                playNamedSound(player, "ENTITY_GHAST_SCREAM", 0.7f, 0.6f)
            }
            "cage" -> {
                playNamedSound(player, "BLOCK_IRON_TRAPDOOR_CLOSE", 1.0f, 0.55f)
                playNamedSound(player, "BLOCK_CHAIN_PLACE", 1.0f, 0.45f)
                playNamedSound(player, "ENTITY_WITCH_CELEBRATE", 0.9f, 0.5f)
            }
            "ritual-pull" -> {
                playNamedSound(player, "ENTITY_EVOKER_CAST_SPELL", 1.0f, 0.55f)
                playNamedSound(player, "BLOCK_BEACON_DEACTIVATE", 1.0f, 0.55f)
                playNamedSound(player, "ENTITY_WITCH_AMBIENT", 0.95f, 0.45f)
            }
            "neck" -> {
                playNamedSound(player, "ENTITY_WITCH_AMBIENT", 1.0f, 0.38f)
                playNamedSound(player, "ENTITY_GHAST_AMBIENT", 0.75f, 0.45f)
                playNamedSound(player, "BLOCK_SCULK_SENSOR_CLICKING", 1.0f, 0.6f)
            }
            "void" -> {
                playNamedSound(player, "ENTITY_ENDERMAN_TELEPORT", 1.0f, 0.45f)
                playNamedSound(player, "ENTITY_GHAST_WARN", 0.9f, 0.5f)
                playNamedSound(player, "ENTITY_ELDER_GUARDIAN_CURSE", 0.75f, 0.55f)
            }
            "fake-death" -> {
                playNamedSound(player, "ENTITY_WITHER_DEATH", 0.35f, 0.65f)
                playNamedSound(player, "ENTITY_GHAST_SCREAM", 0.9f, 0.55f)
                playNamedSound(player, "BLOCK_RESPAWN_ANCHOR_DEPLETE", 0.9f, 0.55f)
            }
            "not-yet" -> {
                playNamedSound(player, "ENTITY_WITCH_AMBIENT", 0.9f, 0.42f)
                playNamedSound(player, "BLOCK_SCULK_SENSOR_CLICKING", 1.0f, 0.55f)
            }
            "possession" -> {
                playNamedSound(player, "ENTITY_ILLUSIONER_PREPARE_MIRROR", 0.75f, 0.6f)
                playNamedSound(player, "ENTITY_WITCH_CELEBRATE", 0.85f, 0.52f)
            }
            "hotbar-curse" -> {
                playNamedSound(player, "ITEM_BOOK_PAGE_TURN", 0.9f, 0.45f)
                playNamedSound(player, "ENTITY_EVOKER_CAST_SPELL", 0.8f, 0.6f)
                playNamedSound(player, "ENTITY_WITCH_AMBIENT", 0.7f, 0.45f)
            }
            "false-memory" -> {
                playNamedSound(player, "BLOCK_STONE_PLACE", 1.0f, 0.45f)
                playNamedSound(player, "BLOCK_GRINDSTONE_USE", 0.7f, 0.5f)
                playNamedSound(player, "ENTITY_ENDERMAN_TELEPORT", 0.65f, 0.6f)
            }
            "tab" -> {
                playNamedSound(player, "UI_BUTTON_CLICK", 0.7f, 0.5f)
                playNamedSound(player, "ENTITY_ENDERMAN_STARE", 0.8f, 0.45f)
            }
            "fake-disconnect" -> {
                playNamedSound(player, "BLOCK_BEACON_DEACTIVATE", 1.0f, 0.5f)
                playNamedSound(player, "ENTITY_ELDER_GUARDIAN_CURSE", 0.6f, 0.55f)
            }
            "mirror" -> {
                playNamedSound(player, "ENTITY_ILLUSIONER_PREPARE_MIRROR", 0.9f, 0.55f)
                playNamedSound(player, "ENTITY_ENDERMAN_STARE", 0.7f, 0.55f)
            }
            "face-flash" -> {
                playNamedSound(player, "ENTITY_ENDERMAN_SCREAM", 0.85f, 0.6f)
                playNamedSound(player, "BLOCK_SCULK_SHRIEKER_SHRIEK", 0.85f, 0.65f)
            }
            "trip" -> {
                playNamedSound(player, "BLOCK_GRAVEL_BREAK", 1.0f, 0.45f)
                playNamedSound(player, "ENTITY_PLAYER_ATTACK_SWEEP", 0.8f, 0.6f)
            }
            "sanity-lie" -> {
                playNamedSound(player, "ENTITY_WITCH_AMBIENT", 0.9f, 0.4f)
                playNamedSound(player, "ENTITY_ENDERMAN_TELEPORT", 0.65f, 0.45f)
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

    private fun playNamedSoundAt(player: Player, location: Location, soundName: String, volume: Float, pitch: Float) {
        runCatching {
            val sound = Sound.valueOf(soundName)
            player.playSound(location, sound, volume, pitch)
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
