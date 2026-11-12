package dev.mox.theone;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.UUID;

public final class AuraEngine {
    private final TheOnePlugin plugin;
    private int particleBudget = 900;
    private long ticks;
    private long nextBuffRefreshTick;
    private int particlesThisTick;
    private BukkitTask ascensionTask;
    private UUID ascensionPlayer;
    private float savedYaw;
    private float savedPitch;

    public AuraEngine(TheOnePlugin plugin) {
        this.plugin = plugin;
    }

    public void tick() {
        ticks += 3;
        UUID holderId = plugin.getHolder();
        if (holderId == null) return;

        Player player = plugin.getServer().getPlayer(holderId);
        if (player == null || !player.isOnline() || player.isDead()) return;

        if (plugin.getConfig().getBoolean("progression.buffs.enabled", true) && ticks >= nextBuffRefreshTick) {
            refreshBuffs(player);
            nextBuffRefreshTick = ticks + Math.max(6, plugin.getConfig().getInt("progression.buffs.refresh-ticks", 40));
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


    /**
     * One-time cinematic made only from particles and sounds.
     * No explosion entity is created and no blocks or entities are damaged.
     */
    public void playAscension(Player player) {
        cancelAscension(true);
        if (!plugin.getConfig().getBoolean("transition.enabled", true)) return;

        ascensionPlayer = player.getUniqueId();
        savedYaw = player.getLocation().getYaw();
        savedPitch = player.getLocation().getPitch();
        final int[] age = {0};
        final int[] emitted = {0};
        final int budget = clamp(plugin.getConfig().getInt("transition.max-particles-per-tick", 850), 100, 3000);
        final BukkitTask[] task = new BukkitTask[1];

        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.4f, 0.55f);
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 0.7f);

        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || player.isDead() || !player.getUniqueId().equals(ascensionPlayer)) {
                task[0].cancel();
                if (ascensionTask == task[0]) ascensionTask = null;
                return;
            }

            int t = age[0]++;
            emitted[0] = 0;
            Location origin = player.getLocation().clone().add(0, 1.0, 0);
            World world = origin.getWorld();
            if (world == null) return;

            // Paper has no native camera-shake API. A tiny temporary view rotation
            // provides the impact, then restores the exact original view direction.
            if (plugin.getConfig().getBoolean("transition.screen-shake", true) && t <= 22) {
                float strength = (float) (1.0 - t / 23.0);
                float yaw = (float) Math.sin(t * 2.35) * 1.45f * strength;
                float pitch = (float) Math.cos(t * 1.85) * 1.15f * strength;
                player.setRotation(savedYaw + yaw, clamp(savedPitch + pitch, -89.0f, 89.0f));
            }

            if (t <= 14) {
                // Expanding spherical shockwave from the player's whole body.
                double radius = 0.18 + t * 0.205;
                for (int lat = 0; lat <= 6 && emitted[0] < budget; lat++) {
                    double phi = Math.PI * lat / 6.0;
                    for (int lon = 0; lon < 16 && emitted[0] < budget; lon++) {
                        double theta = Math.PI * 2.0 * lon / 16.0 + t * 0.075;
                        Location point = origin.clone().add(
                                Math.sin(phi) * Math.cos(theta) * radius,
                                Math.cos(phi) * radius,
                                Math.sin(phi) * Math.sin(theta) * radius);
                        transitionDust(world, point, palette((lat + lon + t) % 4), 1.35f, emitted, budget);
                        if ((lon + lat) % 3 == 0) transitionParticle(world, Particle.ELECTRIC_SPARK, point, emitted, budget);
                    }
                }
                if (t == 0) {
                    player.playSound(origin, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 0.65f);
                    player.playSound(origin, Sound.ENTITY_PLAYER_LEVELUP, 1.2f, 0.5f);
                }
            } else if (t <= 48) {
                // Six colored helices orbit rapidly, then collapse into the player.
                double progress = (t - 15) / 33.0;
                double radius = 3.25 * (1.0 - progress) + 0.12;
                double spin = t * 0.31;
                for (int stream = 0; stream < 6 && emitted[0] < budget; stream++) {
                    double phase = stream * Math.PI / 3.0;
                    for (int segment = 0; segment < 8 && emitted[0] < budget; segment++) {
                        double along = segment / 7.0;
                        double angle = phase + spin + along * Math.PI * 1.55;
                        double localRadius = radius * (1.0 - along * 0.22);
                        double y = Math.sin(angle * 1.4 + stream) * (0.35 + localRadius * 0.17);
                        Location point = origin.clone().add(
                                Math.cos(angle) * localRadius, y, Math.sin(angle) * localRadius);
                        transitionDust(world, point, palette((stream + segment + t / 3) % 4), 1.2f, emitted, budget);
                        if (segment % 2 == 0) transitionParticle(world, Particle.END_ROD, point, emitted, budget);
                    }
                }
                if (t == 15) player.playSound(origin, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.4f, 0.6f);
                if (t == 36) player.playSound(origin, Sound.BLOCK_BEACON_AMBIENT, 1.2f, 0.8f);
            } else if (t <= 55) {
                // Huge spherical visual detonation; intentionally no explosive entity.
                double radius = 0.25 + (t - 49) * 0.48;
                for (int lat = 0; lat <= 5 && emitted[0] < budget; lat++) {
                    double phi = Math.PI * lat / 5.0;
                    for (int lon = 0; lon < 18 && emitted[0] < budget; lon++) {
                        double theta = Math.PI * 2.0 * lon / 18.0 + t * 0.045;
                        Location point = origin.clone().add(
                                Math.sin(phi) * Math.cos(theta) * radius,
                                Math.cos(phi) * radius,
                                Math.sin(phi) * Math.sin(theta) * radius);
                        transitionDust(world, point, palette((lat + lon + t) % 4), 1.65f, emitted, budget);
                        if (lon % 2 == 0) transitionParticle(world, Particle.FIREWORK, point, emitted, budget);
                    }
                }
                if (t == 49) {
                    world.spawnParticle(Particle.FLASH, origin, 1, 0, 0, 0, 0);
                    world.spawnParticle(Particle.EXPLOSION_EMITTER, origin, 1, 0, 0, 0, 0);
                    player.playSound(origin, Sound.ENTITY_GENERIC_EXPLODE, 1.7f, 0.72f);
                    player.playSound(origin, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 1.05f);
                }
            }

            if (t >= 56) {
                player.setRotation(savedYaw, savedPitch);
                task[0].cancel();
                if (ascensionTask == task[0]) ascensionTask = null;
                ascensionPlayer = null;
            }
        }, 0L, 1L);
        ascensionTask = task[0];
    }

    private void cancelAscension(boolean restoreView) {
        if (ascensionTask != null) {
            ascensionTask.cancel();
            ascensionTask = null;
        }
        if (restoreView && ascensionPlayer != null) {
            Player previous = plugin.getServer().getPlayer(ascensionPlayer);
            if (previous != null && previous.isOnline()) previous.setRotation(savedYaw, savedPitch);
        }
        ascensionPlayer = null;
    }

    private static Color palette(int index) {
        return switch (Math.floorMod(index, 4)) {
            case 0 -> Color.fromRGB(255, 245, 205);
            case 1 -> Color.fromRGB(75, 235, 255);
            case 2 -> Color.fromRGB(185, 95, 255);
            default -> Color.fromRGB(255, 65, 95);
        };
    }

    private void transitionDust(World world, Location location, Color color, float size, int[] emitted, int budget) {
        if (emitted[0] >= budget) return;
        world.spawnParticle(Particle.DUST, location, 1, 0, 0, 0, 0, new Particle.DustOptions(color, size));
        emitted[0]++;
    }

    private void transitionParticle(World world, Particle particle, Location location, int[] emitted, int budget) {
        if (emitted[0] >= budget) return;
        world.spawnParticle(particle, location, 1, 0, 0, 0, 0);
        emitted[0]++;
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
            String bar = "▰".repeat(filled) + "▱".repeat(10 - filled);
            String suffix = stage == Stage.THE_ONE ? "MAX EVOLUTION" : kills + " / " + stage.nextThreshold();
            player.sendActionBar(Component.text(stage.displayName() + "  " + bar + "  " + suffix,
                    NamedTextColor.WHITE));
        } catch (Exception ignored) {
            // A transient database read should not break the visual scheduler.
        }
    }

    private void dust(World world, Location location, Color color, float size) {
        if (particlesThisTick >= particleBudget) return;
        world.spawnParticle(Particle.DUST, location, 1, 0, 0, 0, 0,
                new Particle.DustOptions(color, size));
        particlesThisTick++;
    }

    private void spawn(World world, Particle particle, Location location) {
        if (particlesThisTick >= particleBudget) return;
        world.spawnParticle(particle, location, 1, 0, 0, 0, 0);
        particlesThisTick++;
    }

    public void forget(UUID uuid) {
        if (uuid.equals(ascensionPlayer)) cancelAscension(false);
    }

    public void clearAll() {
        cancelAscension(true);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
