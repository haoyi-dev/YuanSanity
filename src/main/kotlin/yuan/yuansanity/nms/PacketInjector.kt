package yuan.yuansanity.nms

import io.netty.channel.Channel
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import yuan.yuansanity.YuanSanity

class PacketInjector(private val plugin: YuanSanity) {

    private val handlerName = "YuanSanity_Injector"

    fun inject(player: Player) {
        val channel = getChannel(player) ?: return
        val action = Runnable {
            try {
                val pipeline = channel.pipeline()

                if (pipeline.get(handlerName) != null) {
                    pipeline.remove(handlerName)
                }

                val handler = object : ChannelDuplexHandler() {
                    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
                        val processed = runCatching {
                            plugin.hallucinationEngine.processOutboundPacket(player, msg.javaClass.simpleName, msg)
                        }.getOrElse {
                            plugin.logger.warning("Loi xu ly outbound packet cho ${player.name}: ${it.message}")
                            msg
                        }

                        if (processed != null) {
                            super.write(ctx, processed, promise)
                        }
                    }
                }

                if (pipeline.get("packet_handler") != null) {
                    pipeline.addBefore("packet_handler", handlerName, handler)
                } else {
                    pipeline.addLast(handlerName, handler)
                }
            } catch (e: Exception) {
                plugin.logger.warning("Loi khi inject packet cho ${player.name}: ${e.message}")
            }
        }

        if (channel.eventLoop().inEventLoop()) {
            action.run()
        } else {
            channel.eventLoop().execute(action)
        }
    }

    fun eject(player: Player) {
        val channel = getChannel(player) ?: return
        val action = Runnable {
            runCatching {
                val pipeline = channel.pipeline()
                if (pipeline.get(handlerName) != null) {
                    pipeline.remove(handlerName)
                }
            }
        }

        if (channel.eventLoop().inEventLoop()) {
            action.run()
        } else {
            channel.eventLoop().execute(action)
        }
    }

    fun sendFakeContainerSlot(player: Player, protocolSlot: Int, itemStack: ItemStack): Boolean {
        return try {
            val connection = getConnection(player) ?: return false
            val packet = createSetSlotPacket(player, protocolSlot, itemStack) ?: return false
            sendPacket(connection, packet)
        } catch (e: Exception) {
            plugin.logger.fine("Khong gui duoc fake slot cho ${player.name}: ${e.message}")
            false
        }
    }

    private fun createSetSlotPacket(player: Player, protocolSlot: Int, itemStack: ItemStack): Any? {
        val craftItemStackClass = findCraftBukkitClass("inventory.CraftItemStack") ?: return null
        val asNmsCopy = craftItemStackClass.getMethod("asNMSCopy", ItemStack::class.java)
        val nmsItem = asNmsCopy.invoke(null, itemStack) ?: return null

        val packetClass = runCatching {
            Class.forName("net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket")
        }.getOrNull() ?: runCatching {
            Class.forName("net.minecraft.network.protocol.game.PacketPlayOutSetSlot")
        }.getOrNull() ?: return null

        val constructor = packetClass.constructors.firstOrNull { constructor ->
            val types = constructor.parameterTypes
            types.size == 4 &&
                types[0] == Int::class.javaPrimitiveType &&
                types[1] == Int::class.javaPrimitiveType &&
                types[2] == Int::class.javaPrimitiveType &&
                types[3].isAssignableFrom(nmsItem.javaClass)
        } ?: return null

        val containerId = 0
        val stateId = getContainerStateId(player)
        return constructor.newInstance(containerId, stateId, protocolSlot, nmsItem)
    }

    private fun getContainerStateId(player: Player): Int {
        return runCatching {
            val handle = player.javaClass.getMethod("getHandle").invoke(player)
            val menuField = handle.javaClass.fields.firstOrNull { it.name == "containerMenu" }
                ?: handle.javaClass.declaredFields.firstOrNull { it.name == "containerMenu" }
                ?: return@runCatching 0
            menuField.isAccessible = true
            val menu = menuField.get(handle) ?: return@runCatching 0
            val stateIdField = menu.javaClass.declaredFields.firstOrNull { it.name == "stateId" }
                ?: return@runCatching 0
            stateIdField.isAccessible = true
            stateIdField.getInt(menu)
        }.getOrDefault(0)
    }

    private fun sendPacket(connection: Any, packet: Any): Boolean {
        val method = connection.javaClass.methods.firstOrNull { method ->
            method.parameterCount == 1 &&
                method.parameterTypes[0].isAssignableFrom(packet.javaClass) &&
                (method.name == "send" || method.name == "a")
        } ?: return false

        method.invoke(connection, packet)
        return true
    }

    private fun getChannel(player: Player): Channel? {
        val connection = getConnection(player) ?: return null
        val networkField = findFieldByTypeSuffix(connection.javaClass, "Connection", "NetworkManager")
            ?: return null
        networkField.isAccessible = true
        val network = networkField.get(connection) ?: return null

        val channelField = network.javaClass.declaredFields.firstOrNull {
            Channel::class.java.isAssignableFrom(it.type)
        } ?: return null
        channelField.isAccessible = true

        return channelField.get(network) as? Channel
    }

    private fun getConnection(player: Player): Any? {
        return try {
            val getHandle = player.javaClass.getMethod("getHandle")
            val serverPlayer = getHandle.invoke(player)

            val connectionField = findFieldByTypeSuffix(serverPlayer.javaClass, "ServerGamePacketListenerImpl", "PlayerConnection")
                ?: return null
            connectionField.isAccessible = true
            connectionField.get(serverPlayer)
        } catch (e: Exception) {
            plugin.logger.warning("Loi khi lay connection cho ${player.name}: ${e.message}")
            null
        }
    }

    private fun findCraftBukkitClass(path: String): Class<*>? {
        runCatching { return Class.forName("org.bukkit.craftbukkit.$path") }

        val craftServerPackage = plugin.server.javaClass.`package`?.name ?: return null
        val version = craftServerPackage.substringAfterLast('.', "")
        if (version.isBlank() || version == "craftbukkit") return null

        return runCatching {
            Class.forName("org.bukkit.craftbukkit.$version.$path")
        }.getOrNull()
    }

    private fun findFieldByTypeSuffix(clazz: Class<*>, vararg suffixes: String): java.lang.reflect.Field? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            for (field in current.declaredFields) {
                for (suffix in suffixes) {
                    if (field.type.name.endsWith(suffix)) {
                        return field
                    }
                }
            }
            current = current.superclass
        }
        return null
    }
}
