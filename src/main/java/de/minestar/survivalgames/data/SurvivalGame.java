package de.minestar.survivalgames.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import org.bukkit.ChatColor;

import de.minestar.survivalgames.manager.LootManager;
import de.minestar.survivalgames.manager.Scheduler;
import de.minestar.survivalgames.threads.LootRefillThread;
import de.minestar.survivalgames.threads.ReturnToLobbyThread;
import de.minestar.survivalgames.threads.StartDeathmatchThread;
import de.minestar.survivalgames.threads.StartGameThread;
import de.minestar.survivalgames.threads.StartPVPThread;
import de.minestar.survivalgames.threads.TimerDeathmatchStart;
import de.minestar.survivalgames.threads.TimerGameStart;
import de.minestar.survivalgames.threads.TimerPVPStart;
import de.minestar.survivalgames.threads.TimerReturnToLobby;
import de.minestar.survivalgames.utils.Chat;

public class SurvivalGame {

    public static final String LIMITER = "----------------------------------";

    private final String gameName;
    private HashMap<String, SurvivalPlayer> completePlayerList, playerList, spectatorList;

    private GameState gameState = GameState.LOBBY;
    private GameSettings settings;

    private LootManager lootManager;
    private Scheduler scheduler;

    public SurvivalGame(String gameName) {
        this.gameName = gameName;
        this.scheduler = new Scheduler();
        this.settings = new GameSettings(this.gameName);
        this.lootManager = new LootManager(this.gameName);
        this.completePlayerList = new HashMap<String, SurvivalPlayer>();
        this.playerList = new HashMap<String, SurvivalPlayer>();
        this.spectatorList = new HashMap<String, SurvivalPlayer>();
    }

    // /////////////////////////////////////////////////////////
    //
    // Methods to handle the game
    //
    // /////////////////////////////////////////////////////////

    public void goToLobby() {
        this.gameState = GameState.LOBBY;
        this.scheduler.cancelTasks();
        this.settings.reset();
        this.lootManager.clearChests();
        this.teleportAllToLobbySpawn();
        this.showAllPlayers();
        this.resetPlayers();
    }

    private void resetPlayers() {
        for (SurvivalPlayer player : this.completePlayerList.values()) {
            player.resetPlayer();
        }
    }

    public void goToPreGame() {
        this.gameState = GameState.PRE_GAME;

        // make everyone a player
        for (SurvivalPlayer player : this.completePlayerList.values()) {
            player.makePlayer();
            player.resetPlayer();
        }

        // teleport everyone to the gamespawn
        this.teleportAllToGameSpawn();

        // start threads
        this.scheduler.scheduleDelayedTask(new StartGameThread(this), this.settings.getPreGameTime() * 1000);
        this.scheduler.scheduleDelayedRepeatingTask(new TimerGameStart(this, System.currentTimeMillis() + ((this.settings.getPreGameTime()) * 1000)), 1000, 1001);

        // TODO: reset time & weather
        // this.settings.getSpectatorSpawn().getLocation().getWorld().setTime(2000);
        // this.settings.getSpectatorSpawn().getLocation().getWorld().setThundering(false);
        // this.settings.getSpectatorSpawn().getLocation().getWorld().setStorm(false);

        // print info
        this.broadcastInfo("The games will start in " + Chat.secondsToMinutes(this.settings.getPreGameTime()) + "! Prepare!");
    }

    public void goToPrePVP() {
        this.gameState = GameState.PRE_PVP;

        // refill loot
        this.refillLoot();

        // start threads
        this.scheduler.scheduleDelayedTask(new StartPVPThread(this), this.settings.getPrePVPTime() * 1000);
        this.scheduler.scheduleDelayedRepeatingTask(new TimerPVPStart(this, System.currentTimeMillis() + (this.settings.getPrePVPTime()) * 1000), 1000, 1001);

        // print info
        this.broadcastInfo("PVP will be enabled in " + Chat.secondsToMinutes(this.settings.getPrePVPTime()) + "!");
    }

    public void goToSurvival() {
        this.gameState = GameState.SURVIVAL;

        // start threads
        this.scheduler.scheduleDelayedTask(new StartDeathmatchThread(this), this.settings.getPreDeathmatchTime() * 1000);
        this.scheduler.scheduleDelayedRepeatingTask(new TimerDeathmatchStart(this, System.currentTimeMillis() + (this.settings.getPreDeathmatchTime() * 1000)), 1000, 1001);

        // print info
        this.broadcastInfo("Deathmatch will start in " + Chat.secondsToMinutes(this.settings.getPreDeathmatchTime()) + "!");
    }

    public void goToDeathmatch() {
        this.gameState = GameState.DEATHMATCH;
        this.teleportAllToGameSpawn();
    }

    public void goToEnd() {
        this.gameState = GameState.END;

        // start threads
        this.scheduler.scheduleDelayedTask(new ReturnToLobbyThread(this), this.settings.getAfterMatchTime() * 1000);
        this.scheduler.scheduleDelayedRepeatingTask(new TimerReturnToLobby(this, System.currentTimeMillis() + (this.settings.getAfterMatchTime() * 1000)), 1000, 1001);

        // show all players
        this.showAllPlayers();

        // print info
        this.broadcastInfo("The game will return to the lobby in" + Chat.secondsToMinutes(this.settings.getPreDeathmatchTime()) + "!");
    }

    public void stopGame() {
        this.broadcast(ChatColor.RED, LIMITER);
        this.broadcast(ChatColor.RED, "Game has been stopped by an admin!");
        this.broadcast(ChatColor.RED, LIMITER);
        this.goToLobby();
    }

    public void closeGame() {
        this.broadcast(ChatColor.RED, LIMITER);
        this.broadcast(ChatColor.RED, "Game has been closed by an admin!");
        this.broadcast(ChatColor.RED, LIMITER);
        this.goToLobby();
        this.cleanUp();
    }

    private void cleanUp() {
        this.completePlayerList.clear();
        this.playerList.clear();
        this.spectatorList.clear();
        this.scheduler.cancelTasks();
        this.settings.reset();
        this.lootManager.clearChests();
    }

    // /////////////////////////////////////////////////////////
    //
    // Methods for gamecontrol
    //
    // /////////////////////////////////////////////////////////

    public void refillLoot() {
        this.lootManager.refillChests();

        int refillTime = this.settings.getNextRefillTime();
        if (refillTime > 0) {
            this.scheduler.scheduleDelayedTask(new LootRefillThread(this), (refillTime + (new Random()).nextInt(180)) * 1000);
        }
    }

    private void showAllPlayers() {
        for (SurvivalPlayer player : this.completePlayerList.values()) {
            player.show();
        }
    }

    public void onPlayerDeath(SurvivalPlayer player) {
        this.playerList.remove(player.getPlayerName());
        this.spectatorList.put(player.getPlayerName(), player);

        if (this.playerList.size() > 1) {
            this.broadcast(ChatColor.DARK_GREEN, "Another one bites the dust...");
            this.broadcast(ChatColor.GRAY, this.playerList.size() + " survivors are still alive!");
        }
        this.checkForWinner();
    }

    public void checkForWinner() {
        if (this.playerList.size() == 1 && !this.isGameInLobby() && !this.isGameInEnd()) {
            this.broadcast(ChatColor.RED, LIMITER);
            this.broadcast(ChatColor.RED, "The game has a winner: " + ChatColor.GOLD + this.getWinner() + "!");
            this.broadcast(ChatColor.RED, LIMITER);
            this.goToEnd();
        } else if (this.playerList.size() < 1 && !this.isGameInLobby() && !this.isGameInEnd()) {
            this.broadcast(ChatColor.RED, LIMITER);
            this.broadcast(ChatColor.RED, "The game has ended!");
            this.broadcast(ChatColor.GOLD, "Nobody survived :-[");
            this.broadcast(ChatColor.RED, LIMITER);
            this.goToEnd();
        }
    }

    public boolean joinGame(String playerName) {
        if (this.completePlayerList.containsKey(playerName)) {
            return false;
        }

        // create the player
        SurvivalPlayer player = new SurvivalPlayer(playerName, this);

        // remove from lists ...
        this.playerList.remove(player);
        this.spectatorList.remove(player);

        // ... and readd the player
        this.completePlayerList.put(playerName, player);
        this.spectatorList.put(playerName, player);

        // show the player, if we are in the lobby or the game has ended. Otherwise hide the player
        if (this.isGameInLobby() || this.isGameInEnd()) {
            if (this.settings.getLobbySpawn() != null) {
                player.teleport(this.settings.getLobbySpawn());
            }
            player.show();
            this.broadcast(ChatColor.GRAY, "'" + playerName + "' joined the survivalgame!");
        } else {
            if (this.settings.getSpectatorSpawn() != null) {
                player.teleport(this.settings.getSpectatorSpawn());
            }
            player.hide();
        }
        return true;
    }

    public boolean quitGame(String playerName) {
        if (!this.completePlayerList.containsKey(playerName)) {
            return false;
        }

        // reset the player
        SurvivalPlayer player = this.completePlayerList.get(playerName);
        player.teleport(this.settings.getLobbySpawn());
        player.resetPlayer();
        player.show();

        // remove from all lists
        this.completePlayerList.remove(playerName);
        this.playerList.remove(playerName);
        this.spectatorList.remove(playerName);

        // check for a winner
        this.checkForWinner();
        return true;
    }

    // /////////////////////////////////////////////////////////
    //
    // Methods for teleportation
    //
    // /////////////////////////////////////////////////////////

    private void teleportAllToLobbySpawn() {
        // teleport
        for (SurvivalPlayer player : this.completePlayerList.values()) {
            player.teleport(this.settings.getLobbySpawn());
        }
    }

    private void teleportAllToGameSpawn() {
        // write spawns into arraylist for randomized use
        ArrayList<PlayerSpawn> unusedSpawns = new ArrayList<PlayerSpawn>();
        for (PlayerSpawn spawn : this.settings.getPlayerSpawns()) {
            unusedSpawns.add(spawn);
        }

        // teleport
        Random random = new Random();
        for (SurvivalPlayer player : this.completePlayerList.values()) {
            if (player.isPlayer()) {
                // player = teleport to random spawnpoint
                int index = (int) (random.nextDouble() * unusedSpawns.size());
                player.teleport(unusedSpawns.get(index));
                unusedSpawns.remove(index);
            } else {
                // spectator = teleport to spectatorspawn
                player.teleport(this.settings.getSpectatorSpawn());
            }
        }
    }

    // /////////////////////////////////////////////////////////
    //
    // Methods for chat
    //
    // /////////////////////////////////////////////////////////

    public void broadcast(ChatColor color, String message) {
        String completeMessage = color + message;
        for (SurvivalPlayer player : this.completePlayerList.values()) {
            player.broadcast(completeMessage);
        }
    }

    public void broadcast(String message) {
        for (SurvivalPlayer player : this.completePlayerList.values()) {
            player.broadcast(message);
        }
    }

    public void broadcastInfo(String message) {
        String completeMessage = ChatColor.GOLD + "[INFO] " + message;
        for (SurvivalPlayer player : this.completePlayerList.values()) {
            player.broadcast(completeMessage);
        }
    }

    // /////////////////////////////////////////////////////////
    //
    // Getter and Setter
    //
    // /////////////////////////////////////////////////////////

    public boolean isGameInLobby() {
        return this.gameState.equals(GameState.LOBBY);
    }

    public boolean isGameInPreGame() {
        return this.gameState.equals(GameState.PRE_GAME);
    }

    public boolean isGameInPrePVP() {
        return this.gameState.equals(GameState.PRE_PVP);
    }

    public boolean isGameInSurvival() {
        return this.gameState.equals(GameState.SURVIVAL);
    }

    public boolean isGameInDeathmatch() {
        return this.gameState.equals(GameState.DEATHMATCH);
    }

    public boolean isGameInEnd() {
        return this.gameState.equals(GameState.END);
    }

    public boolean isSetupComplete() {
        return this.settings.getSpectatorSpawn() != null && this.settings.getLobbySpawn() != null && this.settings.getPlayerSpawns().size() > 1;
    }

    public boolean isGameFull() {
        return this.completePlayerList.size() >= this.settings.getPlayerSpawns().size();
    }

    public SurvivalPlayer getPlayer(String playerName) {
        return this.completePlayerList.get(playerName);
    }

    public String getWinner() {
        for (SurvivalPlayer player : this.playerList.values()) {
            return player.getPlayerName();
        }
        return "UNKNOWN";
    }

    public LootManager getLootManager() {
        return lootManager;
    }

    public String getGameName() {
        return gameName;
    }

    public GameState getGameState() {
        return gameState;
    }

    @Override
    public int hashCode() {
        return this.gameName.hashCode();
    }

    public GameSettings getSettings() {
        return this.settings;
    }

    public boolean equals(SurvivalGame otherGame) {
        return otherGame.gameName.equalsIgnoreCase(this.gameName);
    }
}