package net.hyper_pigeon.duels.game;

import com.google.common.collect.Multimap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.hyper_pigeon.duels.game.map.DuelsMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import xyz.nucleoid.plasmid.game.GameCloseReason;
import xyz.nucleoid.plasmid.game.GameSpace;
import xyz.nucleoid.plasmid.game.common.team.GameTeam;
import xyz.nucleoid.plasmid.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.game.player.PlayerOffer;
import xyz.nucleoid.plasmid.game.player.PlayerOfferResult;
import xyz.nucleoid.plasmid.game.rule.GameRuleType;
import xyz.nucleoid.plasmid.util.ItemStackBuilder;
import xyz.nucleoid.plasmid.util.PlayerRef;
import xyz.nucleoid.stimuli.event.player.PlayerAttackEntityEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class DuelsGameActive {
    private final DuelsConfig config;
    private final GameSpace gameSpace;
    private final ServerWorld world;
    private final DuelsMap gameMap;
    //private final ArrayList<PlayerRef> participants;
    public final Object2ObjectMap<PlayerRef, DuelsPlayer> participants;
    public final Multimap<GameTeam, PlayerRef> teamPlayers;


    public static final ArrayList<DuelsGameActive> runningGames = new ArrayList<>();
    private GamePhase gamePhase;
    private long finishTime;

    public DuelsGameActive(DuelsConfig config, GameSpace gameSpace, ServerWorld world, DuelsMap gameMap, Multimap<GameTeam, PlayerRef> teamPlayers, Object2ObjectMap<PlayerRef, DuelsPlayer> participants) {
        this.config = config;
        this.gameSpace = gameSpace;
        this.world = world;
        this.gameMap = gameMap;
        this.teamPlayers = teamPlayers;
        this.participants = participants;
        gamePhase = GamePhase.GAME_CONTINUE;
    }

    public static void open(DuelsConfig config,GameSpace gameSpace,  ServerWorld world, DuelsMap duelsMap,Multimap<GameTeam, PlayerRef> teamPlayers,Object2ObjectMap<PlayerRef, DuelsPlayer> participants){
        gameSpace.setActivity(game -> {
            DuelsGameActive active = new DuelsGameActive(config, gameSpace, world, duelsMap, teamPlayers, participants);
            runningGames.add(active);


            game.allow(GameRuleType.PVP);
            game.deny(GameRuleType.BREAK_BLOCKS);

            game.listen(GameActivityEvents.TICK, active::tick);
            game.listen(GamePlayerEvents.OFFER, active::onPlayerOffer);
            game.listen(PlayerDeathEvent.EVENT, active::onPlayerDeath);
            game.listen(GameActivityEvents.ENABLE, active::onEnable);
            game.listen(GamePlayerEvents.REMOVE, active::removePlayer);
            game.listen(PlayerAttackEntityEvent.EVENT, active::attackEntity);

        });
    }

    private ActionResult attackEntity(ServerPlayerEntity serverPlayerEntity, Hand hand, Entity entity, EntityHitResult entityHitResult) {
        if(entity instanceof ServerPlayerEntity && (participants.get(PlayerRef.of(serverPlayerEntity)).team).equals(participants.get(PlayerRef.of((ServerPlayerEntity) entity)).team)) {
            return ActionResult.FAIL;
        }
        return ActionResult.PASS;
    }

    private void tick() {
        switch(this.gamePhase) {
            case GAME_CONTINUE -> {
                break;
            }
            case GAME_ENDING -> {
                if(world.getTime() >= finishTime){
                    gamePhase = GamePhase.GAME_FINISHED;
                }
            }
            case GAME_FINISHED -> {
                this.gameSpace.close(GameCloseReason.FINISHED);
            }
        }
    }


    private void removePlayer(ServerPlayerEntity player) {
        if(participants.containsKey(PlayerRef.of(player))) {
            GameTeam gameTeam = participants.get(PlayerRef.of(player)).team;

            this.participants.remove(PlayerRef.of(player));
            this.teamPlayers.values().remove(PlayerRef.of(player));


            tryEndDuel();
        }

    }


    public void equipPlayer(ServerPlayerEntity player) {
        for (ItemStack itemStack : this.config.items()) {
            player.getInventory().insertStack(ItemStackBuilder.of(itemStack).build());
        }

        player.equipStack(EquipmentSlot.HEAD, ItemStackBuilder.of(this.config.armor().get(0)).build());
        player.equipStack(EquipmentSlot.CHEST, ItemStackBuilder.of(this.config.armor().get(1)).build());
        player.equipStack(EquipmentSlot.LEGS, ItemStackBuilder.of(this.config.armor().get(2)).build());
        player.equipStack(EquipmentSlot.FEET, ItemStackBuilder.of(this.config.armor().get(3)).build());

    }

    private void onEnable() {
//        if(config.mode().equals("solo")){
//            Iterator<Vec3d> spawnIterator = new ArrayList<Vec3d>(List.of(gameMap.getSpawn1().centerTop(),gameMap.getSpawn2().centerTop())).iterator();
//            for (GameTeam team : teamPlayers.keySet()) {
//                Vec3d spawn = spawnIterator.next();
//                for (PlayerRef ref : teamPlayers.get(team)) {
//                    participants.get(ref).getServerPlayerEntity().requestTeleport(spawn.getX(),spawn.getY(),spawn.getZ());
//                    participants.get(ref).getServerPlayerEntity().refreshPositionAfterTeleport(spawn.getX(),spawn.getY(),spawn.getZ());
//                }
//            }
//        }
        Iterator<Vec3d> spawnIterator = new ArrayList<Vec3d>(List.of(gameMap.getSpawn1().centerTop(),gameMap.getSpawn2().centerTop())).iterator();
        for (GameTeam team : teamPlayers.keySet()) {
            Vec3d spawn = spawnIterator.next();
            for (PlayerRef ref : teamPlayers.get(team)) {
                participants.get(ref).getServerPlayerEntity().requestTeleport(spawn.getX(),spawn.getY(),spawn.getZ());
                participants.get(ref).getServerPlayerEntity().refreshPositionAfterTeleport(spawn.getX(),spawn.getY(),spawn.getZ());
                equipPlayer(participants.get(ref).getServerPlayerEntity());
            }
        }

    }

    private ActionResult onPlayerDeath(ServerPlayerEntity serverPlayerEntity, DamageSource source) {
//        BlockPos teleportPos = new BlockPos(0,65,0);
//        serverPlayerEntity.requestTeleport(0,65,0);
//        serverPlayerEntity.refreshPositionAndAngles(teleportPos,serverPlayerEntity.getYaw(),serverPlayerEntity.getPitch());

        serverPlayerEntity.changeGameMode(GameMode.SPECTATOR);
        removePlayer(serverPlayerEntity);
        return ActionResult.FAIL;
    }

    private PlayerOfferResult onPlayerOffer(PlayerOffer playerOffer) {
        ServerPlayerEntity player = playerOffer.player();
        Vec3d vec3d = gameMap.getSpawn1().centerTop();
        return playerOffer.accept(this.world, vec3d)
                .and(() -> {
                    player.changeGameMode(GameMode.SPECTATOR);
                });
    }

    public void tryEndDuel(){
//        if(this.config.mode().equals("solo") && participants.size() == 1) {
//            this.gameSpace.getPlayers().
//                    sendMessage(Text.of(participants.get(0).getServerPlayerEntity().getName().getString() + " has won the duel!"));
//
//            gamePhase = GamePhase.GAME_ENDING;
//            this.finishTime = world.getTime() + 20*10;
//        }
        if(this.teamPlayers.keySet().size() == 1){
            DuelsPlayer duelsPlayer = participants.values().iterator().next();
            if(this.config.mode().equals("solo")) {
                this.gameSpace.getPlayers().
                    sendMessage(Text.of(duelsPlayer.getServerPlayerEntity().getName().getString() + " has won the duel!"));
            }
            else{
                GameTeam winningTeam = duelsPlayer.team;
                this.gameSpace.getPlayers().sendMessage(
                        Text.of(winningTeam.config().name().getString() + " has won the duel!")
                );
            }


            gamePhase = GamePhase.GAME_ENDING;
            this.finishTime = world.getTime() + 20*10;
        }
    }





}
