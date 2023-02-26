package net.hyper_pigeon.duels.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.hyper_pigeon.duels.game.map.DuelsMapConfig;
import net.minecraft.item.ItemStack;
import xyz.nucleoid.plasmid.game.common.config.PlayerConfig;

import java.util.List;

public record DuelsConfig(String mode, PlayerConfig playerConfig, int teamSize,List<ItemStack> armor, List<ItemStack> items, DuelsMapConfig mapConfig) {
    public static final Codec<DuelsConfig> CODEC = RecordCodecBuilder.create(instance -> {
        return instance.group(
                Codec.STRING.fieldOf("mode").forGetter(DuelsConfig::mode),
                PlayerConfig.CODEC.fieldOf("players").forGetter(config -> config.playerConfig),
                Codec.INT.optionalFieldOf("team_size", 1).forGetter(DuelsConfig::teamSize),
                Codec.list(ItemStack.CODEC).fieldOf("armor").forGetter(DuelsConfig::armor),
                Codec.list(ItemStack.CODEC).fieldOf("items").forGetter(DuelsConfig::items),
                DuelsMapConfig.CODEC.fieldOf("map").forGetter(config -> config.mapConfig)
        ).apply(instance, DuelsConfig::new);
    });
}
