package net.hyper_pigeon.duels.kits;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.List;
import java.util.Optional;

public record Kit(ItemStackTemplate icon, String name, List<ItemStackTemplate> armor, List<ItemStackTemplate> items, Optional<ItemStackTemplate> offhand) {
    public static final MapCodec<Kit> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ItemStackTemplate.CODEC.fieldOf("icon").forGetter(Kit::icon),
            Codec.STRING.fieldOf("name").forGetter(Kit::name),
            Codec.list(ItemStackTemplate.CODEC).fieldOf("armor").forGetter(Kit::armor),
            Codec.list(ItemStackTemplate.CODEC).fieldOf("items").forGetter(Kit::items),
            ItemStackTemplate.CODEC.optionalFieldOf("offhand").forGetter(Kit::offhand)
    ).apply(instance, Kit::new));
}
