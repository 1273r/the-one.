package dev.mox.theone;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;

public final class TheOnePlugin extends JavaPlugin implements Listener {
    private ProgressStore store;
    private AuraEngine auraEngine;
    private WingsEngine wingsEngine;
    private HaloAbilityEngine haloAbilityEngine;
    private OneCommands commands;
    private UUID holder;
    private boolean effectsEnabled;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        effectsEnabled = getConfig().getBoolean("visual.enabled", true);
        try {
            store = new ProgressStore(getDataFolder().toPath()
                    .resolve(getConfig().getString("storage.database", "the-one.db")).toFile());
            holder = store.getHolder();
        } catch (SQLException exception) {
            getLogger().severe("Could not open progression database: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        auraEngine = new AuraEngine(this);
        wingsEngine = new WingsEngine(this);
        haloAbilityEngine = new HaloAbilityEngine(this);
        commands = new OneCommands(this);
        registerCommand("one");
        registerCommand("makeone");
        registerCommand("removeone");
        registerCommand("forceprog");
        registerCommand("oneinfo");
        registerCommand("progress");
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, auraEngine::tick, 1L, 1L);
        Bukkit.getScheduler().runTaskTimer(this, wingsEngine::tick, 1L, 1L);

        if (holder != null) {
            Player current = Bukkit.getPlayer(holder);
            if (current != null) auraEngine.refreshBuffs(current);
        }
        getLogger().info("THE ONE is online. Persistent progression and orbital engine initialized.");
    }

    private void registerCommand(String name) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(commands);
            command.setTabCompleter(commands);
        }
    }

    @Override
    public void onDisable() {
        if (auraEngine != null) auraEngine.clearAll();
        if (store != null) {
            try {
                store.close();
            } catch (SQLException exception) {
                getLogger().warning("Could not close progression database cleanly: " + exception.getMessage());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerKill(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || holder == null || !holder.equals(killer.getUniqueId())) return;

        try {
            int kills = store.addKill(killer.getUniqueId());
            Stage before = Stage.fromKills(kills - 1);
            Stage after = Stage.fromKills(kills);
            auraEngine.refreshBuffs(killer);
            if (before != after) announceEvolution(killer, after, kills);
            else killer.sendActionBar(Component.text("THE ONE  •  " + kills + " kills  •  " + after.displayName(),
                    NamedTextColor.GRAY));
        } catch (SQLException exception) {
            getLogger().severe("Could not save kill progression: " + exception.getMessage());
            killer.sendMessage(Component.text("Your progression could not be saved. Contact an administrator.",
                    NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(this, () -> {
            // A holder may have been removed while offline. Finish the cleanup on login.
            java.util.List<String> pending = getConfig().getStringList("cleanup.pending-players");
            if (pending.remove(player.getUniqueId().toString())) {
                removeManagedBuffs(player);
                player.sendActionBar(Component.empty());
                getConfig().set("cleanup.pending-players", pending);
                saveConfig();
            }
            if (holder != null && holder.equals(player.getUniqueId())) {
                auraEngine.refreshBuffs(player);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (auraEngine != null) auraEngine.forget(event.getPlayer().getUniqueId());
        if (haloAbilityEngine != null) haloAbilityEngine.clear(event.getPlayer().getUniqueId());
    }

    private void announceEvolution(Player player, Stage stage, int kills) {
        if (getConfig().getBoolean("audio.enabled", true)) {
            float volume = (float) getConfig().getDouble("audio.progression-volume", 0.8);
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, volume, 0.75f);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, volume, 1.25f);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, volume, 1.6f);
        }
        if (getConfig().getBoolean("visual.stage-title", true)) {
            Component title = Component.text(stage.displayName(), NamedTextColor.GOLD);
            Component subtitle = Component.text("EVOLUTION COMPLETE  •  " + kills + " KILLS",
                    NamedTextColor.AQUA);
            player.showTitle(Title.title(title, subtitle,
                    Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2400), Duration.ofMillis(600))));
        }
        player.sendMessage(Component.text("✦ ", NamedTextColor.AQUA)
                .append(Component.text("Your evolution has reached ", NamedTextColor.GRAY))
                .append(Component.text(stage.displayName(), NamedTextColor.GOLD))
                .append(Component.text(".", NamedTextColor.GRAY)));
        auraEngine.refreshBuffs(player);
    }

    public void setHolder(Player player) throws SQLException {
        UUID previousHolder = holder;
        UUID nextHolder = player == null ? null : player.getUniqueId();

        // Persist first so a database failure never strips the current holder's powers.
        store.setHolder(nextHolder);

        if (previousHolder != null && !previousHolder.equals(nextHolder)) {
            if (haloAbilityEngine != null) haloAbilityEngine.clear(previousHolder);
            // Stop every running cinematic/temporary visual before releasing the holder.
            if (auraEngine != null) auraEngine.clearAll();

            java.util.List<String> pending = getConfig().getStringList("cleanup.pending-players");
            if (!pending.contains(previousHolder.toString())) pending.add(previousHolder.toString());
            getConfig().set("cleanup.pending-players", pending);

            Player previous = Bukkit.getPlayer(previousHolder);
            if (previous != null) {
                removeManagedBuffs(previous);
                previous.sendActionBar(Component.empty());
                pending.remove(previousHolder.toString());
                getConfig().set("cleanup.pending-players", pending);
            }
            saveConfig();
        }

        holder = nextHolder;
        if (player != null) {
            auraEngine.refreshBuffs(player);
            if (!player.getUniqueId().equals(previousHolder)) {
                auraEngine.playAscension(player);
            }
            player.sendMessage(Component.text("You are now ", NamedTextColor.GRAY)
                    .append(Component.text("THE ONE.", NamedTextColor.GOLD)));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.0f);
        }
    }

    public void clearAbility(UUID playerId) {
        if (haloAbilityEngine != null) haloAbilityEngine.clear(playerId);
    }

    public boolean isHaloInFlight(UUID playerId) {
        return haloAbilityEngine != null && haloAbilityEngine.isInFlight(playerId);
    }

    public void castAbility(Player player) {
        if (!isHolder(player)) {
            player.sendMessage(Component.text("Only THE ONE can wield the halo.", NamedTextColor.RED));
            return;
        }
        try {
            haloAbilityEngine.cast(player, Stage.fromKills(getKills(player.getUniqueId())));
        } catch (SQLException exception) {
            player.sendMessage(Component.text("Your progression could not be loaded.", NamedTextColor.RED));
            getLogger().warning("Could not read ability progression: " + exception.getMessage());
        }
    }

    public void playAscension(Player player) {
        auraEngine.playAscension(player);
    }

    public void setKills(Player player, int kills) throws SQLException {
        store.setKills(player.getUniqueId(), kills);
        auraEngine.refreshBuffs(player);
    }

    public void resetProgress(Player player) throws SQLException {
        store.setKills(player.getUniqueId(), 0);
        if (holder != null && holder.equals(player.getUniqueId())) auraEngine.refreshBuffs(player);
    }

    public int getKills(UUID uuid) throws SQLException {
        return store.getKills(uuid);
    }

    public UUID getHolder() {
        return holder;
    }

    public boolean isHolder(Player player) {
        return holder != null && holder.equals(player.getUniqueId());
    }

    public boolean effectsEnabled() {
        return effectsEnabled;
    }

    public void toggleEffects() {
        effectsEnabled = !effectsEnabled;
        getConfig().set("visual.enabled", effectsEnabled);
        saveConfig();
    }

    public boolean toggleWings() {
        boolean enabled = !getConfig().getBoolean("wings.enabled", true);
        getConfig().set("wings.enabled", enabled);
        saveConfig();
        return enabled;
    }

    public void reloadSettings() {
        reloadConfig();
        effectsEnabled = getConfig().getBoolean("visual.enabled", true);
        if (auraEngine != null) auraEngine.clearAll();
    }

    public void removeManagedBuffs(Player player) {
        for (Stage stage : Stage.values()) {
            if (stage.buff() == null) continue;
            PotionEffect effect = player.getPotionEffect(stage.buff());
            if (effect != null) player.removePotionEffect(stage.buff());
        }
    }
}
