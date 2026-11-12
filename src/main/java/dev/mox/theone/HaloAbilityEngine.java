package dev.mox.theone;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Stage-scaled, cooldown-gated thrown halo ability. */
public final class HaloAbilityEngine {
    private static final long COOLDOWN_TICKS = 240L;
    private final TheOnePlugin plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Set<UUID> inFlight = new HashSet<>();

    public boolean isInFlight(UUID playerId) { return inFlight.contains(playerId); }

    public HaloAbilityEngine(TheOnePlugin plugin) { this.plugin = plugin; }

    public void cast(Player caster, Stage stage) {
        long now = System.currentTimeMillis() / 50L;
        long ready = cooldowns.getOrDefault(caster.getUniqueId(), 0L);
        if (now < ready) {
            long seconds = Math.max(1L, (ready - now + 19L) / 20L);
            caster.sendMessage("§bHalo recharging: §f" + seconds + "s");
            return;
        }
        cooldowns.put(caster.getUniqueId(), now + COOLDOWN_TICKS);
        inFlight.add(caster.getUniqueId());
        launch(caster, stage);
    }

    private void launch(Player caster, Stage stage) {
        Location eye = caster.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        Location position = eye.clone().add(direction.clone().multiply(0.9));
        World world = caster.getWorld();
        Color primary = stage == Stage.THE_ONE ? Color.fromRGB(255, 220, 135) : stage.color();
        world.playSound(caster.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.35f);

        final int[] age = {0};
        final BukkitTask[] task = new BukkitTask[1];
        task[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!caster.isOnline() || caster.isDead() || !position.getWorld().equals(caster.getWorld())
                    || age[0]++ >= 36) {
                task[0].cancel();
                inFlight.remove(caster.getUniqueId());
                return;
            }
            position.add(direction.clone().multiply(1.22));
            drawProjectile(world, position, primary, age[0]);
            for (LivingEntity target : world.getNearbyLivingEntities(position, 0.78, 0.78, 0.78)) {
                if (target.equals(caster) || target.isDead()) continue;
                if (target instanceof Player other && other.getGameMode().name().equals("SPECTATOR")) continue;
                if (target.getLocation().distanceSquared(position) > 1.15) continue;
                impact(caster, target, stage, position, primary);
                task[0].cancel();
                inFlight.remove(caster.getUniqueId());
                return;
            }
        }, 0L, 1L);
    }

    private void drawProjectile(World world, Location center, Color color, int age) {
        double rotation = age * 0.32;
        for (int i = 0; i < 24; i++) {
            double angle = Math.PI * 2.0 * i / 24.0 + rotation;
            Location point = center.clone().add(Math.cos(angle) * 0.43, Math.sin(angle) * 0.43, 0);
            world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(color, 1.45f));
        }
        world.spawnParticle(Particle.END_ROD, center, 1, 0, 0, 0, 0);
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
        at.getWorld().spawnParticle(Particle.FLASH, at, 1);
        at.getWorld().spawnParticle(Particle.DUST, at, 28, 0.28, 0.28, 0.28, 0,
                new Particle.DustOptions(color, 1.7f));
        at.getWorld().playSound(at, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.75f + stage.ordinal() * 0.12f);
        at.getWorld().playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.1f + stage.ordinal() * 0.08f);
    }
}
