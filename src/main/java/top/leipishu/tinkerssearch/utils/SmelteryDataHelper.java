package top.leipishu.tinkerssearch.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import slimeknights.tconstruct.smeltery.block.entity.controller.HeatingStructureBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.tank.SmelteryTank;

import java.util.ArrayList;
import java.util.List;

public class SmelteryDataHelper {

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

    private static List<FluidStack> getFluidsFromCapability(BlockEntity tileEntity) {
        List<FluidStack> fluids = new ArrayList<>();
        if (tileEntity == null) return fluids;

        try {
            net.minecraftforge.fluids.capability.IFluidHandler fluidHandler =
                    tileEntity.getCapability(
                            net.minecraftforge.fluids.capability.CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY
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

    public static void drawFluidIcon(PoseStack poseStack, int x, int y, FluidStack fluidStack, int size) {
        if (fluidStack == null || fluidStack.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        Fluid fluid = fluidStack.getFluid();

        ResourceLocation stillTexture = fluid.getAttributes().getStillTexture();
        int color = fluid.getAttributes().getColor(fluidStack);

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