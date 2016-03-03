package de.myzelyam.supervanish.events;

import de.myzelyam.supervanish.SuperVanish;
import de.myzelyam.supervanish.hider.TabMgr.TabAction;
import de.myzelyam.supervanish.hooks.EssentialsHook;
import me.confuser.barapi.BarAPI;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public class JoinEvent implements EventExecutor, Listener {
    private final SuperVanish plugin;
    private final FileConfiguration settings, playerData;

    public JoinEvent(SuperVanish plugin) {
        this.plugin = plugin;
        this.settings = plugin.settings;
        this.playerData = plugin.playerData;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void execute(Listener listener, Event event) {
        try {
            if (event instanceof PlayerJoinEvent) {
                PlayerJoinEvent e = (PlayerJoinEvent) event;
                final Player p = e.getPlayer();
                final List<String> invisiblePlayers = plugin.getAllInvisiblePlayers();
                // compatibility delays
                int tabDelay = settings
                        .getInt("Configuration.CompatibilityOptions.ActionDelay.TabNameChangeDelayOnJoinInTicks");
                if (!settings.getBoolean("Configuration.CompatibilityOptions.ActionDelay.Enable"))
                    tabDelay = 0;
                int invisibilityDelay = settings
                        .getInt("Configuration.CompatibilityOptions.ActionDelay.InvisibilityPotionDelayOnJoinInTicks");
                if (!settings.getBoolean("Configuration.CompatibilityOptions.ActionDelay.Enable"))
                    invisibilityDelay = 0;
                // ghost players
                if (settings.getBoolean("Configuration.Players.EnableGhostPlayers")
                        && plugin.ghostTeam != null
                        && !plugin.ghostTeam.hasPlayer(p)) {
                    if (p.hasPermission("sv.see") || p.hasPermission("sv.use")
                            || invisiblePlayers.contains(p.getUniqueId().toString()))
                        plugin.ghostTeam.addPlayer(p);
                }
                // Join-Message
                if (settings.getBoolean(
                        "Configuration.Messages.HideNormalJoinAndLeaveMessagesWhileInvisible",
                        true)
                        && invisiblePlayers.contains(p.getUniqueId().toString())) {
                    e.setJoinMessage(null);
                }
                // vanished:
                if (invisiblePlayers.contains(p.getUniqueId().toString())) {
                    // Essentials
                    if (plugin.getServer().getPluginManager()
                            .getPlugin("Essentials") != null
                            && settings.getBoolean("Configuration.Hooks.EnableEssentialsHook")) {
                        EssentialsHook.hidePlayer(p);
                    }
                    // remember message
                    if (settings.getBoolean("Configuration.Messages.RememberInvisiblePlayersOnJoin")) {
                        p.sendMessage(plugin.convertString(
                                plugin.getMsg("RememberMessage"), p));
                    }
                    // BAR-API
                    if (plugin.getServer().getPluginManager()
                            .getPlugin("BarAPI") != null
                            && settings.getBoolean("Configuration.Messages.UseBarAPI")) {
                        displayBossBar(p);
                    }
                    // hide
                    plugin.getVisibilityAdjuster().getHider().hideToAll(p);
                    // re-add invisibility
                    if (settings.getBoolean("Configuration.Players.EnableGhostPlayers")) {
                        boolean isInvisible = false;
                        for (PotionEffect potionEffect : p.getActivePotionEffects())
                            if (potionEffect.getType() == PotionEffectType.INVISIBILITY) isInvisible = true;
                        if (!isInvisible) {
                            if (invisibilityDelay > 0) {
                                Bukkit.getServer().getScheduler()
                                        .scheduleSyncDelayedTask(plugin, new Runnable() {

                                            @Override
                                            public void run() {
                                                p.addPotionEffect(new PotionEffect(
                                                        PotionEffectType.INVISIBILITY,
                                                        Integer.MAX_VALUE, 1));
                                            }
                                        }, invisibilityDelay);
                            } else {
                                p.addPotionEffect(new PotionEffect(
                                        PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1));
                            }
                        }
                    }
                    // re-add action bar
                    if (plugin.getServer().getPluginManager()
                            .getPlugin("ProtocolLib") != null
                            && settings.getBoolean("Configuration.Messages.DisplayActionBarsToInvisiblePlayers")
                            && !SuperVanish.SERVER_IS_ONE_DOT_SEVEN) {
                        plugin.getActionBarMgr().addActionBar(p);
                    }
                    //
                }
                // not necessarily vanished:
                //
                // hide vanished players to player
                plugin.getVisibilityAdjuster().getHider().hideAllInvisibleTo(p);
                // TAB //
                if (settings.getBoolean("Configuration.Tablist.ChangeTabNames")
                        && invisiblePlayers.contains(p.getUniqueId().toString())) {
                    if (tabDelay > 0) {
                        Bukkit.getServer()
                                .getScheduler()
                                .scheduleSyncDelayedTask(plugin,
                                        new Runnable() {

                                            @Override
                                            public void run() {
                                                plugin.getTabMgr()
                                                        .adjustTabname(
                                                                p,
                                                                TabAction.SET_CUSTOM_TABNAME);
                                            }
                                        }, tabDelay);
                    } else {
                        plugin.getTabMgr().adjustTabname(p,
                                TabAction.SET_CUSTOM_TABNAME);
                    }
                }
                // remove invisibility if required
                if (playerData.getBoolean("PlayerData.Player."
                        + p.getUniqueId().toString() + ".remInvis")
                        && !invisiblePlayers.contains(p.getUniqueId().toString())) {
                    removeInvisibility(p);
                }
            }
        } catch (Exception er) {
            plugin.printException(er);
        }
    }

    private void displayBossBar(final Player p) {
        final String bossBarVanishMessage = plugin.getMsg("Messages.BossBarVanishMessage");
        String bossBarRememberMessage = plugin.getMsg("Messages.BossBarRememberMessage");
        BarAPI.setMessage(p, plugin.convertString(bossBarRememberMessage, p), 100f);
        Bukkit.getServer().getScheduler()
                .scheduleSyncDelayedTask(plugin, new Runnable() {

                    @Override
                    public void run() {
                        final List<String> invisiblePlayers = playerData
                                .getStringList("InvisiblePlayers");
                        if (invisiblePlayers.contains(p.getUniqueId().toString()))
                            BarAPI.setMessage(p, plugin.convertString(bossBarVanishMessage, p),
                                    100f);
                    }
                }, 100);
    }

    private void removeInvisibility(Player p) {
        p.removePotionEffect(PotionEffectType.INVISIBILITY);
        playerData.set("PlayerData.Player." + p.getUniqueId().toString() + ".remInvis",
                null);
        plugin.savePlayerData();
    }
}