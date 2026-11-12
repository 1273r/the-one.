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
        Location center = player.getLocation().clone().add(0, 1.0, 0);
        particlesThisTick = 0;

        particleBudget = clamp(plugin.getConfig().getInt("visual.max-particles-per-tick", 1200), 200, 5000);
        int maxParticles = particleBudget;
        double rotation = ticks * clamp(plugin.getConfig().getDouble("visual.rotation-speed", 0.032), 0.005, 0.2);
        Stage stage = getStage(player);
        drawOrbitalCage(center, stage, rotation, maxParticles);

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

    /**
     * Continuous, independently tilted orbital paths. Each orbit wraps the full
     * player silhouette rather than stacking into a flat belt at the waist.
     */
    private void drawOrbitalCage(Location center, Stage stage, double rotation, int maxParticles) {
        World world = center.getWorld();
        int segments = stage == Stage.THE_ONE ? 56 : 44;
        for (int ring = 0; ring < stage.rings() && particlesThisTick < maxParticles; ring++) {
            double radius = 1.18 + ring * 0.145;
            double tilt = 0.48 + (ring % 4) * 0.22;
            double bank = (ring % 2 == 0 ? 1.0 : -1.0) * (0.28 + (ring % 3) * 0.19);
            double phase = rotation * (ring % 2 == 0 ? 1.0 : -0.76) + ring * 0.71;
            double heightScale = 0.88 + (ring % 3) * 0.12;
            Color color = stage.ringColor(ring, 0, stage.rings());

            Location previous = orbitPoint(center, radius, tilt, bank, phase, heightScale, 0);
            for (int point = 1; point <= segments && particlesThisTick < maxParticles; point++) {
                double angle = Math.PI * 2.0 * point / segments;
                Location next = orbitPoint(center, radius, tilt, bank, phase, heightScale, angle);
                world.spawnParticle(Particle.TRAIL, previous, 1, 0, 0, 0, 0,
                        new Particle.Trail(next, color, 8));
                particlesThisTick++;

                // Small, deliberate glints travel along each orbit; avoid a dotted outline.
                if (point % 11 == 0 && particlesThisTick < maxParticles) {
                    spawn(world, Particle.END_ROD, next.clone().add(0, 0.025, 0));
                }
                previous = next;
            }
        }

        // Four broad spiral currents connect the orbit cage to the player's core.
        if (stage == Stage.THE_ONE) {
            for (int stream = 0; stream < 4 && particlesThisTick < maxParticles; stream++) {
                double angle = rotation * 1.55 + stream * Math.PI / 2.0;
                double radius = 2.35 + Math.sin(ticks * 0.035 + stream) * 0.22;
                double y = 0.25 + Math.sin(ticks * 0.027 + stream * 1.4) * 1.05;
                Location from = center.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
                Location to = center.clone().add(Math.cos(angle + 0.28) * (radius * 0.72),
                        y * 0.58, Math.sin(angle + 0.28) * (radius * 0.72));
                world.spawnParticle(Particle.TRAIL, from, 1, 0, 0, 0, 0,
                        new Particle.Trail(to, stream % 2 == 0 ? Color.fromRGB(255, 210, 125)
                                : Color.fromRGB(100, 225, 255), 9));
                particlesThisTick++;
            }
        }
    }

    private Location orbitPoint(Location center, double radius, double tilt, double bank,
                                double phase, double heightScale, double angle) {
        double a = angle + phase;
        double x = Math.cos(a) * radius;
        double z = Math.sin(a) * radius;
        double y = Math.sin(a * 2.0 + phase * 0.35) * 0.12;

        // Rotate the orbit plane around two different axes for a true 3D cage.
        double y1 = y * Math.cos(tilt) - z * Math.sin(tilt);
        double z1 = y * Math.sin(tilt) + z * Math.cos(tilt);
        double x2 = x * Math.cos(bank) + y1 * Math.sin(bank);
        double y2 = -x * Math.sin(bank) + y1 * Math.cos(bank);
        return center.clone().add(x2, y2 * heightScale, z1);
    }

    private void drawInwardStreams(Location center, Stage stage, double rotation, int maxParticles) {
        World world = center.getWorld();
        int count = stage == Stage.THE_ONE ? 8 : 4;
        for (int stream = 0; stream < count && particlesThisTick < maxParticles; stream++) {
            double angle = rotation * 1.8 + stream * (Math.PI * 2.0 / count);
            double radius = 2.0 + Math.sin(ticks * 0.035 + stream) * 0.20;
            double y = Math.sin(ticks * 0.025 + stream * 1.7) * 1.0;
            Location from = center.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
            Location to = center.clone().add(Math.cos(angle + 0.18) * 0.72, y * 0.32,
                    Math.sin(angle + 0.18) * 0.72);
            Color color = stage.ringColor(stream, stream, count);
            world.spawnParticle(Particle.TRAIL, from, 1, 0, 0, 0, 0, new Particle.Trail(to, color, 10));
            particlesThisTick++;
        }
    }

    private void drawCore(Location center, Stage stage, int maxParticles) {
        World world = center.getWorld();
        if (particlesThisTick < maxParticles) {
            spawn(world, Particle.END_ROD, center.clone().add(0, 0.95 + Math.sin(ticks * 0.06) * 0.18, 0));
        }
        if (particlesThisTick < maxParticles && ticks % 3 == 0) {
            spawn(world, Particle.TOTEM_OF_UNDYING, center.clone().add(0, 0.25, 0));
        }
    }

    /**
     * Cinematic ascension built around Paper's client-rendered trail particles.
     * The sequence uses composed geometry and restrained colors instead of a cloud
     * of isolated dust points. It is entirely visual: no explosion entity, damage,
     * block edits, or knockback are created.
     */
    public void playAscension(Player player) {
        cancelAscension(true);
        if (!plugin.getConfig().getBoolean("transition.enabled", true)) return;

        ascensionPlayer = player.getUniqueId();
        savedYaw = player.getLocation().getYaw();
        savedPitch = player.getLocation().getPitch();
        final int[] age = {0};
        final int[] emitted = {0};
        final int budget = clamp(plugin.getConfig().getInt("transition.max-particles-per-tick", 1600), 200, 4000);
        final BukkitTask[] task = new BukkitTask[1];

        Location start = player.getLocation();
        player.playSound(start, Sound.BLOCK_BEACON_POWER_SELECT, 1.15f, 0.48f);
        player.playSound(start, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 0.62f);

        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || player.isDead() || !player.getUniqueId().equals(ascensionPlayer)) {
                task[0].cancel();
                if (ascensionTask == task[0]) ascensionTask = null;
                return;
            }

            int t = age[0]++;
            emitted[0] = 0;
            Location origin = player.getLocation().clone().add(0, 0.95, 0);
            World world = origin.getWorld();
            if (world == null) return;

            if (t <= 23) {
                // A three-layer seal assembles around the player's feet and waist.
                drawAscensionSeal(origin, t, emitted, budget);
                drawRisingEmbers(origin, t, emitted, budget);
                if (t == 12) player.playSound(origin, Sound.BLOCK_BEACON_AMBIENT, 1.0f, 0.55f);
            } else if (t <= 47) {
                // The seal opens into six interwoven, rising strands.
                drawAscensionHelix(origin, t - 24, emitted, budget);
                drawVerticalLances(origin, t - 24, emitted, budget);
                if (t == 24) player.playSound(origin, Sound.BLOCK_BEACON_ACTIVATE, 1.25f, 0.72f);
            } else if (t <= 77) {
                // All strands spiral inward, visibly feeding the core.
                drawConvergence(origin, t - 48, emitted, budget);
                if (t == 48) player.playSound(origin, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 0.58f);
                if (t == 66) player.playSound(origin, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 1.35f);
            } else if (t <= 90) {
                // A clean expanding spherical shockwave and ground-level ring.
                drawAscensionDetonation(origin, t - 78, emitted, budget);
                if (plugin.getConfig().getBoolean("transition.screen-shake", true) && t <= 80) {
                    float impulse = t == 78 ? 0.42f : -0.22f;
                    player.setRotation(savedYaw + impulse, clamp(savedPitch - impulse * 0.55f, -89.0f, 89.0f));
                } else if (t == 81) {
                    player.setRotation(savedYaw, savedPitch);
                }
                if (t == 78) {
                    world.spawnParticle(Particle.FLASH, origin, 1, 0, 0, 0, 0, Color.WHITE);
                    world.spawnParticle(Particle.EXPLOSION_EMITTER, origin, 1, 0, 0, 0, 0);
                    player.playSound(origin, Sound.ENTITY_GENERIC_EXPLODE, 1.35f, 0.66f);
                    player.playSound(origin, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.15f, 0.92f);
                }
            } else if (t <= 112) {
                // The blast resolves into three slowly turning halos and falling ash.
                drawAscensionAfterglow(origin, t - 91, emitted, budget);
            }

            if (t >= 113) {
                player.setRotation(savedYaw, savedPitch);
                task[0].cancel();
                if (ascensionTask == task[0]) ascensionTask = null;
                ascensionPlayer = null;
            }
        }, 0L, 1L);
        ascensionTask = task[0];
    }

    private void drawAscensionSeal(Location origin, int tick, int[] emitted, int budget) {
        World world = origin.getWorld();
        Color[] colors = {Color.fromRGB(255, 218, 130), Color.fromRGB(115, 235, 255), Color.fromRGB(224, 239, 255)};
        for (int ring = 0; ring < 3 && emitted[0] < budget; ring++) {
            double radius = 1.15 + ring * 0.88;
            double y = -0.72 + ring * 0.66;
            double rotation = tick * (ring % 2 == 0 ? 0.105 : -0.082) + ring * 0.6;
            int segments = 28;
            for (int i = 0; i < segments && emitted[0] < budget; i++) {
                double a = Math.PI * 2.0 * i / segments + rotation;
                double b = Math.PI * 2.0 * (i + 1) / segments + rotation;
                Location from = origin.clone().add(Math.cos(a) * radius, y, Math.sin(a) * radius);
                Location to = origin.clone().add(Math.cos(b) * radius, y, Math.sin(b) * radius);
                trail(world, from, to, colors[ring], 9, emitted, budget);
                if (i % 7 == 0) particle(world, Particle.ELECTRIC_SPARK, from, emitted, budget);
            }
        }
        // Four cardinal marks give the geometry a deliberate ritual-like silhouette.
        for (int i = 0; i < 4 && emitted[0] < budget; i++) {
            double a = tick * 0.055 + i * Math.PI / 2.0;
            Location mark = origin.clone().add(Math.cos(a) * 2.05, -0.05, Math.sin(a) * 2.05);
            particle(world, Particle.SOUL_FIRE_FLAME, mark, emitted, budget);
        }
    }

    private void drawRisingEmbers(Location origin, int tick, int[] emitted, int budget) {
        World world = origin.getWorld();
        for (int i = 0; i < 10 && emitted[0] < budget; i++) {
            double angle = i * Math.PI * 2.0 / 10.0 + tick * 0.17;
            double radius = 1.35 + (i % 3) * 0.42;
            double y = -0.9 + Math.floorMod(tick * 2 + i * 5, 30) * 0.075;
            Location from = origin.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
            Location to = from.clone().add(Math.cos(angle) * 0.12, 0.38, Math.sin(angle) * 0.12);
            trail(world, from, to, i % 2 == 0 ? Color.fromRGB(255, 205, 105) : Color.fromRGB(105, 225, 255),
                    12, emitted, budget);
        }
    }

    private void drawAscensionHelix(Location origin, int tick, int[] emitted, int budget) {
        World world = origin.getWorld();
        Color[] colors = {Color.fromRGB(255, 224, 155), Color.fromRGB(115, 225, 255), Color.fromRGB(215, 235, 255)};
        for (int strand = 0; strand < 6 && emitted[0] < budget; strand++) {
            double phase = strand * Math.PI / 3.0;
            for (int segment = 0; segment < 5 && emitted[0] < budget; segment++) {
                double u = segment / 5.0;
                double a = tick * 0.22 + phase + u * Math.PI * 1.65;
                double y = -1.15 + u * 3.9;
                double radius = 2.5 - u * 0.48;
                Location from = origin.clone().add(Math.cos(a) * radius, y, Math.sin(a) * radius);
                double nextA = a + 0.19;
                Location to = origin.clone().add(Math.cos(nextA) * radius, y + 0.16, Math.sin(nextA) * radius);
                trail(world, from, to, colors[strand % colors.length], 12, emitted, budget);
            }
        }
        // A thin vertical axis makes the rising helix read as one coherent column.
        if (tick % 2 == 0) {
            for (int i = 0; i < 5 && emitted[0] < budget; i++) {
                Location p = origin.clone().add(0, -1.3 + i * 0.72, 0);
                particle(world, Particle.END_ROD, p, emitted, budget);
            }
        }
    }

    private void drawVerticalLances(Location origin, int tick, int[] emitted, int budget) {
        World world = origin.getWorld();
        for (int i = 0; i < 4 && emitted[0] < budget; i++) {
            double a = i * Math.PI / 2.0 + tick * 0.035;
            double x = Math.cos(a) * 2.0;
            double z = Math.sin(a) * 2.0;
            Location from = origin.clone().add(x, -1.15, z);
            Location to = origin.clone().add(x * 0.42, 2.2, z * 0.42);
            trail(world, from, to, Color.fromRGB(105, 220, 255), 10, emitted, budget);
        }
    }

    private void drawConvergence(Location origin, int tick, int[] emitted, int budget) {
        World world = origin.getWorld();
        double progress = tick / 29.0;
        double spin = tick * 0.27;
        for (int strand = 0; strand < 8 && emitted[0] < budget; strand++) {
            double phase = strand * Math.PI / 4.0 + spin;
            for (int segment = 0; segment < 5 && emitted[0] < budget; segment++) {
                double along = segment / 5.0;
                double radius = (3.9 * (1.0 - progress) + 0.18) * (1.0 - along * 0.13);
                double angle = phase + along * Math.PI * 1.3;
                double y = Math.sin(angle * 1.5 + strand) * (0.7 * (1.0 - progress));
                Location from = origin.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
                double nextRadius = Math.max(0.08, radius - 0.24);
                Location to = origin.clone().add(Math.cos(angle + 0.2) * nextRadius, y * 0.78, Math.sin(angle + 0.2) * nextRadius);
                Color color = strand % 3 == 0 ? Color.fromRGB(255, 221, 145)
                        : strand % 3 == 1 ? Color.fromRGB(110, 226, 255) : Color.fromRGB(235, 244, 255);
                trail(world, from, to, color, 11, emitted, budget);
            }
        }
        if (tick % 3 == 0) {
            particle(world, Particle.SCULK_SOUL, origin.clone().add(0, 0.1, 0), emitted, budget);
        }
    }

    private void drawAscensionDetonation(Location origin, int tick, int[] emitted, int budget) {
        World world = origin.getWorld();
        double radius = 0.35 + tick * 0.42;
        for (int layer = 0; layer < 2 && emitted[0] < budget; layer++) {
            double layerRadius = radius * (layer == 0 ? 1.0 : 0.68);
            double yScale = layer == 0 ? 0.82 : 1.22;
            int segments = 40;
            for (int i = 0; i < segments && emitted[0] < budget; i++) {
                double a = Math.PI * 2.0 * i / segments + tick * 0.035 + layer * 0.18;
                double b = Math.PI * 2.0 * (i + 1) / segments + tick * 0.035 + layer * 0.18;
                Location from = origin.clone().add(Math.cos(a) * layerRadius, Math.sin(a) * layerRadius * yScale,
                        Math.sin(a) * layerRadius);
                Location to = origin.clone().add(Math.cos(b) * (layerRadius + 0.24),
                        Math.sin(b) * (layerRadius + 0.24) * yScale, Math.sin(b) * (layerRadius + 0.24));
                trail(world, from, to, layer == 0 ? Color.fromRGB(255, 226, 160) : Color.fromRGB(120, 224, 255),
                        13, emitted, budget);
            }
        }
        // A broad ground ring adds scale without touching the terrain.
        double groundRadius = 1.0 + tick * 0.48;
        for (int i = 0; i < 32 && emitted[0] < budget; i++) {
            double a = Math.PI * 2.0 * i / 32.0 + tick * 0.025;
            Location from = origin.clone().add(Math.cos(a) * groundRadius, -0.82, Math.sin(a) * groundRadius);
            Location to = origin.clone().add(Math.cos(a + 0.14) * (groundRadius + 0.3), -0.82,
                    Math.sin(a + 0.14) * (groundRadius + 0.3));
            trail(world, from, to, Color.fromRGB(255, 192, 100), 9, emitted, budget);
        }
        if (tick % 2 == 0) {
            particle(world, Particle.FIREWORK, origin.clone().add(0, 0.4, 0), emitted, budget);
        }
    }

    private void drawAscensionAfterglow(Location origin, int tick, int[] emitted, int budget) {
        World world = origin.getWorld();
        for (int ring = 0; ring < 3 && emitted[0] < budget; ring++) {
            double radius = 1.25 + ring * 0.72;
            double y = 0.25 + ring * 0.68;
            double rotation = tick * (ring == 1 ? -0.045 : 0.035) + ring;
            for (int i = 0; i < 18 && emitted[0] < budget; i++) {
                double a = Math.PI * 2.0 * i / 18.0 + rotation;
                double b = Math.PI * 2.0 * (i + 1) / 18.0 + rotation;
                Location from = origin.clone().add(Math.cos(a) * radius, y, Math.sin(a) * radius);
                Location to = origin.clone().add(Math.cos(b) * radius, y, Math.sin(b) * radius);
                trail(world, from, to, ring == 1 ? Color.fromRGB(120, 225, 255)
                        : Color.fromRGB(255, 225, 160), 18, emitted, budget);
            }
        }
        for (int i = 0; i < 8 && emitted[0] < budget; i++) {
            double a = i * Math.PI / 4.0 + tick * 0.09;
            Location ash = origin.clone().add(Math.cos(a) * 1.5, 2.2 + (i % 3) * 0.35, Math.sin(a) * 1.5);
            particle(world, Particle.WHITE_ASH, ash, emitted, budget);
        }
    }

    private void trail(World world, Location from, Location to, Color color, int duration,
                       int[] emitted, int budget) {
        if (emitted[0] >= budget) return;
        world.spawnParticle(Particle.TRAIL, from, 1, 0, 0, 0, 0,
                new Particle.Trail(to, color, duration));
        emitted[0]++;
    }

    private void particle(World world, Particle particle, Location location, int[] emitted, int budget) {
        if (emitted[0] >= budget) return;
        world.spawnParticle(particle, location, 1, 0, 0, 0, 0);
        emitted[0]++;
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
