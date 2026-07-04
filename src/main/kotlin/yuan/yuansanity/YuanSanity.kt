package yuan.yuansanity

import org.bukkit.plugin.java.JavaPlugin
import yuan.yuansanity.command.SanityCommand
import yuan.yuansanity.compat.SchedulerAdapter
import yuan.yuansanity.config.LanguageManager
import yuan.yuansanity.engine.HallucinationEngine
import yuan.yuansanity.hook.SanityExpansion
import yuan.yuansanity.listener.PlayerListener
import yuan.yuansanity.manager.SanityManager
import yuan.yuansanity.nms.PacketInjector
import yuan.yuansanity.protocol.ProtocolLibScareEngine

class YuanSanity : JavaPlugin() {

    lateinit var languageManager: LanguageManager
        private set

    lateinit var sanityManager: SanityManager
        private set

    lateinit var packetInjector: PacketInjector
        private set

    lateinit var scheduler: SchedulerAdapter
        private set

    lateinit var hallucinationEngine: HallucinationEngine
        private set

    var protocolScareEngine: ProtocolLibScareEngine? = null
        private set

    var foliaSupport: Boolean = false
        private set

    private lateinit var playerListener: PlayerListener

    override fun onEnable() {
        foliaSupport = detectFolia()

        saveDefaultConfig()
        scheduler = SchedulerAdapter(this)
        languageManager = LanguageManager(this)
        sanityManager = SanityManager(this)
        hallucinationEngine = HallucinationEngine(this)
        packetInjector = PacketInjector(this)
        protocolScareEngine = if (server.pluginManager.getPlugin("ProtocolLib") != null) {
            logger.info("Da ket noi ProtocolLib - bat packet scare nang cao")
            ProtocolLibScareEngine(this)
        } else {
            null
        }

        playerListener = PlayerListener(this)
        server.pluginManager.registerEvents(playerListener, this)

        val cmd = getCommand("sanity")
        val sanityCommand = SanityCommand(this)
        cmd?.setExecutor(sanityCommand)
        cmd?.tabCompleter = sanityCommand

        if (server.pluginManager.getPlugin("PlaceholderAPI") != null) {
            SanityExpansion(this, "yuansanity").register()
            SanityExpansion(this, "sanity").register()
            logger.info("Da ket noi PlaceholderAPI")
        }

        logger.info("YuanSanity da bat - Folia: $foliaSupport")
    }

    override fun onDisable() {
        if (::playerListener.isInitialized) {
            playerListener.shutdown()
        }
        if (::hallucinationEngine.isInitialized) {
            hallucinationEngine.cleanupAllOnDisable()
        }
        server.onlinePlayers.forEach { packetInjector.eject(it) }
        logger.info("YuanSanity da tat")
    }

    private fun detectFolia(): Boolean {
        return try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }
}
