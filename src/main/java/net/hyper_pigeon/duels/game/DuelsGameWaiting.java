package net.hyper_pigeon.duels.game;

import net.hyper_pigeon.duels.config.DuelsConfig;
import net.minecraft.block.Blocks;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.map_templates.MapTemplate;
import xyz.nucleoid.plasmid.game.GameOpenContext;
import xyz.nucleoid.plasmid.game.GameOpenProcedure;
import xyz.nucleoid.plasmid.game.GameResult;
import xyz.nucleoid.plasmid.game.GameSpace;
import xyz.nucleoid.plasmid.game.common.GameWaitingLobby;
import xyz.nucleoid.plasmid.game.common.config.PlayerConfig;
import xyz.nucleoid.plasmid.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.game.player.PlayerOffer;
import xyz.nucleoid.plasmid.game.player.PlayerOfferResult;
import xyz.nucleoid.plasmid.game.rule.GameRuleType;
import xyz.nucleoid.plasmid.game.world.generator.TemplateChunkGenerator;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

import java.util.ArrayList;

public class DuelsGameWaiting {
    private final DuelsConfig config;
    private final GameSpace gameSpace;
    private final ServerWorld world;
    private final GameOpenContext<DuelsConfig> context;
    private ArrayList<ServerPlayerEntity> duelists = new ArrayList<>();

    public DuelsGameWaiting(DuelsConfig config, GameSpace gameSpace, ServerWorld world, GameOpenContext<DuelsConfig> context) {
        this.config = config;
        this.gameSpace = gameSpace;
        this.world = world;
        this.context = context;
    }

    public static GameOpenProcedure open(GameOpenContext<DuelsConfig> context) {
        DuelsConfig duelsConfig = context.config();

        MapTemplate template = MapTemplate.createEmpty();

        for(int i = -30; i <= 30; i++){
            for(int k = -30; k <30;k++){
                template.setBlockState(new BlockPos(i, 64, k), Blocks.BEDROCK.getDefaultState());
            }
        }

        for(int i = -30; i < 30; i++){
            template.setBlockState(new BlockPos(i,65,-30), Blocks.GLASS.getDefaultState());
            template.setBlockState(new BlockPos(i,66,-30), Blocks.GLASS.getDefaultState());
            template.setBlockState(new BlockPos(i,65,30), Blocks.GLASS.getDefaultState());
            template.setBlockState(new BlockPos(i,66,30), Blocks.GLASS.getDefaultState());
            template.setBlockState(new BlockPos(-30,65,i), Blocks.GLASS.getDefaultState());
            template.setBlockState(new BlockPos(-30,66,i), Blocks.GLASS.getDefaultState());
            template.setBlockState(new BlockPos(30,65,i), Blocks.GLASS.getDefaultState());
            template.setBlockState(new BlockPos(30,66,i), Blocks.GLASS.getDefaultState());
        }

        TemplateChunkGenerator generator = new TemplateChunkGenerator(context.server(), template);

        RuntimeWorldConfig worldConfig = new RuntimeWorldConfig()
                .setGenerator(generator)
                .setTimeOfDay(6000)
                .setGameRule(GameRules.DO_IMMEDIATE_RESPAWN, true)
                .setGameRule(GameRules.KEEP_INVENTORY, true);


        return context.openWithWorld(worldConfig, (activity, world) -> {
            DuelsGameWaiting waiting = new DuelsGameWaiting(duelsConfig, activity.getGameSpace(), world, context);
            GameWaitingLobby.addTo(activity, new PlayerConfig(1, 2));
            activity.deny(GameRuleType.PVP);
            activity.deny(GameRuleType.BREAK_BLOCKS);

            activity.listen(GamePlayerEvents.OFFER, waiting::onPlayerOffer);
            activity.listen(PlayerDeathEvent.EVENT, waiting::onPlayerDeath);
            activity.listen(GameActivityEvents.REQUEST_START, waiting::requestStart);
        });
    }

    private GameResult requestStart() {
        DuelsGameActive.open(config,gameSpace,world,duelists);
        return GameResult.ok();
    }

    private ActionResult onPlayerDeath(ServerPlayerEntity serverPlayerEntity, DamageSource source) {
        serverPlayerEntity.setHealth(20.0F);
        serverPlayerEntity.requestTeleport(0,66,0);
        return ActionResult.SUCCESS;
    }

    private PlayerOfferResult onPlayerOffer(PlayerOffer playerOffer) {
        ServerPlayerEntity player = playerOffer.player();
        return playerOffer.accept(this.world, new Vec3d(0.0, 66.0, 0.0))
                .and(() -> {
                    if(this.duelists.size() < 2) {
                        duelists.add(player);
                        player.changeGameMode(GameMode.ADVENTURE);
                    }
                    else {
                        player.changeGameMode(GameMode.SPECTATOR);
                    }
                });
    }


}
