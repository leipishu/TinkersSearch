package top.leipishu.tinkerssearch.jei;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fluids.FluidStack;

public class JeiFluidStackWrapper {

    private final FluidStack fluidStack;
    private final int amount;

    public JeiFluidStackWrapper(FluidStack fluidStack) {
        this.fluidStack = fluidStack;
        this.amount = fluidStack.getAmount();
    }

    public FluidStack getFluidStack() {
        return fluidStack;
    }

    public int getAmount() {
        return amount;
    }

    public Component getDisplayName() {
        return fluidStack.getDisplayName();
    }

    public CompoundTag getTag() {
        return fluidStack.getTag();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof JeiFluidStackWrapper)) return false;
        JeiFluidStackWrapper other = (JeiFluidStackWrapper) obj;
        return fluidStack.isFluidEqual(other.fluidStack);
    }

    @Override
    public int hashCode() {
        return fluidStack.getFluid().hashCode();
    }
}