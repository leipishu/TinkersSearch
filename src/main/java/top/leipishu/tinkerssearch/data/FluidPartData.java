package top.leipishu.tinkerssearch.data;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个流体的全部部件数据。
 *
 * 结构：
 *   FluidPartData
 *     └── MaterialEntry      一个材料对应一页
 *           └── PartInfo      一个部件在该材料下的表现
 *
 * 关键原则：
 *   材料 (MaterialId) 是主键，属性是 (material, statType) 的函数。
 *   同一部件名在不同材料下是独立的 PartInfo。
 */
public class FluidPartData {

    public final ResourceLocation fluidId;
    public final List<MaterialEntry> entries;
    public final long buildTime;

    public FluidPartData(ResourceLocation fluidId, List<MaterialEntry> entries, long buildTime) {
        this.fluidId = fluidId;
        this.entries = entries != null ? entries : new ArrayList<>();
        this.buildTime = buildTime;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    // ============================================================
    // ===== MaterialEntry ========================================
    // ============================================================

    public static class MaterialEntry {
        public enum SourceKind { BASE, COMPOSITE }

        public final MaterialId materialId;
        public final String title;
        public final SourceKind kind;
        public final MaterialId compositeInput;      // 仅 COMPOSITE 时非 null
        public final String compositeInputName;      // 仅 COMPOSITE 时非 null
        public final List<PartInfo> parts;

        public MaterialEntry(MaterialId materialId, String title, SourceKind kind,
                             MaterialId compositeInput, String compositeInputName,
                             List<PartInfo> parts) {
            this.materialId = materialId;
            this.title = title;
            this.kind = kind;
            this.compositeInput = compositeInput;
            this.compositeInputName = compositeInputName;
            this.parts = parts != null ? parts : new ArrayList<>();
        }
    }

    // ============================================================
    // ===== PartInfo =============================================
    // ============================================================

    public static class PartInfo {
        public final ResourceLocation itemId;
        public final Item item;
        public final String displayName;
        public final MaterialId materialId;
        public final MaterialStatsId statType;
        public final PartProperties properties;
        public final int requiredAmount;
        public final ItemStack displayStack;

        public PartInfo(ResourceLocation itemId, Item item, String displayName,
                        MaterialId materialId, MaterialStatsId statType,
                        PartProperties properties, int requiredAmount, ItemStack displayStack) {
            this.itemId = itemId;
            this.item = item;
            this.displayName = displayName;
            this.materialId = materialId;
            this.statType = statType;
            this.properties = properties;
            this.requiredAmount = requiredAmount;
            this.displayStack = displayStack;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // ============================================================
    // ===== PartProperties =======================================
    // ============================================================

    public static class PartProperties {
        public final List<Component> statLines;
        public final List<ModifierInfo> modifiers;

        public PartProperties(List<Component> statLines, List<ModifierInfo> modifiers) {
            this.statLines = statLines != null ? statLines : new ArrayList<>();
            this.modifiers = modifiers != null ? modifiers : new ArrayList<>();
        }

        public boolean hasStats() {
            return !statLines.isEmpty() || !modifiers.isEmpty();
        }
    }

    // ============================================================
    // ===== ModifierInfo =========================================
    // ============================================================

    public static class ModifierInfo {
        public final String id;
        public final Component displayName;
        public final int level;
        public final List<Component> descriptionLines;

        public ModifierInfo(String id, Component displayName, int level, List<Component> descriptionLines) {
            this.id = id;
            this.displayName = displayName;
            this.level = level;
            this.descriptionLines = descriptionLines != null ? descriptionLines : new ArrayList<>();
        }
    }
}