package think.rpgitems.power.impl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import think.rpgitems.I18n;
import think.rpgitems.power.*;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static think.rpgitems.power.Utils.checkCooldown;

/**
 * Power rescue.
 * <p>
 * The rescue power teleports the user to spawn (or to their bed when {@link #useBed} is active)
 * or rescue them in place when {@link #inPlace}
 * when their health gets below the {@link #healthTrigger} while in combat with an enemy
 * or when they takes a damage greater than {@link #damageTrigger}
 * </p>
 */
@SuppressWarnings("WeakerAccess")
@PowerMeta(immutableTrigger = true)
public class PowerRescue extends BasePower implements PowerHurt, PowerHitTaken {
    private static Cache<UUID, Long> rescueTime = CacheBuilder.newBuilder().expireAfterWrite(5, TimeUnit.SECONDS).build();
    /**
     * Health trigger of rescue
     */
    @Property(order = 1)
    public int healthTrigger = 4;
    /**
     * Whether use bed instead of home
     */
    @Property(order = 2)
    public boolean useBed = true;
    /**
     * Whether rescue in place instead of teleport
     */
    @Property(order = 3)
    public boolean inPlace = false;
    /**
     * Cooldown time of this power
     */
    @Property(order = 0)
    public long cooldown = 0;
    /**
     * Cost of this power
     */
    @Property
    public int cost = 0;
    /**
     * Damage trigger of rescue
     */
    @Property
    public double damageTrigger = 1024;

    @Override
    public String displayText() {
        return I18n.format("power.rescue.display", ((double) healthTrigger) / 2, (double) cooldown / 20d);
    }

    @Override
    public String getName() {
        return "rescue";
    }

    // shouldn't be called if takeHit works. leave it as-is now
    @Override
    public PowerResult<Void> hurt(Player target, ItemStack stack, EntityDamageEvent event) {
        double health = target.getHealth() - event.getFinalDamage();
        if (health > healthTrigger) return PowerResult.noop();
        rescue(target, stack, event, false);
        return PowerResult.ok();
    }

    @Override
    public PowerResult<Double> takeHit(Player target, ItemStack stack, double damage, EntityDamageEvent event) {
        double health = target.getHealth() - event.getFinalDamage();
        if (health > healthTrigger && event.getFinalDamage() < damageTrigger) return PowerResult.noop();
        Long last = rescueTime.getIfPresent(target.getUniqueId());
        if (last != null && System.currentTimeMillis() - last < 3000) {
            event.setCancelled(true);
            return PowerResult.ok(0.0);
        }
        return rescue(target, stack, event, true);
    }

    private PowerResult<Double> rescue(Player target, ItemStack stack, EntityDamageEvent event, boolean canceled) {
        if (!checkCooldown(this, target, cooldown, true, true)) return PowerResult.cd();
        if (!getItem().consumeDurability(stack, cost)) return PowerResult.cost();
        rescueTime.put(target.getUniqueId(), System.currentTimeMillis());
        target.sendMessage(I18n.format("power.rescue.info"));
        DamageCause cause = event.getCause();
        if (!canceled) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST, 2, 255));
            target.setHealth(healthTrigger + event.getDamage());
        } else {
            event.setCancelled(true);
        }
        target.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 200, 10), true);
        target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 400, 2), true);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 400, 2), true);
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 10, 1);

        if (inPlace && cause != DamageCause.DRAGON_BREATH
                    && cause != DamageCause.DROWNING
                    && cause != DamageCause.SUFFOCATION
                    && cause != DamageCause.VOID) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 160, 10));
        } else if (useBed && target.getBedSpawnLocation() != null)
            target.teleport(target.getBedSpawnLocation());
        else
            target.teleport(target.getWorld().getSpawnLocation());

        return PowerResult.ok(0.0);
    }
}
