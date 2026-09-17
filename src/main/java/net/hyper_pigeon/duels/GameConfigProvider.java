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
import net.minecraft.network.chat.Component;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;

public class GameConfigProvider implements DataProvider {
    private final PackOutput output;
    private final CompletableFuture<HolderLookup.Provider> providerFuture;
    String[] modes = {"single", "duos"};
    Map<String, String> modeToIcon = Map.of("single", "iron_sword", "duos", "shield");
    Map<String, Integer> modeSize = Map.of("single", 1, "duos", 2);
    Map<String, String> mapToIcon = Map.of("mountains", "grass_block", "urban", "wind_charge");
    String randomGameString = """
    {
      "type": "plasmid:random",
      "games": |GAMES|,
      "name": {
        "translate": "game.nucleoid.random"
      },
      "icon": "emerald"
    }
    """;

    String portalString = """
        {
            "type": "nucleoid_extras:styled/simple_menu",
            "icon": "|ICON|",
            "games": |GAMES|,
            "name": {
                "translate": "game.nucleoid.|MODE|"
            }
        }
        """;
    String rootPortal = """
            {
              "type": "nucleoid_extras:styled/advanced_menu",
              "name": {
                "translate": "|NAME|"
              },
              "icon": "|ICON|",
              "entries": |ENTRIES|
            }
            """;
    String portalEntry = """
            {
                "type": "nucleoid_extras:quick_portal",
                "portal": "duels:duels/|MAP|/root",
                "quick_portal": "duels:duels/|MAP|/root"
            }
            """;
    String portalEntryWithQuickAction = """
            {
                "type": "nucleoid_extras:quick_portal",
                "portal": "duels:duels/|MAP|/|MODE|/root",
                "quick_portal": "duels:duels/|MAP|/|MODE|/random",
                "message": {
                  "translate": "text.duels.ui.action.modes"
                }
            }
            """;
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
        // i hate all of this code
        // why must i be born to be a mediocre programmer
        ArrayList<String> portals = new ArrayList<>();
        for (String mode : modes) {
            generateConfigsForMode(mode, maps, kits, ops, assetWriter);
        }
        for (Identifier map : maps) {
            portals.add(
                    portalEntry.replace("|MAP|", map.getPath())
            );
            //mapToIcon.get(map.getPath())
            ArrayList<String> entries = new ArrayList<>();
            for (String mode : modes) {
                entries.add(
                        portalEntryWithQuickAction.replace("|MAP|", map.getPath())
                                .replace("|MODE|", mode)
                );
            }
            assetWriter.accept("data/duels/game_portals/duels/"+map.getPath()+"/root.json",
                    rootPortal.replace("|NAME|", "duels.map."+map.getPath())
                            .replace("|ENTRIES|", entries.toString())
                            .replace("|ICON|", mapToIcon.get(map.getPath())).getBytes(StandardCharsets.UTF_8));
        }
        assetWriter.accept("data/duels/game_portals/duels/root.json",
                rootPortal.replace("|NAME|", "gameType.duels.duels")
                        .replace("|ENTRIES|", portals.toString())
                        .replace("|ICON|", "iron_sword").getBytes(StandardCharsets.UTF_8));
    }

    private void generateConfigsForMode(String mode, ArrayList<Identifier> maps, ArrayList<Kit> kits, RegistryOps<JsonElement> ops, BiConsumer<String, byte[]> assetWriter) {
        int teamSize = modeSize.get(mode);
        HashMap<Identifier, ArrayList<String>> gameNames = new HashMap<>();
        for (Identifier map : maps) {
            ArrayList<String> names = new ArrayList<>();
            for (Kit kit : kits) {
                GameConfig<?> config = new GameConfig<>(
                        Duels.TYPE, null, null, List.of(Component.translatable("gameType.duels.duels.desc", teamSize+"v"+teamSize, kit.name().replace("_", " ")), Component.empty()), kit.icon(), CustomValuesConfig.empty(),
                        new DuelsConfig(mode,
                                new WaitingLobbyConfig(teamSize * 2, teamSize * 2),
                                teamSize,
                                kit,
                                new DuelsMapConfig(map)));
                String configName = kit.name() + "_" + map.getPath();
                DataResult<JsonElement> result = GameConfig.ENTRY_CODEC.encodeStart(ops, Holder.direct(config));
                result.ifSuccess(json -> {
                    assetWriter.accept("data/duels/plasmid/game/"+mode+"/"+configName+".json", result.getOrThrow().toString().getBytes(StandardCharsets.UTF_8));
                    names.add("\"duels:"+mode+"/"+configName+"\"");
                });
                result.ifError(error -> {
                    Duels.LOGGER.error("Failed to generate game config for {} in {} mode because {}", map.toString(), mode, error.message());
                });
            }
            gameNames.put(map, names);
            assetWriter.accept("data/duels/plasmid/game/"+mode+"/random_"+map.getPath()+".json", randomGameString.replace("|GAMES|", names.toString()).getBytes(StandardCharsets.UTF_8));
            assetWriter.accept("data/duels/game_portals/duels/"+map.getPath()+"/"+mode+"/root.json",
                    portalString.replace("|ICON|", modeToIcon.get(mode))
                            .replace("|GAMES|", names.toString())
                            .replace("|MODE|", mode).getBytes(StandardCharsets.UTF_8)
            );
            assetWriter.accept("data/duels/game_portals/duels/"+map.getPath()+"/"+mode+"/random.json",
                    """
                    {
                      "type": "plasmid:concurrent_game",
                      "game": "duels:|MODE|/random_|MAP|"
                    }
                    """.replace("|MAP|", map.getPath()).replace("|MODE|", mode).getBytes(StandardCharsets.UTF_8)
            );
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
