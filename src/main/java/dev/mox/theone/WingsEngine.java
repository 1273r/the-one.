package dev.mox.theone;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.UUID;

/** Compact, fast-flapping cosmetic wings with no delayed trail particles. */
public final class WingsEngine {
    private static final Color ICE = Color.fromRGB(125, 225, 255);
    private static final Color WHITE = Color.fromRGB(242, 250, 255);
    private static final Color GOLD = Color.fromRGB(255, 218, 145);
    private final TheOnePlugin plugin;
    private long tick;

    public WingsEngine(TheOnePlugin plugin) { this.plugin = plugin; }

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
        Location shoulders = body.clone().add(0, 1.48, 0).add(back.clone().multiply(0.28));
        double beat = Math.sin(tick * 0.19);
        double lift = beat * 0.30;
        double sweep = 0.18 + (1.0 - beat) * 0.07;
        drawWing(world, shoulders, right, back, -1, lift, sweep);
        drawWing(world, shoulders, right, back, 1, lift, sweep);
    }

    private void drawWing(World world, Location root, Vector right, Vector back,
                          int side, double lift, double sweep) {
        Vector outward = right.clone().multiply(side);
        for (int feather = 0; feather < 9; feather++) {
            double u = feather / 8.0;
            double span = 0.30 + u * 2.35;
            double rise = 0.12 + Math.sin(u * Math.PI * 0.82) * 1.12
                    - u * 0.24 + lift * (0.30 + u * 0.70);
            Location tip = root.clone().add(outward.clone().multiply(span))
                    .add(back.clone().multiply(sweep * (0.25 + u * 0.75))).add(0, rise, 0);
            Location base = root.clone().add(outward.clone().multiply(0.10 + u * 0.18))
                    .add(back.clone().multiply(0.05));
            Color color = feather % 6 == 0 ? GOLD : feather % 2 == 0 ? ICE : WHITE;
            drawFeather(world, base, tip, outward, color, 4, 1.45f);
        }
        for (int feather = 0; feather < 6; feather++) {
            double u = feather / 5.0;
            Location tip = root.clone().add(outward.clone().multiply(0.28 + u * 1.95))
                    .add(back.clone().multiply(sweep * (0.35 + u * 0.55) + 0.12))
                    .add(0, -0.12 + Math.sin(u * Math.PI * 0.75) * 0.42 + lift * 0.35, 0);
            Location base = root.clone().add(outward.clone().multiply(0.12 + u * 0.12));
            drawFeather(world, base, tip, outward, feather % 3 == 0 ? ICE : WHITE, 3, 1.25f);
        }
        for (int i = 0; i <= 10; i++) {
            double u = i / 10.0;
            Location edge = root.clone().add(outward.clone().multiply(2.68 * u))
                    .add(back.clone().multiply(sweep * (0.25 + u * 0.75)))
                    .add(0, 0.12 + Math.sin(u * Math.PI * 0.82) * 1.12
                            - u * 0.24 + lift * (0.30 + u * 0.70), 0);
            dust(world, edge, u > 0.82 ? GOLD : ICE, 1.5f);
        }
    }

    private void drawFeather(World world, Location base, Location tip, Vector outward,
                             Color color, int steps, float size) {
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            Location point = base.clone().add(tip.clone().subtract(base).multiply(t));
            point.add(outward.clone().multiply(Math.sin(t * Math.PI) * 0.045));
            dust(world, point, color, size);
        }
    }

    private void dust(World world, Location location, Color color, float size) {
        world.spawnParticle(Particle.DUST, location, 1, 0, 0, 0, 0,
                new Particle.DustOptions(color, size));
    }
}
