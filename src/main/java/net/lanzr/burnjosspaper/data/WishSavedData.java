package net.lanzr.burnjosspaper.data;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 环形缓存库存： 环形缓冲区 + Container 实现
 *
 * 物理存储为固定大小的 ItemStack 数组，
 * 通过 headOffset 实现逻辑视图：
 *   - 新增物品写入 headOffset 位置后 headOffset 前进
 *   - 逻辑索引 0 = 最新物品（物理 headOffset-1）
 *   - 逻辑索引 1 = 第二新（物理 headOffset-2）
 */
public class WishSavedData implements Container {

    private static WishSavedData instance;

    private final ItemStack[] items;

    private int headOffset;

    private final int containerSize;

    private WishSavedData(int containerSize) {
        this.containerSize = Math.max(containerSize, 1);
        this.items = new ItemStack[this.containerSize];
        this.headOffset = 0;
        clearContent();
    }

    public static void create(int size) {
        instance = new WishSavedData(size);
    }

    public static WishSavedData get() {
        return instance;
    }


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

    //  逻辑索引 0 = 最新（headOffset-1）
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

    @Override
    public void setItem(int logicalIndex, ItemStack stack) {
        if (logicalIndex < 0 || logicalIndex >= containerSize) return;
        int phys = logicalToPhysical(logicalIndex);
        items[phys] = stack.copy();
    }

    @Override
    public void setChanged() {
        // 不持久化
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
    }


    private int logicalToPhysical(int logicalIndex) {
        int raw = headOffset - 1 - logicalIndex;
        int mod = raw % containerSize;
        return mod >= 0 ? mod : mod + containerSize;
    }

    //  添加新物品。先找同类堆叠，剩余部分写入 headOffset 后前进。

    public void addItem(ItemStack stack) {
        if (stack.isEmpty()) return;

        ItemStack toAdd = stack.copy();

        // 同类堆叠
        for (int p = 0; p < containerSize; p++) {
            ItemStack existing = items[p];
            if (!existing.isEmpty() && ItemStack.isSameItemSameTags(existing, toAdd)) {
                int maxStack = Math.min(existing.getMaxStackSize(), toAdd.getMaxStackSize());
                int space = maxStack - existing.getCount();
                if (space > 0) {
                    int toMove = Math.min(space, toAdd.getCount());
                    existing.grow(toMove);
                    toAdd.shrink(toMove);
                    if (toAdd.isEmpty()) return;
                }
            }
        }

        //  写入 headOffset（满则覆盖最旧）
        items[headOffset] = toAdd.copy();
        headOffset = (headOffset + 1) % containerSize;
    }

    public int getHeadOffset() {
        return headOffset;
    }
}
