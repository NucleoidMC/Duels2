package net.hyper_pigeon.duels.game;

import com.google.common.collect.Multimap;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.hyper_pigeon.duels.Duels;
import net.hyper_pigeon.duels.game.map.DuelsMap;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.GameType;
import xyz.nucleoid.plasmid.api.game.*;
import xyz.nucleoid.plasmid.api.game.common.team.GameTeam;
import xyz.nucleoid.plasmid.api.game.config.GameConfig;
import xyz.nucleoid.plasmid.api.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.api.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.api.game.player.*;
import xyz.nucleoid.plasmid.api.game.rule.GameRuleType;
import xyz.nucleoid.plasmid.api.util.PlayerRef;
import xyz.nucleoid.plasmid.impl.game.manager.GameSpaceManagerImpl;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.item.ItemUseEvent;
import xyz.nucleoid.stimuli.event.player.PlayerAttackEntityEvent;
import xyz.nucleoid.stimuli.event.player.PlayerConsumeHungerEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;
import xyz.nucleoid.stimuli.event.projectile.ProjectileHitEvent;

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
    private long finishTime = -1;
    private long votingTime = -1;
    private long rematchTime = -1;
    private ArrayList<ServerPlayer> playersVoted = new ArrayList<>();
    private int votesYes = 0;
    private int votesNo = 0;
    private boolean voteFinished = false;
    private boolean creatingGameSpace = false;

    private GameOpenContext<DuelsConfig> context;

    public DuelsGameActive(DuelsConfig config, GameSpace gameSpace, ServerLevel level, DuelsMap gameMap, Multimap<GameTeam, PlayerRef> teamPlayers, Object2ObjectMap<PlayerRef, DuelsPlayer> participants, GameOpenContext<DuelsConfig> context) {
        this.config = config;
        this.gameSpace = gameSpace;
        this.level = level;
        this.gameMap = gameMap;
        this.teamPlayers = teamPlayers;
        this.participants = participants;
        this.context = context;
        gamePhase = GamePhase.GAME_CONTINUE;
    }

    public static void open(DuelsConfig config, GameSpace gameSpace, ServerLevel level, DuelsMap duelsMap, Multimap<GameTeam, PlayerRef> teamPlayers, Object2ObjectMap<PlayerRef, DuelsPlayer> participants, GameOpenContext<DuelsConfig> context){
        gameSpace.setActivity(game -> {
            DuelsGameActive active = new DuelsGameActive(config, gameSpace, level, duelsMap, teamPlayers, participants, context);
            runningGames.add(active);

            game.allow(GameRuleType.PVP);
            game.allow(GameRuleType.HUNGER);
            game.allow(GameRuleType.SATURATED_REGENERATION);
            game.deny(GameRuleType.BREAK_BLOCKS);

            game.listen(GameActivityEvents.TICK, active::tick);
            game.listen(GamePlayerEvents.OFFER, JoinOffer::acceptSpectators);
            game.listen(GamePlayerEvents.ACCEPT, active::acceptPlayer);
            game.listen(PlayerDeathEvent.EVENT, active::onPlayerDeath);
            game.listen(GameActivityEvents.ENABLE, active::onEnable);
            game.listen(GamePlayerEvents.REMOVE, active::removePlayer);
            game.listen(PlayerAttackEntityEvent.EVENT, active::attackEntity);
            game.listen(ProjectileHitEvent.ENTITY, active::projectileHit);
            game.listen(PlayerConsumeHungerEvent.EVENT, active::consumeHunger);
            game.listen(ItemUseEvent.EVENT, active::onItemUse);
        });
    }

    private EventResult consumeHunger(ServerPlayer player, int foodLevel, float saturation, float exhaustion) {
        if (foodLevel <= 16) {
            return EventResult.DENY;
        }
        return EventResult.PASS;
    }

    private JoinAcceptorResult acceptPlayer(JoinAcceptor acceptor) {
        Vec3 pos = gameMap.getSpawn1().centerTop();
        return acceptor.teleport(level, pos).thenRunForEach((plr) -> {
            plr.setGameMode(GameType.SPECTATOR);
        });
    }

    private EventResult attackEntity(ServerPlayer serverPlayer, InteractionHand hand, Entity entity, EntityHitResult entityHitResult) {
        if((entity instanceof ServerPlayer hitPlayer && getTeam(serverPlayer) != null && getTeam(hitPlayer) != null && Objects.equals(getTeam(serverPlayer), getTeam(hitPlayer)))) {
            return EventResult.DENY;
        }
        return EventResult.PASS;
    }

    private EventResult projectileHit(Projectile projectile, EntityHitResult entityHitResult) {
        return attackEntity((ServerPlayer) projectile.getOwner(), null, entityHitResult.getEntity(), entityHitResult);
    }

    // regen wind charges on use
    private InteractionResult onItemUse(ServerPlayer player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        if (itemStack.getItem().equals(Items.WIND_CHARGE)) {
            itemStack.setCount(64);
        }
        return InteractionResult.PASS;
    }
    private void tick() {
        switch(this.gamePhase) {
            case GAME_CONTINUE -> {
                break;
            }
            case GAME_ENDING -> {
                if (rematchTime != -1 && level.getGameTime() >= rematchTime) {
                    if (creatingGameSpace) break;
                    creatingGameSpace = true;
                    MutablePlayerSet players = this.gameSpace.getPlayers().copy(gameSpace.getServer());
                    GameSpaceManager.get().open(gameSpace.getMetadata().sourceConfig()).handleAsync((newGameSpace, throwable) -> {
                        for (ServerPlayer player : players) {
                            JoinIntent intent;
                            GameSpacePlayers oldPlayers = this.gameSpace.getPlayers();
                            intent = oldPlayers.participants().contains(player) ? JoinIntent.PLAY : JoinIntent.SPECTATE;
                            oldPlayers.kick(player);
                            GamePlayerJoiner.tryJoin(player, newGameSpace, intent);
                        }
                        return null;
                    });
                    break;
                }
                if (level.getGameTime() >= votingTime && !voteFinished) {
                    PlayerSet players = gameSpace.getPlayers().participants();
                    players.forEach((player) -> {
                        player.sendSystemMessage(Component.translatable("duels.vote.waiting"), true);
                        if (!player.hasContainerOpen() && !playersVoted.contains(player)) {
                            openVotingGui(player);
                        }
                    });
                    if ((votesNo + votesYes) >= players.size()) {
                        if (votesYes == votesNo) {
                            gameSpace.getPlayers().sendMessage(Component.translatable("duels.vote.tie"));
                            finishTime = level.getGameTime() + SharedConstants.TICKS_PER_SECOND * 5;
                        } else if (votesYes >= votesNo) {
                            gameSpace.getPlayers().sendMessage(Component.translatable("duels.vote.rematch"));
                            rematchTime = level.getGameTime() + SharedConstants.TICKS_PER_SECOND * 5;
                            finishTime = level.getGameTime() + SharedConstants.TICKS_PER_SECOND * 30;
                        } else {
                            gameSpace.getPlayers().sendMessage(Component.translatable("duels.vote.end"));
                            finishTime = level.getGameTime() + SharedConstants.TICKS_PER_SECOND * 5;
                        }
                        voteFinished = true;
                        gameSpace.getPlayers().playSound(SoundEvents.NOTE_BLOCK_PLING.value());
                    }
                }
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
        player.setHealth(20);
        player.getInventory().clearContent();
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5);
        for (ItemStackTemplate itemStackTemplate : this.config.items()) {
            player.getInventory().add(itemStackTemplate.create());
        }
        if (this.config.offhand().isPresent()) {
            player.setItemInHand(InteractionHand.OFF_HAND, this.config.offhand().get().create());
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
                participants.get(ref).getServerPlayer().teleportTo(spawn.x(),spawn.y(),spawn.z());
                participants.get(ref).getServerPlayer().snapTo(spawn.x(),spawn.y(),spawn.z());
                equipPlayer(participants.get(ref).getServerPlayer());
            }
        }
    }

    private EventResult onPlayerDeath(ServerPlayer serverPlayer, DamageSource source) {
        serverPlayer.setGameMode(GameType.SPECTATOR);
        gameSpace.getPlayers().playSound(SoundEvents.CONDUIT_DEACTIVATE);
        removePlayer(serverPlayer);
        return EventResult.DENY;
    }

    private void openVotingGui(ServerPlayer player) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_3x3, player, false);
        gui.setTitle(Component.translatable("duels.vote.title"));
        gui.setSlot(3, GuiElementBuilder.from(Items.WOOL.green().getDefaultInstance()).setName(Component.translatable("duels.vote.yes")).setCallback(() -> {
            votesYes++;
            playersVoted.add(player);
            player.closeContainer();
            gameSpace.getPlayers().playSound(SoundEvents.NOTE_BLOCK_PLING.value());
        }));

        gui.setSlot(5, GuiElementBuilder.from(Items.WOOL.red().getDefaultInstance()).setName(Component.translatable("duels.vote.no")).setCallback(() -> {
            votesNo++;
            playersVoted.add(player);
            player.closeContainer();
            gameSpace.getPlayers().playSound(SoundEvents.NOTE_BLOCK_PLING.value());
        }));
        gui.open();
    }

    public void tryEndDuel(){
        if(this.teamPlayers.keySet().size() == 1) {
            DuelsPlayer duelsPlayer = participants.values().iterator().next();
            if(this.config.mode().equals("single")) {
                this.gameSpace.getPlayers().sendMessage(Component.translatable("duels.win_text", duelsPlayer.getServerPlayer().getName()));
            }
            else {
                GameTeam winningTeam = duelsPlayer.team;
                this.gameSpace.getPlayers().sendMessage(
                        Component.translatable("duels.win_text", winningTeam.config().name())
                );
            }
            this.gameSpace.getPlayers().playSound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 1, 1);

            gamePhase = GamePhase.GAME_ENDING;
            this.votingTime = level.getGameTime() + SharedConstants.TICKS_PER_SECOND * 5;
            this.finishTime = level.getGameTime() + SharedConstants.TICKS_PER_SECOND * 30;
        }
    }
}
