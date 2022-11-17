package net.hyper_pigeon.duels.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.ItemStack;
import xyz.nucleoid.plasmid.game.common.config.PlayerConfig;

import java.util.List;

public record DuelsConfig(String mode, PlayerConfig playerConfig, List<ItemStack> armor, List<ItemStack> items) {
    public static final Codec<DuelsConfig> CODEC = RecordCodecBuilder.create(instance -> {
        return instance.group(
                Codec.STRING.fieldOf("mode").forGetter(DuelsConfig::mode),
                PlayerConfig.CODEC.fieldOf("players").forGetter(config -> config.playerConfig),
                Codec.list(ItemStack.CODEC).fieldOf("armor").forGetter(DuelsConfig::armor),
                Codec.list(ItemStack.CODEC).fieldOf("items").forGetter(DuelsConfig::items)
        ).apply(instance, DuelsConfig::new);
    });
}
