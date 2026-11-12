package dev.mox.theone;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Oversized, layered cosmetic wings for THE ONE holder.
 * Built as broad feathered silhouettes from particles; no gameplay effects.
 */
public final class WingsEngine {
    private static final Color ICE = Color.fromRGB(115, 225, 255);
    private static final Color WHITE = Color.fromRGB(235, 250, 255);
    private static final Color GOLD = Color.fromRGB(255, 215, 135);
    private final TheOnePlugin plugin;
    private long tick;

    public WingsEngine(TheOnePlugin plugin) {
        this.plugin = plugin;
    }

    public void tick() {
        tick += 3;
        if (!plugin.getConfig().getBoolean("wings.enabled", true)) return;
        java.util.UUID holder = plugin.getHolder();
        if (holder == null) return;
        Player player = plugin.getServer().getPlayer(holder);
        if (player == null || !player.isOnline() || player.isDead()) return;

        Location body = player.getLocation();
        World world = body.getWorld();
        if (world == null) return;

        double yaw = Math.toRadians(body.getYaw());
        Vector right = new Vector(Math.cos(yaw), 0, Math.sin(yaw));
        Vector back = new Vector(Math.sin(yaw), 0, -Math.cos(yaw));
        Location shoulders = body.clone().add(0, 1.42, 0).add(back.clone().multiply(0.34));

        // Wide, slow wingbeat with a slight delayed feather response.
        double beat = Math.sin(tick * 0.050);
        double lift = beat * 0.58;
        double sweep = 0.38 + (1.0 - beat) * 0.22;

        drawWing(world, shoulders, right, back, -1, lift, sweep);
        drawWing(world, shoulders, right, back, 1, lift, sweep);
    }

    private void drawWing(World world, Location root, Vector right, Vector back,
                          int side, double lift, double sweep) {
        Vector outward = right.clone().multiply(side);

        // Broad inner wing: a luminous layered fan, not a thin outline.
        for (int feather = 0; feather < 15; feather++) {
            double u = feather / 14.0;
            double span = 0.52 + u * 3.55;
            double rise = 0.28 + Math.sin(u * Math.PI * 0.82) * 1.10
                    + lift * (0.48 + u * 0.78) - u * 0.48;
            double rear = sweep * (0.25 + u * 0.95);
            Location tip = root.clone().add(outward.clone().multiply(span))
                    .add(back.clone().multiply(rear)).add(0, rise, 0);
            Location start = root.clone().add(outward.clone().multiply(0.12 + u * 0.30))
                    .add(back.clone().multiply(0.10));
            Color featherColor = feather % 5 == 0 ? GOLD : feather % 2 == 0 ? ICE : WHITE;
            drawVane(world, start, tip, outward, featherColor, 9, 1.45f);
        }

        // Secondary lower feathers add a filled silhouette and visible feather layers.
        for (int feather = 0; feather < 12; feather++) {
            double u = feather / 11.0;
            double span = 0.38 + u * 3.05;
            double rise = -0.12 + Math.sin(u * Math.PI * 0.72) * 0.55
                    + lift * (0.34 + u * 0.48) - u * 0.24;
            Location tip = root.clone().add(outward.clone().multiply(span))
                    .add(back.clone().multiply(sweep * (0.35 + u * 0.72) + 0.22))
                    .add(0, rise, 0);
            Location start = root.clone().add(outward.clone().multiply(0.20 + u * 0.22))
                    .add(back.clone().multiply(0.18));
            drawVane(world, start, tip, outward, feather % 3 == 0 ? ICE : WHITE, 7, 1.12f);
        }

        // Curved top edge and scalloped outer edge make the wing read from a distance.
        Location previous = root.clone();
        for (int i = 1; i <= 28; i++) {
            double u = i / 28.0;
            Location edge = root.clone().add(outward.clone().multiply(4.08 * u))
                    .add(back.clone().multiply(sweep * (0.25 + u)))
                    .add(0, 0.30 + Math.sin(u * Math.PI * 0.82) * 1.10
                            + lift * (0.48 + u * 0.78) - u * 0.48 + 0.10, 0);
            line(world, previous, edge, u > 0.78 ? GOLD : ICE, 1.65f);
            previous = edge;
        }

        // Bright shoulder plumage and outer-tip glints.
        for (int i = 0; i < 5; i++) {
            Location shoulder = root.clone().add(outward.clone().multiply(0.16 + i * 0.10))
                    .add(0, 0.12 + i * 0.055, 0);
            dust(world, shoulder, i == 0 ? GOLD : WHITE, 1.8f);
        }
        if (tick % 6 == 0) {
            Location tip = root.clone().add(outward.clone().multiply(4.08))
                    .add(back.clone().multiply(sweep * 1.25))
                    .add(0, 0.30 + Math.sin(0.82 * Math.PI) * 1.10 + lift * 1.26 - 0.48 + 0.10, 0);
            world.spawnParticle(Particle.END_ROD, tip, 2, 0.035, 0.035, 0.035, 0);
        }
    }

    private void drawVane(World world, Location start, Location tip, Vector outward,
                          Color color, int steps, float size) {
        Location previous = start;
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            Location point = start.clone().add(tip.clone().subtract(start).multiply(t));
            point.add(outward.clone().multiply(Math.sin(t * Math.PI) * 0.075));
            line(world, previous, point, color, size);
            previous = point;
        }
    }

    private void line(World world, Location from, Location to, Color color, float size) {
        world.spawnParticle(Particle.TRAIL, from, 1, 0, 0, 0, 0,
                new Particle.Trail(to, color, 7));
        // A larger dust core gives each trail visible body rather than a hairline.
        Location midpoint = from.clone().add(to).multiply(0.5);
        dust(world, midpoint, color, size);
    }

    private void dust(World world, Location location, Color color, float size) {
        world.spawnParticle(Particle.DUST, location, 1, 0, 0, 0, 0,
                new Particle.DustOptions(color, size));
    }
}
