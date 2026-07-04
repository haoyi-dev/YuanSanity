package yuan.yuansanity.compat

import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import yuan.yuansanity.YuanSanity

class SchedulerAdapter(private val plugin: YuanSanity) {

    fun runPlayerTask(player: Player, action: () -> Unit): TaskHandle {
        if (!plugin.foliaSupport) {
            val task = plugin.server.scheduler.runTask(plugin, Runnable { action() })
            return TaskHandle { task.cancel() }
        }

        val task = player.scheduler.run(plugin, { _ -> action() }, null)
        return TaskHandle { task?.cancel() }
    }

    fun runPlayerTimer(player: Player, delayTicks: Long, periodTicks: Long, action: () -> Unit): TaskHandle {
        if (!plugin.foliaSupport) {
            val task = plugin.server.scheduler.runTaskTimer(plugin, Runnable { action() }, delayTicks, periodTicks)
            return TaskHandle { task.cancel() }
        }

        val task = player.scheduler.runAtFixedRate(plugin, { scheduledTask ->
            if (!player.isOnline) {
                scheduledTask.cancel()
                return@runAtFixedRate
            }
            action()
        }, null, delayTicks, periodTicks)
        return TaskHandle { task?.cancel() }
    }

    fun runPlayerLater(player: Player, delayTicks: Long, action: () -> Unit): TaskHandle {
        if (!plugin.foliaSupport) {
            val task = plugin.server.scheduler.runTaskLater(plugin, Runnable { action() }, delayTicks)
            return TaskHandle { task.cancel() }
        }

        val task = player.scheduler.runDelayed(plugin, { _ -> action() }, null, delayTicks)
        return TaskHandle { task?.cancel() }
    }

    fun runRegion(location: Location, action: () -> Unit): TaskHandle {
        if (!plugin.foliaSupport) {
            val task = plugin.server.scheduler.runTask(plugin, Runnable { action() })
            return TaskHandle { task.cancel() }
        }

        val task = plugin.server.regionScheduler.run(plugin, location) { _ -> action() }
        return TaskHandle { task.cancel() }
    }

    fun runEntityTask(entity: Entity, action: () -> Unit): TaskHandle {
        if (!plugin.foliaSupport) {
            val task = plugin.server.scheduler.runTask(plugin, Runnable { action() })
            return TaskHandle { task.cancel() }
        }

        val task = entity.scheduler.run(plugin, { _ -> action() }, null)
        return TaskHandle { task?.cancel() }
    }

    fun runEntityLater(entity: Entity, delayTicks: Long, action: () -> Unit): TaskHandle {
        if (!plugin.foliaSupport) {
            val task = plugin.server.scheduler.runTaskLater(plugin, Runnable { action() }, delayTicks)
            return TaskHandle { task.cancel() }
        }

        val task = entity.scheduler.runDelayed(plugin, { _ -> action() }, null, delayTicks)
        return TaskHandle { task?.cancel() }
    }

    class TaskHandle(private val cancelAction: () -> Unit) {
        fun cancel() {
            runCatching { cancelAction() }
        }
    }
}
