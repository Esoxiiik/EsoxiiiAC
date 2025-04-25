package com.anticheatsystem.listeners;

import com.anticheatsystem.AntiCheatMain;
import com.anticheatsystem.checks.Check;
import com.anticheatsystem.checks.combat.AimbotCheck;
import com.anticheatsystem.checks.combat.AutoClickerCheck;
import com.anticheatsystem.checks.combat.KillAuraCheck;
import com.anticheatsystem.checks.combat.ReachCheck;
import com.anticheatsystem.checks.misc.DisablerCheck;
import com.anticheatsystem.checks.movement.FlyCheck;
import com.anticheatsystem.checks.movement.JesusCheck;
import com.anticheatsystem.checks.movement.SpeedCheck;
import com.anticheatsystem.checks.movement.TeleportCheck;
import com.anticheatsystem.checks.movement.TimerCheck;
import com.anticheatsystem.checks.player.InventoryCheck;
import com.anticheatsystem.checks.player.NukerCheck;
import com.anticheatsystem.checks.player.ScaffoldCheck;
import com.anticheatsystem.checks.player.XRayCheck;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Główny listener, który przechwytuje wydarzenia graczy i przekazuje je
 * do odpowiednich sprawdzeń.
 */
public class PlayerListener implements Listener {

    private final AntiCheatMain plugin;
    private final Map<UUID, Location> lastPlayerLocations = new HashMap<>();
    private final Map<UUID, Long> lastMoveTime = new HashMap<>();
    private final Map<UUID, Long> lastClickTime = new HashMap<>();
    private final Map<UUID, Integer> clicksPerSecond = new HashMap<>();
    
    public PlayerListener(AntiCheatMain plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Resetuj dane dla nowo połączonego gracza
        lastPlayerLocations.put(player.getUniqueId(), player.getLocation());
        lastMoveTime.put(player.getUniqueId(), System.currentTimeMillis());
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // Usuń dane dla gracza, który się wylogował
        lastPlayerLocations.remove(playerId);
        lastMoveTime.remove(playerId);
        lastClickTime.remove(playerId);
        clicksPerSecond.remove(playerId);
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        Location from = event.getFrom();
        Location to = event.getTo();
        
        // Pomiń, jeśli gracz ma uprawnienia do omijania
        if (player.hasPermission("anticheat.bypass")) {
            return;
        }
        
        // Pomiń, jeśli świat jest wyłączony
        if (plugin.getConfigManager().isWorldExempt(player.getWorld().getName())) {
            return;
        }
        
        // Oblicz prędkość ruchu
        long currentTime = System.currentTimeMillis();
        long timeDelta = currentTime - lastMoveTime.getOrDefault(playerId, currentTime);
        lastMoveTime.put(playerId, currentTime);
        
        // Pobierz ostatnią pozycję
        Location lastLocation = lastPlayerLocations.getOrDefault(playerId, from);
        lastPlayerLocations.put(playerId, to);
        
        // Sprawdź, czy gracz lata
        if (plugin.getConfigManager().isCheckEnabled("movement.fly")) {
            Check flyCheck = plugin.getCheckManager().getCheck("movement.fly");
            if (flyCheck instanceof FlyCheck) {
                ((FlyCheck) flyCheck).checkFly(player, from, to, timeDelta);
            }
        }
        
        // Sprawdź prędkość gracza
        if (plugin.getConfigManager().isCheckEnabled("movement.speed")) {
            Check speedCheck = plugin.getCheckManager().getCheck("movement.speed");
            if (speedCheck instanceof SpeedCheck) {
                ((SpeedCheck) speedCheck).checkSpeed(player, from, to, timeDelta);
            }
        }
        
        // Sprawdź, czy gracz się teleportuje
        if (plugin.getConfigManager().isCheckEnabled("movement.teleport")) {
            Check teleportCheck = plugin.getCheckManager().getCheck("movement.teleport");
            if (teleportCheck instanceof TeleportCheck) {
                ((TeleportCheck) teleportCheck).checkTeleport(player, lastLocation, to, timeDelta);
            }
        }
        
        // Sprawdź, czy gracz chodzi po wodzie (Jesus hack)
        if (plugin.getConfigManager().isCheckEnabled("movement.jesus")) {
            Check jesusCheck = plugin.getCheckManager().getCheck("movement.jesus");
            if (jesusCheck instanceof JesusCheck) {
                ((JesusCheck) jesusCheck).checkJesus(player, from, to, event);
            }
        }
        
        // Rejestruj ruch dla wykrywania Timer hack
        if (plugin.getConfigManager().isCheckEnabled("movement.timer")) {
            Check timerCheck = plugin.getCheckManager().getCheck("movement.timer");
            if (timerCheck instanceof TimerCheck) {
                ((TimerCheck) timerCheck).registerMovement(player);
            }
        }
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getDamager();
        Entity target = event.getEntity();
        
        // Pomiń, jeśli gracz ma uprawnienia do omijania
        if (player.hasPermission("anticheat.bypass")) {
            return;
        }
        
        // Pomiń, jeśli świat jest wyłączony
        if (plugin.getConfigManager().isWorldExempt(player.getWorld().getName())) {
            return;
        }
        
        // Aktualizuj licznik kliknięć
        updateClickCounter(player.getUniqueId());
        
        // Sprawdź, czy gracz używa aimbot
        if (plugin.getConfigManager().isCheckEnabled("combat.aimbot")) {
            Check aimbotCheck = plugin.getCheckManager().getCheck("combat.aimbot");
            if (aimbotCheck instanceof AimbotCheck) {
                ((AimbotCheck) aimbotCheck).checkAimbot(player, target);
            }
        }
        
        // Sprawdź, czy gracz używa auto-clickera
        if (plugin.getConfigManager().isCheckEnabled("combat.autoclicker")) {
            Check autoClickerCheck = plugin.getCheckManager().getCheck("combat.autoclicker");
            if (autoClickerCheck instanceof AutoClickerCheck) {
                int cps = clicksPerSecond.getOrDefault(player.getUniqueId(), 0);
                ((AutoClickerCheck) autoClickerCheck).checkAutoClicker(player, cps);
            }
        }
        
        // Sprawdź zasięg gracza
        if (plugin.getConfigManager().isCheckEnabled("combat.reach")) {
            Check reachCheck = plugin.getCheckManager().getCheck("combat.reach");
            if (reachCheck instanceof ReachCheck) {
                ((ReachCheck) reachCheck).checkReach(player, target);
            }
        }
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        
        // Aktualizuj licznik kliknięć (dla auto-clickera)
        updateClickCounter(player.getUniqueId());
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        
        // Pomiń, jeśli gracz ma uprawnienia do omijania
        if (player.hasPermission("anticheat.bypass")) {
            return;
        }
        
        // Pomiń, jeśli świat jest wyłączony
        if (plugin.getConfigManager().isWorldExempt(player.getWorld().getName())) {
            return;
        }
        
        // Sprawdź, czy gracz używa X-Ray
        if (plugin.getConfigManager().isCheckEnabled("player.xray")) {
            Check xrayCheck = plugin.getCheckManager().getCheck("player.xray");
            if (xrayCheck instanceof XRayCheck) {
                ((XRayCheck) xrayCheck).checkXRay(player, event.getBlock());
            }
        }
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        
        // Pomiń, jeśli gracz ma uprawnienia do omijania
        if (player.hasPermission("anticheat.bypass")) {
            return;
        }
        
        // Pomiń, jeśli świat jest wyłączony
        if (plugin.getConfigManager().isWorldExempt(player.getWorld().getName())) {
            return;
        }
        
        // Sprawdź, czy gracz oszukuje w inwentarzu
        if (plugin.getConfigManager().isCheckEnabled("player.inventory")) {
            Check inventoryCheck = plugin.getCheckManager().getCheck("player.inventory");
            if (inventoryCheck instanceof InventoryCheck) {
                ((InventoryCheck) inventoryCheck).checkInventory(player, event);
            }
        }
    }
    
    /**
     * Aktualizuje licznik kliknięć dla gracza (używany do wykrywania auto-clickera)
     */
    private void updateClickCounter(UUID playerId) {
        long currentTime = System.currentTimeMillis();
        long lastTime = lastClickTime.getOrDefault(playerId, 0L);
        
        // Jeśli minęła sekunda, zresetuj licznik
        if (currentTime - lastTime > 1000) {
            clicksPerSecond.put(playerId, 1);
        } else {
            // W przeciwnym razie zwiększ licznik
            int clicks = clicksPerSecond.getOrDefault(playerId, 0);
            clicksPerSecond.put(playerId, clicks + 1);
        }
        
        lastClickTime.put(playerId, currentTime);
    }
}