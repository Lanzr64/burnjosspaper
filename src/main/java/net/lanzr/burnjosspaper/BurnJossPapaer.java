package net.lanzr.burnjosspaper;

import net.lanzr.burnjosspaper.menu.PaginationContainer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
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
import net.minecraftforge.registries.ForgeRegistries;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(BurnJossPapaer.MODID)
public class BurnJossPapaer {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "burnjosspaper";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    public BurnJossPapaer(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();


        MinecraftForge.EVENT_BUS.register(this);


        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    // ──────────────────────────────────────────────
    //  Fire capture — save burned items to wish inventory
    // ──────────────────────────────────────────────

    @SubscribeEvent
    public void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        Entity entity = event.getEntity();
        if (!(entity instanceof ItemEntity itemEntity)) return;
        LOGGER.info("remove reason"+ entity.getRemovalReason());
//        if (entity.getRemovalReason() != Entity.RemovalReason.KILLED) return;

        LOGGER.info("remove reason"+ entity.isInLava()+" / " + entity.isOnFire());

        // Check if the item was on fire or in lava when it died
        if (!entity.isInLava() && !entity.isOnFire()) return;

        ItemStack stack = itemEntity.getItem();
        if (stack.isEmpty()) return;

        // 黑名单检查
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId != null && Config.blackListItem.contains(itemId.toString())) {
            LOGGER.info("Blacklisted item {} burned, skipped", itemId);
            return;
        }

        LOGGER.info("try get wish data");
        WishSavedData data = WishSavedData.get();
        if (data == null) return;

        data.addItem(stack.copy());
        LOGGER.info("Captured burning item: {} x{} → wish inventory",
                stack.getHoverName().getString(), stack.getCount());
    }

    // ──────────────────────────────────────────────
    //  /wish [page]  command — opens PaginationContainer
    // ──────────────────────────────────────────────

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("wish")
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
                                            (id, inv, p) -> new PaginationContainer(id, inv, data, page),
                                            Component.literal("Wish Inventory")
                                    ));
                                    source.sendSuccess(() -> Component.literal(
                                            "Opening wish inventory at page " + page + "..."), false);
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
                                    (id, inv, p) -> new PaginationContainer(id, inv, data),
                                    Component.literal("Wish Inventory")
                            ));
                            source.sendSuccess(() -> Component.literal("Opening wish inventory..."), false);
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
