package net.lanzr.burnjosspaper.event;


import com.mojang.logging.LogUtils;
import net.lanzr.burnjosspaper.BurnJossPapaer;
import net.lanzr.burnjosspaper.Config;
import net.lanzr.burnjosspaper.data.WishSavedData;
import net.lanzr.burnjosspaper.command.BegCommand;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;

@Mod.EventBusSubscriber
public class ModEvent {
    public static final Logger LOGGER = BurnJossPapaer.LOGGER;
    private static final Map<BlockPos, Long> flintAndSteelFires = new HashMap<>();
    private static final long FIRE_RECORD_TTL = 10_000L;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        BegCommand.register(event);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getItemStack().getItem() == Items.FLINT_AND_STEEL) {
            BlockPos pos = event.getPos();
            flintAndSteelFires.put(pos, System.currentTimeMillis());
//            LOGGER.debug("Recorded flint-and-steel fire at {}", pos);
        }
    }
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        // 每次服务器启动创建新的空容器
        WishSavedData.create(Config.CONTAINER_SIZE.get());
//        LOGGER.info("Wish mod initialized — public container size: {}", Config.containerSize);
    }
//    处理被烧毁的物品
    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        Entity entity = event.getEntity();
        if (!(entity instanceof ItemEntity itemEntity)) return;

        if (!entity.isOnFire()) return;

        // 检查是否在打火石点火的范围内（1格内且10秒内）
        if (!isValidFire(entity.blockPosition())) {
//            LOGGER.info("Fire not from flint-and-steel, skipped");
            return;
        }

        ItemStack stack = itemEntity.getItem();
        if (stack.isEmpty()) return;

        // 黑名单检查
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId != null && Config.BLACK_LIST_ITEM.get().contains(itemId.toString())) {
//            LOGGER.info("Blacklisted item {} burned, skipped", itemId);
            return;
        }

        WishSavedData data = WishSavedData.get();
        if (data == null) return;

        data.addItem(stack.copy());
//        LOGGER.info("Captured burning item: {} x{} → wish inventory",
//                stack.getHoverName().getString(), stack.getCount());
    }

    private static boolean isValidFire(BlockPos itemPos) {
        long now = System.currentTimeMillis();
        // 清理过期记录并检查匹配
        var it = flintAndSteelFires.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            BlockPos firePos = entry.getKey();
            long timestamp = entry.getValue();
            if (now - timestamp > FIRE_RECORD_TTL) {
                it.remove();
                continue;
            }
            // 快速距离对比
            if (Math.abs(itemPos.getX() - firePos.getX()) <= 1
                    && Math.abs(itemPos.getY() - firePos.getY()) <= 1
                    && Math.abs(itemPos.getZ() - firePos.getZ()) <= 1) {
                return true;
            }
        }
        return false;
    }

}
