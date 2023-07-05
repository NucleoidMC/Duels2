package net.hyper_pigeon.duels.game;

import net.minecraft.server.network.ServerPlayerEntity;
import xyz.nucleoid.plasmid.game.common.team.GameTeam;

public class DuelsPlayer {
    public GameTeam team = null;
    private final ServerPlayerEntity serverPlayerEntity;

    public DuelsPlayer(ServerPlayerEntity serverPlayerEntity) {
        this.serverPlayerEntity = serverPlayerEntity;
    }

    public ServerPlayerEntity getServerPlayerEntity() {
        return serverPlayerEntity;
    }

    public GameTeam getTeam(){
        return team;
    }
}
