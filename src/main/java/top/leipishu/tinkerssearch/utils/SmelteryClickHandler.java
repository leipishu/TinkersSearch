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

public class SmelteryClickHandler {

    private static SmelteryBlockEntity cachedSmeltery = null;
    private static long cacheTime = 0;
    private static final long CACHE_DURATION = 1000;

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

        BlockEntity be = getSmelteryFromScreen(screen);
        if (be == null || !(be instanceof SmelteryBlockEntity)) {
            System.err.println("Tinker's Search: Failed to get smeltery BlockEntity");
            return false;
        }

        SmelteryBlockEntity smeltery = (SmelteryBlockEntity) be;
        List<FluidStack> fluids = smeltery.getTank().getFluids();
        if (fluids == null || fluids.isEmpty()) {
            System.err.println("Tinker's Search: No fluids in smeltery");
            return false;
        }

        int index = findFluidIndex(fluids, fluidStack);
        if (index < 0) {
            System.err.println("Tinker's Search: Fluid not found in smeltery: " +
                    fluidStack.getDisplayName().getString());
            return false;
        }

        return clickFluid(index);
    }

    public static boolean clickFluidByName(FluidStack fluidStack) {
        if (fluidStack == null) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isSpectator()) return false;
        if (!(mc.screen instanceof AbstractContainerScreen)) return false;

        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.screen;
        String className = screen.getClass().getName();
        if (!className.contains("SmelteryScreen") && !className.contains("smeltery")) return false;

        BlockEntity be = getSmelteryFromScreen(screen);
        if (be == null || !(be instanceof SmelteryBlockEntity)) return false;

        SmelteryBlockEntity smeltery = (SmelteryBlockEntity) be;
        List<FluidStack> fluids = smeltery.getTank().getFluids();
        if (fluids == null || fluids.isEmpty()) return false;

        String targetName = fluidStack.getDisplayName().getString();
        for (int i = 0; i < fluids.size(); i++) {
            String fluidName = fluids.get(i).getDisplayName().getString();
            if (fluidName.equals(targetName)) {
                return clickFluid(i);
            }
        }

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

    private static int findFluidIndex(List<FluidStack> fluids, FluidStack target) {
        if (target == null || fluids == null) return -1;

        for (int i = 0; i < fluids.size(); i++) {
            if (fluids.get(i).isFluidEqual(target)) {
                return i;
            }
        }

        String targetName = target.getDisplayName().getString();
        for (int i = 0; i < fluids.size(); i++) {
            String fluidName = fluids.get(i).getDisplayName().getString();
            if (fluidName.equalsIgnoreCase(targetName)) {
                return i;
            }
        }

        String targetRegistryName = target.getFluid().getRegistryName().toString();
        for (int i = 0; i < fluids.size(); i++) {
            String registryName = fluids.get(i).getFluid().getRegistryName().toString();
            if (registryName.equals(targetRegistryName)) {
                return i;
            }
        }

        return -1;
    }

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

        BlockEntity be = getSmelteryFromScreen(screen);
        if (be == null || !(be instanceof SmelteryBlockEntity)) {
            System.err.println("Tinker's Search: Failed to get smeltery BlockEntity");
            return false;
        }

        SmelteryBlockEntity smeltery = (SmelteryBlockEntity) be;
        List<FluidStack> fluids = smeltery.getTank().getFluids();
        if (fluids == null || fluidIndex < 0 || fluidIndex >= fluids.size()) {
            System.err.println("Tinker's Search: Invalid fluid index: " + fluidIndex);
            return false;
        }

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

    public static BlockEntity getSmelteryFromScreen(AbstractContainerScreen<?> screen) {
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
            } catch (Exception e) {
                System.err.println("Tinker's Search: Error accessing field '" + name + "': " + e.getMessage());
            }
        }

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

    public static int getSmelteryCapacity() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen)) return 0;

        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.screen;
        BlockEntity be = getSmelteryFromScreen(screen);
        if (be == null || !(be instanceof SmelteryBlockEntity)) return 0;

        SmelteryBlockEntity smeltery = (SmelteryBlockEntity) be;
        SmelteryTank<?> tank = smeltery.getTank();
        if (tank == null) return 0;

        return tank.getCapacity();
    }

    public static List<FluidStack> getSmelteryFluids() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen)) return null;

        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.screen;
        BlockEntity be = getSmelteryFromScreen(screen);
        if (be == null || !(be instanceof SmelteryBlockEntity)) return null;

        SmelteryBlockEntity smeltery = (SmelteryBlockEntity) be;
        return smeltery.getTank().getFluids();
    }
}