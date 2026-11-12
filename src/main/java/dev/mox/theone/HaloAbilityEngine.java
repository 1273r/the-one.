package dev.mox.theone;

import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Stage-scaled, cooldown-gated thrown halo ability. */
public final class HaloAbilityEngine {
    private static final long COOLDOWN_TICKS = 240L;
    private final TheOnePlugin plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, BukkitTask> activeTasks = new HashMap<>();
    private final Set<UUID> inFlight = new HashSet<>();

    public HaloAbilityEngine(TheOnePlugin plugin) { this.plugin = plugin; }

    public boolean isInFlight(UUID playerId) {
        return inFlight.contains(playerId);
    }

    public void cast(Player caster, Stage stage) {
        UUID id = caster.getUniqueId();
        if (activeTasks.containsKey(id)) {
            caster.sendMessage("§bYour halo is already in flight.");
            return;
        }

        long now = System.currentTimeMillis() / 50L;
        long ready = cooldowns.getOrDefault(id, 0L);
        if (now < ready) {
            long seconds = Math.max(1L, (ready - now + 19L) / 20L);
            caster.sendMessage("§bHalo recharging: §f" + seconds + "s");
            return;
        }

        cooldowns.put(id, now + COOLDOWN_TICKS);
        caster.sendMessage("§b✦ HALO RELEASED");
        caster.playSound(caster.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.9f, 1.55f);
        launch(caster, stage);
    }

    private void launch(Player caster, Stage stage) {
        UUID id = caster.getUniqueId();
        World world = caster.getWorld();
        Vector direction = caster.getEyeLocation().getDirection().normalize();
        Location position = caster.getEyeLocation().add(direction.clone().multiply(0.9));
        Color primary = stage == Stage.THE_ONE ? Color.fromRGB(255, 220, 135) : stage.color();
        Vector reference = Math.abs(direction.getY()) > 0.92
                ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
        Vector right = direction.clone().crossProduct(reference).normalize();
        Vector up = right.clone().crossProduct(direction).normalize();

        inFlight.add(id);
        final int[] age = {0};
        final BukkitTask[] holder = new BukkitTask[1];
        holder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!caster.isOnline() || caster.isDead() || !world.equals(caster.getWorld())
                    || age[0] >= 36) {
                finish(id, holder[0]);
                return;
            }

            Location previous = position.clone();
            position.add(direction.clone().multiply(1.22));
            var blockHit = world.rayTraceBlocks(previous, direction, 1.22,
                    FluidCollisionMode.NEVER, true);
            if (blockHit != null && blockHit.getHitBlock() != null) {
                drawImpact(world, blockHit.getHitPosition().toLocation(world), primary);
                finish(id, holder[0]);
                return;
            }

            drawProjectile(world, position, primary, age[0]++, right, up);
            for (LivingEntity target : world.getNearbyLivingEntities(position, 1.0, 1.0, 1.0)) {
                if (target.equals(caster) || target.isDead()) continue;
                if (target instanceof Player other && other.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
                BoundingBox hitbox = target.getBoundingBox().expand(0.30);
                if (!hitbox.contains(position.toVector())) continue;

                impact(caster, target, stage, position, primary);
                finish(id, holder[0]);
                return;
            }
        }, 0L, 1L);
        activeTasks.put(id, holder[0]);
    }

    private void finish(UUID id, BukkitTask task) {
        if (task != null) task.cancel();
        activeTasks.remove(id);
        inFlight.remove(id);
    }

    public void clear(UUID id) {
        BukkitTask task = activeTasks.remove(id);
        if (task != null) task.cancel();
        inFlight.remove(id);
        cooldowns.remove(id);
    }

    public void clearAll() {
        for (BukkitTask task : activeTasks.values()) task.cancel();
        activeTasks.clear();
        inFlight.clear();
        cooldowns.clear();
    }

    private void drawProjectile(World world, Location center, Color color, int age, Vector right, Vector up) {
        double rotation = age * 0.32;
        for (int i = 0; i < 32; i++) {
            double angle = Math.PI * 2.0 * i / 32.0 + rotation;
            Vector offset = right.clone().multiply(Math.cos(angle) * 0.46)
                    .add(up.clone().multiply(Math.sin(angle) * 0.46));
            world.spawnParticle(Particle.DUST, center.clone().add(offset), 1, 0, 0, 0, 0,
                    new Particle.DustOptions(color, 1.65f));
        }
        world.spawnParticle(Particle.END_ROD, center, 1, 0, 0, 0, 0);
    }

    private void drawImpact(World world, Location at, Color color) {
        world.spawnParticle(Particle.FLASH, at, 1);
        world.spawnParticle(Particle.DUST, at, 18, 0.18, 0.18, 0.18, 0,
                new Particle.DustOptions(color, 1.5f));
        world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.25f);
    }

    private void impact(Player caster, LivingEntity target, Stage stage, Location at, Color color) {
        double damage = switch (stage) {
            case AWAKENING -> 2.0;
            case WITNESS -> 3.0;
            case ASCENDED -> 4.0;
            case EXALTED -> 4.5;
            case DIVINE -> 5.0;
            case UNBOUND -> 5.5;
            case THE_ONE -> 6.0;
        };
        int duration = switch (stage) {
            case AWAKENING -> 20;
            case WITNESS -> 40;
            case ASCENDED -> 50;
            case EXALTED -> 60;
            case DIVINE -> 70;
            case UNBOUND -> 80;
            case THE_ONE -> 90;
        };

        target.damage(damage, caster);
        switch (stage) {
            case AWAKENING -> target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 0, true, true, true));
            case WITNESS -> target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, duration, 0, true, true, true));
            case ASCENDED -> {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 0, true, true, true));
                target.setVelocity(target.getVelocity().multiply(0.55));
            }
            case EXALTED -> {
                target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, duration, 0, true, true, true));
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 0, true, true, true));
            }
            case DIVINE -> {
                target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, duration, 0, true, true, true));
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 1, true, true, true));
            }
            case UNBOUND, THE_ONE -> {
                target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, duration, 1, true, true, true));
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 1, true, true, true));
            }
        }
        drawImpact(at.getWorld(), at, color);
        at.getWorld().playSound(at, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f,
                0.75f + stage.ordinal() * 0.12f);
    }
}
