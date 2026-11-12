package dev.mox.theone;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * Large animated cosmetic wings for THE ONE holder.
 * Uses short-lived point particles rather than TRAIL particles, so the shape
 * is rebuilt at the player's current position instead of drawing delayed paths.
 */
public final class WingsEngine {
    private static final Color ICE = Color.fromRGB(115, 225, 255);
    private static final Color WHITE = Color.fromRGB(240, 250, 255);
    private static final Color GOLD = Color.fromRGB(255, 215, 135);
    private final TheOnePlugin plugin;
    private long tick;

    public WingsEngine(TheOnePlugin plugin) {
        this.plugin = plugin;
    }

    public void tick() {
        tick++;
        if (!plugin.getConfig().getBoolean("wings.enabled", true)) return;
        UUID holder = plugin.getHolder();
        if (holder == null) return;
        Player player = plugin.getServer().getPlayer(holder);
        if (player == null || !player.isOnline() || player.isDead()) return;

        Location body = player.getLocation();
        World world = body.getWorld();
        if (world == null) return;

        double yaw = Math.toRadians(body.getYaw());
        Vector right = new Vector(Math.cos(yaw), 0, Math.sin(yaw));
        Vector back = new Vector(Math.sin(yaw), 0, -Math.cos(yaw));
        Location shoulders = body.clone().add(0, 1.48, 0).add(back.clone().multiply(0.38));

        // One broad, slow flap. The tips lag slightly behind the shoulder motion.
        double beat = Math.sin(tick * 0.045);
        double lift = beat * 0.48;
        double sweep = 0.34 + (1.0 - beat) * 0.12;

        drawWing(world, shoulders, right, back, -1, lift, sweep);
        drawWing(world, shoulders, right, back, 1, lift, sweep);
    }

    private void drawWing(World world, Location root, Vector right, Vector back,
                          int side, double lift, double sweep) {
        Vector outward = right.clone().multiply(side);

        // Primary flight feathers: a huge arcing fan extending well beyond the body.
        for (int feather = 0; feather < 14; feather++) {
            double u = feather / 13.0;
            double span = 0.48 + u * 5.05;
            double rise = 0.28 + Math.sin(u * Math.PI * 0.84) * 2.05
                    - u * 0.52 + lift * (0.35 + u * 0.72);
            Location tip = root.clone().add(outward.clone().multiply(span))
                    .add(back.clone().multiply(sweep * (0.25 + u * 0.75)))
                    .add(0, rise, 0);
            Location start = root.clone().add(outward.clone().multiply(0.12 + u * 0.32))
                    .add(back.clone().multiply(0.08));
            Color color = feather % 7 == 0 ? GOLD : feather % 2 == 0 ? ICE : WHITE;
            drawFeather(world, start, tip, outward, color, 7, 1.75f);
        }

        // Lower covert feathers create a broad filled lower edge rather than wireframe wings.
        for (int feather = 0; feather < 10; feather++) {
            double u = feather / 9.0;
            double span = 0.35 + u * 4.45;
            double rise = -0.22 + Math.sin(u * Math.PI * 0.76) * 0.78
                    - u * 0.30 + lift * (0.18 + u * 0.32);
            Location tip = root.clone().add(outward.clone().multiply(span))
                    .add(back.clone().multiply(sweep * (0.35 + u * 0.68) + 0.24))
                    .add(0, rise, 0);
            Location start = root.clone().add(outward.clone().multiply(0.22 + u * 0.25))
                    .add(back.clone().multiply(0.20));
            drawFeather(world, start, tip, outward, feather % 4 == 0 ? ICE : WHITE, 5, 1.45f);
        }

        // Smooth outer silhouette and bright shoulder fan.
        Location previous = root.clone().add(outward.clone().multiply(0.12));
        for (int i = 1; i <= 22; i++) {
            double u = i / 22.0;
            Location edge = root.clone().add(outward.clone().multiply(5.53 * u))
                    .add(back.clone().multiply(sweep * (0.25 + u)))
                    .add(0, 0.28 + Math.sin(u * Math.PI * 0.84) * 2.05
                            - u * 0.52 + lift * (0.35 + u * 0.72), 0);
            dust(world, edge, u > 0.82 ? GOLD : ICE, 1.85f);
            previous = edge;
        }
        for (int i = 0; i < 7; i++) {
            Location shoulder = root.clone().add(outward.clone().multiply(0.12 + i * 0.12))
                    .add(0, 0.08 + i * 0.045, 0);
            dust(world, shoulder, i == 0 ? GOLD : WHITE, 2.0f);
        }
        if (tick % 5 == 0) {
            Location tip = root.clone().add(outward.clone().multiply(5.53))
                    .add(back.clone().multiply(sweep))
                    .add(0, 0.28 + lift * 1.07, 0);
            world.spawnParticle(Particle.END_ROD, tip, 2, 0.025, 0.025, 0.025, 0);
        }
    }

    private void drawFeather(World world, Location start, Location tip, Vector outward,
                             Color color, int steps, float size) {
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            Location point = start.clone().add(tip.clone().subtract(start).multiply(t));
            point.add(outward.clone().multiply(Math.sin(t * Math.PI) * 0.10));
            dust(world, point, color, size);
        }
    }

    private void dust(World world, Location location, Color color, float size) {
        world.spawnParticle(Particle.DUST, location, 1, 0, 0, 0, 0,
                new Particle.DustOptions(color, size));
    }
}
