package com.example.superguard;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SecurityEventHandler {

    private final Map<UUID, Vec3> lastValidPos = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> airTicks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastInteractTime = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastBreakTime = new ConcurrentHashMap<>();
    private final Map<UUID, BlockPos> openContainerPos = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> warnCount = new ConcurrentHashMap<>();

    private final Map<UUID, Long> lastChatTime = new ConcurrentHashMap<>();

    private final Map<UUID, Integer> oreCounter = new ConcurrentHashMap<>();
    private final Map<UUID, Long> oreWindowStart = new ConcurrentHashMap<>();
    private final Map<UUID, BlockPos> lastMinedOrePos = new ConcurrentHashMap<>();

    private int tickCounter = 0;
    private int clearElapsedTicks = 0;

    private void sendActionBarAll(MinecraftServer server, Component message) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.displayClientMessage(message, true);
        }
    }

    private void warnPlayerAndStaff(ServerPlayer player, String reason) {
        int count = warnCount.merge(player.getUUID(), 1, Integer::sum);
        player.sendSystemMessage(Component.literal("[SuperGuard TOTAL] ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                .append(Component.literal("Блокировка (" + count + "): " + reason).withStyle(ChatFormatting.YELLOW)));

        if (player.getServer() != null) {
            Component staffMsg = Component.literal("[SuperGuard ALERT] ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                    .append(Component.literal(player.getName().getString() + " -> " + reason + " (Нарушений: " + count + ")").withStyle(ChatFormatting.GRAY));
            for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
                if (p.hasPermissions(2) || CommandHandler.isSuperAdmin(p.getName().getString().toLowerCase())) {
                    if (!p.getUUID().equals(player.getUUID())) {
                        p.sendSystemMessage(staffMsg);
                    }
                }
            }
        }
        SuperGuardMod.LOGGER.warn("[ALERT] Player {} -> {}", player.getName().getString(), reason);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onServerChat(ServerChatEvent event) {
        if (!SuperGuardConfig.ENABLE_MOD.get()) return;
        ServerPlayer player = event.getPlayer();
        String name = player.getName().getString();

        if (SuperGuardConfig.ENABLE_AUTH.get() && !CommandHandler.isAuthenticated(name)) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("⚠ Вы не авторизованы! Используйте /login <пароль> или /register <пароль> <пароль>").withStyle(ChatFormatting.RED));
            return;
        }

        if (SuperGuardConfig.ENABLE_CHAT_GUARD.get() && !player.hasPermissions(2) && !CommandHandler.isSuperAdmin(name.toLowerCase())) {
            long now = System.currentTimeMillis();
            long last = lastChatTime.getOrDefault(player.getUUID(), 0L);
            if (now - last < 1500) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("⚠ Пожалуйста, не спамьте в чат!").withStyle(ChatFormatting.RED));
                return;
            }
            lastChatTime.put(player.getUUID(), now);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onCommand(CommandEvent event) {
        if (!SuperGuardConfig.ENABLE_MOD.get()) return;
        if (event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer player) {
            String name = player.getName().getString().toLowerCase();
            if (SuperGuardConfig.ENABLE_AUTH.get() && !CommandHandler.isAuthenticated(name)) {
                String raw = event.getParseResults().getReader().getString().toLowerCase().trim();
                if (!raw.startsWith("login") && !raw.startsWith("register")) {
                    event.setCanceled(true);
                    player.sendSystemMessage(Component.literal("⚠ Авторизуйтесь перед использованием команд!").withStyle(ChatFormatting.RED));
                    return;
                }
            }

            if (SuperGuardConfig.BLOCK_CHEAT_COMMANDS.get() && !player.hasPermissions(2) && !CommandHandler.isSuperAdmin(name)) {
                String raw = event.getParseResults().getReader().getString().toLowerCase().trim();
                if (raw.startsWith("/")) raw = raw.substring(1);
                String cmd = raw.split(" ")[0];
                if (cmd.contains(":")) cmd = cmd.split(":")[1];

                if (SuperGuardConfig.BLOCKED_COMMANDS.get().contains(cmd)) {
                    event.setCanceled(true);
                    warnPlayerAndStaff(player, "Попытка использовать запрещённую команду: /" + cmd);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!SuperGuardConfig.ENABLE_MOD.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        if (SuperGuardConfig.ENABLE_AUTH.get() && !CommandHandler.isAuthenticated(player.getName().getString())) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("⚠ Авторизуйтесь для взаимодействия! Используйте /login").withStyle(ChatFormatting.RED));
            return;
        }

        if (!SuperGuardConfig.ENABLE_TOTAL_ANTI_DUPE.get()) return;

        long now = System.currentTimeMillis();
        BlockPos pos = event.getPos();
        BlockState state = event.getLevel().getBlockState(pos);
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String blockName = blockId.toString();

        long minDelay = SuperGuardConfig.DUPE_PRONE_CREATE_BLOCKS.get().contains(blockName) ? 120L : 45L;
        long prev = lastInteractTime.getOrDefault(player.getUUID(), 0L);
        if (now - prev < minDelay) {
            event.setCanceled(true);
            return;
        }
        lastInteractTime.put(player.getUUID(), now);

        if (SuperGuardConfig.BLOCK_DRAIN_AND_CONTRAPTION_EXPLOITS.get() && SuperGuardConfig.DUPE_PRONE_CREATE_BLOCKS.get().contains(blockName)) {
            AABB checkBox = new AABB(pos).inflate(2.5);
            List<Entity> nearbyEntities = event.getLevel().getEntities(player, checkBox, e ->
                    e instanceof AbstractContraptionEntity
                            || BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().startsWith("createaeronautics:")
                            || BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().startsWith("sable:")
                            || BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().startsWith("valkyrienskies:")
            );
            if (!nearbyEntities.isEmpty()) {
                event.setCanceled(true);
                warnPlayerAndStaff(player, "Заблокирован эксплойт Create/Aeronautics возле движущейся конструкции (" + blockName + ")");
                return;
            }

            for (Map.Entry<UUID, BlockPos> entry : openContainerPos.entrySet()) {
                if (!entry.getKey().equals(player.getUUID()) && entry.getValue().equals(pos)) {
                    event.setCanceled(true);
                    warnPlayerAndStaff(player, "Попытка одновременного дюпа на блоке " + blockName);
                    return;
                }
            }
        }

        ItemStack stack = event.getItemStack();
        if (!stack.isEmpty()) {
            if (stack.getCount() > stack.getMaxStackSize() || stack.getCount() <= 0) {
                stack.setCount(Math.max(1, Math.min(stack.getCount(), stack.getMaxStackSize())));
                event.setCanceled(true);
                warnPlayerAndStaff(player, "Нелегальный размер стака (Anti-Dupe)");
                return;
            }
        }

        double distSq = player.distanceToSqr(Vec3.atCenterOf(pos));
        if (!player.isCreative() && distSq > 49.0) {
            event.setCanceled(true);
            warnPlayerAndStaff(player, "Взаимодействие с блоком сквозь стены/издалека (Freecam / Reach)");
            return;
        }

        openContainerPos.put(player.getUUID(), pos);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPistonPre(PistonEvent.Pre event) {
        if (!SuperGuardConfig.ENABLE_MOD.get() || !SuperGuardConfig.BLOCK_DRAIN_AND_CONTRAPTION_EXPLOITS.get()) return;
        BlockPos targetPos = event.getFaceOffsetPos();
        BlockState state = event.getLevel().getBlockState(targetPos);
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if (SuperGuardConfig.DUPE_PRONE_CREATE_BLOCKS.get().contains(id)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!SuperGuardConfig.ENABLE_MOD.get()) return;
        BlockPos pos = event.getPos();
        BlockState state = event.getState();

        if (event.getPlayer() instanceof ServerPlayer player) {
            if (SuperGuardConfig.ENABLE_AUTH.get() && !CommandHandler.isAuthenticated(player.getName().getString())) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("⚠ Авторизуйтесь для ломания блоков! Используйте /login").withStyle(ChatFormatting.RED));
                return;
            }
        }

        if (SuperGuardConfig.ENABLE_TOTAL_ANTI_DUPE.get()) {
            for (Map.Entry<UUID, BlockPos> entry : openContainerPos.entrySet()) {
                if (entry.getValue().equals(pos)) {
                    if (event.getLevel() instanceof ServerLevel sl) {
                        Player target = sl.getPlayerByUUID(entry.getKey());
                        if (target instanceof ServerPlayer sp) {
                            sp.closeContainer();
                            warnPlayerAndStaff(sp, "Прервана попытка дюпа при поломке активного блока/осушителя");
                        }
                    }
                    openContainerPos.remove(entry.getKey());
                }
            }
        }

        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        if (SuperGuardConfig.ENABLE_ANTI_CHEAT.get() && !player.isCreative()) {
            double distSq = player.distanceToSqr(Vec3.atCenterOf(pos));
            if (distSq > 49.0) {
                event.setCanceled(true);
                warnPlayerAndStaff(player, "Ломание блока за пределами дистанции (Nuker / Freecam)");
                return;
            }

            long now = System.currentTimeMillis();
            long lastBreak = lastBreakTime.getOrDefault(player.getUUID(), 0L);
            if (now - lastBreak < 35 && state.getDestroySpeed(event.getLevel(), pos) > 0.5f) {
                event.setCanceled(true);
                warnPlayerAndStaff(player, "Аномально быстрое ломание блоков (FastBreak / Nuker)");
                return;
            }
            lastBreakTime.put(player.getUUID(), now);
        }

        if (SuperGuardConfig.ENABLE_ANTI_XRAY_HEURISTICS.get() && !player.isCreative()) {
            if (isValuableOre(state)) {
                long now = System.currentTimeMillis();
                long windowStart = oreWindowStart.getOrDefault(player.getUUID(), now);
                if (now - windowStart > 300_000L) {
                    oreWindowStart.put(player.getUUID(), now);
                    oreCounter.put(player.getUUID(), 0);
                }

                boolean completelyHidden = true;
                for (Direction dir : Direction.values()) {
                    BlockState adj = event.getLevel().getBlockState(pos.relative(dir));
                    if (adj.isAir() || adj.is(Blocks.WATER) || adj.is(Blocks.LAVA) || adj.is(Blocks.CAVE_AIR)) {
                        completelyHidden = false;
                        break;
                    }
                }

                if (completelyHidden) {
                    int mined = oreCounter.merge(player.getUUID(), 1, Integer::sum);
                    BlockPos prevOre = lastMinedOrePos.get(player.getUUID());
                    lastMinedOrePos.put(player.getUUID(), pos);

                    if (prevOre != null && prevOre.distSqr(pos) > 64.0 && mined >= 10 && mined % 5 == 0) {
                        warnPlayerAndStaff(player, "Подозрительный поиск скрытых руд вслепую (" + mined + " руд за 5 мин) [Anti-XRay]");
                    }

                    if (mined > SuperGuardConfig.MAX_ORES_PER_5_MINUTES.get()) {
                        event.setCanceled(true);
                        warnPlayerAndStaff(player, "Превышен лимит добычи скрытых ценных руд! [Anti-XRay Блокировка]");
                    }
                }
            }
        }
    }

    private boolean isValuableOre(BlockState state) {
        return state.is(BlockTags.DIAMOND_ORES)
                || state.is(BlockTags.EMERALD_ORES)
                || state.is(BlockTags.GOLD_ORES)
                || state.is(Blocks.ANCIENT_DEBRIS);
    }

    @SubscribeEvent
    public void onContainerClose(PlayerContainerEvent.Close event) {
        openContainerPos.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onItemToss(ItemTossEvent event) {
        if (!SuperGuardConfig.ENABLE_MOD.get() || !SuperGuardConfig.ENABLE_TOTAL_ANTI_DUPE.get()) return;
        if (event.getPlayer() instanceof ServerPlayer player) {
            ItemStack stack = event.getEntity().getItem();
            if (stack.getCount() > stack.getMaxStackSize() || stack.getCount() <= 0) {
                event.setCanceled(true);
                warnPlayerAndStaff(player, "Попытка выбросить некорректный/дюпнутый стак");
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (!SuperGuardConfig.ENABLE_MOD.get() || event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        Entity entity = event.getEntity();

        if (SuperGuardConfig.ENABLE_TOTAL_ANTI_DUPE.get()) {
            Entity existing = serverLevel.getEntity(entity.getUUID());
            if (existing != null && existing != entity) {
                event.setCanceled(true);
                SuperGuardMod.LOGGER.warn("[Total Anti-Dupe] Blocked cloned entity UUID: {}", entity.getUUID());
                return;
            }
        }

        if (SuperGuardConfig.ENABLE_LAG_SHIELD.get()) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            String fullId = id.toString();
            boolean isContraptionOrShip = entity instanceof AbstractContraptionEntity
                    || fullId.startsWith("createaeronautics:")
                    || fullId.startsWith("sable:")
                    || fullId.startsWith("valkyrienskies:");

            if (isContraptionOrShip) {
                ChunkPos cp = new ChunkPos(entity.blockPosition());
                AABB chunkBox = new AABB(cp.getMinBlockX(), serverLevel.getMinBuildHeight(), cp.getMinBlockZ(),
                        cp.getMaxBlockX() + 1, serverLevel.getMaxBuildHeight(), cp.getMaxBlockZ() + 1);
                List<Entity> nearby = serverLevel.getEntities(entity, chunkBox, e ->
                        e instanceof AbstractContraptionEntity
                                || BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().startsWith("createaeronautics:")
                                || BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().startsWith("sable:")
                                || BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().startsWith("valkyrienskies:")
                );

                int limit = SuperGuardConfig.MAX_CONTRAPTIONS_PER_CHUNK.get();
                if (nearby.size() >= limit) {
                    entity.noPhysics = true;
                    entity.setNoGravity(true);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttack(AttackEntityEvent event) {
        if (!SuperGuardConfig.ENABLE_MOD.get() || !SuperGuardConfig.ENABLE_ANTI_CHEAT.get()) return;
        if (event.getEntity() instanceof ServerPlayer player && !player.isCreative()) {
            double distSq = player.distanceToSqr(event.getTarget());
            if (distSq > 36.0) {
                event.setCanceled(true);
                warnPlayerAndStaff(player, "Атака за пределами допустимой дистанции (KillAura / Reach)");
            }
        }
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!SuperGuardConfig.ENABLE_MOD.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        if (CommandHandler.isVanished(player.getName().getString())) {
            player.setInvisible(true);
        }

        if (SuperGuardConfig.ENABLE_AUTH.get() && !CommandHandler.isAuthenticated(player.getName().getString())) {
            player.setDeltaMovement(0, player.getDeltaMovement().y, 0);
            if (player.tickCount % 200 == 0) {
                player.sendSystemMessage(Component.literal("⚠ Авторизуйтесь! Введите /login <пароль> или /register <пароль> <пароль>").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            }
            return;
        }

        if (SuperGuardConfig.ENABLE_TOTAL_ANTI_DUPE.get()) {
            BlockPos cPos = openContainerPos.get(player.getUUID());
            if (cPos != null) {
                BlockState bs = player.level().getBlockState(cPos);
                if (bs.isAir() || player.distanceToSqr(Vec3.atCenterOf(cPos)) > 64.0) {
                    player.closeContainer();
                    openContainerPos.remove(player.getUUID());
                    warnPlayerAndStaff(player, "Взаимодействие прервано: блок исчез или сдвинут (Create/Aeronautics Anti-Dupe)");
                }
            }
        }

        if (SuperGuardConfig.ENABLE_ANTI_CHEAT.get()) {
            if (player.isCreative() || player.isSpectator() || player.getAbilities().mayfly || player.isPassenger() || player.isFallFlying() || player.isInWater()) {
                lastValidPos.put(player.getUUID(), player.position());
                airTicks.put(player.getUUID(), 0);
                return;
            }

            Vec3 current = player.position();
            Vec3 prev = lastValidPos.get(player.getUUID());

            if (prev != null) {
                double dx = current.x - prev.x;
                double dz = current.z - prev.z;
                double horizSpeed = Math.sqrt(dx * dx + dz * dz);

                if (horizSpeed > 1.45 && player.hurtTime == 0) {
                    player.teleportTo(prev.x, prev.y, prev.z);
                    warnPlayerAndStaff(player, "Аномальная скорость передвижения (SpeedHack / Timer)");
                    return;
                }

                if (!player.onGround() && current.y >= prev.y && !player.onClimbable()) {
                    int air = airTicks.merge(player.getUUID(), 1, Integer::sum);
                    if (air > 35) {
                        player.teleportTo(prev.x, prev.y, prev.z);
                        airTicks.put(player.getUUID(), 0);
                        warnPlayerAndStaff(player, "Нелегальный полет или зависание в воздухе (FlyHack)");
                        return;
                    }
                } else {
                    airTicks.put(player.getUUID(), 0);
                }
            }
            lastValidPos.put(player.getUUID(), current);
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (!SuperGuardConfig.ENABLE_MOD.get()) return;
        MinecraftServer server = event.getServer();

        if (SuperGuardConfig.ENABLE_LAG_SHIELD.get()) {
            tickCounter++;
            if (tickCounter % 100 == 0) {
                for (ServerLevel level : server.getAllLevels()) {
                    Map<ChunkPos, Integer> itemCounts = new ConcurrentHashMap<>();
                    for (Entity entity : level.getAllEntities()) {
                        if (entity instanceof ItemEntity item) {
                            ChunkPos cp = new ChunkPos(item.blockPosition());
                            int count = itemCounts.merge(cp, 1, Integer::sum);
                            if (count > SuperGuardConfig.MAX_DROPPED_ITEMS_PER_CHUNK.get()) {
                                ItemStack st = item.getItem();
                                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(st.getItem());
                                if (SuperGuardConfig.JUNK_ITEMS_TO_CLEAR.get().contains(itemId.toString())) {
                                    item.discard();
                                }
                            }
                        }
                    }
                }
            }
        }

        if (SuperGuardConfig.ENABLE_SCHEDULED_ITEM_CLEAR.get()) {
            int intervalTicks = SuperGuardConfig.CLEAR_INTERVAL_MINUTES.get() * 60 * 20;
            clearElapsedTicks++;
            int remainingTicks = intervalTicks - clearElapsedTicks;

            if (remainingTicks == 6000) {
                sendActionBarAll(server, Component.literal("⚠ Внимание! Очистка мусора на земле через 5 минут!")
                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
            } else if (remainingTicks == 1200) {
                sendActionBarAll(server, Component.literal("⚠ Очистка мусора через 1 минуту! Ценные вещи не пропадут.")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            } else if (remainingTicks == 600) {
                sendActionBarAll(server, Component.literal("Tag: Очистка мусора через 30 секунд!")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            } else if (remainingTicks <= 200 && remainingTicks > 0 && remainingTicks % 20 == 0) {
                int sec = remainingTicks / 20;
                sendActionBarAll(server, Component.literal("⚠ Очистка мусора через " + sec + " сек!")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            } else if (remainingTicks <= 0) {
                int removedJunkCount = 0;
                List<String> junkList = (List<String>) SuperGuardConfig.JUNK_ITEMS_TO_CLEAR.get();

                for (ServerLevel level : server.getAllLevels()) {
                    List<ItemEntity> toNewRemove = new ArrayList<>();
                    for (Entity entity : level.getAllEntities()) {
                        if (entity instanceof ItemEntity item) {
                            ItemStack st = item.getItem();
                            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(st.getItem());
                            if (junkList.contains(itemId.toString())) {
                                removedJunkCount += st.getCount();
                                toNewRemove.add(item);
                            }
                        }
                    }
                    for (ItemEntity item : toNewRemove) {
                        item.discard();
                    }
                }

                sendActionBarAll(server, Component.literal("✔ Очистка завершена! Удалено мусора: " + removedJunkCount + " (ценное сохранено)")
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
                SuperGuardMod.LOGGER.info("[Auto-Clear] Cleanup completed. Removed {} junk items, valuable items preserved.", removedJunkCount);
                clearElapsedTicks = 0;
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        lastValidPos.remove(id);
        airTicks.remove(id);
        lastInteractTime.remove(id);
        lastBreakTime.remove(id);
        openContainerPos.remove(id);
        warnCount.remove(id);
        oreCounter.remove(id);
        oreWindowStart.remove(id);
        lastMinedOrePos.remove(id);
    }
}
