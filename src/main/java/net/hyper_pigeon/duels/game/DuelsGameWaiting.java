package net.hyper_pigeon.duels.game;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.hyper_pigeon.duels.game.map.DuelsMap;
import net.hyper_pigeon.duels.game.map.DuelsMapGenerator;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.plasmid.game.GameOpenContext;
import xyz.nucleoid.plasmid.game.GameOpenProcedure;
import xyz.nucleoid.plasmid.game.GameResult;
import xyz.nucleoid.plasmid.game.GameSpace;
import xyz.nucleoid.plasmid.game.common.GameWaitingLobby;
import xyz.nucleoid.plasmid.game.common.team.GameTeam;
import xyz.nucleoid.plasmid.game.common.team.GameTeamConfig;
import xyz.nucleoid.plasmid.game.common.team.GameTeamKey;
import xyz.nucleoid.plasmid.game.common.team.TeamAllocator;
import xyz.nucleoid.plasmid.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.game.player.PlayerOffer;
import xyz.nucleoid.plasmid.game.player.PlayerOfferResult;
import xyz.nucleoid.plasmid.game.rule.GameRuleType;
import xyz.nucleoid.plasmid.util.PlayerRef;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class DuelsGameWaiting {
    private final DuelsConfig config;
    private final GameSpace gameSpace;
    private final ServerWorld world;
    private final GameOpenContext<DuelsConfig> context;
    public final Object2ObjectMap<PlayerRef, DuelsPlayer> participants;
    private DuelsMap duelsMap;

    public DuelsGameWaiting(DuelsConfig config, GameSpace gameSpace, ServerWorld world, DuelsMap duelsMap, GameOpenContext<DuelsConfig> context) {
        this.config = config;
        this.gameSpace = gameSpace;
        this.world = world;
        this.context = context;
        this.duelsMap = duelsMap;
        this.participants = new Object2ObjectOpenHashMap<>();
    }

    public static GameOpenProcedure open(GameOpenContext<DuelsConfig> context) {
        DuelsConfig duelsConfig = context.config();
        DuelsMap duelsMap = new DuelsMapGenerator(duelsConfig.mapConfig(), context.server()).create();

        RuntimeWorldConfig worldConfig = new RuntimeWorldConfig()
                .setGenerator(duelsMap.asGenerator(context.server()))
                .setTimeOfDay(6000)
                .setGameRule(GameRules.DO_IMMEDIATE_RESPAWN, true)
                .setGameRule(GameRules.KEEP_INVENTORY, true);


        return context.openWithWorld(worldConfig, (activity, world) -> {
            DuelsGameWaiting waiting = new DuelsGameWaiting(duelsConfig, activity.getGameSpace(), world, duelsMap, context);
            GameWaitingLobby.addTo(activity, duelsConfig.playerConfig());
            activity.deny(GameRuleType.PVP);
            activity.deny(GameRuleType.BREAK_BLOCKS);

            activity.listen(GamePlayerEvents.OFFER, waiting::onPlayerOffer);
            activity.listen(GamePlayerEvents.JOIN, waiting::onPlayerJoin);
            activity.listen(GamePlayerEvents.LEAVE, waiting::playerLeave);
            activity.listen(PlayerDeathEvent.EVENT, waiting::onPlayerDeath);
            activity.listen(GameActivityEvents.REQUEST_START, waiting::requestStart);
        });
    }


    private GameResult requestStart() {
        HashSet<GameTeam> teams = new HashSet<>();
        List<DyeColor> teamColors = new ArrayList<>(Arrays.asList(
                DyeColor.BLUE,
                DyeColor.RED,
                DyeColor.YELLOW,
                DyeColor.GREEN,
                DyeColor.ORANGE,
                DyeColor.LIGHT_BLUE,
                DyeColor.PINK,
                DyeColor.PURPLE,
                DyeColor.CYAN,
                DyeColor.LIME,
                DyeColor.GREEN,
                DyeColor.MAGENTA,
                DyeColor.BROWN,
                DyeColor.GRAY,
                DyeColor.BLACK
        ));

        for (int i = 0; i < Math.round(participants.size() / (float) config.teamSize()); i++) {
            GameTeam team = new GameTeam(
                    new GameTeamKey("DuelsTeam" + i),
                    GameTeamConfig.builder()
                            .setName(Text.of("Team " + (i+1)))
                            .setCollision(AbstractTeam.CollisionRule.PUSH_OWN_TEAM)
                            .setFriendlyFire(false)
                            .setColors(config.teamSize() > 1 ? GameTeamConfig.Colors.from(teamColors.get(i)) : GameTeamConfig.Colors.NONE)
                            .build()
            );
            teams.add(team);
        }

        TeamAllocator<GameTeam, ServerPlayerEntity> allocator = new TeamAllocator<>(teams);
        for (ServerPlayerEntity playerEntity : gameSpace.getPlayers()) {
            allocator.add(playerEntity, null);
        }

        Multimap<GameTeam, PlayerRef> teamPlayers = HashMultimap.create();
        allocator.allocate((team, player) -> {
            teamPlayers.put(team, PlayerRef.of(player));
            participants.get(PlayerRef.of(player)).team = team;
        });

        DuelsGameActive.open(config, gameSpace, world, duelsMap,teamPlayers,participants);
        return GameResult.ok();
    }

    private ActionResult onPlayerDeath(ServerPlayerEntity serverPlayerEntity, DamageSource source) {
        serverPlayerEntity.setHealth(20.0F);
        Vec3d teleportPos = duelsMap.getSpawn1().centerTop();
        serverPlayerEntity.requestTeleport(teleportPos.getX(), teleportPos.getY(), teleportPos.getZ());
        return ActionResult.SUCCESS;
    }

    private PlayerOfferResult onPlayerOffer(PlayerOffer playerOffer) {
        ServerPlayerEntity player = playerOffer.player();
        Vec3d waitingSpawnPos = duelsMap.getSpawn1().centerTop();

        return playerOffer.accept(this.world, waitingSpawnPos)
                .and(() -> {
                    if (this.participants.size() < this.config.playerConfig().maxPlayers()) {
                        player.changeGameMode(GameMode.ADVENTURE);
                    } else {
                        player.changeGameMode(GameMode.SPECTATOR);
                    }
                });
    }

    private void onPlayerJoin(ServerPlayerEntity serverPlayerEntity) {
        if (this.participants.size() < this.config.playerConfig().maxPlayers()) {
            DuelsPlayer duelsPlayer = new DuelsPlayer(serverPlayerEntity);
            participants.put(PlayerRef.of(serverPlayerEntity), duelsPlayer);
        }
    }

    private void playerLeave (ServerPlayerEntity serverPlayerEntity){
        if (participants.containsKey(PlayerRef.of(serverPlayerEntity))) {
            participants.remove(PlayerRef.of(serverPlayerEntity));
        }
    }
}
