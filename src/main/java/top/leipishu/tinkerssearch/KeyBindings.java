package top.leipishu.tinkerssearch;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ClientRegistry;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = "tinkerssearch", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class KeyBindings {

    public static final String KEY_CATEGORY = "key.category.tinkerssearch";
    public static final String KEY_TOGGLE_PANEL = "key.tinkerssearch.toggle_panel";

    public static KeyMapping togglePanelKey;

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            togglePanelKey = new KeyMapping(
                    KEY_TOGGLE_PANEL,
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_F,
                    KEY_CATEGORY
            );
            ClientRegistry.registerKeyBinding(togglePanelKey);
            System.out.println("Tinker's Search: KeyBinding registered!");
        });
    }
}