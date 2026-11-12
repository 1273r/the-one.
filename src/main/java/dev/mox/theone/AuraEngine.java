package dev.mox.theone;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.UUID;

public final class AuraEngine {
    private final TheOnePlugin plugin;
    private int particleBudget = 900;
    private long ticks;
    private int particlesThisTick;

    public AuraEngine(TheOnePlugin plugin) {
        this.plugin = plugin;
    }

    public void tick() {
        ticks += 3;
        UUID holderId = plugin.getHolder();
        if (holderId == null) return;

        Player player = plugin.getServer().getPlayer(holderId);
        if (player == null || !player.isOnline() || player.isDead()) return;

        if (plugin.getConfig().getBoolean("progression.buffs.enabled", true)
                && ticks % Math.max(6, plugin.getConfig().getInt("progression.buffs.refresh-ticks", 40)) < 3) {
            refreshBuffs(player);
        }

        if (!plugin.effectsEnabled()) return;
        Location center = player.getLocation().clone().add(0, 0.08, 0);
        particlesThisTick = 0;

        particleBudget = clamp(plugin.getConfig().getInt("visual.max-particles-per-tick", 900), 100, 5000);
        int maxParticles = particleBudget;
        int points = clamp(plugin.getConfig().getInt("visual.points-per-ring", 56), 16, 96);
        double baseRadius = clamp(plugin.getConfig().getDouble("visual.base-radius", 0.95), 0.4, 2.0);
        double radiusStep = clamp(plugin.getConfig().getDouble("visual.radius-per-ring", 0.145), 0.04, 0.35);
        double heightStep = clamp(plugin.getConfig().getDouble("visual.height-spacing", 0.19), 0.08, 0.35);
        float size = (float) clamp(plugin.getConfig().getDouble("visual.particle-size", 1.25), 0.5, 2.5);
        double rotationSpeed = clamp(plugin.getConfig().getDouble("visual.rotation-speed", 0.045), 0.005, 0.2);
        double tiltSpeed = clamp(plugin.getConfig().getDouble("visual.tilt-speed", 0.13), 0.01, 0.5);
        double rotation = ticks * rotationSpeed;
        Stage stage = getStage(player);

        for (int ring = 0; ring < stage.rings() && particlesThisTick < maxParticles; ring++) {
            double radius = baseRadius + ring * radiusStep;
            double vertical = (ring - (stage.rings() - 1) / 2.0) * heightStep;
            double tiltX = Math.sin(ticks * tiltSpeed * 0.06 + ring * 0.77) * (0.28 + ring * 0.018);
            double tiltZ = Math.cos(ticks * tiltSpeed * 0.045 + ring * 0.61) * (0.20 + ring * 0.012);
            double ringRotation = rotation * (ring % 2 == 0 ? 1.0 : -0.82) + ring * 0.47;

            for (int point = 0; point < points && particlesThisTick < maxParticles; point++) {
                double angle = (Math.PI * 2.0 * point / points) + ringRotation;
                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;
                double y = 0;

                // Rotate the ring plane in 3D; each ring has its own changing axis.
                double rotatedY = y * Math.cos(tiltX) - z * Math.sin(tiltX);
                double rotatedZ = y * Math.sin(tiltX) + z * Math.cos(tiltX);
                double rotatedX = x * Math.cos(tiltZ) + rotatedZ * Math.sin(tiltZ);
                rotatedZ = -x * Math.sin(tiltZ) + rotatedZ * Math.cos(tiltZ);

                Location pointLocation = center.clone().add(rotatedX, vertical + rotatedY, rotatedZ);
                Color color = stage.ringColor(ring, point, points);
                dust(center.getWorld(), pointLocation, color, size);

                if (plugin.getConfig().getBoolean("visual.ring-highlights", true)
                        && point % 8 == 0 && particlesThisTick < maxParticles) {
                    spawn(center.getWorld(), Particle.END_ROD, pointLocation.clone().add(0, 0.025, 0));
                }
            }
        }

        if (plugin.getConfig().getBoolean("visual.inward-streams", true)) {
            drawInwardStreams(center, stage, rotation, maxParticles);
        }
        if (plugin.getConfig().getBoolean("visual.core-particles", true)) {
            drawCore(center, stage, maxParticles);
        }
        if (plugin.getConfig().getBoolean("visual.action-bar", true) && ticks % 12 == 0) {
            sendProgressBar(player, stage);
        }
    }

    private void drawInwardStreams(Location center, Stage stage, double rotation, int maxParticles) {
        World world = center.getWorld();
        for (int stream = 0; stream < 4 && particlesThisTick < maxParticles; stream++) {
            double angle = rotation * 1.8 + stream * Math.PI / 2.0;
            double radius = 1.9 + Math.sin(ticks * 0.035 + stream) * 0.18;
            double y = Math.sin(ticks * 0.025 + stream * 1.7) * 0.85;
            Location trail = center.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
            spawn(world, Particle.ELECTRIC_SPARK, trail);
            if (particlesThisTick < maxParticles) {
                Location inner = center.clone().add(Math.cos(angle + 0.16) * (radius * 0.68),
                        y * 0.68, Math.sin(angle + 0.16) * (radius * 0.68));
                dust(world, inner, stage.color(), 0.9f);
            }
        }
    }

    private void drawCore(Location center, Stage stage, int maxParticles) {
        World world = center.getWorld();
        if (particlesThisTick < maxParticles) {
            spawn(world, Particle.END_ROD, center.clone().add(0, 0.85 + Math.sin(ticks * 0.06) * 0.12, 0));
        }
        if (particlesThisTick < maxParticles && ticks % 2 == 0) {
            spawn(world, Particle.TOTEM_OF_UNDYING, center.clone().add(0, 0.15, 0));
        }
    }

    public void refreshBuffs(Player player) {
        if (!plugin.getConfig().getBoolean("progression.buffs.enabled", true)) return;
        try {
            Stage stage = Stage.fromKills(plugin.getKills(player.getUniqueId()));
            if (stage.buff() == null) return;
            int duration = Math.max(40, plugin.getConfig().getInt("progression.buffs.duration-ticks", 100));
            player.addPotionEffect(new PotionEffect(stage.buff(), duration, stage.amplifier(), true, false, false));
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not refresh THE ONE buff: " + exception.getMessage());
        }
    }

    private Stage getStage(Player player) {
        try {
            return Stage.fromKills(plugin.getKills(player.getUniqueId()));
        } catch (Exception exception) {
            return Stage.AWAKENING;
        }
    }

    private void sendProgressBar(Player player, Stage stage) {
        try {
            int kills = plugin.getKills(player.getUniqueId());
            int percent = stage.progressPercent(kills);
            int filled = percent / 10;
            String bar = "§b" + "▰".repeat(filled) + "§8" + "▱".repeat(10 - filled);
            String suffix = stage == Stage.THE_ONE ? "§6MAX EVOLUTION" : "§7" + kills + " / " + stage.nextThreshold();
            player.sendActionBar("§f" + stage.displayName() + "  " + bar + "  " + suffix);
        } catch (Exception ignored) {
            // A transient database read should not break the visual scheduler.
        }
    }

    private void dust(World world, Location location, Color color, float size) {
        if (particlesThisTick >= plugin.getConfig().getInt("visual.max-particles-per-tick", 900)) return;
        world.spawnParticle(Particle.DUST, location, 1, 0, 0, 0, 0,
                new Particle.DustOptions(color, size));
        particlesThisTick++;
    }

    private void spawn(World world, Particle particle, Location location) {
        if (particlesThisTick >= plugin.getConfig().getInt("visual.max-particles-per-tick", 900)) return;
        world.spawnParticle(particle, location, 1, 0, 0, 0, 0);
        particlesThisTick++;
    }

    public void forget(UUID uuid) {
        // The renderer derives its anchor directly from the holder's live location.
    }

    public void clearAll() {
        // No persistent particle entities are created; Bukkit clears the visual stream naturally.
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
