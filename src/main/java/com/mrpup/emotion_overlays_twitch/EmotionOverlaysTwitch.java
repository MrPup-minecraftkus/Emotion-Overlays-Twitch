package com.mrpup.emotion_overlays_twitch;

import com.mrpup.emotion_overlays.client.EmojiTextureManager;
import com.mrpup.emotion_overlays.common.EmojiRegistry;
import com.mrpup.emotion_overlays_twitch.tvAddon.SevenTVLoader;
import com.mrpup.emotion_overlays_twitch.tvAddon.SevenTVTextureLoader;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(EmotionOverlaysTwitch.MOD_ID)
public class EmotionOverlaysTwitch {

    public static final String MOD_ID = "emotion_overlays_twitch";

    public EmotionOverlaysTwitch(IEventBus modEventBus, ModContainer modContainer) {
       if (FMLEnvironment.dist == Dist.CLIENT) {
           EmojiTextureManager.setExternalProvider(new SevenTVTextureLoader());
           EmojiRegistry.setExternalSearchHook(query -> SevenTVLoader.searchEmotes());
           SevenTVLoader.loadGlobalEmotes();
           SevenTVLoader.searchEmotes();
       }

        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
    }

}
