package net.hyper_pigeon.duels.game;

import net.hyper_pigeon.duels.config.DuelsConfig;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import xyz.nucleoid.plasmid.game.GameCloseReason;
import xyz.nucleoid.plasmid.game.GameSpace;
import xyz.nucleoid.plasmid.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.game.player.PlayerOffer;
import xyz.nucleoid.plasmid.game.player.PlayerOfferResult;
import xyz.nucleoid.plasmid.game.rule.GameRuleType;
import xyz.nucleoid.plasmid.util.ItemStackBuilder;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

import java.util.ArrayList;

public class DuelsGameActive {
    private final DuelsConfig config;
    private final GameSpace gameSpace;
    private final ServerWorld world;
    private final ArrayList<ServerPlayerEntity> duelists;
    public static final ArrayList<DuelsGameActive> runningGames = new ArrayList<>();
    private GamePhase gamePhase;
    private long endTime;

    public DuelsGameActive(DuelsConfig config, GameSpace gameSpace, ServerWorld world, ArrayList<ServerPlayerEntity> duelists) {
        this.config = config;
        this.gameSpace = gameSpace;
        this.world = world;
        this.duelists = duelists;
        gamePhase = GamePhase.GAME_CONTINUE;
    }

    public static void open(DuelsConfig config,GameSpace gameSpace,  ServerWorld world, ArrayList<ServerPlayerEntity> duelists){
        gameSpace.setActivity(game -> {
            DuelsGameActive active = new DuelsGameActive(config, gameSpace, world, duelists);
            runningGames.add(active);


            game.allow(GameRuleType.PVP);
            game.deny(GameRuleType.BREAK_BLOCKS);

            game.listen(GameActivityEvents.TICK, active::tick);
            game.listen(GamePlayerEvents.OFFER, active::onPlayerOffer);
            game.listen(PlayerDeathEvent.EVENT, active::onPlayerDeath);
            game.listen(GameActivityEvents.ENABLE, active::onEnable);


        });
    }

    private void tick() {
        switch(this.gamePhase) {
            case GAME_CONTINUE -> {
                break;
            }
            case GAME_ENDING -> {
                if(world.getTime() >= endTime){
                    gamePhase = GamePhase.GAME_FINISHED;
                }
            }
            case GAME_FINISHED -> {
                this.gameSpace.close(GameCloseReason.FINISHED);
            }
        }
    }

    //shamelessly ripped from https://github.com/NucleoidMC/skywars/blob/1.19/src/main/java/us/potatoboy/skywars/kit/Kit.java

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
        BlockPos teleportPos = new BlockPos(-25,65,-25);
        for (ServerPlayerEntity duelist : duelists){
            equipPlayer(duelist);
            duelist.requestTeleport(teleportPos.getX(),teleportPos.getY(),teleportPos.getZ());
            duelist.refreshPositionAfterTeleport(teleportPos.getX(),teleportPos.getY(),teleportPos.getZ());
            teleportPos = new BlockPos(25,65,25);
        }
    }

    private ActionResult onPlayerDeath(ServerPlayerEntity serverPlayerEntity, DamageSource source) {

        BlockPos teleportPos = new BlockPos(0,65,0);
        serverPlayerEntity.requestTeleport(0,65,0);
        serverPlayerEntity.refreshPositionAndAngles(teleportPos,serverPlayerEntity.getYaw(),serverPlayerEntity.getPitch());

        serverPlayerEntity.changeGameMode(GameMode.SPECTATOR);


        if (duelists.contains(serverPlayerEntity)){
            duelists.remove(serverPlayerEntity);
            this.gameSpace.getPlayers().
                    sendMessage(Text.of(duelists.get(0).getName().getString() + " has won the duel!"));

            gamePhase = GamePhase.GAME_ENDING;
            this.endTime = world.getTime() + 20*10;
        }
        return ActionResult.FAIL;
    }

    private PlayerOfferResult onPlayerOffer(PlayerOffer playerOffer) {
        ServerPlayerEntity player = playerOffer.player();
        BlockPos blockPos = new BlockPos(-25,65,-25);
        return playerOffer.accept(this.world, new Vec3d(blockPos.getX(), blockPos.getY(), blockPos.getZ()))
                .and(() -> {
                    player.changeGameMode(GameMode.SPECTATOR);
                });
    }


}
