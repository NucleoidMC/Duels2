package net.hyper_pigeon.duels.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.ItemStack;

import java.util.List;

public record DuelsConfig(String mode, List<ItemStack> armor, List<ItemStack> items) {
    public static final Codec<DuelsConfig> CODEC = RecordCodecBuilder.create(instance -> {
        return instance.group(
                Codec.STRING.fieldOf("mode").forGetter(DuelsConfig::mode),
                Codec.list(ItemStack.CODEC).fieldOf("armor").forGetter(DuelsConfig::armor),
                Codec.list(ItemStack.CODEC).fieldOf("items").forGetter(DuelsConfig::items)
        ).apply(instance, DuelsConfig::new);
    });
}
