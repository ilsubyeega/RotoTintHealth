package win.roto.mcplugin.tinthealth

import com.github.yannicklamprecht.worldborder.api.Position
import com.github.yannicklamprecht.worldborder.api.WorldBorderAction
import com.github.yannicklamprecht.worldborder.api.WorldBorderApi
import org.bukkit.attribute.Attribute
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.*
import kotlin.math.roundToInt


class Main : JavaPlugin(), Listener {

    private lateinit var worldBorderApi: WorldBorderApi
    private var task = -1

    private var borderEnabled = false

    override fun onEnable() {

        val worldBorderApiRegisteredServiceProvider = server.servicesManager.getRegistration(WorldBorderApi::class.java)
        if (worldBorderApiRegisteredServiceProvider == null) {
            logger.info("WorldBorderAPI plugin isn't found!, Please install the plugin for using this plugin.")
            server.pluginManager.disablePlugin(this)
            return
        }
        worldBorderApi = worldBorderApiRegisteredServiceProvider.provider

        server.pluginManager.registerEvents(this, this)
        task = server.scheduler.scheduleSyncRepeatingTask(this, borderTask, 0, 1)
    }

    override fun onDisable() {
        if (task != -1) {
            server.scheduler.cancelTask(task)
            task = -1
        }
    }


    /**
     * This stores the online player's uuid and health points.
     */
    private val playerHealthList: HashMap<UUID, Double> = HashMap()

    /**
     * Removes all offline players from playerHealthList, then invokes updateBorder when player's hp changes to new hp, saves the health to playerHealthList afterwards.
     */
    private val borderTask: () -> Unit = {
        if (borderEnabled) {
            val onlinePlayers = server.onlinePlayers
            val onlinePlayersUUID = onlinePlayers.map { it.uniqueId }.toSet()

            // Remove offline players
            playerHealthList.keys.retainAll(onlinePlayersUUID)

            for (player in onlinePlayers) {
                if (!playerHealthList.contains(player.uniqueId) || playerHealthList[player.uniqueId] != player.health) {
                    updateBorder(player)
                    playerHealthList[player.uniqueId] = player.health
                }
            }
        }
    }

    private fun startBorder() {
        for (player in server.onlinePlayers)
            initBorder(player)
    }

    private fun stopBorder() {
        for (player in server.onlinePlayers)
            resetBorder(player)
    }

    /**
     *  Fired when a player joins the server or changes worlds.
     */
    private fun initBorder(player: Player) {
        val worldBorder = worldBorderApi.getWorldBorder(player)
        worldBorder.center(Position(player.location.x, player.location.z))
        worldBorder.size(Double.MAX_VALUE)
        worldBorder.send(player, WorldBorderAction.INITIALIZE)

        updateBorder(player)
    }

    /**
     * Fired when a player got new health point to update border
     */
    private fun updateBorder(player: Player) {
        val maxHp = player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value
        val percentage =
            (player.health / (maxHp ?: 20.0) * 100)

        /*
            There is a issue that the dark Vignette showing when it is out of the border warning radius.
            For fixing this, it shows the borders at close.
         */
        //val warnDistance = (80000 + (-6000 * percentage) + 600000).roundToInt()

        val warnDistance = (100000 + (-6000 * percentage) + 600000).roundToInt()
        val worldBorder = worldBorderApi.getWorldBorder(player)

        worldBorder.center(Position(player.location.x, player.location.z))
        worldBorder.warningDistanceInBlocks(warnDistance)
        worldBorder.lerp(200000.0, 200000.0, 4000)

        worldBorder.send(player, WorldBorderAction.SET_CENTER)
        worldBorder.send(player, WorldBorderAction.LERP_SIZE)
        worldBorder.send(player, WorldBorderAction.SET_WARNING_BLOCKS)
    }

    /**
     *  Changes the player's worldborder to the default world's worldborder.
     */
    private fun resetBorder(player: Player) {
        worldBorderApi.resetWorldBorderToGlobal(player)
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        initBorder(event.player)
    }

    @EventHandler
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        initBorder(event.player)
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender.isOp) {
            if (args.isEmpty()) return false

            when (args[0]) {
                "enable" -> {
                    if (!borderEnabled) {
                        sender.sendPluginMessage("Enabled RotoTintHealth Border")
                        sender.sendPluginMessage("This means that some worldborder features of other plugins may be broken.")
                        borderEnabled = true
                        startBorder()
                    } else {
                        sender.sendPluginMessage("The Border is already enabled!")
                    }
                    return true
                }
                "disable" -> {
                    if (borderEnabled) {
                        sender.sendPluginMessage("Disabled RotoTintHealth Border")
                        borderEnabled = false
                        stopBorder()
                    } else {
                        sender.sendPluginMessage("The Border is already disabled!")
                    }
                    return true
                }
                "sethp" -> {
                    if (sender is Player) {
                        if (args[1].isNotEmpty()) {
                            try {
                                val hp = args[1].toDouble()
                                sender.health = hp
                            } catch (_: Exception) {
                                sender.sendPluginMessage("${args[1]} is not double type or more than maxed value")
                            }
                        } else {
                            sender.sendPluginMessage("Usage: /tinthealth sethp [hp]")
                        }
                    } else {
                        sender.sendPluginMessage("You must run this command as player!")
                    }
                    return true
                }
            }
        }
        return false
    }
}

fun CommandSender.sendPluginMessage(text: String) {
    this.sendMessage("§e[RotoTintHealth] §7$text")
}