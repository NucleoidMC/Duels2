package net.hyper_pigeon.duels.game.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;

/*
Taken from https://github.com/NucleoidMC/Duels
 */
public class DuelsMapConfig {
    public static final Codec<DuelsMapConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("id").forGetter(config -> config.id)
    ).apply(instance, DuelsMapConfig::new));

    public final Identifier id;

    public DuelsMapConfig(@NotNull Identifier id) {
        this.id = id;
    }
}