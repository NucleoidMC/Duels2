package net.hyper_pigeon.duels.game;

import xyz.nucleoid.plasmid.game.GameSpace;
import xyz.nucleoid.plasmid.util.PlayerRef;

import java.util.ArrayList;

public class DuelsStageManager {
    private final ArrayList<PlayerRef> participants;
    private long startTime;
    private long closeTime;
    private long finishTime;

    public DuelsStageManager(ArrayList<PlayerRef> participants, long startTime, long closeTime){
        this.participants = participants;
        this.startTime = startTime;
        this.closeTime = closeTime;
    }

//    public GamePhase tick(GameSpace space) {
//
//    }
}
