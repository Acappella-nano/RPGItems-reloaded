package think.rpgitems.power.impl;

import cat.nyaa.nyaacore.Pair;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import think.rpgitems.RPGItems;
import think.rpgitems.power.*;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static think.rpgitems.power.Utils.checkCooldown;

@PowerMeta(defaultTrigger = "RIGHT_CLICK", generalInterface = PowerPlain.class)
public class PowerScoreboard extends BasePower implements PowerHit, PowerHitTaken, PowerHurt, PowerLeftClick, PowerRightClick, PowerOffhandClick, PowerProjectileHit, PowerSneak, PowerSprint, PowerOffhandItem, PowerMainhandItem, PowerTick, PowerSneaking, PowerPlain, PowerBowShoot {

    /**
     * Tag(s) to add and remove, according to the following format
     * `TO_ADD,!TO_REMOVE`
     */
    @Property
    public String tag;

    /**
     * Team(s) to join and leave, according to the following format
     * `JOIN,!LEAVE`
     */
    @Property
    public String team;

    /**
     * Cooldown time of this power
     */
    @Property
    public long cooldown = 0;

    @Property
    public ScoreboardOperation scoreOperation = ScoreboardOperation.NO_OP;

    @Property
    public int value = 0;

    @Property
    public String objective = "";

    /**
     * Cost of this power
     */
    @Property
    public int cost = 0;

    @Property
    public boolean reverseTagAfterDelay = false;

    @Property
    public long delay = 20;

    @Property
    public boolean abortOnSuccess = false;

    private static LoadingCache<String, Pair<Set<String>, Set<String>>> teamCache = CacheBuilder
            .newBuilder()
            .concurrencyLevel(1)
            .expireAfterAccess(1, TimeUnit.DAYS)
            .build(CacheLoader.from(PowerSelector::parse));

    private static LoadingCache<String, Pair<Set<String>, Set<String>>> tagCache = CacheBuilder
            .newBuilder()
            .concurrencyLevel(1)
            .expireAfterAccess(1, TimeUnit.DAYS)
            .build(CacheLoader.from(PowerSelector::parse));

    @Override
    public PowerResult<Void> fire(Player player, ItemStack stack) {
        if (!checkCooldown(this, player, cooldown, true, true)) return PowerResult.cd();
        if (!getItem().consumeDurability(stack, cost)) return PowerResult.cost();

        Scoreboard scoreboard = player.getScoreboard();

        Objective objective = scoreboard.getObjective(this.objective);
        if (objective != null) {
            Score sc = objective.getScore(player.getName());
            int ori = sc.getScore();
            switch (scoreOperation) {
                case ADD_SCORE:
                    sc.setScore(ori+value);
                    break;
                case SET_SCORE:
                    sc.setScore(value);
                    break;
                case RESET_SCORE:
                    sc.setScore(0);
                    break;
                default:
            }
        }
        if (this.team != null) {
            Pair<Set<String>, Set<String>> team = teamCache.getUnchecked(this.team);
            team.getKey().stream().map(scoreboard::getTeam).forEach(t -> t.addEntry(player.getName()));
            team.getValue().stream().map(scoreboard::getTeam).forEach(t -> t.removeEntry(player.getName()));
        }

        if (this.tag != null) {
            Pair<Set<String>, Set<String>> tag = tagCache.getUnchecked(this.tag);
            tag.getKey().forEach(player::addScoreboardTag);
            tag.getValue().forEach(player::removeScoreboardTag);
            if (reverseTagAfterDelay) {
                (new BukkitRunnable() {
                    @Override
                    public void run() {
                        tag.getKey().forEach(player::removeScoreboardTag);
                        tag.getValue().forEach(player::addScoreboardTag);
                    }
                }).runTaskLater(RPGItems.plugin, delay);
            }
        }
        return abortOnSuccess ? PowerResult.abort() : PowerResult.ok();
    }

    @Override
    public PowerResult<Void> leftClick(Player player, ItemStack stack, PlayerInteractEvent event) {
        return fire(player, stack);
    }

    @Override
    public PowerResult<Void> rightClick(Player player, ItemStack stack, PlayerInteractEvent event) {
        return fire(player, stack);
    }

    @Override
    public String getName() {
        return "scoreboard";
    }

    @Override
    public String displayText() {
        return null;
    }

    @Override
    public PowerResult<Double> hit(Player player, ItemStack stack, LivingEntity entity, double damage, EntityDamageByEntityEvent event) {
        return fire(player, stack).with(damage);
    }

    @Property
    public boolean requireHurtByEntity = true;

    @Override
    public PowerResult<Double> takeHit(Player target, ItemStack stack, double damage, EntityDamageEvent event) {
        if (!requireHurtByEntity || event instanceof EntityDamageByEntityEvent) {
            return fire(target, stack).with(damage);
        }
        return PowerResult.noop();
    }

    @Override
    public PowerResult<Void> hurt(Player target, ItemStack stack, EntityDamageEvent event) {
        if (!requireHurtByEntity || event instanceof EntityDamageByEntityEvent) {
            return fire(target, stack);
        }
        return PowerResult.noop();
    }

    @Override
    public PowerResult<Void> offhandClick(Player player, ItemStack stack, PlayerInteractEvent event) {
        return fire(player, stack);
    }

    @Override
    public PowerResult<Void> projectileHit(Player player, ItemStack stack, ProjectileHitEvent event) {
        return fire(player, stack);
    }

    @Override
    public PowerResult<Void> sneak(Player player, ItemStack stack, PlayerToggleSneakEvent event) {
        return fire(player, stack);
    }

    @Override
    public PowerResult<Void> sprint(Player player, ItemStack stack, PlayerToggleSprintEvent event) {
        return fire(player, stack);
    }

    @Override
    public PowerResult<Float> bowShoot(Player player, ItemStack itemStack, EntityShootBowEvent e) {
        return fire(player, itemStack).with(e.getForce());
    }

    @Override
    public PowerResult<Boolean> swapToMainhand(Player player, ItemStack stack, PlayerSwapHandItemsEvent event) {
        return fire(player, stack).with(true);
    }

    @Override
    public PowerResult<Boolean> swapToOffhand(Player player, ItemStack stack, PlayerSwapHandItemsEvent event) {
        return fire(player, stack).with(true);
    }

    @Override
    public PowerResult<Void> tick(Player player, ItemStack stack) {
        return fire(player, stack);
    }

    @Override
    public PowerResult<Void> sneaking(Player player, ItemStack stack) {
        return fire(player, stack);
    }
    public enum ScoreboardOperation {
        NO_OP, ADD_SCORE, SET_SCORE, RESET_SCORE;
    }
}
