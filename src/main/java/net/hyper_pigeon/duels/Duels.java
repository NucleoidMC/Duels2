package net.hyper_pigeon.duels;

import net.fabricmc.api.ModInitializer;
import net.hyper_pigeon.duels.config.DuelsConfig;
import net.hyper_pigeon.duels.game.DuelsGameWaiting;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.nucleoid.plasmid.game.GameType;

public class Duels implements ModInitializer {

    public static final String ID = "duels";
    public static final Logger LOGGER = LogManager.getLogger(ID);

    public static final GameType<DuelsConfig> TYPE = GameType.register(
            new Identifier(ID, "duels"),
            DuelsConfig.CODEC,
            DuelsGameWaiting::open
    );

    @Override
    public void onInitialize() {
    }
}
