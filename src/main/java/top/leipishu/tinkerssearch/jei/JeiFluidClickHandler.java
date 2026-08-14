package top.leipishu.tinkerssearch.jei;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import slimeknights.tconstruct.smeltery.block.entity.controller.SmelteryBlockEntity;
import top.leipishu.tinkerssearch.utils.SmelteryClickHandler;

import java.util.List;

public class JeiFluidClickHandler {

    public static boolean handleLeftClick(FluidStack fluidStack) {
        if (fluidStack == null || fluidStack.isEmpty()) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isSpectator()) return false;

        if (!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen)) {
            System.out.println("Tinker's Search: Not in a container screen");
            return false;
        }

        net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen =
                (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) mc.screen;
        String className = screen.getClass().getName();
        if (!className.contains("SmelteryScreen") && !className.contains("smeltery")) {
            System.out.println("Tinker's Search: Not in smeltery screen");
            return false;
        }

        BlockEntity smeltery = SmelteryClickHandler.getSmelteryFromScreen(screen);
        if (smeltery == null || !(smeltery instanceof SmelteryBlockEntity)) {
            System.out.println("Tinker's Search: Failed to get smeltery BlockEntity");
            return false;
        }

        SmelteryBlockEntity smelteryBE = (SmelteryBlockEntity) smeltery;
        List<FluidStack> fluids = smelteryBE.getTank().getFluids();
        int index = -1;

        for (int i = 0; i < fluids.size(); i++) {
            if (fluids.get(i).isFluidEqual(fluidStack)) {
                index = i;
                break;
            }
        }

        if (index < 0) {
            String targetName = fluidStack.getDisplayName().getString();
            for (int i = 0; i < fluids.size(); i++) {
                if (fluids.get(i).getDisplayName().getString().equals(targetName)) {
                    index = i;
                    break;
                }
            }
        }

        if (index < 0) {
            System.out.println("Tinker's Search: Fluid not found in smeltery");
            return false;
        }

        return SmelteryClickHandler.clickFluid(index);
    }

    public static boolean handleRightClick(FluidStack fluidStack) {
        if (fluidStack == null || fluidStack.isEmpty()) return false;

        try {
            if (Jei.isJeiAvailable()) {
                System.out.println("Tinker's Search: Opening recipe for: " + fluidStack.getDisplayName().getString());
                return true;
            }
            return false;
        } catch (Exception e) {
            System.err.println("Tinker's Search: Failed to open JEI recipe: " + e.getMessage());
            return false;
        }
    }

    public static boolean handleShiftLeftClick(FluidStack fluidStack) {
        if (fluidStack == null || fluidStack.isEmpty()) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isSpectator()) return false;

        if (!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen)) {
            return false;
        }

        net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen =
                (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) mc.screen;
        String className = screen.getClass().getName();
        if (!className.contains("SmelteryScreen") && !className.contains("smeltery")) {
            return false;
        }

        BlockEntity smeltery = SmelteryClickHandler.getSmelteryFromScreen(screen);
        if (smeltery == null || !(smeltery instanceof SmelteryBlockEntity)) {
            return false;
        }

        SmelteryBlockEntity smelteryBE = (SmelteryBlockEntity) smeltery;
        List<FluidStack> fluids = smelteryBE.getTank().getFluids();
        String targetName = fluidStack.getDisplayName().getString();

        boolean success = false;
        for (int i = fluids.size() - 1; i >= 0; i--) {
            if (fluids.get(i).getDisplayName().getString().equals(targetName) ||
                    fluids.get(i).isFluidEqual(fluidStack)) {
                if (SmelteryClickHandler.clickFluid(i)) {
                    success = true;
                }
            }
        }

        return success;
    }
}