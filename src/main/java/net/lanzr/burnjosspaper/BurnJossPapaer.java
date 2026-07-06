package net.lanzr.burnjosspaper;

import net.lanzr.burnjosspaper.menu.PaginationContainer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.BlockPos;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(BurnJossPapaer.MODID)
public class BurnJossPapaer {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "burnjosspaper";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    /** 打火石使用记录：位置 → 时间戳 */
    private static final Map<BlockPos, Long> flintAndSteelFires = new HashMap<>();
    /** 记录有效期（毫秒） */
    private static final long FIRE_RECORD_TTL = 10_000L;
    public BurnJossPapaer() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        MinecraftForge.EVENT_BUS.register(this);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    // ──────────────────────────────────────────────
    //  打火石追踪 — 记录玩家用打火石点火的位置
    // ──────────────────────────────────────────────

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getItemStack().getItem() == Items.FLINT_AND_STEEL) {
            BlockPos pos = event.getPos();
            flintAndSteelFires.put(pos, System.currentTimeMillis());
            LOGGER.debug("Recorded flint-and-steel fire at {}", pos);
        }
    }

    /**
     * 检查物品烧毁位置是否在打火石点火的范围内（1格内且10秒内）
     */
    private boolean isFlintAndSteelFire(BlockPos itemPos) {
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
            // 检查是否在1格范围内（打火石放置的火通常在点击位置或其上方/侧面）
            if (Math.abs(itemPos.getX() - firePos.getX()) <= 1
                    && Math.abs(itemPos.getY() - firePos.getY()) <= 1
                    && Math.abs(itemPos.getZ() - firePos.getZ()) <= 1) {
                return true;
            }
        }
        return false;
    }

    // ──────────────────────────────────────────────
    //  Fire capture — save burned items to wish inventory
    // ──────────────────────────────────────────────

    @SubscribeEvent
    public void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        Entity entity = event.getEntity();
        if (!(entity instanceof ItemEntity itemEntity)) return;
//        LOGGER.info("remove reason"+ entity.getRemovalReason());
//        if (entity.getRemovalReason() != Entity.RemovalReason.KILLED) return;

//        LOGGER.info("remove reason"+ entity.isInLava()+" / " + entity.isOnFire());

        // 只处理着火的物品（不处理岩浆中的）
        if (!entity.isOnFire()) return;

        // 检查是否在打火石点火的范围内（1格内且10秒内）
        if (!isFlintAndSteelFire(entity.blockPosition())) {
//            LOGGER.info("Fire not from flint-and-steel, skipped");
            return;
        }

        ItemStack stack = itemEntity.getItem();
        if (stack.isEmpty()) return;

        // 黑名单检查
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId != null && Config.blackListItem.contains(itemId.toString())) {
//            LOGGER.info("Blacklisted item {} burned, skipped", itemId);
            return;
        }

//        LOGGER.info("try get wish data");
        WishSavedData data = WishSavedData.get();
        if (data == null) return;

        data.addItem(stack.copy());
//        LOGGER.info("Captured burning item: {} x{} → wish inventory",
//                stack.getHoverName().getString(), stack.getCount());
    }

    // ──────────────────────────────────────────────
    //  /wish [page]  command — opens PaginationContainer
    // ──────────────────────────────────────────────

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("beg")
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    int page = IntegerArgumentType.getInteger(ctx, "page");
                                    var source = ctx.getSource();
                                    var player = source.getPlayer();
                                    if (player == null) {
                                        source.sendFailure(Component.literal("This command can only be used by players."));
                                        return 0;
                                    }

                                    WishSavedData data = WishSavedData.get();
                                    if (data == null) {
                                        source.sendFailure(Component.literal("Wish inventory not initialized."));
                                        return 0;
                                    }

                                    player.openMenu(new SimpleMenuProvider(
                                            (id, inv, p) -> new PaginationContainer(id, inv, data, page, true),
                                            Component.literal("§a阴间供品")
                                    ));
//                                    source.sendSuccess(() -> Component.literal(
//                                            "Opening wish inventory at page " + page + "..."), false);
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .executes(ctx -> {
                            // No argument → open page 1
                            var source = ctx.getSource();
                            var player = source.getPlayer();
                            if (player == null) {
                                source.sendFailure(Component.literal("This command can only be used by players."));
                                return 0;
                            }

                            WishSavedData data = WishSavedData.get();
                            if (data == null) {
                                source.sendFailure(Component.literal("Wish inventory not initialized."));
                                return 0;
                            }

                            player.openMenu(new SimpleMenuProvider(
                                    (id, inv, p) -> new PaginationContainer(id, inv, data, 1, true),
                                    Component.literal("§a阴间供品")
                            ));
//                            source.sendSuccess(() -> Component.literal("Opening wish inventory..."), false);
                            return Command.SINGLE_SUCCESS;
                        })
        );
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // 每次服务器启动创建新的空容器
        WishSavedData.create(Config.containerSize);
        LOGGER.info("Wish mod initialized — public container size: {}", Config.containerSize);
    }
}
