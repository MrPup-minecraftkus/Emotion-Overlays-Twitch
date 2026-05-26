package com.mrpup.emotion_overlays_twitch.tvAddon;

import com.mrpup.emotion_overlays.animate.AnimatedTextureManager;
import com.mrpup.emotion_overlays.client.EmojiTextureManager;
import com.mrpup.emotion_overlays.client.ExternalTextureProvider;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SevenTVTextureLoader implements ExternalTextureProvider {

    private record PendingEmote(String url, boolean isGif, String displayName) {}

    private static final Map<String, PendingEmote> METADATA = new ConcurrentHashMap<>();
    private static final Set<String> COMPLETED = ConcurrentHashMap.newKeySet();
    private static final Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    private static final ExecutorService POOL = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "7tv-loader");
        t.setDaemon(true);
        return t;
    });

    public static void registerPending(String safeName, String url,
                                       boolean isGif, String displayName) {
        METADATA.put(safeName, new PendingEmote(url, isGif, displayName));
    }

    @Override
    public boolean isPending(String cpHex) {
        return METADATA.containsKey(cpHex) && !COMPLETED.contains(cpHex);
    }

    public static boolean isPendingStatic(String cpHex) {
        return METADATA.containsKey(cpHex) && !COMPLETED.contains(cpHex);
    }

    @Override
    public void requestLoad(String cpHex) {
        PendingEmote pending = METADATA.get(cpHex);
        if (pending == null) return;
        if (COMPLETED.contains(cpHex)) return;
        if (EmojiTextureManager.isLoaded(cpHex)) {
            COMPLETED.add(cpHex);
            return;
        }
        if (AnimatedTextureManager.isAnimated(cpHex)) {
            COMPLETED.add(cpHex);
            return;
        }
        if (!IN_FLIGHT.add(cpHex)) return;

        POOL.submit(() -> {
            try {
                SevenTVLoader.downloadAndRegister(
                        cpHex, pending.displayName(), pending.url(), pending.isGif());
                COMPLETED.add(cpHex);
            } catch (Exception e) {

            } finally {
                IN_FLIGHT.remove(cpHex);

            }
        });
    }
}
