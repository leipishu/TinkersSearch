package top.leipishu.tinkerssearch.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import slimeknights.tconstruct.common.network.TinkerNetwork;
import slimeknights.tconstruct.smeltery.block.entity.controller.SmelteryBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.tank.SmelteryTank;
import slimeknights.tconstruct.smeltery.network.SmelteryFluidClickedPacket;

import java.lang.reflect.Field;
import java.util.List;

/**
 * 处理冶炼炉液体点击的工具类
 */
public class SmelteryClickHandler {

    // ==================== 缓存冶炼炉数据（减少反射开销） ====================
    private static SmelteryBlockEntity cachedSmeltery = null;
    private static long cacheTime = 0;
    private static final long CACHE_DURATION = 1000; // 1秒缓存

    // ==================== 核心点击方法 ====================

    /**
     * 通过 FluidStack 直接点击流体（推荐方式）
     * 自动在冶炼炉中查找匹配的流体并点击
     *
     * @param fluidStack 要点击的流体
     * @return 是否成功
     */
    public static boolean clickFluidByStack(FluidStack fluidStack) {
        if (fluidStack == null) {
            System.err.println("Tinker's Search: clickFluidByStack called with null fluid");
            return false;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isSpectator()) {
            System.err.println("Tinker's Search: Player is null or spectator");
            return false;
        }

        // 检查是否在冶炼炉屏幕中
        if (!(mc.screen instanceof AbstractContainerScreen)) {
            System.err.println("Tinker's Search: Not in a container screen");
            return false;
        }

        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.screen;
        String className = screen.getClass().getName();
        if (!className.contains("SmelteryScreen") && !className.contains("smeltery")) {
            System.err.println("Tinker's Search: Not in smeltery screen");
            return false;
        }

        // 获取冶炼炉中的所有流体
        SmelteryBlockEntity smeltery = getSmelteryFromScreen(screen);
        if (smeltery == null) {
            System.err.println("Tinker's Search: Failed to get smeltery BlockEntity");
            return false;
        }

        // 获取当前所有流体
        List<FluidStack> fluids = SmelteryDataHelper.getMoltenFluids(smeltery);
        if (fluids == null || fluids.isEmpty()) {
            System.err.println("Tinker's Search: No fluids in smeltery");
            return false;
        }

        // 查找匹配的流体索引
        int index = findFluidIndex(fluids, fluidStack);
        if (index < 0) {
            System.err.println("Tinker's Search: Fluid not found in smeltery: " +
                    fluidStack.getDisplayName().getString());
            return false;
        }

        // 执行点击
        return clickFluid(index);
    }

    /**
     * 通过流体名称点击（备用方案）
     * 当直接传递 FluidStack 失败时使用
     */
    public static boolean clickFluidByName(FluidStack fluidStack) {
        if (fluidStack == null) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isSpectator()) return false;
        if (!(mc.screen instanceof AbstractContainerScreen)) return false;

        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.screen;
        String className = screen.getClass().getName();
        if (!className.contains("SmelteryScreen") && !className.contains("smeltery")) return false;

        SmelteryBlockEntity smeltery = getSmelteryFromScreen(screen);
        if (smeltery == null) return false;

        List<FluidStack> fluids = SmelteryDataHelper.getMoltenFluids(smeltery);
        if (fluids == null || fluids.isEmpty()) return false;

        // 通过名称匹配
        String targetName = fluidStack.getDisplayName().getString();
        for (int i = 0; i < fluids.size(); i++) {
            String fluidName = fluids.get(i).getDisplayName().getString();
            if (fluidName.equals(targetName)) {
                return clickFluid(i);
            }
        }

        // 尝试通过注册名匹配
        String targetRegistryName = fluidStack.getFluid().getRegistryName().toString();
        for (int i = 0; i < fluids.size(); i++) {
            String registryName = fluids.get(i).getFluid().getRegistryName().toString();
            if (registryName.equals(targetRegistryName)) {
                return clickFluid(i);
            }
        }

        System.err.println("Tinker's Search: Fluid not found by name: " + targetName);
        return false;
    }

    /**
     * 在流体列表中查找匹配的流体索引
     * 支持三种匹配方式：精确匹配 → 名称匹配 → 注册名匹配
     */
    private static int findFluidIndex(List<FluidStack> fluids, FluidStack target) {
        if (target == null || fluids == null) return -1;

        // 1. 精确匹配（比较流体类型和NBT）
        for (int i = 0; i < fluids.size(); i++) {
            if (fluids.get(i).isFluidEqual(target)) {
                return i;
            }
        }

        // 2. 名称匹配（忽略大小写）
        String targetName = target.getDisplayName().getString();
        for (int i = 0; i < fluids.size(); i++) {
            String fluidName = fluids.get(i).getDisplayName().getString();
            if (fluidName.equalsIgnoreCase(targetName)) {
                return i;
            }
        }

        // 3. 注册名匹配
        String targetRegistryName = target.getFluid().getRegistryName().toString();
        for (int i = 0; i < fluids.size(); i++) {
            String registryName = fluids.get(i).getFluid().getRegistryName().toString();
            if (registryName.equals(targetRegistryName)) {
                return i;
            }
        }

        return -1;
    }

    /**
     * 点击卡片，将指定索引的液体移到最下面
     *
     * @param fluidIndex 液体在列表中的索引（从0开始）
     * @return 是否成功发送网络包
     */
    public static boolean clickFluid(int fluidIndex) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isSpectator()) {
            System.err.println("Tinker's Search: Player is null or spectator");
            return false;
        }

        if (!(mc.screen instanceof AbstractContainerScreen)) {
            System.err.println("Tinker's Search: Not in a container screen");
            return false;
        }

        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.screen;
        String className = screen.getClass().getName();
        if (!className.contains("SmelteryScreen") && !className.contains("smeltery")) {
            System.err.println("Tinker's Search: Not in smeltery screen");
            return false;
        }

        // 验证索引是否有效
        SmelteryBlockEntity smeltery = getSmelteryFromScreen(screen);
        if (smeltery == null) {
            System.err.println("Tinker's Search: Failed to get smeltery BlockEntity");
            return false;
        }

        List<FluidStack> fluids = SmelteryDataHelper.getMoltenFluids(smeltery);
        if (fluids == null || fluidIndex < 0 || fluidIndex >= fluids.size()) {
            System.err.println("Tinker's Search: Invalid fluid index: " + fluidIndex);
            return false;
        }

        // 发送网络包 - 与 GuiSmelteryTank.handleClick 完全一致
        try {
            TinkerNetwork.getInstance().sendToServer(new SmelteryFluidClickedPacket(fluidIndex));
            System.out.println("Tinker's Search: Sent click packet for fluid index " + fluidIndex +
                    " (" + fluids.get(fluidIndex).getDisplayName().getString() + ")");
            return true;
        } catch (Exception e) {
            System.err.println("Tinker's Search: Failed to send click packet: " + e.getMessage());
            return false;
        }
    }

    // ==================== 获取冶炼炉数据 ====================

    /**
     * 从屏幕中获取冶炼炉 BlockEntity（带缓存）
     */
    private static SmelteryBlockEntity getSmelteryFromScreen(AbstractContainerScreen<?> screen) {
        long now = System.currentTimeMillis();
        if (cachedSmeltery != null && (now - cacheTime) < CACHE_DURATION) {
            return cachedSmeltery;
        }

        String[] fieldNames = {"te", "tileEntity", "blockEntity", "smeltery", "tile"};
        for (String name : fieldNames) {
            try {
                Field field = screen.getClass().getDeclaredField(name);
                field.setAccessible(true);
                Object obj = field.get(screen);
                if (obj instanceof SmelteryBlockEntity) {
                    cachedSmeltery = (SmelteryBlockEntity) obj;
                    cacheTime = now;
                    return cachedSmeltery;
                }
            } catch (NoSuchFieldException ignored) {
                // 继续尝试下一个字段名
            } catch (Exception e) {
                System.err.println("Tinker's Search: Error accessing field '" + name + "': " + e.getMessage());
            }
        }

        // 如果通过屏幕字段找不到，尝试从容器获取
        try {
            Object container = screen.getMenu();
            if (container != null) {
                Field tileField = container.getClass().getDeclaredField("tile");
                tileField.setAccessible(true);
                Object obj = tileField.get(container);
                if (obj instanceof SmelteryBlockEntity) {
                    cachedSmeltery = (SmelteryBlockEntity) obj;
                    cacheTime = now;
                    return cachedSmeltery;
                }
            }
        } catch (Exception ignored) {}

        System.err.println("Tinker's Search: Could not find smeltery BlockEntity");
        return null;
    }

    /**
     * 获取当前屏幕上冶炼炉的容量
     */
    public static int getSmelteryCapacity() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen)) return 0;

        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.screen;
        SmelteryBlockEntity smeltery = getSmelteryFromScreen(screen);
        if (smeltery == null) return 0;

        SmelteryTank<?> tank = smeltery.getTank();
        if (tank == null) return 0;

        return tank.getCapacity();
    }

    /**
     * 获取当前屏幕上冶炼炉的所有流体
     */
    public static List<FluidStack> getSmelteryFluids() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen)) return null;

        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.screen;
        SmelteryBlockEntity smeltery = getSmelteryFromScreen(screen);
        if (smeltery == null) return null;

        return SmelteryDataHelper.getMoltenFluids(smeltery);
    }

    // ==================== 兼容旧版方法（保留） ====================

    /**
     * 获取当前冶炼炉屏幕的 leftPos（通过反射）
     */
    private static int getLeftPos(AbstractContainerScreen<?> screen) {
        try {
            Field field = AbstractContainerScreen.class.getDeclaredField("leftPos");
            field.setAccessible(true);
            return field.getInt(screen);
        } catch (Exception e) {
            System.err.println("Tinker's Search: Failed to get leftPos: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 获取当前冶炼炉屏幕的 topPos（通过反射）
     */
    private static int getTopPos(AbstractContainerScreen<?> screen) {
        try {
            Field field = AbstractContainerScreen.class.getDeclaredField("topPos");
            field.setAccessible(true);
            return field.getInt(screen);
        } catch (Exception e) {
            System.err.println("Tinker's Search: Failed to get topPos: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 从卡片点击坐标获取流体索引（简化版，适用于你的面板布局）
     */
    public static int getFluidIndexFromCardClick(double mouseX, double mouseY, List<FluidStack> fluids,
                                                 int cardStartX, int cardStartY, int cardWidth, int cardHeight, int cardsPerRow, int spacing) {
        if (fluids == null || fluids.isEmpty()) return -1;

        for (int i = 0; i < fluids.size(); i++) {
            int row = i / cardsPerRow;
            int col = i % cardsPerRow;
            int cardX = cardStartX + col * (cardWidth + spacing);
            int cardY = cardStartY + row * (cardHeight + spacing);

            if (mouseX >= cardX && mouseX <= cardX + cardWidth &&
                    mouseY >= cardY && mouseY <= cardY + cardHeight) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 从卡片点击坐标获取流体（直接返回 FluidStack）
     */
    public static FluidStack getFluidFromCardClick(double mouseX, double mouseY, List<FluidStack> fluids,
                                                   int cardStartX, int cardStartY, int cardWidth, int cardHeight, int cardsPerRow, int spacing) {
        int index = getFluidIndexFromCardClick(mouseX, mouseY, fluids, cardStartX, cardStartY, cardWidth, cardHeight, cardsPerRow, spacing);
        if (index >= 0 && index < fluids.size()) {
            return fluids.get(index);
        }
        return null;
    }
}