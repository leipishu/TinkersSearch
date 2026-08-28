package top.leipishu.tinkerssearch.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
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

        if (tileEntity instanceof HeatingStructureBlockEntity) {
            HeatingStructureBlockEntity controller = (HeatingStructureBlockEntity) tileEntity;
            SmelteryTank<?> tank = controller.getTank();
            if (tank != null && tank.getTanks() > 0) {
                FluidStack fluid = tank.getFluidInTank(0);
                if (fluid != null && !fluid.isEmpty()) {
                    return fluid;
                }
                for (int i = 0; i < tank.getTanks(); i++) {
                    FluidStack f = tank.getFluidInTank(i);
                    if (f != null && !f.isEmpty()) {
                        return f;
                    }
                }
            }
        }

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
                    ForgeCapabilities.FLUID_HANDLER
            ).orElse(null);

            if (fluidHandler == null) return null;

            int tankCount = fluidHandler.getTanks();
            if (tankCount == 0) return null;

            FluidStack fluid = fluidHandler.getFluidInTank(0);
            if (fluid != null && !fluid.isEmpty()) {
                return fluid;
            }

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
                    ForgeCapabilities.FLUID_HANDLER
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
     * 绘制流体图标 - 1.20.1
     * 使用 GuiGraphics.blit(int x, int y, int zLevel, int width, int height, TextureAtlasSprite sprite)
     */
    public static void drawFluidIcon(GuiGraphics graphics, int x, int y, FluidStack fluidStack, int size) {
        if (fluidStack == null || fluidStack.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        Fluid fluid = fluidStack.getFluid();

        // 通过 IClientFluidTypeExtensions 获取纹理和颜色
        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluid);
        ResourceLocation stillTexture = extensions.getStillTexture(fluidStack);
        int color = extensions.getTintColor(fluidStack);

        try {
            // 先尝试获取精灵，如果纹理不存在则用纯色填充
            TextureAtlasSprite sprite = mc.getTextureAtlas(
                    new ResourceLocation("textures/atlas/blocks.png")
            ).apply(stillTexture);

            if (sprite == null) {
                graphics.fill(x, y, x + size, y + size, color | 0xFF000000);
                return;
            }

            // 绑定纹理图集
            RenderSystem.setShaderTexture(0, new ResourceLocation("textures/atlas/blocks.png"));

            // 设置颜色
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;
            float a = ((color >> 24) & 0xFF) / 255.0f;
            RenderSystem.setShaderColor(r, g, b, a);

            // ✅ 正确写法：直接使用 sprite 绘制，GuiGraphics 会自动处理 UV 坐标
            graphics.blit(x, y, 0, size, size, sprite);

            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

            // 边框
            graphics.fill(x, y, x + size, y + 1, 0xFF666666);
            graphics.fill(x, y + size - 1, x + size, y + size, 0xFF666666);
            graphics.fill(x, y, x + 1, y + size, 0xFF666666);
            graphics.fill(x + size - 1, y, x + size, y + size, 0xFF666666);

        } catch (Exception ex) {
            // 出错时用纯色填充
            graphics.fill(x, y, x + size, y + size, color | 0xFF000000);
        }
    }
}