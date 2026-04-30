package com.modforge.createamultipliercommandwiththefol;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class CreateAMultiplierCommandWithTheFolMod extends JavaPlugin implements Listener {
    private static final int MIN_MULTIPLIER = 1;
    private static final int MAX_MULTIPLIER = 1000;

    private final Map<UUID, PlayerSettings> blockMultipliers = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        registerMultiplierCommand("multiplier", blockMultipliers);
        getLogger().info("CreateAMultiplierCommandWithTheFol enabled");
    }

    private void registerMultiplierCommand(String commandName, Map<UUID, PlayerSettings> settings) {
        PluginCommand command = getCommand(commandName);
        if (command == null) {
            getLogger().warning("Command /" + commandName + " is missing from plugin.yml");
            return;
        }
        MultiplierCommand handler = new MultiplierCommand(settings);
        command.setExecutor(handler);
        command.setTabCompleter(handler);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDrops(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        int multiplier = getEffectiveMultiplier(blockMultipliers, player.getUniqueId());
        if (multiplier <= 1 || event.getItems().isEmpty()) return;

        for (Item item : event.getItems()) {
            multiplyDropItem(item, multiplier);
        }
    }

    private static int getEffectiveMultiplier(Map<UUID, PlayerSettings> settings, UUID playerId) {
        PlayerSettings playerSettings = settings.get(playerId);
        if (playerSettings == null || !playerSettings.enabled) return 1;
        return Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER, playerSettings.value));
    }

    private static void multiplyDropItem(Item item, int multiplier) {
        ItemStack original = item.getItemStack();
        List<ItemStack> split = splitMultiplied(original, multiplier);
        if (split.isEmpty()) {
            item.remove();
            return;
        }

        item.setItemStack(split.get(0));
        Location location = item.getLocation();
        for (int i = 1; i < split.size(); i++) {
            location.getWorld().dropItemNaturally(location, split.get(i));
        }
    }

    private static List<ItemStack> splitMultiplied(ItemStack stack, int multiplier) {
        List<ItemStack> result = new ArrayList<>();
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0 || multiplier <= 0) return result;

        int maxStackSize = Math.max(1, stack.getMaxStackSize());
        long totalLong = (long) stack.getAmount() * (long) multiplier;
        int total = (int) Math.min(totalLong, (long) maxStackSize * MAX_MULTIPLIER);

        while (total > 0) {
            int nextAmount = Math.min(maxStackSize, total);
            ItemStack copy = stack.clone();
            copy.setAmount(nextAmount);
            result.add(copy);
            total -= nextAmount;
        }
        return result;
    }

    private static final class PlayerSettings {
        private boolean enabled = false;
        private int value = 1;
    }

    private final class MultiplierCommand implements CommandExecutor, TabCompleter {
        private final Map<UUID, PlayerSettings> settings;

        private MultiplierCommand(Map<UUID, PlayerSettings> settings) {
            this.settings = settings;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can use this command.");
                return true;
            }

            if (args.length < 1 || args.length > 2) {
                player.sendMessage("Usage: /" + label + " <ON|OFF> [value]");
                return true;
            }

            String state = args[0].toUpperCase(Locale.ROOT);
            PlayerSettings playerSettings = settings.computeIfAbsent(player.getUniqueId(), id -> new PlayerSettings());

            if ("ON".equals(state)) {
                if (args.length == 2) {
                    Integer parsed = parseMultiplier(player, args[1]);
                    if (parsed == null) return true;
                    playerSettings.value = parsed;
                    playerSettings.enabled = true;
                    player.sendMessage("Multiplier set to x" + playerSettings.value);
                    return true;
                }

                playerSettings.enabled = true;
                player.sendMessage("Multiplier enabled (x" + playerSettings.value + ")");
                return true;
            }

            if ("OFF".equals(state)) {
                playerSettings.enabled = false;
                player.sendMessage("Multiplier disabled");
                return true;
            }

            player.sendMessage("Invalid state. Use ON or OFF.");
            return true;
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            if (args.length == 1) return List.of("ON", "OFF");
            if (args.length == 2 && "ON".equalsIgnoreCase(args[0])) return List.of("1", "2", "5", "10");
            return List.of();
        }

        private Integer parseMultiplier(Player player, String raw) {
            try {
                int value = Integer.parseInt(raw);
                if (value < MIN_MULTIPLIER || value > MAX_MULTIPLIER) {
                    player.sendMessage("Value must be a positive integer between 1 and 1000.");
                    return null;
                }
                return value;
            } catch (NumberFormatException ignored) {
                player.sendMessage("Value must be a whole number between 1 and 1000.");
                return null;
            }
        }
    }
}
