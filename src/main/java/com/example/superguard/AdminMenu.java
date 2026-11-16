package com.example.superguard;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.SimpleMenuProvider;

import java.util.List;

public class AdminMenu {

    public static void openDashboard(ServerPlayer admin) {
        SimpleContainer container = new SimpleContainer(54);

        ItemStack borderItem1 = new ItemStack(Items.PURPLE_STAINED_GLASS_PANE);
        borderItem1.set(DataComponents.CUSTOM_NAME, Component.literal("§8[ §5SuperGuard v9 §8]"));
        
        ItemStack borderItem2 = new ItemStack(Items.BLUE_STAINED_GLASS_PANE);
        borderItem2.set(DataComponents.CUSTOM_NAME, Component.literal("§8[ §9Server Security §8]"));

        for (int i = 0; i < 54; i++) {
            if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) {
                container.setItem(i, i % 2 == 0 ? borderItem1 : borderItem2);
            }
        }

        ItemStack infoItem = new ItemStack(Items.NETHER_STAR);
        infoItem.set(DataComponents.CUSTOM_NAME, Component.literal("§6★ ПАНЕЛЬ УПРАВЛЕНИЯ СЕРВЕРОМ"));
        infoItem.set(DataComponents.LORE, new ItemLore(List.of(
            Component.literal("§7Онлайн игроков: §a" + admin.getServer().getPlayerList().getPlayerCount()),
            Component.literal("§7Защита от дюпов и читов: §aАКТИВНА"),
            Component.literal("§eНажмите на игрока для точного управления")
        )));
        container.setItem(4, infoItem);

        int slot = 10;
        for (ServerPlayer player : admin.getServer().getPlayerList().getPlayers()) {
            if (slot == 17 || slot == 26 || slot == 35) slot += 2;
            if (slot >= 44) break;

            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            String nickname = player.getName().getString();
            
            head.set(DataComponents.CUSTOM_NAME, Component.literal("§e👤 Ник: §f§l" + nickname));
            
            boolean vanished = CommandHandler.isVanished(nickname.toLowerCase());
            boolean auth = CommandHandler.isAuthenticated(nickname);
            
            head.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("§7Никнейм: §b" + nickname),
                Component.literal("§7Авторизация: " + (auth ? "§aАвторизован" : "§cНе вошел")),
                Component.literal("§7Vanish: " + (vanished ? "§aВкл (Невидим)" : "§cВыкл")),
                Component.literal("§7Пинг: §e" + player.connection.latency() + " ms"),
                Component.literal("§7Координаты: §f" + player.blockPosition().toShortString()),
                Component.literal("§a▶ Нажмите для управления игроком")
            )));

            container.setItem(slot++, head);
        }

        admin.openMenu(new SimpleMenuProvider((id, inventory, player) -> 
            new ChestMenu(MenuType.GENERIC_9x6, id, inventory, container, 6), 
            Component.literal("§5§lSuperGuard ➔ Список Игроков")
        ));
    }

    public static void openPlayerMenu(ServerPlayer admin, String targetName) {
        SimpleContainer container = new SimpleContainer(27);
        
        ItemStack bg = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        bg.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
        for (int i = 0; i < 27; i++) container.setItem(i, bg);

        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        head.set(DataComponents.CUSTOM_NAME, Component.literal("§6Управление игроком: §e§l" + targetName));

        ItemStack invBtn = new ItemStack(Items.CHEST);
        invBtn.set(DataComponents.CUSTOM_NAME, Component.literal("§a📦 Просмотр инвентаря"));
        invBtn.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("§7Открыть инвентарь игрока " + targetName))));

        ItemStack tpBtn = new ItemStack(Items.ENDER_PEARL);
        tpBtn.set(DataComponents.CUSTOM_NAME, Component.literal("§b⚡ Телепорт к игроку"));

        ItemStack kickBtn = new ItemStack(Items.BARRIER);
        kickBtn.set(DataComponents.CUSTOM_NAME, Component.literal("§c👢 Кикнуть игрока"));

        ItemStack backBtn = new ItemStack(Items.ARROW);
        backBtn.set(DataComponents.CUSTOM_NAME, Component.literal("§e⬅ Назад к списку игроков"));

        container.setItem(4, head);
        container.setItem(10, invBtn);
        container.setItem(12, tpBtn);
        container.setItem(16, kickBtn);
        container.setItem(22, backBtn);

        admin.openMenu(new SimpleMenuProvider((id, inventory, player) -> 
            new ChestMenu(MenuType.GENERIC_9x3, id, inventory, container, 3), 
            Component.literal("§8Игрок: " + targetName)
        ));
    }
}
