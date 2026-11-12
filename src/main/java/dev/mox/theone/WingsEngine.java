package dev.mox.theone;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Cosmetic, animated wing silhouette for the current holder.
 * The wings are drawn from lightweight dust/rod particles and never affect gameplay.
 */
public final class WingsEngine {
    private static final Color ICE = Color.fromRGB(145, 232, 255);
    private static final Color WHITE = Color.fromRGB(235, 248, 255);
    private static final Color GOLD = Color.fromRGB(255, 218, 145);
    private final TheOnePlugin plugin;
    private long tick;

    public WingsEngine(TheOnePlugin plugin) {
        this.plugin = plugin;
    }

    public void tick() {
        tick += 3;
        if (!plugin.getConfig().getBoolean("wings.enabled", true)) return;
        if (plugin.getHolder() == null) return;

        Player player = plugin.getServer().getPlayer(plugin.getHolder());
        if (player == null || !player.isOnline() || player.isDead()) return;

        Location base = player.getLocation().clone().add(0, 1.48, 0);
        World world = base.getWorld();
        if (world == null) return;

        // Bukkit yaw 0 faces +Z. Place the wing roots just behind the player's shoulders.
        double yaw = Math.toRadians(player.getLocation().getYaw());
        Vector right = new Vector(Math.cos(yaw), 0, Math.sin(yaw));
        Vector back = new Vector(Math.sin(yaw), 0, -Math.cos(yaw));
        base.add(back.clone().multiply(0.30));

        // A slow, readable flap: one complete cycle about every 1.8 seconds.
        double flap = Math.sin(tick * 0.058);
        double lift = flap * 0.34;
        double fold = 0.18 + (1.0 - flap) * 0.12;

        for (int side : new int[]{-1, 1}) {
            drawWing(world, base, right, side, lift, fold);
        }
    }

    private void drawWing(World world, Location root, Vector right, int side, double lift, double fold) {
        Vector outward = right.clone().multiply(side);
        Location[] featherTips = new Location[7];

        // Six long feather vanes fan outward and slightly upward from each shoulder.
        for (int feather = 0; feather < 6; feather++) {
            double u = feather / 5.0;
            double span = 0.55 + u * 1.38;
            double sweep = 0.12 + u * 0.42;
            double rise = lift + Math.sin(u * Math.PI * 0.82) * 0.42 - fold * u;
            Location tip = root.clone()
                    .add(outward.clone().multiply(span))
                    .add(0, rise + (feather - 2.5) * 0.055, 0)
                    .add(right.clone().multiply(side * sweep * 0.0));
            featherTips[feather] = tip;

            Location start = root.clone().add(outward.clone().multiply(0.12 + u * 0.10));
            int steps = 4;
            for (int step = 0; step <= steps; step++) {
                double t = step / (double) steps;
                Location point = start.clone().add(tip.clone().subtract(start).multiply(t));
                // Feather vanes have a subtle curved edge instead of a straight particle stick.
                point.add(outward.clone().multiply(Math.sin(t * Math.PI) * 0.075));
                Color color = feather % 3 == 0 ? GOLD : feather % 3 == 1 ? WHITE : ICE;
                dust(world, point, color, step == steps ? 1.18f : 0.88f);
            }
        }

        // Outer leading edge ties the feathers together into a single wing silhouette.
        Location previous = root.clone();
        for (int i = 0; i < 12; i++) {
            double u = (i + 1) / 12.0;
            double span = 1.95 * u;
            double rise = lift + Math.sin(u * Math.PI * 0.82) * 0.42 - fold * u;
            Location next = root.clone().add(outward.clone().multiply(span))
                    .add(0, rise + 0.13, 0);
            dust(world, previous.clone().add(next).multiply(0.5), i % 3 == 0 ? GOLD : ICE, 1.05f);
            previous = next;
        }

        // A small bright joint at each shoulder and a glint at the outer tip.
        dust(world, root.clone().add(outward.clone().multiply(0.16)), WHITE, 1.45f);
        if (Math.floorMod(tick, 2) == 0) {
            world.spawnParticle(Particle.END_ROD, featherTips[5], 1, 0, 0, 0, 0);
        }
    }

    private void dust(World world, Location location, Color color, float size) {
        world.spawnParticle(Particle.DUST, location, 1, 0, 0, 0, 0,
                new Particle.DustOptions(color, size));
    }
}
