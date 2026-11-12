package com.yesmenn.technicals.client;

import com.yesmenn.technicals.client.screen.ObserversEyeScreen;
import com.yesmenn.technicals.network.OpenObserversEyeScreenPayload;
import net.minecraft.client.Minecraft;

public final class ObserversEyeClientHooks {
    private ObserversEyeClientHooks() {
    }

    public static void openScreen(OpenObserversEyeScreenPayload payload) {
        Minecraft.getInstance().setScreen(new ObserversEyeScreen(payload));
    }
}
