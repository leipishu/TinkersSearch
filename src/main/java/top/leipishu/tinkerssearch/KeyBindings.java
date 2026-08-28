package top.leipishu.tinkerssearch;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent; // 关键：导入新的事件
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = TinkersSearch.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class KeyBindings {

    public static final String KEY_CATEGORY = "key.category.tinkerssearch";
    public static final String KEY_TOGGLE_PANEL = "key.tinkerssearch.toggle_panel";

    // 1. 将 KeyMapping 实例化提前，以便在事件中注册
    public static final KeyMapping togglePanelKey = new KeyMapping(
            KEY_TOGGLE_PANEL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F,
            KEY_CATEGORY
    );

    // 2. 监听 RegisterKeyMappingsEvent 事件来注册按键
    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(togglePanelKey);
        System.out.println("Tinker's Search: KeyBinding registered!");
    }
}