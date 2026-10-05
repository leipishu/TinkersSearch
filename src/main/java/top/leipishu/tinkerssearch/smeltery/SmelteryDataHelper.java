package top.leipishu.tinkerssearch.smeltery;

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
     * 绘制流体图标（不透明）。
     */
    public static void drawFluidIcon(GuiGraphics graphics, int x, int y, FluidStack fluidStack, int size) {
        drawFluidIcon(graphics, x, y, fluidStack, size, 1.0f);
    }

    /**
     * 绘制流体图标（带外部 alpha）。
     *
     * <p>外部 alpha 会与流体自身颜色 alpha 相乘，用于面板滑入/滑出时的
     * 图标延迟淡入淡出。
     *
     * @param alpha 0..1；&le;0.01 时直接跳过绘制
     */
    public static void drawFluidIcon(GuiGraphics graphics, int x, int y,
                                     FluidStack fluidStack, int size, float alpha) {
        if (fluidStack == null || fluidStack.isEmpty()) return;
        if (alpha <= 0.01f) return;
        if (alpha > 1f) alpha = 1f;

        Minecraft mc = Minecraft.getInstance();
        Fluid fluid = fluidStack.getFluid();

        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluid);
        ResourceLocation stillTexture = extensions.getStillTexture(fluidStack);
        int color = extensions.getTintColor(fluidStack);

        try {
            TextureAtlasSprite sprite = mc.getTextureAtlas(
                    new ResourceLocation("textures/atlas/blocks.png")
            ).apply(stillTexture);

            if (sprite == null) {
                int a8 = (int) (((color >>> 24) & 0xFF) * alpha);
                graphics.fill(x, y, x + size, y + size, (a8 << 24) | (color & 0x00FFFFFF));
                return;
            }

            RenderSystem.setShaderTexture(0, new ResourceLocation("textures/atlas/blocks.png"));

            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;
            float a = ((color >> 24) & 0xFF) / 255.0f * alpha;

            RenderSystem.setShaderColor(r, g, b, a);
            graphics.blit(x, y, 0, size, size, sprite);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

            int borderAlpha = (int) (0xFF * alpha);
            int borderColor = (borderAlpha << 24) | 0x00666666;
            graphics.fill(x, y, x + size, y + 1, borderColor);
            graphics.fill(x, y + size - 1, x + size, y + size, borderColor);
            graphics.fill(x, y, x + 1, y + size, borderColor);
            graphics.fill(x + size - 1, y, x + size, y + size, borderColor);

        } catch (Exception ex) {
            int a8 = (int) (((color >>> 24) & 0xFF) * alpha);
            graphics.fill(x, y, x + size, y + size, (a8 << 24) | (color & 0x00FFFFFF));
        }
    }
}