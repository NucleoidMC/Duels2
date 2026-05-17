package net.hyper_pigeon.duels.game;

import com.google.common.collect.Multimap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.hyper_pigeon.duels.game.map.DuelsMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.GameType;
import xyz.nucleoid.plasmid.api.game.GameCloseReason;
import xyz.nucleoid.plasmid.api.game.GameSpace;
import xyz.nucleoid.plasmid.api.game.common.team.GameTeam;
import xyz.nucleoid.plasmid.api.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.api.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.api.game.player.JoinAcceptor;
import xyz.nucleoid.plasmid.api.game.player.JoinAcceptorResult;
import xyz.nucleoid.plasmid.api.game.player.JoinOffer;
import xyz.nucleoid.plasmid.api.game.rule.GameRuleType;
import xyz.nucleoid.plasmid.api.util.ItemStackBuilder;
import xyz.nucleoid.plasmid.api.util.PlayerRef;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.player.PlayerAttackEntityEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public class DuelsGameActive {
    private final DuelsConfig config;
    private final GameSpace gameSpace;
    private final ServerLevel level;
    private final DuelsMap gameMap;
    public final Object2ObjectMap<PlayerRef, DuelsPlayer> participants;
    public final Multimap<GameTeam, PlayerRef> teamPlayers;


    public static final ArrayList<DuelsGameActive> runningGames = new ArrayList<>();
    private GamePhase gamePhase;
    private long finishTime;

    public DuelsGameActive(DuelsConfig config, GameSpace gameSpace, ServerLevel level, DuelsMap gameMap, Multimap<GameTeam, PlayerRef> teamPlayers, Object2ObjectMap<PlayerRef, DuelsPlayer> participants) {
        this.config = config;
        this.gameSpace = gameSpace;
        this.level = level;
        this.gameMap = gameMap;
        this.teamPlayers = teamPlayers;
        this.participants = participants;
        gamePhase = GamePhase.GAME_CONTINUE;
    }

    public static void open(DuelsConfig config, GameSpace gameSpace, ServerLevel level, DuelsMap duelsMap, Multimap<GameTeam, PlayerRef> teamPlayers, Object2ObjectMap<PlayerRef, DuelsPlayer> participants){
        gameSpace.setActivity(game -> {
            DuelsGameActive active = new DuelsGameActive(config, gameSpace, level, duelsMap, teamPlayers, participants);
            runningGames.add(active);

            game.allow(GameRuleType.PVP);
            game.deny(GameRuleType.BREAK_BLOCKS);

            game.listen(GameActivityEvents.TICK, active::tick);
            game.listen(GamePlayerEvents.OFFER, JoinOffer::acceptSpectators);
            game.listen(GamePlayerEvents.ACCEPT, active::acceptPlayer);
            game.listen(PlayerDeathEvent.EVENT, active::onPlayerDeath);
            game.listen(GameActivityEvents.ENABLE, active::onEnable);
            game.listen(GamePlayerEvents.REMOVE, active::removePlayer);
            game.listen(PlayerAttackEntityEvent.EVENT, active::attackEntity);
        });
    }

    private JoinAcceptorResult acceptPlayer(JoinAcceptor acceptor) {
        Vec3 pos = gameMap.getSpawn1().centerTop();
        return acceptor.teleport(level, pos).thenRunForEach((plr) -> {
            plr.setGameMode(GameType.SPECTATOR);
        });
    }

    private EventResult attackEntity(ServerPlayer serverPlayerEntity, InteractionHand hand, Entity entity, EntityHitResult entityHitResult) {
        if((this.config.mode().equals("duos") && entity instanceof ServerPlayer hitPlayer && getTeam(serverPlayerEntity) != null && getTeam(hitPlayer) != null && Objects.equals(getTeam(serverPlayerEntity), getTeam(hitPlayer)))) {
            return EventResult.DENY;
        }
        return EventResult.PASS;
    }

    private void tick() {
        switch(this.gamePhase) {
            case GAME_CONTINUE -> {
                break;
            }
            case GAME_ENDING -> {
                if(level.getGameTime() >= finishTime){
                    gamePhase = GamePhase.GAME_FINISHED;
                }
            }
            case GAME_FINISHED -> {
                this.gameSpace.close(GameCloseReason.FINISHED);
            }
        }
    }

    private GameTeam getTeam(ServerPlayer player){
        if(participants.get(PlayerRef.of(player)) != null){
            return participants.get(PlayerRef.of(player)).getTeam();
        }
        return null;
    }

    private void removePlayer(ServerPlayer player) {
        if(participants.containsKey(PlayerRef.of(player))) {
            this.participants.remove(PlayerRef.of(player));
            this.teamPlayers.values().remove(PlayerRef.of(player));
            tryEndDuel();
        }
    }


    public void equipPlayer(ServerPlayer player) {
        for (ItemStackTemplate itemStackTemplate : this.config.items()) {
            player.getInventory().add(itemStackTemplate.create());
        }

        player.setItemSlot(EquipmentSlot.HEAD, this.config.armor().get(0).create());
        player.setItemSlot(EquipmentSlot.CHEST,this.config.armor().get(1).create());
        player.setItemSlot(EquipmentSlot.LEGS, this.config.armor().get(2).create());
        player.setItemSlot(EquipmentSlot.FEET, this.config.armor().get(3).create());

    }

    private void onEnable() {
        Iterator<Vec3> spawnIterator = new ArrayList<Vec3>(List.of(gameMap.getSpawn1().centerTop(),gameMap.getSpawn2().centerTop())).iterator();
        for (GameTeam team : teamPlayers.keySet()) {
            Vec3 spawn = spawnIterator.next();
            for (PlayerRef ref : teamPlayers.get(team)) {
                participants.get(ref).getServerPlayerEntity().teleportTo(spawn.x(),spawn.y(),spawn.z());
                participants.get(ref).getServerPlayerEntity().snapTo(spawn.x(),spawn.y(),spawn.z());
                equipPlayer(participants.get(ref).getServerPlayerEntity());
            }
        }
    }

    private EventResult onPlayerDeath(ServerPlayer serverPlayerEntity, DamageSource source) {
        serverPlayerEntity.setGameMode(GameType.SPECTATOR);
        removePlayer(serverPlayerEntity);
        return EventResult.DENY;
    }

    public void tryEndDuel(){
        if(this.teamPlayers.keySet().size() == 1) {
            DuelsPlayer duelsPlayer = participants.values().iterator().next();
            if(this.config.mode().equals("solo")) {
                this.gameSpace.getPlayers().sendMessage(Component.translatable("duels.win_text", duelsPlayer.getServerPlayerEntity().getName()));
            }
            else {
                GameTeam winningTeam = duelsPlayer.team;
                this.gameSpace.getPlayers().sendMessage(
                        Component.translatable("duels.win_text", winningTeam.config().name())
                );
            }
            this.gameSpace.getPlayers().playSound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 1, 1);

            gamePhase = GamePhase.GAME_ENDING;
            this.finishTime = level.getGameTime() + 20*10;
        }
    }
}
