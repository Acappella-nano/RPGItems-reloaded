package think.rpgitems.power.impl;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.librazy.nclangchecker.LangKey;
import think.rpgitems.RPGItems;
import think.rpgitems.data.Context;
import think.rpgitems.power.*;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static think.rpgitems.Events.*;
import static think.rpgitems.power.Utils.checkCooldown;

/**
 * @Author ReinWD
 * @email ReinWDD@gmail.com
 * Wrote & Maintained by ReinWD
 * if you have any issue, please send me email or @ReinWD in issues.
 * Accepted language: 中文, English.
 */
@PowerMeta(defaultTrigger = "RIGHT_CLICK", generalInterface = PowerPlain.class)
public class PowerBeam extends BasePower implements PowerPlain, PowerRightClick, PowerLeftClick, PowerSneak, PowerSneaking, PowerSprint, PowerBowShoot, PowerHitTaken, PowerHit, PowerHurt {
    @Property
    public int length = 10;

    @Property
    public Particle particle = Particle.LAVA;

    @Property
    public int amount = 200;

    @Property
    public Mode mode = Mode.BEAM;

    @Property
    public boolean pierce = true;

    @Property
    public boolean ignoreWall = true;

    @Property
    public double damage = 20;

    @Property
    public int movementTicks = 40;

    @Property
    public double offsetX = 0;

    @Property
    public double offsetY = 0;

    @Property
    public double offsetZ = 0;

    @Property
    public double spawnsPerBlock = 2;
    double lengthPerSpawn = 1 / spawnsPerBlock;

    /**
     * Cost of this power
     */
    @Property
    public int cost = 0;
    /**
     * Cooldown time of this power
     */
    @Property
    public long cooldown = 0;

    @Property
    public boolean cone = false;

    @Property
    public double coneRange = 30;

    @Property
    public boolean homing = false;

    @Property
    public double homingAngle = 1;

    @Property
    public double homingRange = 30;

    @Property
    public HomingTargetMode homingTargetMode = HomingTargetMode.ONE_TARGET;

    @Property
    public Target homingTarget = Target.MOBS;

    @Property
    public int stepsBeforeHoming = 5;

    @Property
    public int burstCount = 1;

    @Property
    public int beamAmount = 1;

    @Property
    public int burstInterval = 1;

    @Property
    public int bounce = 0;

    @Property
    public boolean hitSelfWhenBounced = false;

    @Property
    public double gravity = 0;

    @Property
    @Serializer(ExtraDataSerializer.class)
    @Deserializer(ExtraDataSerializer.class)
    public Object extraData;

    @Property
    public double speed = 0;

    @Property
    public boolean requireHurtByEntity = true;


    /**
     * Whether to suppress the hit trigger
     */
    @Property
    public boolean suppressMelee = false;

    private Set<Material> transp = Stream.of(Material.values())
            .filter(material -> material.isBlock())
            .filter(material -> !material.isSolid() || !material.isOccluding())
            .collect(Collectors.toSet());


    @Override
    public PowerResult<Void> fire(Player player, ItemStack stack) {
        if (!checkCooldown(this, player, cooldown, true, true)) return PowerResult.cd();
        if (!getItem().consumeDurability(stack, cost)) return PowerResult.cost();
        return beam(player, stack);
    }

    @Override
    public @LangKey(skipCheck = true) String getName() {
        return "beam";
    }

    @Override
    public String displayText() {
        return null;
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
    public PowerResult<Void> sneak(Player player, ItemStack stack, PlayerToggleSneakEvent event) {
        return fire(player, stack);
    }

    @Override
    public PowerResult<Void> sneaking(Player player, ItemStack stack) {
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
    public PowerResult<Double> hit(Player player, ItemStack stack, LivingEntity entity, double damage, EntityDamageByEntityEvent event) {
        return fire(player, stack).with(event.getDamage());
    }

    @Override
    public PowerResult<Double> takeHit(Player target, ItemStack stack, double damage, EntityDamageEvent event) {
        if (!requireHurtByEntity || event instanceof EntityDamageByEntityEvent) {
            return fire(target, stack).with(event.getDamage());
        }
        return PowerResult.noop();
    }

    private PowerResult<Void> beam(LivingEntity from, ItemStack stack) {
        if (burstCount > 0) {
            for (int i = 0; i < burstCount; i++) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (cone) {
                            for (int j = 0; j < beamAmount; j++) {
                                internalFireBeam(from, stack);
                            }
                        } else {
                            internalFireBeam(from, stack);
                        }
                    }
                }.runTaskLaterAsynchronously(RPGItems.plugin, i * burstInterval);
            }
            return PowerResult.ok();
        } else {
            return internalFireBeam(from, stack);
        }
    }

    final Vector crosser = new Vector(1, 1, 1);

    private PowerResult<Void> internalFireBeam(LivingEntity from, ItemStack stack) {
        lengthPerSpawn = 1 / spawnsPerBlock;
        Location fromLocation = from.getEyeLocation();
        Vector towards = from.getEyeLocation().getDirection();

        if (cone) {
            double phi = random.nextDouble() * 360;
            double theta;
            if (coneRange > 0) {
                theta = random.nextDouble() * coneRange;
                Vector clone = towards.clone();
                Vector cross = clone.clone().add(crosser);
                Vector vertical = clone.getCrossProduct(cross).getCrossProduct(towards);
                towards.rotateAroundAxis(vertical, Math.toRadians(theta));
                towards.rotateAroundAxis(clone, Math.toRadians(phi));
            }
        }


        Entity target = null;
        if (from instanceof Player && homing) {
            target = getNextTarget(from.getEyeLocation().getDirection(), fromLocation, from);
//                    Utils.getLivingEntitiesInCone(Utils.getNearestLivingEntities(this, fromLocation, ((Player) from), Math.min(1000, length), 0), fromLocation.toVector(), homingRange, from.getEyeLocation().getDirection()).stream()
//                    .filter(livingEntity -> {
//                        switch (homingTarget) {
//                            case MOBS:
//                                return !(livingEntity instanceof Player);
//                            case PLAYERS:
//                                return livingEntity instanceof Player && !((Player) livingEntity).getGameMode().equals(GameMode.SPECTATOR);
//                            case ALL:
//                                return !(livingEntity instanceof Player) || !((Player) livingEntity).getGameMode().equals(GameMode.SPECTATOR);
//                        }
//                        return true;
//                    })
//                    .findFirst().orElse(null);
        }

        switch (mode) {
            case BEAM:
                new PlainTask(from, towards, amount, length, target, bounce, stack).runTask(RPGItems.plugin);
                break;
            case PROJECTILE:
                new MovingTask(from, towards, amount, length, target, bounce, stack).runTask(RPGItems.plugin);
                break;
        }
        return PowerResult.ok();
    }
    private Random random = new Random();

    private Vector yUnit = new Vector(0, 1, 0);

    @Override
    public PowerResult<Void> hurt(Player target, ItemStack stack, EntityDamageEvent event) {
        if (!requireHurtByEntity || event instanceof EntityDamageByEntityEvent) {
            return fire(target, stack);
        }
        return PowerResult.noop();
    }

    class PlainTask extends BukkitRunnable {
        private int bounces;
        private double length;
        private final ItemStack stack;
        private LivingEntity from;
        private Vector towards;
        private final int apS;
        private Entity target;
        boolean bounced = false;

        public PlainTask(LivingEntity from, Vector towards, int amount, double actualLength, Entity target, int bounces, ItemStack stack) {
            this.from = from;
            this.towards = towards;
            this.length = actualLength;
            this.stack = stack;
            this.apS = amount / ((int) Math.floor(actualLength));
            this.target = target;
            this.bounces = bounces;
        }

        @Override
        public void run() {
            World world = from.getWorld();
            towards.normalize();
            Location lastLocation = from.getEyeLocation();
            double lpT = length / ((double) movementTicks);
            double partsPerTick = lpT / lengthPerSpawn;
            for (int i = 0; i < movementTicks; i++) {
                boolean isStepHit = false;
                Vector step = new Vector(0, 0, 0);
                for (int j = 0; j < partsPerTick; j++) {
                    boolean isHit = tryHit(from, lastLocation, stack, bounced && hitSelfWhenBounced);
                    isStepHit = isHit || isStepHit;
                    Block block = lastLocation.getBlock();
                    if (transp.contains(block.getType())) {
                        spawnParticle(from, world, lastLocation, (int) Math.ceil(apS / partsPerTick));
                    } else if (!ignoreWall) {
                        if (bounces > 0) {
                            bounces--;
                            bounced = true;
                            makeBounce(block, towards, lastLocation.clone().subtract(step));
                        } else {
                            Context.instance().removeTemp(from.getUniqueId(), DAMAGE_SOURCE_ITEM);
                            return;
                        }
                    }
                    step = towards.clone().normalize().multiply(lengthPerSpawn);
                    lastLocation.add(step);
                    towards = addGravity(towards, partsPerTick);
                    towards = homingCorrect(towards, lastLocation, target, i, () -> target = getNextTarget(from.getEyeLocation().getDirection(), from.getEyeLocation(), from));
                }
                if (isStepHit && homingTargetMode.equals(HomingTargetMode.MULTI_TARGET)){
                    target = getNextTarget(from.getEyeLocation().getDirection(), from.getEyeLocation(), from);
                }
                if (isStepHit && !pierce) {
                    Context.instance().removeTemp(from.getUniqueId(), DAMAGE_SOURCE_ITEM);
                    return;
                }
            }
            Context.instance().removeTemp(from.getUniqueId(), DAMAGE_SOURCE_ITEM);
        }


    }

    Vector gravityVector = new Vector(0, -gravity / 20, 0);

    private Vector addGravity(Vector towards, double partsPerTick) {
        double gravityPerTick = (-gravity / 20d) / partsPerTick;
        gravityVector.setY(gravityPerTick);
        return towards.add(gravityVector);
    }

    private class MovingTask extends BukkitRunnable {
        private final LivingEntity from;
        private int bounces;
        private Vector towards;
        private final ItemStack stack;
        private final int amountPerSec;
        private final List<BukkitRunnable> runnables = new LinkedList<>();
        private Entity target;
        boolean bounced = false;

        public MovingTask(LivingEntity from, Vector towards, int apS, double actualLength, Entity target, int bounces, ItemStack stack) {
            this.from = from;
            this.towards = towards;
            this.stack = stack;
            this.amountPerSec = apS / ((int) Math.floor(actualLength));
            this.target = target;
            this.bounces = bounces;
        }

        @Override
        public void run() {
            World world = from.getWorld();
            double lpT = ((double) length) / ((double) movementTicks);
            double partsPerTick = lpT / lengthPerSpawn;
            Location lastLocation = from.getEyeLocation();
            towards.normalize();
            final int[] finalI = {0};
            BukkitRunnable bukkitRunnable = new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        boolean isStepHit = false;
                        Vector step = new Vector(0, 0, 0);
                        for (int k = 0; k < partsPerTick; k++) {
                            boolean isHit = tryHit(from, lastLocation, stack, bounced && hitSelfWhenBounced);
                            isStepHit = isHit || isStepHit;
                            Block block = lastLocation.getBlock();
                            if (transp.contains(block.getType())) {
                                spawnParticle(from, world, lastLocation, (int) (amountPerSec / spawnsPerBlock));
                            } else if (!ignoreWall) {
                                if (bounces > 0) {
                                    bounces--;
                                    bounced = true;
                                    makeBounce(block, towards, lastLocation.clone().subtract(step));
                                } else {
                                    this.cancel();
                                    return;
                                }
                            }
                            step = towards.clone().normalize().multiply(lengthPerSpawn);
                            lastLocation.add(step);
                            towards = addGravity(towards, partsPerTick);

                            towards = homingCorrect(towards, lastLocation, target, finalI[0], () -> target = getNextTarget(from.getEyeLocation().getDirection(), from.getEyeLocation(), from));
                        }
                        if (isStepHit && homingTargetMode.equals(HomingTargetMode.MULTI_TARGET)){
                            target = getNextTarget(from.getEyeLocation().getDirection(), from.getEyeLocation(), from);
                        }
                        if (isStepHit && !pierce) {
                            this.cancel();
                            Context.instance().removeTemp(from.getUniqueId(), DAMAGE_SOURCE_ITEM);
                            return;
                        }
                        if (finalI[0] >= movementTicks) {
                            this.cancel();
                            Context.instance().removeTemp(from.getUniqueId(), DAMAGE_SOURCE_ITEM);
                        }
                        finalI[0]++;
                    } catch (Exception ex) {
                        from.getServer().getLogger().log(Level.WARNING, "", ex);
                        this.cancel();
                        Context.instance().removeTemp(from.getUniqueId(), DAMAGE_SOURCE_ITEM);
                    }
                }
            };
            bukkitRunnable.runTaskTimer(RPGItems.plugin, 0, 1);
        }
    }

    private void makeBounce(Block block, Vector towards, Location lastLocation) {
        RayTraceResult rayTraceResult = block.rayTrace(lastLocation, towards, towards.length(), FluidCollisionMode.NEVER);
        if (rayTraceResult == null) {
            return;
        } else {
            towards.rotateAroundNonUnitAxis(rayTraceResult.getHitBlockFace().getDirection(), Math.toRadians(180)).multiply(-1);
        }
    }

    private Vector homingCorrect(Vector towards, Location lastLocation, Entity target, int i, Runnable runnable) {
        if (target == null || i < stepsBeforeHoming) {
            return towards;
        }
        if (target.isDead()){
            runnable.run();
        }
        Location targetLocation;
        if (target instanceof LivingEntity) {
            targetLocation = ((LivingEntity) target).getEyeLocation();
        } else {
            targetLocation = target.getLocation();
        }

        Vector clone = towards.clone();
        Vector targetDirection = targetLocation.toVector().subtract(lastLocation.toVector());
        float angle = clone.angle(targetDirection);
        Vector crossProduct = clone.clone().getCrossProduct(targetDirection);
        double actualAng = homingAngle / spawnsPerBlock;
        if (angle > Math.toRadians(actualAng)) {
            //↓a legacy but functionable way to rotate.
            //will create a enlarging circle
            clone.add(clone.clone().getCrossProduct(crossProduct).normalize().multiply(-1 * Math.tan(actualAng)));
            // ↓a better way to rotate.
            // will create a exact circle.
//            clone.rotateAroundAxis(crossProduct, actualAng);
        } else {
            clone = targetDirection.normalize();
        }
        return clone;
    }

    private LivingEntity getNextTarget(Vector towards, Location lastLocation, Entity from) {
        int radius = Math.min(this.length, 300);
        return Utils.getLivingEntitiesInCone(from.getNearbyEntities(radius, this.length, this.length).stream()
                .filter(entity -> entity instanceof LivingEntity && !entity.equals(from) && !entity.isDead())
                .map(entity -> ((LivingEntity) entity))
                        .collect(Collectors.toList())
                , lastLocation.toVector(), homingRange, towards).stream()
                .filter(livingEntity -> {
                    switch (homingTarget) {
                        case MOBS:
                            return !(livingEntity instanceof Player);
                        case PLAYERS:
                            return livingEntity instanceof Player && !((Player) livingEntity).getGameMode().equals(GameMode.SPECTATOR);
                        case ALL:
                            return !(livingEntity instanceof Player) || !((Player) livingEntity).getGameMode().equals(GameMode.SPECTATOR);
                    }
                    return true;
                })
                .findFirst().orElse(null);
    }

    private void spawnParticle(LivingEntity from, World world, Location lastLocation, int i) {
        if ((lastLocation.distance(from.getEyeLocation()) < 1)) {
            return;
        }
//        if (from instanceof Player) {
//            ((Player) from).spawnParticle(this.particle, lastLocation, i / 2, offsetX, offsetY, offsetZ, speed, extraData);
//        }
        world.spawnParticle(this.particle, lastLocation, i, offsetX, offsetY, offsetZ, speed, extraData, true);
    }

    private boolean tryHit(LivingEntity from, Location loc, ItemStack stack, boolean canHitSelf) {

        double offsetLength = new Vector(offsetX, offsetY, offsetZ).length();
        double length = Double.isNaN(offsetLength) ? 0 : Math.max(offsetLength, 10);
        Collection<Entity> candidates = from.getWorld().getNearbyEntities(loc, length, length, length);
        boolean result = false;
        if (!pierce) {
            List<Entity> collect = candidates.stream()
                    .filter(entity -> (entity instanceof LivingEntity) && (canHitSelf || !entity.equals(from)) && !entity.isDead())
                    .filter(entity -> canHit(loc, entity))
                    .limit(1)
                    .collect(Collectors.toList());
            if (!collect.isEmpty()) {
                Entity entity = collect.get(0);
                if (entity instanceof LivingEntity) {
                    Context.instance().putTemp(from.getUniqueId(), DAMAGE_SOURCE, getNamespacedKey().toString());
                    Context.instance().putTemp(from.getUniqueId(), OVERRIDING_DAMAGE, damage);
                    Context.instance().putTemp(from.getUniqueId(), SUPPRESS_MELEE, suppressMelee);
                    Context.instance().putTemp(from.getUniqueId(), DAMAGE_SOURCE_ITEM, stack);
                    ((LivingEntity) entity).damage(damage, from);
                    Context.instance().putTemp(from.getUniqueId(), SUPPRESS_MELEE, null);
                    Context.instance().putTemp(from.getUniqueId(), OVERRIDING_DAMAGE, null);
                    Context.instance().putTemp(from.getUniqueId(), DAMAGE_SOURCE, null);
                    Context.instance().removeTemp(from.getUniqueId(), DAMAGE_SOURCE_ITEM);
                }
                return true;
            }
        } else {
            List<Entity> collect = candidates.stream()
                    .filter(entity -> (entity instanceof LivingEntity) && (canHitSelf || !entity.equals(from)))
                    .filter(entity -> canHit(loc, entity))
                    .collect(Collectors.toList());
            Context.instance().putTemp(from.getUniqueId(), DAMAGE_SOURCE, getNamespacedKey().toString());
            Context.instance().putTemp(from.getUniqueId(), OVERRIDING_DAMAGE, damage);
            Context.instance().putTemp(from.getUniqueId(), SUPPRESS_MELEE, suppressMelee);
            Context.instance().putTemp(from.getUniqueId(), DAMAGE_SOURCE_ITEM, stack);

            if (!collect.isEmpty()) {
                collect.stream()
                        .map(entity -> ((LivingEntity) entity))
                        .forEach(livingEntity -> {
                            livingEntity.damage(damage, from);
                        });
                result = true;
            }
            Context.instance().putTemp(from.getUniqueId(), SUPPRESS_MELEE, null);
            Context.instance().putTemp(from.getUniqueId(), OVERRIDING_DAMAGE, null);
            Context.instance().putTemp(from.getUniqueId(), DAMAGE_SOURCE, null);
            Context.instance().removeTemp(from.getUniqueId(), DAMAGE_SOURCE_ITEM);

        }
        return result;
    }

    private boolean canHit(Location loc, Entity entity) {
        BoundingBox boundingBox = entity.getBoundingBox();
        BoundingBox particleBox;
        double x = Math.max(offsetX, 0.1);
        double y = Math.max(offsetY, 0.1);
        double z = Math.max(offsetZ, 0.1);
        particleBox = BoundingBox.of(loc, x + 0.1, y + 0.1, z + 0.1);
        return boundingBox.overlaps(particleBox) || particleBox.overlaps(boundingBox);
    }

    private enum Mode {
        BEAM,
        PROJECTILE,
        ;

    }

    public class ExtraDataSerializer implements Getter, Setter {
        @Override
        public String get(Object object) {
            if (object instanceof Particle.DustOptions) {
                Color color = ((Particle.DustOptions) object).getColor();
                return color.getRed() + "," + color.getGreen() + "," + color.getBlue() + "," + ((Particle.DustOptions) object).getSize();
            }
            return "";
        }

        @Override
        public Optional set(String value) throws IllegalArgumentException {
            String[] split = value.split(",", 4);
            int r = Integer.parseInt(split[0]);
            int g = Integer.parseInt(split[1]);
            int b = Integer.parseInt(split[2]);
            float size = Float.parseFloat(split[3]);
            return Optional.of(new Particle.DustOptions(Color.fromRGB(r, g, b), size));
        }
    }

    enum Target {
        MOBS, PLAYERS, ALL
    }

    private enum HomingTargetMode {
        ONE_TARGET, MULTI_TARGET;
    }
}
