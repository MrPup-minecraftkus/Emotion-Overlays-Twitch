package com.mrpup.emotion_overlays_twitch.client;

import com.mrpup.emotion_overlays.client.emoji.EmojiTextureManager;
import com.mrpup.emotion_overlays.common.EmojiRegistry;
import com.mrpup.emotion_overlays_twitch.client.tvAddon.SevenTVLoader;
import com.mrpup.emotion_overlays_twitch.client.tvAddon.SevenTVTextureLoader;
import net.fabricmc.api.ClientModInitializer;

public class EmotionOverlaysTwitchClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
        EmojiTextureManager.setExternalProvider(new SevenTVTextureLoader());
        EmojiRegistry.setExternalSearchHook(query ->
                SevenTVLoader.searchEmotes());
        SevenTVLoader.loadGlobalEmotes();
        SevenTVLoader.searchEmotes();
	}
}