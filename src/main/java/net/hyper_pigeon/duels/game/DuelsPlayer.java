package net.hyper_pigeon.duels.game;

import net.minecraft.server.level.ServerPlayer;
import xyz.nucleoid.plasmid.api.game.common.team.GameTeam;

public class DuelsPlayer {
    public GameTeam team = null;
    private final ServerPlayer serverPlayerEntity;

    public DuelsPlayer(ServerPlayer serverPlayerEntity) {
        this.serverPlayerEntity = serverPlayerEntity;
    }

    public ServerPlayer getServerPlayerEntity() {
        return serverPlayerEntity;
    }

    public GameTeam getTeam(){
        return team;
    }
}
