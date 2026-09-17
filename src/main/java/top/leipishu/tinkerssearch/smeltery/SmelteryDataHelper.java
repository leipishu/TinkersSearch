package top.leipishu.tinkerssearch.smeltery;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import slimeknights.tconstruct.smeltery.block.entity.controller.HeatingStructureBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.tank.SmelteryTank;

import java.util.ArrayList;
import java.util.List;

public class SmelteryDataHelper {

    /**
     * 获取冶炼炉中所有熔融流体
     */
    public static List<FluidStack> getMoltenFluids(BlockEntity tileEntity) {
        List<FluidStack> fluids = new ArrayList<>();
        if (tileEntity == null) {
            return fluids;
        }

        if (tileEntity instanceof HeatingStructureBlockEntity) {
            HeatingStructureBlockEntity controller = (HeatingStructureBlockEntity) tileEntity;
            SmelteryTank<?> tank = controller.getTank();
            if (tank != null) {
                int tankCount = tank.getTanks();
                for (int i = 0; i < tankCount; i++) {
                    FluidStack fs = tank.getFluidInTank(i);
                    if (fs != null && !fs.isEmpty()) {
                        fluids.add(fs);
                    }
                }
            }
            return fluids;
        }

        fluids = getFluidsFromCapability(tileEntity);
        return fluids;
    }

    /**
     * 获取冶炼炉中最下方的流体
     * 槽位 0 是最底部
     */
    public static FluidStack getBottomFluid(BlockEntity tileEntity) {
        if (tileEntity == null) return null;

        // ===== 方法1：通过 HeatingStructureBlockEntity 获取 =====
        if (tileEntity instanceof HeatingStructureBlockEntity) {
            HeatingStructureBlockEntity controller = (HeatingStructureBlockEntity) tileEntity;
            SmelteryTank<?> tank = controller.getTank();
            if (tank != null && tank.getTanks() > 0) {
                // 槽位 0 是最底部
                FluidStack fluid = tank.getFluidInTank(0);
                if (fluid != null && !fluid.isEmpty()) {
                    return fluid;
                }
                // 如果槽位 0 为空，遍历查找第一个非空
                for (int i = 0; i < tank.getTanks(); i++) {
                    FluidStack f = tank.getFluidInTank(i);
                    if (f != null && !f.isEmpty()) {
                        return f;
                    }
                }
            }
        }

        // ===== 方法2：通过 Capability 获取 =====
        FluidStack bottom = getBottomFluidFromCapability(tileEntity);
        if (bottom != null) return bottom;

        return null;
    }

    /**
     * 通过 Capability 获取最下方流体
     */
    private static FluidStack getBottomFluidFromCapability(BlockEntity tileEntity) {
        if (tileEntity == null) return null;

        try {
            IFluidHandler fluidHandler = tileEntity.getCapability(
                    CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY
            ).orElse(null);

            if (fluidHandler == null) return null;

            int tankCount = fluidHandler.getTanks();
            if (tankCount == 0) return null;

            // 槽位 0 是最底部
            FluidStack fluid = fluidHandler.getFluidInTank(0);
            if (fluid != null && !fluid.isEmpty()) {
                return fluid;
            }

            // 如果槽位 0 为空，遍历查找第一个非空
            for (int i = 0; i < tankCount; i++) {
                FluidStack f = fluidHandler.getFluidInTank(i);
                if (f != null && !f.isEmpty()) {
                    return f;
                }
            }
        } catch (Exception e) {
            System.err.println("Tinker's Search: Capability bottom fluid error: " + e.getMessage());
        }

        return null;
    }

    /**
     * 获取最下方流体的名称（用于高亮匹配）
     */
    public static String getBottomFluidName(BlockEntity tileEntity) {
        FluidStack bottom = getBottomFluid(tileEntity);
        if (bottom == null) return null;
        return bottom.getDisplayName().getString();
    }

    private static List<FluidStack> getFluidsFromCapability(BlockEntity tileEntity) {
        List<FluidStack> fluids = new ArrayList<>();
        if (tileEntity == null) return fluids;

        try {
            IFluidHandler fluidHandler = tileEntity.getCapability(
                    CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY
            ).orElse(null);

            if (fluidHandler == null) return fluids;

            int tankCount = fluidHandler.getTanks();
            for (int i = 0; i < tankCount; i++) {
                FluidStack fs = fluidHandler.getFluidInTank(i);
                if (fs != null && !fs.isEmpty()) {
                    fluids.add(fs);
                }
            }
        } catch (Exception e) {
            System.err.println("Tinker's Search: Capability error: " + e.getMessage());
        }

        return fluids;
    }

    /**
     * 绘制流体图标 - 1.19.2 使用 PoseStack + GuiComponent
     */
    public static void drawFluidIcon(PoseStack poseStack, int x, int y, FluidStack fluidStack, int size) {
        if (fluidStack == null || fluidStack.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        Fluid fluid = fluidStack.getFluid();

        // 1.19.2：通过 IClientFluidTypeExtensions 获取纹理和颜色（Fluid.getAttributes() 已弃用）
        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluid);
        ResourceLocation stillTexture = extensions.getStillTexture(fluidStack);
        int color = extensions.getTintColor(fluidStack);

        try {
            TextureAtlasSprite sprite = mc.getTextureAtlas(
                    new ResourceLocation("textures/atlas/blocks.png")
            ).apply(stillTexture);

            if (sprite == null) {
                GuiComponent.fill(poseStack, x, y, x + size, y + size, color | 0xFF000000);
                return;
            }

            RenderSystem.setShaderTexture(0, new ResourceLocation("textures/atlas/blocks.png"));

            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;
            float a = ((color >> 24) & 0xFF) / 255.0f;

            RenderSystem.setShaderColor(r, g, b, a);
            GuiComponent.blit(poseStack, x, y, 0, size, size, sprite);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

            GuiComponent.fill(poseStack, x, y, x + size, y + 1, 0xFF666666);
            GuiComponent.fill(poseStack, x, y + size - 1, x + size, y + size, 0xFF666666);
            GuiComponent.fill(poseStack, x, y, x + 1, y + size, 0xFF666666);
            GuiComponent.fill(poseStack, x + size - 1, y, x + size, y + size, 0xFF666666);

        } catch (Exception ex) {
            GuiComponent.fill(poseStack, x, y, x + size, y + size, color | 0xFF000000);
        }
    }
}
