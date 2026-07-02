package net.lanzr.burnjosspaper;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 公共许愿库存 — 环形缓冲区 + Container 实现
 *
 * 物理存储为固定大小的 ItemStack 数组，
 * 通过 headOffset 实现逻辑视图：
 *   - 新增物品写入 headOffset 位置后 headOffset 前进
 *   - 逻辑索引 0 = 最新物品（物理 headOffset-1）
 *   - 逻辑索引 1 = 第二新（物理 headOffset-2）…依此类推
 *
 * 直接实现 {@link Container} 接口，可被 {@code PaginationContainer} 直接使用。
 */
public class WishSavedData extends SavedData implements Container {

    private static final String DATA_NAME = "wish_inventory";

    // NBT tags — new format
    private static final String TAG_CONTAINER_SIZE = "containerSize";
    private static final String TAG_HEAD_OFFSET   = "headOffset";
    private static final String TAG_ITEMS          = "items";
    private static final String TAG_SLOT           = "slot";

    // NBT tags — legacy page format (for migration)
    private static final String TAG_LEGACY_PAGE_COUNT = "pageCount";
    private static final String TAG_LEGACY_PAGES      = "pages";

    /** 物理存储 */
    private ItemStack[] items;
    /** 下一个物品写入的物理索引（0 ~ containerSize-1） */
    private int headOffset;
    /** 容器大小 */
    private int containerSize;

    // ========== 构造 ==========

    public WishSavedData(int containerSize) {
        this.containerSize = Math.max(containerSize, 1);
        this.items = new ItemStack[this.containerSize];
        for (int i = 0; i < this.containerSize; i++) {
            items[i] = ItemStack.EMPTY;
        }
        this.headOffset = 0;
    }

    // ========== Container 接口实现 ==========

    @Override
    public int getContainerSize() {
        return containerSize;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    /**
     * 获取逻辑索引处的物品（返回实际引用，非副本）。
     * 逻辑索引 0 = 最新（headOffset-1），逻辑索引递增 = 按插入顺序回溯。
     */
    @Override
    public ItemStack getItem(int logicalIndex) {
        if (logicalIndex < 0 || logicalIndex >= containerSize) {
            return ItemStack.EMPTY;
        }
        return items[logicalToPhysical(logicalIndex)];
    }

    @Override
    public ItemStack removeItem(int logicalIndex, int amount) {
        if (logicalIndex < 0 || logicalIndex >= containerSize) return ItemStack.EMPTY;
        int phys = logicalToPhysical(logicalIndex);
        ItemStack stack = items[phys];
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack removed = stack.split(amount);
        if (stack.isEmpty()) {
            items[phys] = ItemStack.EMPTY;
        }
        setChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int logicalIndex) {
        if (logicalIndex < 0 || logicalIndex >= containerSize) return ItemStack.EMPTY;
        int phys = logicalToPhysical(logicalIndex);
        ItemStack stack = items[phys];
        items[phys] = ItemStack.EMPTY;
        return stack;
    }

    /**
     * 设置逻辑索引处的物品。通过偏移量映射到物理位置。
     */
    @Override
    public void setItem(int logicalIndex, ItemStack stack) {
        if (logicalIndex < 0 || logicalIndex >= containerSize) return;
        int phys = logicalToPhysical(logicalIndex);
        items[phys] = stack.copy();
        setChanged();
    }

    @Override
    public void setChanged() {
        super.setDirty();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < containerSize; i++) {
            items[i] = ItemStack.EMPTY;
        }
        headOffset = 0;
        setChanged();
    }

    // ========== 环形缓冲区 ==========

    /**
     * 逻辑索引 → 物理索引 映射
     *
     * 物理数组是顺次写入的（新物品始终写入 headOffset），
     * 逻辑索引则按 "最新在前" 排列：
     *   逻辑 0 → 物理 headOffset - 1  （最新）
     *   逻辑 1 → 物理 headOffset - 2
     *   ...
     *   逻辑 k → 物理 headOffset - 1 - k
     */
    private int logicalToPhysical(int logicalIndex) {
        int raw = headOffset - 1 - logicalIndex;
        int mod = raw % containerSize;
        return mod >= 0 ? mod : mod + containerSize;
    }

    /**
     * 添加一个新物品到环形缓冲区。
     * 物品写入当前 headOffset 位置，然后 headOffset 前进一位。
     * 如果该位置已有物品（缓冲区已满），旧物品被覆盖。
     */
    public void addItem(ItemStack stack) {
        if (stack.isEmpty()) return;

        ItemStack toAdd = stack.copy();

        // 第一遍：尝试与已有物品堆叠（按物理顺序查找）
        for (int p = 0; p < containerSize; p++) {
            ItemStack existing = items[p];
            if (!existing.isEmpty() && ItemStack.isSameItemSameTags(existing, toAdd)) {
                int maxStack = Math.min(existing.getMaxStackSize(), toAdd.getMaxStackSize());
                int space = maxStack - existing.getCount();
                if (space > 0) {
                    int toMove = Math.min(space, toAdd.getCount());
                    existing.grow(toMove);
                    toAdd.shrink(toMove);
                    if (toAdd.isEmpty()) {
                        setChanged();
                        return;
                    }
                }
            }
        }

        // 第二遍：剩余部分写入 headOffset 位置（覆盖旧物品）
        items[headOffset] = toAdd.copy();
        headOffset = (headOffset + 1) % containerSize;
        setChanged();
    }

    // ========== 持久化（Forge 1.20.1 API） ==========

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        tag.putInt(TAG_CONTAINER_SIZE, containerSize);
        tag.putInt(TAG_HEAD_OFFSET, headOffset);

        ListTag itemsList = new ListTag();
        for (int i = 0; i < containerSize; i++) {
            ItemStack stack = items[i];
            if (!stack.isEmpty()) {
                CompoundTag slotTag = new CompoundTag();
                slotTag.putInt(TAG_SLOT, i);
                stack.save(slotTag);
                itemsList.add(slotTag);
            }
        }
        tag.put(TAG_ITEMS, itemsList);
        return tag;
    }

    /**
     * 从 NBT 加载，支持新旧两种格式：
     *   - 新格式：containerSize + headOffset + items
     *   - 旧格式（page-based）：pageCount + pages
     */
    public static WishSavedData load(CompoundTag tag) {
        if (tag.contains(TAG_CONTAINER_SIZE)) {
            return loadNewFormat(tag);
        } else {
            return loadLegacyFormat(tag);
        }
    }

    private static WishSavedData loadNewFormat(CompoundTag tag) {
        int size = tag.getInt(TAG_CONTAINER_SIZE);
        WishSavedData data = new WishSavedData(size);
        data.headOffset = tag.getInt(TAG_HEAD_OFFSET);

        ListTag itemsList = tag.getList(TAG_ITEMS, Tag.TAG_COMPOUND);
        for (Tag raw : itemsList) {
            CompoundTag slotTag = (CompoundTag) raw;
            int slot = slotTag.getInt(TAG_SLOT);
            if (slot >= 0 && slot < size) {
                data.items[slot] = ItemStack.of(slotTag);
            }
        }
        return data;
    }

    /**
     * 从旧格式（page-based）迁移到新格式。
     * 如果物品数超过 containerSize，只保留最新的 containerSize 个。
     */
    private static WishSavedData loadLegacyFormat(CompoundTag tag) {
        int size = Math.max(Config.containerSize, 1);
        WishSavedData data = new WishSavedData(size);

        int loadedPageCount = tag.getInt(TAG_LEGACY_PAGE_COUNT);
        ListTag pagesList = tag.getList(TAG_LEGACY_PAGES, Tag.TAG_LIST);

        // 收集所有旧物品（按页面顺序）
        List<ItemStack> legacyItems = new ArrayList<>();
        for (int p = 0; p < pagesList.size() && p < loadedPageCount; p++) {
            ListTag itemsList = pagesList.getList(p);
            List<ItemStack> pageItems = new ArrayList<>();
            for (Tag raw : itemsList) {
                CompoundTag slotTag = (CompoundTag) raw;
                int slot = slotTag.getInt("slot");
                while (pageItems.size() <= slot) {
                    pageItems.add(ItemStack.EMPTY);
                }
                pageItems.set(slot, ItemStack.of(slotTag));
            }
            legacyItems.addAll(pageItems);
        }

        // 过滤掉空物品
        List<ItemStack> nonEmpty = new ArrayList<>();
        for (ItemStack s : legacyItems) {
            if (!s.isEmpty()) nonEmpty.add(s);
        }

        // 如果物品数超过容器大小，只保留最新的
        int start = Math.max(0, nonEmpty.size() - size);
        List<ItemStack> toKeep = nonEmpty.subList(start, nonEmpty.size());

        // 按插入顺序写入物理数组（从索引 0 开始）
        for (int i = 0; i < toKeep.size() && i < size; i++) {
            data.items[i] = toKeep.get(i).copy();
        }
        data.headOffset = toKeep.size() % size;

        return data;
    }

    /**
     * 根据新的 size 调整容器大小。
     * 保留尽可能多的最新物品，丢弃最旧的溢出物品。
     */
    public void resize(int newSize) {
        if (newSize == containerSize) return;
        newSize = Math.max(newSize, 1);

        // 按最新在前顺序收集所有非空物品
        List<ItemStack> ordered = new ArrayList<>();
        for (int i = 0; i < containerSize; i++) {
            ItemStack stack = getItem(i);
            if (!stack.isEmpty()) ordered.add(stack);
        }

        // 重建数组
        ItemStack[] newItems = new ItemStack[newSize];
        for (int i = 0; i < newSize; i++) {
            newItems[i] = ItemStack.EMPTY;
        }

        // 保留最新的 newSize 个物品
        int keep = Math.min(ordered.size(), newSize);
        for (int i = 0; i < keep; i++) {
            newItems[i] = ordered.get(i);
        }

        this.items = newItems;
        this.containerSize = newSize;
        this.headOffset = keep % newSize;
        setChanged();
    }

    // ========== 工厂方法（Forge 1.20.1 API） ==========

    public static WishSavedData get(Level level) {
        if (level.getServer() == null) return null;
        ServerLevel overworld = level.getServer().overworld();
        WishSavedData data = overworld.getDataStorage().computeIfAbsent(
                (CompoundTag tag) -> load(tag),
                (Supplier<WishSavedData>) () -> new WishSavedData(Config.containerSize),
                DATA_NAME
        );
        // 如果配置中的容器大小发生变化，进行 resize
        if (data.containerSize != Config.containerSize) {
            data.resize(Config.containerSize);
        }
        return data;
    }

    // ========== 工具方法 ==========

    /** 获取当前 headOffset（物理写入位置） */
    public int getHeadOffset() {
        return headOffset;
    }

    /** 物理数组（用于序列化 / 调试） */
    public ItemStack[] getRawItems() {
        return items;
    }
}
