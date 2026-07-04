package yuan.yuansanity.config

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import yuan.yuansanity.YuanSanity
import java.io.File

class LanguageManager(private val plugin: YuanSanity) {

    private lateinit var langConfig: FileConfiguration
    private var prefix: String = ""

    init {
        reload()
    }

    fun reload() {
        val langCode = plugin.config.getString("language", "vi") ?: "vi"

        val langFile = File(plugin.dataFolder, "lang/$langCode.yml")
        if (!langFile.exists()) {
            langFile.parentFile.mkdirs()
            try {
                plugin.saveResource("lang/$langCode.yml", false)
            } catch (_: Exception) {
                plugin.logger.warning("Khong tim thay file ngon ngu: lang/$langCode.yml")
                langFile.createNewFile()
            }
        }

        langConfig = YamlConfiguration.loadConfiguration(langFile)
        prefix = langConfig.getString("prefix", "&8[&4YuanSanity&8]") ?: "&8[&4YuanSanity&8]"
    }

    fun getMessage(path: String, vararg placeholders: Pair<String, String>): Component {
        val raw = langConfig.getString("messages.$path")
            ?: return Component.text("Thieu message: $path")
        var result = raw
        for ((key, value) in placeholders) {
            result = result.replace("%$key%", value)
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize("$prefix $result")
    }

    fun getRawMessage(path: String, vararg placeholders: Pair<String, String>): String {
        val raw = langConfig.getString("messages.$path") ?: return "Thieu message: $path"
        var result = raw
        for ((key, value) in placeholders) {
            result = result.replace("%$key%", value)
        }
        return "$prefix $result"
    }
}
