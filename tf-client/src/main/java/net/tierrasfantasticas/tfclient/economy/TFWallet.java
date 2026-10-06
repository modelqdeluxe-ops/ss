package net.tierrasfantasticas.tfclient.economy;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.util.TFJson;

/** Monedas propias del TF Client (economy.mode=tf): &lt;mundo&gt;/tfclient/monedas.json. */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class TFWallet {
    private static TFWallet instance;

    private final Path file;
    private final Map<UUID, Long> balances = new HashMap<>();
    private boolean dirty;

    private TFWallet(Path file) {
        this.file = file;
        JsonObject json = TFJson.read(file);
        if (json != null) {
            for (String key : json.keySet()) {
                try {
                    balances.put(UUID.fromString(key), Math.max(0, json.get(key).getAsLong()));
                } catch (RuntimeException ignored) {
                    // entrada rota: se ignora
                }
            }
        }
    }

    public static TFWallet get(MinecraftServer server) {
        if (instance == null) instance = new TFWallet(TFJson.worldFile(server, "monedas.json"));
        return instance;
    }

    public long balance(UUID uuid) {
        return balances.getOrDefault(uuid, 0L);
    }

    public void add(UUID uuid, long amount) {
        set(uuid, Math.max(0, Math.addExact(balance(uuid), amount)));
    }

    public boolean take(UUID uuid, long amount) {
        long have = balance(uuid);
        if (have < amount) return false;
        set(uuid, have - amount);
        return true;
    }

    public void set(UUID uuid, long amount) {
        balances.put(uuid, Math.max(0, amount));
        dirty = true;
    }

    private void save() {
        if (!dirty) return;
        dirty = false;
        JsonObject json = new JsonObject();
        balances.forEach((uuid, amount) -> json.addProperty(uuid.toString(), amount));
        TFJson.write(file, json);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && instance != null && event.getServer().getTickCount() % 200 == 0) instance.save();
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        if (instance != null) instance.save();
        instance = null;
    }
}
