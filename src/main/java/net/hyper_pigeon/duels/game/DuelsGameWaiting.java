package net.hyper_pigeon.duels.game;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.hyper_pigeon.duels.game.map.DuelsMap;
import net.hyper_pigeon.duels.game.map.DuelsMapGenerator;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.scores.Team;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;
import xyz.nucleoid.fantasy.RuntimeLevelConfig;
import xyz.nucleoid.plasmid.api.game.GameOpenContext;
import xyz.nucleoid.plasmid.api.game.GameOpenProcedure;
import xyz.nucleoid.plasmid.api.game.GameResult;
import xyz.nucleoid.plasmid.api.game.GameSpace;
import xyz.nucleoid.plasmid.api.game.common.GameWaitingLobby;
import xyz.nucleoid.plasmid.api.game.common.team.GameTeam;
import xyz.nucleoid.plasmid.api.game.common.team.GameTeamConfig;
import xyz.nucleoid.plasmid.api.game.common.team.GameTeamKey;
import xyz.nucleoid.plasmid.api.game.common.team.TeamAllocator;
import xyz.nucleoid.plasmid.api.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.api.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.api.game.player.*;
import xyz.nucleoid.plasmid.api.game.rule.GameRuleType;
import xyz.nucleoid.plasmid.api.util.PlayerRef;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class DuelsGameWaiting {
    private final DuelsConfig config;
    private final GameSpace gameSpace;
    private final ServerLevel level;
    private final GameOpenContext<DuelsConfig> context;
    public final Object2ObjectMap<PlayerRef, DuelsPlayer> participants;
    private DuelsMap duelsMap;

    public DuelsGameWaiting(DuelsConfig config, GameSpace gameSpace, ServerLevel level, DuelsMap duelsMap, GameOpenContext<DuelsConfig> context) {
        this.config = config;
        this.gameSpace = gameSpace;
        this.level = level;
        this.context = context;
        this.duelsMap = duelsMap;
        this.participants = new Object2ObjectOpenHashMap<>();
    }

    public static GameOpenProcedure open(GameOpenContext<DuelsConfig> context) {
        DuelsConfig duelsConfig = context.config();
        DuelsMap duelsMap = new DuelsMapGenerator(duelsConfig.mapConfig(), context.server()).create();

        RuntimeLevelConfig levelConfig = new RuntimeLevelConfig()
                .setGenerator(duelsMap.asGenerator(context.server()))
                //.setTimeOfDay(6000)
                .setGameRule(GameRules.IMMEDIATE_RESPAWN, true)
                .setGameRule(GameRules.KEEP_INVENTORY, true);


        return context.openWithLevel(levelConfig, (activity, level) -> {
            DuelsGameWaiting waiting = new DuelsGameWaiting(duelsConfig, activity.getGameSpace(), level, duelsMap, context);
            GameWaitingLobby.addTo(activity, duelsConfig.playerConfig());
            activity.deny(GameRuleType.PVP);
            activity.deny(GameRuleType.BREAK_BLOCKS);

            activity.listen(GamePlayerEvents.OFFER, JoinOffer::accept);
            activity.listen(GamePlayerEvents.ACCEPT, waiting::acceptPlayer);
            activity.listen(GamePlayerEvents.JOIN, waiting::onPlayerJoin);
            activity.listen(GamePlayerEvents.LEAVE, waiting::playerLeave);
            activity.listen(PlayerDeathEvent.EVENT, waiting::onPlayerDeath);
            activity.listen(GameActivityEvents.REQUEST_START, waiting::requestStart);
        });
    }

    private JoinAcceptorResult acceptPlayer(JoinAcceptor acceptor) {
        Vec3 waitingSpawnPos = duelsMap.getSpawn1().centerTop();
        return acceptor.teleport(this.level, waitingSpawnPos).thenRunForEach((plr, intent) -> {
            if (intent == JoinIntent.SPECTATE) {
                plr.setGameMode(GameType.SPECTATOR);
            } else {
                plr.setGameMode(GameType.ADVENTURE);
            }
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
                            .setName(Component.nullToEmpty("Team " + (i+1)))
                            .setCollision(Team.CollisionRule.PUSH_OWN_TEAM)
                            .setFriendlyFire(false)
                            .setColors(config.teamSize() > 1 ? GameTeamConfig.Colors.from(teamColors.get(i)) : GameTeamConfig.Colors.NONE)
                            .build()
            );
            teams.add(team);
        }

        TeamAllocator<GameTeam, ServerPlayer> allocator = new TeamAllocator<>(teams);
        for (ServerPlayer playerEntity : gameSpace.getPlayers()) {
            allocator.add(playerEntity, null);
        }

        Multimap<GameTeam, PlayerRef> teamPlayers = HashMultimap.create();
        allocator.allocate((team, player) -> {
            teamPlayers.put(team, PlayerRef.of(player));
            participants.get(PlayerRef.of(player)).team = team;
        });

        DuelsGameActive.open(config, gameSpace, level, duelsMap,teamPlayers,participants);
        return GameResult.ok();
    }

    private EventResult onPlayerDeath(ServerPlayer serverPlayer, DamageSource source) {
        serverPlayer.setHealth(20.0F);
        Vec3 teleportPos = duelsMap.getSpawn1().centerTop();
        serverPlayer.teleportTo(teleportPos.x(), teleportPos.y(), teleportPos.z());
        return EventResult.ALLOW;
    }

    private void onPlayerJoin(ServerPlayer serverPlayer) {
        if (this.participants.size() < this.config.playerConfig().playerConfig().maxPlayers().getAsInt()) {
            DuelsPlayer duelsPlayer = new DuelsPlayer(serverPlayer);
            participants.put(PlayerRef.of(serverPlayer), duelsPlayer);
        }
    }

    private void playerLeave (ServerPlayer serverPlayer){
        participants.remove(PlayerRef.of(serverPlayer));
    }
}
