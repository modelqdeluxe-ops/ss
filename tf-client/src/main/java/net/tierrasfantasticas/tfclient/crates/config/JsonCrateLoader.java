/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.crates.config;

import net.tierrasfantasticas.tfclient.crates.FSCrates;
import net.tierrasfantasticas.tfclient.crates.client.color.FSTextStyle;
import net.tierrasfantasticas.tfclient.crates.crate.CrateRegistry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

public final class JsonCrateLoader {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

   private JsonCrateLoader() {
   }

   public static Path folder() {
      return FMLPaths.CONFIGDIR.get().resolve("fscrates").resolve("cajas");
   }

   public static Path fileFor(String id) {
      return folder().resolve(sanitize(id) + ".json");
   }

   private static String sanitize(String id) {
      String clean = (id == null ? "" : id.trim().toLowerCase(Locale.ROOT)).replaceAll("[^a-z0-9_.-]", "_");
      return clean.isEmpty() ? "crate" : clean;
   }

   public static int loadAll(MinecraftServer server) {
      if (server == null) {
         return 0;
      } else {
         Path dir = folder();
         int loaded = 0;

         try {
            Files.createDirectories(dir);
         } catch (IOException var11) {
            FSCrates.LOGGER.error("[FSCrates] No se pudo crear la carpeta {}: {}", dir, var11.toString());
            return 0;
         }

         ServerLevel overworld = server.overworld();
         if (overworld == null) {
            return 0;
         } else {
            CrateRegistry registry = CrateRegistry.get(overworld);
            List<Path> files = new ArrayList<>();

            try (Stream<Path> stream = Files.list(dir)) {
               stream.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")).sorted().forEach(files::add);
            } catch (IOException var13) {
               FSCrates.LOGGER.error("[FSCrates] No se pudo listar {}: {}", dir, var13.toString());
               return 0;
            }

            for (Path file : files) {
               try {
                  CrateConfig config = read(file);
                  if (config != null) {
                     registry.put(config);
                     loaded++;
                  }
               } catch (Exception var10) {
                  FSCrates.LOGGER.error("[FSCrates] Error leyendo '{}': {}", file.getFileName(), var10.toString());
               }
            }

            if (loaded > 0) {
               registry.setDirty();
               CrateRegistry.bumpGeneration();
            }

            FSCrates.LOGGER.info("[FSCrates] Configuracion JSON recargada: {} caja(s) desde {}", loaded, dir);
            return loaded;
         }
      }
   }

   public static CrateConfig read(Path file) throws IOException {
      JsonObject json;
      try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
         JsonElement parsed = JsonParser.parseReader(reader);
         if (parsed == null || !parsed.isJsonObject()) {
            FSCrates.LOGGER.warn("[FSCrates] '{}' no contiene un objeto JSON; se ignora.", file.getFileName());
            return null;
         }

         json = parsed.getAsJsonObject();
      }

      String id = string(json, "id", "");
      if (id.isBlank()) {
         String name = file.getFileName().toString();
         id = name.substring(0, name.length() - 5);
      }

      CrateConfig config = new CrateConfig(id.toLowerCase(Locale.ROOT));
      JsonObject info = section(json, "informacion");
      JsonObject visual = section(json, "visual");
      JsonObject keys = section(json, "llaves");
      JsonObject unique = section(keys, "llaveUnica");
      JsonObject opening = section(json, "apertura");
      JsonObject pity = section(json, "pity");
      JsonObject media = section(json, "media");
      config.displayName = string(info, "nombreVisible", config.displayName);
      config.rarity = Rarity.byName(string(info, "rareza", config.rarity.name()));
      config.styleId = string(visual, "estiloBloque", config.styleId);
      config.animationId = string(visual, "animacion", config.animationId);
      config.glow = bool(visual, "brillar", config.glow);
      config.particles = bool(visual, "particulas", config.particles);
      config.floatingName = bool(visual, "mostrarNombreFlotante", config.floatingName);
      config.nameColorHexOverride = string(visual, "colorNombreHex", config.nameColorHexOverride);
      config.sizeScale = (float)number(visual, "escala", (double)config.sizeScale);
      config.yOffset = (float)number(visual, "offsetAltura", (double)config.yOffset);
      config.yawOffset = (float)number(visual, "offsetRotacion", (double)config.yawOffset);
      config.floatingText.clear();
      config.floatingText.addAll(strings(visual, "textoFlotante"));
      config.consumeKey = bool(keys, "consumirLlave", config.consumeKey);
      config.uniqueKeyEnabled = bool(unique, "activada", bool(keys, "llaveUnicaActivada", config.uniqueKeyEnabled));
      config.uniqueKeyModel = string(unique, "modelo", string(keys, "llaveUnicaModelo", config.uniqueKeyModel));
      config.uniqueKeyName = string(unique, "nombre", string(keys, "llaveUnicaNombre", config.uniqueKeyName));
      config.rolls = Math.max(1, (int)number(opening, "tiradasPorApertura", (double)config.rolls));
      config.cooldownSeconds = Math.max(0, (int)number(opening, "cooldownSegundos", (double)config.cooldownSeconds));
      if (opening.has("retrasoAperturaSegundos")) {
         config.openDelayTicks = Math.max(0, (int)Math.round(number(opening, "retrasoAperturaSegundos", 0.0) * 20.0));
      }

      config.allowSkip = bool(opening, "permitirSaltar", config.allowSkip);
      config.broadcast = bool(opening, "anuncioGlobal", config.broadcast);
      config.openOncePerPlayer = bool(opening, "soloUnaVezPorJugador", bool(opening, "soloUnAperturaPorJugador", config.openOncePerPlayer));
      config.requiredPermission = string(opening, "permisoRequerido", config.requiredPermission);
      config.showOdds = bool(opening, "mostrarProbabilidadesSobreElCofre", config.showOdds);
      config.showOddsInPool = bool(opening, "mostrarProbabilidadesEnRecompensas", bool(opening, "mostrarProbabilidades", config.showOddsInPool));
      config.pityEnabled = bool(pity, "activado", bool(pity, "pityActivado", config.pityEnabled));
      config.pityInterval = Math.max(1, (int)number(pity, "cadaCuantasAperturas", number(pity, "pityIntervalo", (double)config.pityInterval)));
      config.pityRarity = Rarity.byName(string(pity, "rarezaGarantizada", string(pity, "pityRareza", config.pityRarity.name())));
      JsonObject chances = json.has("probabilidadPorRareza") && json.get("probabilidadPorRareza").isJsonObject()
         ? json.getAsJsonObject("probabilidadPorRareza")
         : (json.has("probabilidadRareza") && json.get("probabilidadRareza").isJsonObject() ? json.getAsJsonObject("probabilidadRareza") : null);
      if (chances != null) {
         config.rarityChances.clear();

         for (Rarity rarity : Rarity.values()) {
            if (chances.has(rarity.name())) {
               config.rarityChances.put(rarity, Double.valueOf(Math.max(0.0, chances.get(rarity.name()).getAsDouble())));
            }
         }

         if (config.rarityChances.isEmpty()) {
            config.rarityChances.putAll(CrateConfig.defaultRarityChances());
         }
      }

      config.videos.clear();
      config.videos.addAll(strings(media, "videos"));
      config.music.clear();
      config.music.addAll(strings(media, "musica"));
      JsonObject scene = section(json, "escena");
      config.sceneHeader = readStyle(scene, "lineaDeArriba", config.sceneHeader);
      config.sceneSubtitle = readStyle(scene, "lineaDeAbajo", config.sceneSubtitle);
      config.nameStyle = readStyle(scene, "estiloDelNombre", config.nameStyle);
      config.sceneLines.clear();
      if (scene.has("mensajeExtra") && scene.get("mensajeExtra").isJsonArray()) {
         JsonArray array = scene.getAsJsonArray("mensajeExtra");

         for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);
            if (element.isJsonObject()) {
               config.sceneLines.add(FSTextStyle.fromJson(element.getAsJsonObject()));
            } else {
               try {
                  config.sceneLines.add(FSTextStyle.migrate(element.getAsString()));
               } catch (Exception var18) {
               }
            }
         }
      }

      config.rewards.clear();
      if (json.has("recompensas") && json.get("recompensas").isJsonArray()) {
         JsonArray rewards = json.getAsJsonArray("recompensas");

         for (int ix = 0; ix < rewards.size(); ix++) {
            JsonElement element = rewards.get(ix);
            if (element.isJsonObject()) {
               RewardEntry entry = readReward(element.getAsJsonObject(), file, ix);
               if (entry != null) {
                  config.rewards.add(entry);
               }
            }
         }
      }

      config.rewards.addAll(readPreservedRewards(json));
      return config;
   }

   private static RewardEntry readReward(JsonObject json, Path file, int index) {
      String type = string(json, "tipo", "ITEM").trim().toUpperCase(Locale.ROOT);
      if (!"ITEM".equals(type)) {
         FSCrates.LOGGER
            .warn(
               "[FSCrates] '{}' recompensa #{}: el tipo '{}' no se puede definir en JSON (solo ITEM). Usa el editor in-game. Se ignora.",
               new Object[]{file.getFileName(), index, type}
            );
         return null;
      } else {
         RewardEntry entry = new RewardEntry(RewardEntry.Type.ITEM);
         entry.label = string(json, "etiqueta", entry.label);
         entry.rarity = string(json, "rareza", "");
         entry.chance = number(json, "probabilidad", entry.chance);
         entry.guaranteed = bool(json, "garantizada", entry.guaranteed);
         entry.minAmount = Math.max(1, (int)number(json, "cantidadMinima", (double)entry.minAmount));
         entry.maxAmount = Math.max(entry.minAmount, (int)number(json, "cantidadMaxima", (double)entry.maxAmount));
         entry.item = readItem(json.has("item") && json.get("item").isJsonObject() ? json.getAsJsonObject("item") : null, file, index);
         return entry;
      }
   }

   private static ItemStack readItem(JsonObject json, Path file, int index) {
      if (json == null) {
         return ItemStack.EMPTY;
      } else {
         String itemId = string(json, "id", "").trim();
         if (!itemId.isEmpty() && !"minecraft:air".equals(itemId)) {
            ResourceLocation location = ResourceLocation.tryParse(itemId);
            if (location != null && ForgeRegistries.ITEMS.containsKey(location)) {
               Item item = (Item)ForgeRegistries.ITEMS.getValue(location);
               if (item == null) {
                  return ItemStack.EMPTY;
               } else {
                  ItemStack stack = new ItemStack(item);
                  stack.setCount(Math.max(1, (int)number(json, "Count", 1.0)));
                  if (json.has("tag") && json.get("tag").isJsonObject() && json.getAsJsonObject("tag").size() > 0) {
                     try {
                        JsonObject tagJson = json.getAsJsonObject("tag");
                        String snbt = tagJson.has("__snbt") ? tagJson.get("__snbt").getAsString() : GSON.toJson(tagJson);
                        CompoundTag nbt = TagParser.parseTag(snbt);
                        stack.setTag(nbt);
                     } catch (Exception var10) {
                        FSCrates.LOGGER
                           .warn(
                              "[FSCrates] '{}' recompensa #{}: el NBT de '{}' no se pudo leer ({}). Se entrega sin NBT.",
                              new Object[]{file.getFileName(), index, itemId, var10.getMessage()}
                           );
                     }
                  }

                  return stack;
               }
            } else {
               FSCrates.LOGGER
                  .warn(
                     "[FSCrates] '{}' recompensa #{}: el item '{}' no existe (mod no instalado?). Se deja vacio.",
                     new Object[]{file.getFileName(), index, itemId}
                  );
               return ItemStack.EMPTY;
            }
         } else {
            return ItemStack.EMPTY;
         }
      }
   }

   private static List<RewardEntry> readPreservedRewards(JsonObject json) {
      List<RewardEntry> out = new ArrayList<>();
      if (json.has("recompensasAvanzadas") && json.get("recompensasAvanzadas").isJsonArray()) {
         JsonArray array = json.getAsJsonArray("recompensasAvanzadas");

         for (int i = 0; i < array.size(); i++) {
            try {
               String snbt = array.get(i).getAsString();
               if (snbt != null && !snbt.isBlank()) {
                  out.add(RewardEntry.load(TagParser.parseTag(snbt)));
               }
            } catch (Exception var5) {
               FSCrates.LOGGER.warn("[FSCrates] No se pudo leer una recompensa avanzada: {}", var5.toString());
            }
         }

         return out;
      } else {
         return out;
      }
   }

   public static void saveToFile(CrateConfig config) {
      if (config != null) {
         Path file = fileFor(config.id);

         try {
            Files.createDirectories(file.getParent());

            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
               GSON.toJson(toJson(config), writer);
            }

            FSCrates.LOGGER.info("[FSCrates] Caja guardada en {}", file);
         } catch (Exception var7) {
            FSCrates.LOGGER.error("[FSCrates] No se pudo escribir '{}': {}", file, var7.toString());
         }
      }
   }

   public static JsonObject toJson(CrateConfig config) {
      JsonObject root = new JsonObject();
      root.addProperty("id", config.id);
      root.add("_ayuda", helpBlock());
      JsonObject info = new JsonObject();
      info.addProperty("nombreVisible", config.displayName);
      info.addProperty("rareza", config.rarity.name());
      root.add("informacion", info);
      JsonObject visual = new JsonObject();
      visual.addProperty("estiloBloque", config.styleId == null ? "" : config.styleId);
      visual.addProperty("animacion", config.animationId == null ? "" : config.animationId);
      visual.addProperty("brillar", config.glow);
      visual.addProperty("particulas", config.particles);
      visual.addProperty("mostrarNombreFlotante", config.floatingName);
      JsonArray floating = new JsonArray();
      config.floatingText.forEach(floating::add);
      visual.add("textoFlotante", floating);
      visual.addProperty("colorNombreHex", config.nameColorHexOverride == null ? "" : config.nameColorHexOverride);
      visual.addProperty("escala", round((double)config.sizeScale));
      visual.addProperty("offsetAltura", round((double)config.yOffset));
      visual.addProperty("offsetRotacion", round((double)config.yawOffset));
      root.add("visual", visual);
      JsonObject keys = new JsonObject();
      keys.addProperty("consumirLlave", config.consumeKey);
      JsonObject unique = new JsonObject();
      unique.addProperty("activada", config.uniqueKeyEnabled);
      unique.addProperty("modelo", config.uniqueKeyModel == null ? "" : config.uniqueKeyModel);
      unique.addProperty("nombre", config.uniqueKeyName == null ? "" : config.uniqueKeyName);
      keys.add("llaveUnica", unique);
      root.add("llaves", keys);
      JsonObject opening = new JsonObject();
      opening.addProperty("tiradasPorApertura", config.rolls);
      opening.addProperty("cooldownSegundos", config.cooldownSeconds);
      opening.addProperty("retrasoAperturaSegundos", round((double)config.openDelayTicks / 20.0));
      opening.addProperty("permitirSaltar", config.allowSkip);
      opening.addProperty("anuncioGlobal", config.broadcast);
      opening.addProperty("soloUnaVezPorJugador", config.openOncePerPlayer);
      opening.addProperty("permisoRequerido", config.requiredPermission == null ? "" : config.requiredPermission);
      opening.addProperty("mostrarProbabilidadesSobreElCofre", config.showOdds);
      opening.addProperty("mostrarProbabilidadesEnRecompensas", config.showOddsInPool);
      root.add("apertura", opening);
      JsonObject pity = new JsonObject();
      pity.addProperty("activado", config.pityEnabled);
      pity.addProperty("cadaCuantasAperturas", config.pityInterval);
      pity.addProperty("rarezaGarantizada", (config.pityRarity == null ? Rarity.LEGENDARY : config.pityRarity).name());
      root.add("pity", pity);
      JsonObject chances = new JsonObject();

      for (Rarity rarity : Rarity.values()) {
         chances.addProperty(rarity.name(), round(config.rarityChance(rarity)));
      }

      root.add("probabilidadPorRareza", chances);
      JsonObject scene = new JsonObject();
      scene.add("lineaDeArriba", (config.sceneHeader == null ? new FSTextStyle() : config.sceneHeader).toJson());
      scene.add("lineaDeAbajo", (config.sceneSubtitle == null ? new FSTextStyle() : config.sceneSubtitle).toJson());
      scene.add("estiloDelNombre", (config.nameStyle == null ? new FSTextStyle() : config.nameStyle).toJson());
      JsonArray sceneLines = new JsonArray();

      for (FSTextStyle line : config.sceneLines) {
         sceneLines.add(line.toJson());
      }

      scene.add("mensajeExtra", sceneLines);
      root.add("escena", scene);
      JsonObject media = new JsonObject();
      JsonArray videos = new JsonArray();
      config.videos.forEach(videos::add);
      media.add("videos", videos);
      JsonArray music = new JsonArray();
      config.music.forEach(music::add);
      media.add("musica", music);
      root.add("media", media);
      JsonArray rewards = new JsonArray();
      JsonArray advanced = new JsonArray();

      for (RewardEntry entry : config.rewards) {
         if (entry.type == RewardEntry.Type.ITEM) {
            rewards.add(rewardToJson(entry));
         } else {
            advanced.add(entry.save().toString());
         }
      }

      root.add("recompensas", rewards);
      if (!advanced.isEmpty()) {
         root.add("recompensasAvanzadas", advanced);
      }

      return root;
   }

   private static JsonObject helpBlock() {
      JsonObject help = new JsonObject();
      help.addProperty("queEsEsto", "Configuracion de una caja de TF Crates. Editala y aplica con /tf crates reload");
      help.addProperty("rarezas", "COMMON, RARE, EPIC, LEGENDARY, MYTHIC");
      help.addProperty("colores", "Usa & o el caracter de seccion para colores. Ejemplo: &d&lCaja Epica");
      help.addProperty("idsDeItems", "Consulta la lista completa en config/fscrates/items_referencia.json");
      help.addProperty("recompensas", "Aqui solo se pueden definir las de tipo ITEM. Las de COMMAND, XP, EFFECT y KEY se configuran en el editor del juego.");
      help.addProperty("probabilidad", "Es un peso relativo dentro de su rareza, no un porcentaje absoluto. El % real se calcula solo.");
      help.addProperty("garantizada", "Si es true la recompensa se entrega SIEMPRE, ademas de las que salgan por sorteo.");
      help.addProperty(
         "videos", "Links directos a MP4 (H.264) o a imagenes PNG/JPG. Los .webm NO se pueden reproducir. Si pones varios, en cada apertura sale uno al azar."
      );
      help.addProperty("musica", "Links directos a MP3, OGG o WAV.");
      help.addProperty("escena", "Textos de la pantalla de pre-apertura. Se admiten colores con &.");
      help.addProperty("cache", "Cada media se descarga UNA vez y se guarda en .minecraft/fscrates/cache/. Despues ya no se vuelve a bajar.");
      return help;
   }

   private static double round(double value) {
      return (double)Math.round(value * 1000.0) / 1000.0;
   }

   private static JsonObject rewardToJson(RewardEntry entry) {
      JsonObject json = new JsonObject();
      json.addProperty("tipo", "ITEM");
      json.addProperty("etiqueta", entry.label == null ? "" : entry.label);
      json.addProperty("rareza", entry.rarity == null ? "" : entry.rarity);
      json.addProperty("probabilidad", entry.chance);
      json.addProperty("garantizada", entry.guaranteed);
      json.addProperty("cantidadMinima", entry.minAmount);
      json.addProperty("cantidadMaxima", entry.maxAmount);
      JsonObject item = new JsonObject();
      ItemStack stack = entry.item;
      if (stack != null && !stack.isEmpty()) {
         item.addProperty("id", ForgeRegistries.ITEMS.getKey(stack.getItem()).toString());
         item.addProperty("Count", stack.getCount());
         CompoundTag tag = stack.getTag();
         item.add("tag", (JsonElement)(tag != null && !tag.isEmpty() ? nbtToJson(tag) : new JsonObject()));
      } else {
         item.addProperty("id", "minecraft:air");
         item.addProperty("Count", 1);
         item.add("tag", new JsonObject());
      }

      json.add("item", item);
      return json;
   }

   private static JsonElement nbtToJson(CompoundTag tag) {
      try {
         JsonElement converted = (JsonElement)new Dynamic(NbtOps.INSTANCE, tag).convert(JsonOps.INSTANCE).getValue();
         if (converted != null && converted.isJsonObject()) {
            return converted;
         }
      } catch (Exception var2) {
         FSCrates.LOGGER.warn("[FSCrates] No se pudo convertir el NBT a JSON, se guarda como SNBT: {}", var2.toString());
      }

      JsonObject wrapper = new JsonObject();
      wrapper.addProperty("__snbt", tag.toString());
      return wrapper;
   }

   private static FSTextStyle readStyle(JsonObject json, String key, FSTextStyle fallback) {
      if (json.has(key)) {
         JsonElement element = json.get(key);
         if (element.isJsonObject()) {
            return FSTextStyle.fromJson(element.getAsJsonObject());
         }

         try {
            return FSTextStyle.migrate(element.getAsString());
         } catch (Exception var5) {
         }
      }

      return fallback == null ? new FSTextStyle() : fallback;
   }

   private static JsonObject section(JsonObject json, String name) {
      return json.has(name) && json.get(name).isJsonObject() ? json.getAsJsonObject(name) : json;
   }

   private static String string(JsonObject json, String key, String fallback) {
      try {
         if (json.has(key) && json.get(key).isJsonPrimitive()) {
            return json.get(key).getAsString();
         }
      } catch (Exception var4) {
      }

      return fallback;
   }

   private static boolean bool(JsonObject json, String key, boolean fallback) {
      try {
         if (json.has(key) && json.get(key).isJsonPrimitive()) {
            return json.get(key).getAsBoolean();
         }
      } catch (Exception var4) {
      }

      return fallback;
   }

   private static double number(JsonObject json, String key, double fallback) {
      try {
         if (json.has(key) && json.get(key).isJsonPrimitive()) {
            return json.get(key).getAsDouble();
         }
      } catch (Exception var5) {
      }

      return fallback;
   }

   private static List<String> strings(JsonObject json, String key) {
      List<String> out = new ArrayList<>();
      if (json.has(key) && json.get(key).isJsonArray()) {
         JsonArray array = json.getAsJsonArray(key);

         for (int i = 0; i < array.size(); i++) {
            try {
               String value = array.get(i).getAsString();
               if (value != null && !value.isBlank()) {
                  out.add(value.trim());
               }
            } catch (Exception var6) {
            }
         }
      }

      return out;
   }
}
