package net.hyper_pigeon.duels.game;

import net.minecraft.server.level.ServerPlayer;
import xyz.nucleoid.plasmid.api.game.common.team.GameTeam;

public class DuelsPlayer {
    public GameTeam team = null;
    private final ServerPlayer serverPlayer;

    public DuelsPlayer(ServerPlayer serverPlayer) {
        this.serverPlayer = serverPlayer;
    }

    public ServerPlayer getServerPlayer() {
        return serverPlayer;
    }

    public GameTeam getTeam(){
        return team;
    }
}
