package com.mrpup.emotion_overlays_twitch.client.tvAddon;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mrpup.emotion_overlays.client.animate.AnimatedTexture;
import com.mrpup.emotion_overlays.client.animate.AnimatedTextureManager;
import com.mrpup.emotion_overlays.client.emoji.EmojiTextureManager;
import com.mrpup.emotion_overlays.common.EmojiEntry;
import com.mrpup.emotion_overlays.common.EmojiRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static com.mojang.text2speech.Narrator.LOGGER;

public class SevenTVLoader {

    public static void loadGlobalEmotes() {
        CompletableFuture.runAsync(() -> {
            try {
                URL url = new URL("https://7tv.io/v3/emote-sets/global");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                String json = new String(conn.getInputStream().readAllBytes());

                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                JsonArray emotes = root.getAsJsonArray("emotes");

                for (JsonElement el : emotes) {
                    registerMetadata(el.getAsJsonObject());
                }

                LOGGER.info("[7TV] Зареєстровано метадані: {} емотів", emotes.size());
            } catch (Exception e) {
                LOGGER.error("[7TV] Помилка завантаження списку", e);
            }
        });
    }


    public static void searchEmotes() {
        CompletableFuture.runAsync(() -> {
            try {
                LOGGER.info("[7TV] searchEmotes() запущено...");
                int totalLoaded = 0;

                for (int page = 1; page <= 10; page++) {
                    JsonObject variables = new JsonObject();
                    variables.addProperty("page", page);

                    JsonObject body = new JsonObject();
                    body.addProperty("query",
                            "query GetTopEmotes($page: Int) { " +
                                    "emotes(query: \"\", limit: 100, page: $page, " +
                                    "filter: {category: \"TOP\"}, " +
                                    "sort: {value: \"usage_count\", order: DESCENDING}) { " +
                                    "items { id name host { url files { name format } } } } }");
                    body.add("variables", variables);

                    String bodyStr = body.toString();

                    HttpURLConnection conn = (HttpURLConnection) new URL("https://7tv.io/v3/gql").openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                    conn.setDoOutput(true);
                    conn.getOutputStream().write(bodyStr.getBytes(StandardCharsets.UTF_8));

                    int status = conn.getResponseCode();
                    if (status != 200) {
                        String error = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                        LOGGER.warn("[7TV] GQL HTTP {} на сторінці {}: {}", status, page, error);
                        break;
                    }

                    String json = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    JsonObject root = JsonParser.parseString(json).getAsJsonObject();

                    if (!root.has("data") || root.get("data").isJsonNull()) break;

                    JsonArray items = root.getAsJsonObject("data")
                            .getAsJsonObject("emotes")
                            .getAsJsonArray("items");

                    if (items == null || items.size() == 0) break;

                    for (JsonElement el : items) {
                        registerMetadata(el.getAsJsonObject());
                    }

                    totalLoaded += items.size();

                    if (items.size() < 100) break;
                    Thread.sleep(300);
                }

                LOGGER.info("[7TV] searchEmotes завершено: {} емотів", totalLoaded);
            } catch (Exception e) {
                LOGGER.error("[7TV] Помилка searchEmotes", e);
            }
        });
    }


    private static void registerMetadata(JsonObject emote) {
        try {
            if (!emote.has("name") || emote.get("name").isJsonNull()) return;
            String name = emote.get("name").getAsString();
            String id = emote.get("id").getAsString();

            JsonObject dataSource = emote.has("data") ? emote.getAsJsonObject("data") : emote;
            if (!dataSource.has("host") || dataSource.get("host").isJsonNull()) return;

            JsonObject host = dataSource.getAsJsonObject("host");
            String baseUrl = host.get("url").getAsString();
            JsonArray files = host.getAsJsonArray("files");

            String fileUrl = null;
            boolean isGif = false;

            for (String preferredSize : new String[]{"2x", "1x", "4x"}) {
                for (JsonElement f : files) {
                    JsonObject file = f.getAsJsonObject();
                    String format = file.get("format").getAsString();
                    String fileName = file.get("name").getAsString();

                    if (!fileName.startsWith("2x") && !fileName.startsWith("1x")) continue;

                    if (format.equals("GIF") && fileUrl == null) {
                        fileUrl = "https:" + baseUrl + "/" + fileName;
                        isGif = true;
                    } else if (format.equals("PNG") && fileUrl == null) {
                        fileUrl = "https:" + baseUrl + "/" + fileName;
                        isGif = false;
                    }
                }
                if (fileUrl != null) break;
            }

            if (fileUrl == null) {
                return;
            }

            String cleaned = name.toLowerCase().replaceAll("[^a-z0-9._-]", "_");
            final String safeName;
            if (EmojiTextureManager.isLoaded(cleaned) ||
                    AnimatedTextureManager.isAnimated(cleaned) ||
                    SevenTVTextureLoader.isPendingStatic(cleaned)) {
                safeName = cleaned + "_" + id.toLowerCase();
            } else {
                safeName = cleaned;
            }
            final String finalUrl = fileUrl;
            final boolean finalIsGif = isGif;

            if (SevenTVTextureLoader.isPendingStatic(safeName) ||
                    EmojiTextureManager.isLoaded(safeName) ||
                    AnimatedTextureManager.isAnimated(safeName)) return;

            SevenTVTextureLoader.registerPending(safeName, finalUrl, finalIsGif, name);

            CompletableFuture.runAsync(() ->
                    downloadAndRegister(safeName, name, finalUrl, finalIsGif)
            );

        } catch (Exception e) {
        }
    }

    public static void downloadAndRegister(String safeName, String displayName,
                                           String texUrl, boolean isAnimated) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(texUrl).openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.connect();

            if (conn.getResponseCode() != 200) {
                return;
            }

            byte[] bytes = conn.getInputStream().readAllBytes();

            boolean isWebp = bytes.length > 12 &&
                    bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F' &&
                    bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            boolean isGif = bytes.length > 3 &&
                    bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F';

            if (isGif) {
                AnimatedTexture animated = GifDecoder.decode(bytes);
                Minecraft.getInstance().execute(() -> {
                    Identifier loc = Identifier.fromNamespaceAndPath(
                            "seventv_addon", "emote/" + safeName);
                    AnimatedTextureManager.register(safeName, animated, loc);
                    EmojiTextureManager.registerExternal(safeName, loc);
                    EmojiRegistry.registerEntry(new EmojiEntry(safeName, safeName, displayName, "7TV"));
                    SevenTVTextureLoader.markCompleted(safeName);
                });
            } else {
                Minecraft.getInstance().execute(() -> {
                    try {
                        NativeImage img = NativeImage.read(new ByteArrayInputStream(bytes));
                        DynamicTexture dynTex = new DynamicTexture(
                                () -> "seventv_addon:emote/" + safeName, img);
                        Identifier loc = Identifier.fromNamespaceAndPath(
                                "seventv_addon", "emote/" + safeName);
                        Minecraft.getInstance().getTextureManager().register(loc, dynTex);
                        EmojiTextureManager.registerExternal(safeName, loc);
                        EmojiRegistry.registerEntry(new EmojiEntry(safeName, safeName, displayName, "7TV"));
                        SevenTVTextureLoader.markCompleted(safeName);
                    } catch (IOException e) { }
                });
            }
        } catch (Exception e) {
        }
    }
}
