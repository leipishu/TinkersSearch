package top.leipishu.tinkerssearch.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.smeltery.block.entity.controller.HeatingStructureBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.module.FuelModule;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Supplier;

/**
 * 冶炼炉温度读取器 - 从 FuelModule 读取温度
 */
public class SmelteryTemperatureReader {

    private int cachedTemperature = 0;
    private long lastTemperatureReadTime = 0;
    private static final long TEMPERATURE_READ_COOLDOWN = 1000;
    private static final boolean DEBUG_TEMPERATURE = false;

    public int getCurrentSmelteryTemperature() {
        long now = System.currentTimeMillis();

        if (now - lastTemperatureReadTime < TEMPERATURE_READ_COOLDOWN) {
            return cachedTemperature;
        }

        int temp = readTemperatureFromSmeltery();
        cachedTemperature = temp;
        lastTemperatureReadTime = now;
        return temp;
    }

    public int forceRefreshTemperature() {
        int temp = readTemperatureFromSmeltery();
        cachedTemperature = temp;
        lastTemperatureReadTime = System.currentTimeMillis();
        if (DEBUG_TEMPERATURE) {
            System.out.println("Tinker's Search: Force refreshed temperature: " + temp + "°C");
        }
        return temp;
    }

    private int readTemperatureFromSmeltery() {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;

        if (screen == null) {
            return 0;
        }

        BlockEntity target = null;
        String[] fieldNames = {"te", "tileEntity", "blockEntity", "smeltery", "tile"};
        for (String name : fieldNames) {
            try {
                Field field = screen.getClass().getDeclaredField(name);
                field.setAccessible(true);
                Object obj = field.get(screen);
                if (obj instanceof BlockEntity) {
                    target = (BlockEntity) obj;
                    break;
                }
            } catch (Exception ignored) {}
        }

        if (target == null) {
            return 0;
        }

        if (!(target instanceof HeatingStructureBlockEntity)) {
            return 0;
        }

        HeatingStructureBlockEntity controller = (HeatingStructureBlockEntity) target;

        // ===== 清除 FuelModule 缓存 =====
        try {
            FuelModule fuelModule = controller.getFuelModule();
            if (fuelModule != null) {
                String[] cacheFields = {"heat", "fuel", "lastFuel", "currentFuel"};
                for (String fieldName : cacheFields) {
                    try {
                        Field field = FuelModule.class.getDeclaredField(fieldName);
                        field.setAccessible(true);
                        if (field.getType() == int.class || field.getType() == Integer.class) {
                            field.setInt(fuelModule, 0);
                        } else {
                            field.set(fuelModule, null);
                        }
                    } catch (Exception ignored) {}
                }

                try {
                    Method updateMethod = FuelModule.class.getMethod("update");
                    updateMethod.invoke(fuelModule);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        // ===== 直接从燃料槽读取温度 =====
        try {
            FuelModule fuelModule = controller.getFuelModule();
            if (fuelModule != null) {
                try {
                    Field tankSupplierField = FuelModule.class.getDeclaredField("tankSupplier");
                    tankSupplierField.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    Supplier<List<BlockPos>> tankSupplier = (Supplier<List<BlockPos>>) tankSupplierField.get(fuelModule);
                    List<BlockPos> tankPositions = tankSupplier.get();

                    if (tankPositions != null && !tankPositions.isEmpty()) {
                        Level level = controller.getLevel();
                        for (BlockPos pos : tankPositions) {
                            BlockEntity te = level.getBlockEntity(pos);
                            if (te != null) {
                                // ✅ 1.20.1：使用 ForgeCapabilities.FLUID_HANDLER
                                IFluidHandler fluidHandler = te.getCapability(ForgeCapabilities.FLUID_HANDLER)
                                        .orElse(null);
                                if (fluidHandler != null && fluidHandler.getTanks() > 0) {
                                    FluidStack fluid = fluidHandler.getFluidInTank(0);
                                    if (!fluid.isEmpty()) {
                                        int temp = getFluidTemperature(fluid);
                                        if (temp > 0) {
                                            if (DEBUG_TEMPERATURE || temp != cachedTemperature) {
                                                System.out.println("Tinker's Search: Temperature: " + temp + "°C (" + fluid.getDisplayName().getString() + ")");
                                            }
                                            return temp;
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    if (DEBUG_TEMPERATURE) {
                        System.out.println("Tinker's Search: tankSupplier failed: " + e.getMessage());
                    }
                }
            }
        } catch (Exception ignored) {}

        // ===== 最后尝试从 FuelModule 读取 =====
        try {
            FuelModule fuelModule = controller.getFuelModule();
            if (fuelModule != null) {
                Method getTempMethod = FuelModule.class.getMethod("getTemperature");
                int temp = (int) getTempMethod.invoke(fuelModule);
                if (temp > 0) {
                    return temp;
                }
            }
        } catch (Exception ignored) {}

        return 0;
    }

    private int getFluidTemperature(FluidStack fluid) {
        if (fluid == null || fluid.isEmpty()) {
            return 0;
        }

        Fluid fluidObj = fluid.getFluid();

        // 策略1：MeltingFuelLookup
        try {
            Class<?> fuelLookupClass = Class.forName("slimeknights.tconstruct.library.recipe.fuel.MeltingFuelLookup");
            Method findFuelMethod = fuelLookupClass.getMethod("findFuel", Fluid.class);
            Object fuel = findFuelMethod.invoke(null, fluidObj);
            if (fuel != null) {
                Method getTempMethod = fuel.getClass().getMethod("getTemperature");
                int temp = (int) getTempMethod.invoke(fuel);
                if (temp > 0) {
                    return temp;
                }
            }
        } catch (Exception ignored) {}

        // 策略2：FluidAttributes（1.20.1 中已被移除，但保留兜底）
        try {
            Method getAttributesMethod = Fluid.class.getMethod("getAttributes");
            Object attributes = getAttributesMethod.invoke(fluidObj);
            if (attributes != null) {
                try {
                    Method getTemperatureMethod = attributes.getClass().getMethod("getTemperature");
                    int temp = (int) getTemperatureMethod.invoke(attributes);
                    if (temp > 0) {
                        return temp;
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        // 策略3：硬编码
        String name = fluid.getDisplayName().getString().toLowerCase();
        // ✅ 1.20.1：通过 ForgeRegistries 获取注册名
        ResourceLocation rl = ForgeRegistries.FLUIDS.getKey(fluidObj);
        String registryName = rl != null ? rl.toString().toLowerCase() : "";

        if (name.contains("lava") || name.contains("岩浆") || registryName.contains("lava")) {
            return 1300;
        }
        if (name.contains("blazing") || name.contains("烈焰血") || registryName.contains("blazing")) {
            return 1500;
        }
        if (name.contains("blaze") || name.contains("烈焰")) {
            return 1500;
        }
        if (name.contains("fuel") || name.contains("燃料")) {
            return 1000;
        }
        if (name.contains("oil") || name.contains("油")) {
            return 800;
        }
        if (registryName.contains("molten") || registryName.contains("tconstruct")) {
            return 900;
        }

        return 0;
    }

    public void invalidateCache() {
        cachedTemperature = 0;
        lastTemperatureReadTime = 0;
    }
}