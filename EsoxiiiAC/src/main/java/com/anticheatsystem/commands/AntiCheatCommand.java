package com.anticheatsystem.commands;

import com.anticheatsystem.AntiCheatMain;
import com.anticheatsystem.checks.Check;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Obsługuje komendy pluginu anti-cheat
 */
public class AntiCheatCommand implements CommandExecutor, TabCompleter {

    private final AntiCheatMain plugin;
    
    public AntiCheatCommand(AntiCheatMain plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "reload":
                if (!hasPermission(sender, "esoxiiiac.reload")) {
                    plugin.sendMessage(sender, "Nie masz uprawnień do przeładowania konfiguracji.");
                    return true;
                }
                handleReload(sender);
                break;
                
            case "check":
                if (!hasPermission(sender, "esoxiiiac.check")) {
                    plugin.sendMessage(sender, "Nie masz uprawnień do sprawdzania graczy.");
                    return true;
                }
                if (args.length < 2) {
                    plugin.sendMessage(sender, "Użycie: /anticheat check <gracz>");
                } else {
                    handleCheck(sender, args[1]);
                }
                break;
                
            case "violations":
                if (!hasPermission(sender, "esoxiiiac.check")) {
                    plugin.sendMessage(sender, "Nie masz uprawnień do sprawdzania naruszeń.");
                    return true;
                }
                if (args.length < 2) {
                    plugin.sendMessage(sender, "Użycie: /anticheat violations <gracz>");
                } else {
                    handleViolations(sender, args[1]);
                }
                break;
                
            case "reset":
                if (!hasPermission(sender, "esoxiiiac.reset")) {
                    plugin.sendMessage(sender, "Nie masz uprawnień do resetowania naruszeń.");
                    return true;
                }
                if (args.length < 2) {
                    plugin.sendMessage(sender, "Użycie: /anticheat reset <gracz|all>");
                } else {
                    handleReset(sender, args[1]);
                }
                break;
                
            default:
                showHelp(sender);
                break;
        }
        
        return true;
    }
    
    /**
     * Sprawdza, czy sender ma określone uprawnienie
     * Obsługuje również stare uprawnienia dla kompatybilności wstecznej
     */
    private boolean hasPermission(CommandSender sender, String permission) {
        // Operator zawsze ma wszystkie uprawnienia
        if (sender.isOp()) {
            return true;
        }
        
        // Sprawdź uprawnienie esoxiiiac
        if (sender.hasPermission(permission)) {
            return true;
        }
        
        // Sprawdź starą permisję dla kompatybilności wstecznej
        String oldPermission = permission.replace("esoxiiiac.", "anticheat.");
        return sender.hasPermission(oldPermission);
    }
    
    /**
     * Wyświetla pomoc dotyczącą komend
     */
    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "========== AntiCheat Help ==========");
        sender.sendMessage(ChatColor.GRAY + "/anticheat reload" + ChatColor.WHITE + " - Przeładowuje konfigurację");
        sender.sendMessage(ChatColor.GRAY + "/anticheat check <gracz>" + ChatColor.WHITE + " - Sprawdza czy gracz jest podejrzany");
        sender.sendMessage(ChatColor.GRAY + "/anticheat violations <gracz>" + ChatColor.WHITE + " - Pokazuje naruszenia gracza");
        sender.sendMessage(ChatColor.GRAY + "/anticheat reset <gracz|all>" + ChatColor.WHITE + " - Resetuje naruszenia dla gracza lub wszystkich");
        sender.sendMessage(ChatColor.RED + "==================================");
    }
    
    /**
     * Obsługuje podkomendę reload
     */
    private void handleReload(CommandSender sender) {
        plugin.getConfigManager().reloadConfig();
        plugin.getCheckManager().reloadChecks();
        plugin.sendMessage(sender, "Konfiguracja przeładowana pomyślnie.");
    }
    
    /**
     * Obsługuje podkomendę check
     */
    private void handleCheck(CommandSender sender, String playerName) {
        Player target = Bukkit.getPlayerExact(playerName);
        
        if (target == null) {
            plugin.sendMessage(sender, "Gracz " + playerName + " nie jest online.");
            return;
        }
        
        int violations = plugin.getViolationManager().getTotalViolations(playerName);
        boolean tooMany = plugin.getViolationManager().hasTooManyViolations(playerName);
        
        if (violations == 0) {
            plugin.sendMessage(sender, "Gracz " + playerName + " nie ma żadnych naruszeń.");
        } else if (tooMany) {
            plugin.sendMessage(sender, "Gracz " + playerName + " ma " + violations + 
                    " naruszeń i jest wysoce podejrzany!");
        } else {
            plugin.sendMessage(sender, "Gracz " + playerName + " ma " + violations + 
                    " naruszeń, ale jest poniżej progu (" + 
                    plugin.getConfigManager().getMaxViolations() + ").");
        }
    }
    
    /**
     * Obsługuje podkomendę violations
     */
    private void handleViolations(CommandSender sender, String playerName) {
        Map<String, Integer> violations = plugin.getViolationManager().getPlayerViolations(playerName);
        
        if (violations.isEmpty()) {
            plugin.sendMessage(sender, "Gracz " + playerName + " nie ma żadnych naruszeń.");
            return;
        }
        
        sender.sendMessage(ChatColor.RED + "=== Naruszenia dla " + playerName + " ===");
        
        for (Map.Entry<String, Integer> entry : violations.entrySet()) {
            String checkName = entry.getKey();
            int vioCount = entry.getValue();
            
            sender.sendMessage(ChatColor.GRAY + checkName + ": " + ChatColor.WHITE + vioCount);
        }
        
        int total = plugin.getViolationManager().getTotalViolations(playerName);
        sender.sendMessage(ChatColor.RED + "Łącznie: " + total);
    }
    
    /**
     * Obsługuje podkomendę reset
     */
    private void handleReset(CommandSender sender, String playerName) {
        if (playerName.equalsIgnoreCase("all")) {
            plugin.getViolationManager().resetAllViolations();
            plugin.sendMessage(sender, "Zresetowano naruszenia dla wszystkich graczy.");
        } else {
            plugin.getViolationManager().resetPlayerViolations(playerName);
            plugin.sendMessage(sender, "Zresetowano naruszenia dla gracza " + playerName + ".");
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // Podkomendy
            String start = args[0].toLowerCase();
            for (String sub : Arrays.asList("reload", "check", "violations", "reset")) {
                if (sub.startsWith(start)) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            // Argumenty dla podkomend
            String subCommand = args[0].toLowerCase();
            String start = args[1].toLowerCase();
            
            if (subCommand.equals("check") || subCommand.equals("violations") || subCommand.equals("reset")) {
                // Sugeruj graczy
                for (Player player : Bukkit.getOnlinePlayers()) {
                    String name = player.getName();
                    if (name.toLowerCase().startsWith(start)) {
                        completions.add(name);
                    }
                }
                
                // Dodaj "all" dla komendy reset
                if (subCommand.equals("reset") && "all".startsWith(start)) {
                    completions.add("all");
                }
            }
        }
        
        return completions;
    }
}