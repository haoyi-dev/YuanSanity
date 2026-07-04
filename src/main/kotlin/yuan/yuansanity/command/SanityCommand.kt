package yuan.yuansanity.command

import net.kyori.adventure.text.Component
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import yuan.yuansanity.YuanSanity

class SanityCommand(private val plugin: YuanSanity) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            reply(sender, plugin.languageManager.getMessage("usage"))
            return true
        }

        when (args[0].lowercase()) {
            "get" -> handleGet(sender, args)
            "set" -> handleSet(sender, args)
            "reload" -> handleReload(sender)
            else -> reply(sender, plugin.languageManager.getMessage("usage"))
        }

        return true
    }

    private fun handleGet(sender: CommandSender, args: Array<out String>) {
        val target: Player = if (args.size >= 2) {
            plugin.server.getPlayer(args[1]) ?: run {
                reply(sender, plugin.languageManager.getMessage("player-not-found"))
                return
            }
        } else if (sender is Player) {
            sender
        } else {
            reply(sender, plugin.languageManager.getMessage("usage"))
            return
        }

        plugin.scheduler.runPlayerTask(target) {
            val sanity = plugin.sanityManager.getSanity(target)
            reply(sender,
                plugin.languageManager.getMessage("sanity-level", "sanity" to String.format("%.1f", sanity))
            )
        }
    }

    private fun handleSet(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("yuansanity.admin")) {
            reply(sender, plugin.languageManager.getMessage("no-permission"))
            return
        }

        if (args.size < 3) {
            reply(sender, plugin.languageManager.getMessage("usage"))
            return
        }

        val target = plugin.server.getPlayer(args[1]) ?: run {
            reply(sender, plugin.languageManager.getMessage("player-not-found"))
            return
        }

        val value = args[2].toDoubleOrNull() ?: run {
            reply(sender, plugin.languageManager.getMessage("usage"))
            return
        }

        plugin.scheduler.runPlayerTask(target) {
            plugin.sanityManager.setSanity(target, value)
            reply(sender,
                plugin.languageManager.getMessage(
                    "set-sanity",
                    "player" to target.name,
                    "value" to String.format("%.1f", plugin.sanityManager.getSanity(target))
                )
            )
        }
    }

    private fun handleReload(sender: CommandSender) {
        if (!sender.hasPermission("yuansanity.admin")) {
            reply(sender, plugin.languageManager.getMessage("no-permission"))
            return
        }

        plugin.reloadConfig()
        plugin.languageManager.reload()
        reply(sender, plugin.languageManager.getMessage("reload-success"))
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (args.size == 1) {
            return listOf("get", "set", "reload").filter { it.startsWith(args[0].lowercase()) }
        }

        if (args.size == 2 && (args[0].equals("get", true) || args[0].equals("set", true))) {
            return plugin.server.onlinePlayers.map { it.name }.filter { it.lowercase().startsWith(args[1].lowercase()) }
        }

        if (args.size == 3 && args[0].equals("set", true)) {
            return listOf("0", "25", "50", "75", "100")
        }

        return emptyList()
    }

    private fun reply(sender: CommandSender, message: Component) {
        if (plugin.foliaSupport && sender is Player) {
            plugin.scheduler.runPlayerTask(sender) {
                if (sender.isOnline) {
                    sender.sendMessage(message)
                }
            }
            return
        }

        sender.sendMessage(message)
    }
}
