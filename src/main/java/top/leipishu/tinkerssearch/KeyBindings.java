package top.leipishu.tinkerssearch;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = "tinkerssearch", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class KeyBindings {

    public static final String KEY_CATEGORY = "key.category.tinkerssearch";
    public static final String KEY_TOGGLE_PANEL = "key.tinkerssearch.toggle_panel";

    // ✅ 直接实例化 KeyMapping
    public static final KeyMapping togglePanelKey = new KeyMapping(
            KEY_TOGGLE_PANEL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F,
            KEY_CATEGORY
    );

    // ✅ 通过 RegisterKeyMappingsEvent 注册
    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(togglePanelKey);
        System.out.println("Tinker's Search: KeyBinding registered!");
    }

    // ✅ 保留 FMLClientSetupEvent（可选，用于其他客户端初始化）
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // 如果还有其他客户端初始化逻辑可以放这里
        // KeyBinding 已经通过 RegisterKeyMappingsEvent 注册，不需要在这里处理
    }
}
