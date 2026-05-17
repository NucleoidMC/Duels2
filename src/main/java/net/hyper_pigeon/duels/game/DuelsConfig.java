package net.hyper_pigeon.duels.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.hyper_pigeon.duels.game.map.DuelsMapConfig;
import net.minecraft.world.item.ItemStackTemplate;
import xyz.nucleoid.plasmid.api.game.common.config.WaitingLobbyConfig;

import java.util.List;

public record DuelsConfig(String mode, WaitingLobbyConfig playerConfig, int teamSize, List<ItemStackTemplate> armor, List<ItemStackTemplate> items, DuelsMapConfig mapConfig) {
    public static final MapCodec<DuelsConfig> CODEC = RecordCodecBuilder.mapCodec(instance -> {
        return instance.group(
                Codec.STRING.fieldOf("mode").forGetter(DuelsConfig::mode),
                WaitingLobbyConfig.CODEC.fieldOf("players").forGetter(DuelsConfig::playerConfig),
                Codec.INT.optionalFieldOf("team_size", 1).forGetter(DuelsConfig::teamSize),
                Codec.list(ItemStackTemplate.CODEC).fieldOf("armor").forGetter(DuelsConfig::armor),
                Codec.list(ItemStackTemplate.CODEC).fieldOf("items").forGetter(DuelsConfig::items),
                DuelsMapConfig.CODEC.fieldOf("map").forGetter(DuelsConfig::mapConfig)
        ).apply(instance, DuelsConfig::new);
    });
}
