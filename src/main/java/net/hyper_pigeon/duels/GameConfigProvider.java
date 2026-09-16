package net.hyper_pigeon.duels;

import com.google.common.hash.HashCode;
import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.hyper_pigeon.duels.game.DuelsConfig;
import net.hyper_pigeon.duels.game.map.DuelsMapConfig;
import net.hyper_pigeon.duels.kits.Kit;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.StrictJsonParser;
import net.minecraft.util.Util;
import org.jspecify.annotations.NonNull;
import xyz.nucleoid.plasmid.api.game.common.config.WaitingLobbyConfig;
import xyz.nucleoid.plasmid.api.game.config.CustomValuesConfig;
import xyz.nucleoid.plasmid.api.game.config.GameConfig;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;

public class GameConfigProvider implements DataProvider {
    private final PackOutput output;
    private final CompletableFuture<HolderLookup.Provider> providerFuture;

    public GameConfigProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        this.output = output;
        this.providerFuture = registriesFuture;
    }

    public void runWriters(BiConsumer<String, byte[]> assetWriter) {
        HolderLookup.Provider provider;
        try {
            provider = providerFuture.get();
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        ArrayList<Identifier> maps = new ArrayList<>();
        ArrayList<Kit> kits = new ArrayList<>();
        for (File file : Objects.requireNonNull(output.getOutputFolder().getParent().resolve("resources/data/duels/map_template").toFile().listFiles())) {
            maps.add(Identifier.tryBuild("duels", file.getName().split("\\.")[0]));
        }
        var ops = provider.createSerializationContext(JsonOps.INSTANCE);
        for (File file : Objects.requireNonNull(output.getOutputFolder().getParent().resolve("resources/data/duels/duels_kits").toFile().listFiles())) {
            try {
                FileReader reader = new FileReader(file);
                try {
                    var result = Kit.CODEC.codec().decode(ops, StrictJsonParser.parse(reader));
                    result.ifSuccess((res) -> {
                        kits.add(res.getFirst());
                    });
                    result.ifError(err -> {
                        Duels.LOGGER.error("Failed to parse kit {}: {}", file.getName(), err.message());
                    });
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                reader.close();
            } catch (Exception e) {
                Duels.LOGGER.error("Failed to open kit file {}: {}", file.getName(), e.getMessage());
            }
        }

        generateConfigsForMode("single", maps, kits, ops, assetWriter);
        generateConfigsForMode("duos", maps, kits, ops, assetWriter);
    }

    private void generateConfigsForMode(String mode, ArrayList<Identifier> maps, ArrayList<Kit> kits, RegistryOps<JsonElement> ops, BiConsumer<String, byte[]> assetWriter) {
        int teamSize = mode.equals("single") ? 1 : 2;
        for (Identifier map : maps) {
            for (Kit kit : kits) {
                GameConfig<?> config = new GameConfig<>(
                        Duels.TYPE, null, null, null, kit.icon(), CustomValuesConfig.empty(),
                        new DuelsConfig(mode,
                                new WaitingLobbyConfig(teamSize * 2, teamSize * 2),
                                teamSize,
                                kit,
                                new DuelsMapConfig(map)));
                String configName = kit.name() + "_" + map.getPath();
                DataResult<JsonElement> result = GameConfig.ENTRY_CODEC.encodeStart(ops, Holder.direct(config));
                result.ifSuccess(json -> {
                    assetWriter.accept("data/duels/plasmid/game/"+mode+"/"+configName+".json", result.getOrThrow().toString().getBytes(StandardCharsets.UTF_8));
                });
                result.ifError(error -> {
                    Duels.LOGGER.error("Failed to generate game config for {} in {} mode because {}", map.toString(), mode, error.message());
                });
            }
        }
    }

    @Override
    public CompletableFuture<?> run(CachedOutput writer) {
        BiConsumer<String, byte[]> assetWriter = (path, data) -> {
            try {
                writer.writeIfNeeded(this.output.getOutputFolder().resolve(path), data, HashCode.fromBytes(data));
            } catch (IOException e) {
                e.printStackTrace();
            }
        };
        return CompletableFuture.runAsync(() -> {
            runWriters(assetWriter);
        }, Util.backgroundExecutor());
    }

    @Override
    public @NonNull String getName() {
        return "duels:config";
    }
}
