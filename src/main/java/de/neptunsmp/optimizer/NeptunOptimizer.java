package de.neptunsmp.optimizer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class NeptunOptimizer extends JavaPlugin implements Listener {
    private NamespacedKey mobCountKey;
    private BukkitTask mergeTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        mobCountKey = new NamespacedKey(this, "mob_stack_count");
        Bukkit.getPluginManager().registerEvents(this, this);
        mergeTask = Bukkit.getScheduler().runTaskTimer(this, this::runMerges, 40L,
                Math.max(10L, getConfig().getLong("merge-interval-ticks", 20L)));
        getLogger().info("NeptunOptimizer enabled.");
    }

    @Override
    public void onDisable() {
        if (mergeTask != null) mergeTask.cancel();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("neptunoptimizer.admin")) {
            sender.sendMessage(ChatColor.RED + "Dafür hast du keine Rechte.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(ChatColor.AQUA + "NeptunOptimizer " + getDescription().getVersion());
            sender.sendMessage(ChatColor.YELLOW + "/nopt trades" + ChatColor.GRAY + " - setzt Trades des angeklickten Villagers zurück und macht sie unbegrenzt nutzbar");
            sender.sendMessage(ChatColor.YELLOW + "/nopt merge" + ChatColor.GRAY + " - führt Entity-Zusammenführungen sofort aus");
            sender.sendMessage(ChatColor.GRAY + "Trade-Neuwürfe sind über die öffentliche Paper-API nicht zuverlässig verfügbar.");
            return true;
        }
        if (args[0].equalsIgnoreCase("merge")) {
            runMerges();
            sender.sendMessage(ChatColor.GREEN + "Zusammenführung ausgeführt.");
            return true;
        }
        if (args[0].equalsIgnoreCase("trades")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Dieser Befehl ist nur ingame verfügbar.");
                return true;
            }
            Entity target = getTargetVillager(player, 6.0);
            if (!(target instanceof Villager villager)) {
                player.sendMessage(ChatColor.RED + "Schaue innerhalb von 6 Blöcken einen Villager an.");
                return true;
            }
            List<MerchantRecipe> updated = new ArrayList<>();
            for (MerchantRecipe recipe : villager.getRecipes()) {
                MerchantRecipe copy = new MerchantRecipe(recipe.getResult(), 0,
                        Integer.MAX_VALUE, recipe.hasExperienceReward(), recipe.getVillagerExperience(),
                        recipe.getPriceMultiplier(), recipe.getDemand(), recipe.getSpecialPrice());
                copy.setIngredients(recipe.getIngredients());
                updated.add(copy);
            }
            villager.setRecipes(updated);
            player.sendMessage(ChatColor.GREEN + "Trades zurückgesetzt und auf unbegrenzte Nutzung gestellt.");
            return true;
        }
        sender.sendMessage(ChatColor.RED + "Unbekannter Befehl. /nopt help");
        return true;
    }

    private Entity getTargetVillager(Player player, double maxDistance) {
        return player.getNearbyEntities(maxDistance, maxDistance, maxDistance).stream()
                .filter(e -> e instanceof Villager)
                .filter(e -> player.hasLineOfSight(e))
                .min((a, b) -> Double.compare(a.getLocation().distanceSquared(player.getLocation()),
                        b.getLocation().distanceSquared(player.getLocation())))
                .orElse(null);
    }

    @EventHandler
    public void onSpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof Mob mob && getConfig().getBoolean("mobs.disable-ai", false)
                && isStackableMob(mob)) {
            mob.setAI(false);
            mob.setCollidable(false);
        }
    }

    @EventHandler
    public void onMobDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !isStackableMob(mob)) return;
        int count = mob.getPersistentDataContainer().getOrDefault(mobCountKey, PersistentDataType.INTEGER, 1);
        if (count <= 1 || event.getFinalDamage() < mob.getHealth()) return;
        event.setCancelled(true);
        mob.getPersistentDataContainer().set(mobCountKey, PersistentDataType.INTEGER, count - 1);
        mob.setHealth(mob.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
        updateMobName(mob, count - 1);
    }

    private boolean isStackableMob(Mob mob) {
        return getConfig().getStringList("mobs.types").contains(mob.getType().name());
    }

    private void runMerges() {
        for (World world : Bukkit.getWorlds()) {
            mergeItems(world);
            mergeExperience(world);
            mergeMobs(world);
        }
    }

    private void mergeItems(World world) {
        if (!getConfig().getBoolean("items.enabled", true)) return;
        double radius = getConfig().getDouble("items.radius", 2.0);
        Set<UUID> processed = new HashSet<>();
        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof Item base) || !base.isValid() || processed.contains(base.getUniqueId())) continue;
            ItemStack baseStack = base.getItemStack();
            int max = baseStack.getMaxStackSize();
            int amount = baseStack.getAmount();
            List<Item> merged = new ArrayList<>();
            for (Entity nearby : base.getNearbyEntities(radius, radius, radius)) {
                if (!(nearby instanceof Item item) || !item.isValid() || item == base) continue;
                if (!sameItem(baseStack, item.getItemStack())) continue;
                if (amount >= max) break;
                int take = Math.min(max - amount, item.getItemStack().getAmount());
                amount += take;
                ItemStack remainder = item.getItemStack().clone();
                remainder.setAmount(remainder.getAmount() - take);
                if (remainder.getAmount() <= 0) {
                    processed.add(item.getUniqueId());
                    item.remove();
                } else {
                    item.setItemStack(remainder);
                }
                merged.add(item);
            }
            if (!merged.isEmpty()) {
                baseStack.setAmount(amount);
                base.setItemStack(baseStack);
                if (getConfig().getBoolean("items.show-stack-name", true) && amount > 1) {
                    base.setCustomName(ChatColor.YELLOW + baseStack.getType().name().toLowerCase().replace('_', ' ') + " x" + amount);
                    base.setCustomNameVisible(true);
                }
            }
        }
    }

    private boolean sameItem(ItemStack a, ItemStack b) {
        if (a.getType() != b.getType()) return false;
        ItemStack x = a.clone();
        ItemStack y = b.clone();
        x.setAmount(1);
        y.setAmount(1);
        return x.isSimilar(y);
    }

    private void mergeExperience(World world) {
        if (!getConfig().getBoolean("xp.enabled", true)) return;
        double radius = getConfig().getDouble("xp.radius", 3.0);
        Set<UUID> processed = new HashSet<>();
        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof ExperienceOrb base) || !base.isValid() || processed.contains(base.getUniqueId())) continue;
            int total = base.getExperience();
            for (Entity nearby : base.getNearbyEntities(radius, radius, radius)) {
                if (nearby instanceof ExperienceOrb orb && orb.isValid() && orb != base) {
                    total += orb.getExperience();
                    processed.add(orb.getUniqueId());
                    orb.remove();
                }
            }
            base.setExperience(Math.min(total, getConfig().getInt("xp.max-per-orb", 32767)));
        }
    }

    private void mergeMobs(World world) {
        if (!getConfig().getBoolean("mobs.enabled", true)) return;
        double radius = getConfig().getDouble("mobs.radius", 4.0);
        Set<UUID> processed = new HashSet<>();
        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof Mob base) || !base.isValid() || !isStackableMob(base)
                    || processed.contains(base.getUniqueId())) continue;
            int count = base.getPersistentDataContainer().getOrDefault(mobCountKey, PersistentDataType.INTEGER, 1);
            int cap = getConfig().getInt("mobs.max-stack", 50);
            for (Entity nearby : base.getNearbyEntities(radius, radius, radius)) {
                if (!(nearby instanceof Mob other) || other == base || !other.isValid() || !isStackableMob(other)) continue;
                if (other.getType() != base.getType() || count >= cap) continue;
                int otherCount = other.getPersistentDataContainer().getOrDefault(mobCountKey, PersistentDataType.INTEGER, 1);
                int transfer = Math.min(otherCount, cap - count);
                count += transfer;
                if (transfer == otherCount) {
                    processed.add(other.getUniqueId());
                    other.remove();
                } else {
                    other.getPersistentDataContainer().set(mobCountKey, PersistentDataType.INTEGER, otherCount - transfer);
                    updateMobName(other, otherCount - transfer);
                }
            }
            base.getPersistentDataContainer().set(mobCountKey, PersistentDataType.INTEGER, count);
            if (getConfig().getBoolean("mobs.disable-ai", false)) base.setAI(false);
            base.setCollidable(false);
            updateMobName(base, count);
        }
    }

    private void updateMobName(Mob mob, int count) {
        if (count <= 1) {
            mob.setCustomName(null);
            mob.setCustomNameVisible(false);
        } else {
            mob.setCustomName(ChatColor.YELLOW + mob.getType().name().toLowerCase().replace('_', ' ') + " x" + count);
            mob.setCustomNameVisible(true);
        }
    }
}
