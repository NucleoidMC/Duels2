package net.hyper_pigeon.duels.game.map;
import net.hyper_pigeon.duels.Duels;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.map_templates.MapTemplate;
import xyz.nucleoid.map_templates.MapTemplateSerializer;
import xyz.nucleoid.plasmid.game.GameOpenException;

import java.io.IOException;


public class DuelsMapGenerator{
    private final DuelsMapConfig config;
    private final MinecraftServer server;

    public DuelsMapGenerator(@NotNull DuelsMapConfig config, MinecraftServer server){
        this.config = config;
        this.server = server;
    }

    public @NotNull DuelsMap create() throws GameOpenException {
        MapTemplate template;
        try {
            template = MapTemplateSerializer.loadFromResource(server, this.config.id);
        } catch (IOException e) {
            throw new GameOpenException(Text.of("Could not load duels map " + config.id + " !"));
        }

        BlockBounds spawn1 = template.getMetadata().getFirstRegionBounds("spawn1");
        BlockBounds spawn2 = template.getMetadata().getFirstRegionBounds("spawn2");
        if (spawn1 == null || spawn2 == null) {
            Duels.LOGGER.error("Insufficient spawn data! Game will not work.");
            throw new GameOpenException(Text.of("Insufficient spawn data!"));
        }

        return new DuelsMap(template, spawn1, spawn2, config);
    }
}
