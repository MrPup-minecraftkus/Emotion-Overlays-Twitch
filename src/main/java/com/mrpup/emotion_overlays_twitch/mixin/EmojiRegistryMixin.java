package com.mrpup.emotion_overlays_twitch.mixin;

import com.mrpup.emotion_overlays.common.EmojiEntry;
import com.mrpup.emotion_overlays.common.EmojiRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(value = EmojiRegistry.class, remap = false)
public class EmojiRegistryMixin {

    @Shadow
    private static Map<String, EmojiEntry> BY_CP;

    @Inject(method = "registerEntry", at = @At("TAIL"))
    private static void fixByCp(EmojiEntry entry, CallbackInfo ci) {
        BY_CP.put(entry.cpHex(), entry);
    }
}