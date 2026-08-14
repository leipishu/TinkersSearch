package top.leipishu.tinkerssearch.jei;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import mezz.jei.api.ingredients.IIngredientRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class JeiFluidRenderer implements IIngredientRenderer<JeiFluidStackWrapper> {

    public static final JeiFluidRenderer INSTANCE = new JeiFluidRenderer();

    @Override
    public void render(PoseStack poseStack, @Nullable JeiFluidStackWrapper wrapper) {
        if (wrapper == null) return;

        FluidStack fluidStack = wrapper.getFluidStack();
        if (fluidStack == null || fluidStack.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();

        ResourceLocation stillTexture = fluidStack.getFluid().getAttributes().getStillTexture();
        TextureAtlasSprite sprite = mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(stillTexture);

        if (sprite != null) {
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);

            int color = fluidStack.getFluid().getAttributes().getColor(fluidStack);
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;

            RenderSystem.setShaderColor(r, g, b, 1.0f);

            BufferBuilder buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

            float u0 = sprite.getU0();
            float u1 = sprite.getU1();
            float v0 = sprite.getV0();
            float v1 = sprite.getV1();

            buffer.vertex(poseStack.last().pose(), 0, 16, 0).uv(u0, v1).color(255, 255, 255, 255).endVertex();
            buffer.vertex(poseStack.last().pose(), 16, 16, 0).uv(u1, v1).color(255, 255, 255, 255).endVertex();
            buffer.vertex(poseStack.last().pose(), 16, 0, 0).uv(u1, v0).color(255, 255, 255, 255).endVertex();
            buffer.vertex(poseStack.last().pose(), 0, 0, 0).uv(u0, v0).color(255, 255, 255, 255).endVertex();

            Tesselator.getInstance().end();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    /**
     * 修复：添加 TooltipFlag 参数
     * JEI 9.x 中 getTooltip 方法签名为 getTooltip(T ingredient, TooltipFlag tooltipFlag)
     */
    @Override
    public List<Component> getTooltip(JeiFluidStackWrapper wrapper, TooltipFlag tooltipFlag) {
        List<Component> list = new ArrayList<>();
        FluidStack fluidStack = wrapper.getFluidStack();

        // 流体名称
        list.add(fluidStack.getDisplayName().copy().withStyle(ChatFormatting.WHITE));

        // 流体量
        int amount = wrapper.getAmount();
        String amountStr = amount >= 1000 ?
                String.format("%.1f B", amount / 1000.0) :
                amount + " mB";
        list.add(new TextComponent(amountStr).withStyle(ChatFormatting.GRAY));

        // 操作提示
        list.add(new TextComponent("左键: 移动到最下面").withStyle(ChatFormatting.GREEN));
        list.add(new TextComponent("右键: 查看配方").withStyle(ChatFormatting.GOLD));
        list.add(new TextComponent("Shift+左键: 批量操作").withStyle(ChatFormatting.DARK_GREEN));

        return list;
    }

    @Override
    public Font getFontRenderer(Minecraft minecraft, JeiFluidStackWrapper wrapper) {
        return minecraft.font;
    }
}