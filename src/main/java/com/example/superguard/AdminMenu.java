package com.example.superguard;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.SimpleMenuProvider;

import java.util.List;

public class AdminMenu {

    public static void openDashboard(ServerPlayer admin) {
        SimpleContainer container = new SimpleContainer(54);

        // Стильная рамка из синих и фиолетовых стеклянных панелей
        ItemStack borderItem1 = new ItemStack(Items.PURPLE_STAINED_GLASS_PANE);
        borderItem1.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§8[ §5SuperGuard v7 §8]"));
        
        ItemStack borderItem2 = new ItemStack(Items.BLUE_STAINED_GLASS_PANE);
        borderItem2.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§8[ §9Server Security §8]"));

        for (int i = 0; i < 54; i++) {
            if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) {
                container.setItem(i, i % 2 == 0 ? borderItem1 : borderItem2);
            }
        }

        // Информационная звезда в центре верха
        ItemStack infoItem = new ItemStack(Items.NETHER_STAR);
        infoItem.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§6★ ПАНЕЛЬ УПРАВЛЕНИЯ СЕРВЕРОМ"));
        infoItem.set(net.minecraft.core.component.DataComponents.LORE, List.of(
            Component.literal("§7Онлайн игроков: §a" + admin.getServer().getPlayerList().getPlayerCount()),
            Component.literal("§7Защита от дюпов и читов: §aАКТИВНА"),
            Component.literal("§eНажмите на игрока для точного управления")
        ));
        container.setItem(4, infoItem);

        int slot = 10;
        for (ServerPlayer player : admin.getServer().getPlayerList().getPlayers()) {
            if (slot == 17 || slot == 26 || slot == 35) slot += 2;
            if (slot >= 44) break;

            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            String nickname = player.getName().getString();
            
            // Явно и крупно пишем никнейм игрока!
            head.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§e👤 Ник: §f§l" + nickname));
            
            boolean vanished = CommandHandler.isVanished(nickname.toLowerCase());
            boolean auth = CommandHandler.isAuthenticated(nickname);
            
            head.set(net.minecraft.core.component.DataComponents.LORE, List.of(
                Component.literal("§7Никнейм: §b" + nickname),
                Component.literal("§7Авторизация: " + (auth ? "§aАвторизован" : "§cНе вошел")),
                Component.literal("§7Vanish: " + (vanished ? "§aВкл (Невидим)" : "§cВыкл")),
                Component.literal("§7Пинг: §e" + player.connection.latency() + " ms"),
                Component.literal("§7Координаты: §f" + player.blockPosition().toShortString()),
                Component.literal("§a▶ Нажмите для управления игроком")
            ));

            container.setItem(slot++, head);
        }

        admin.openMenu(new SimpleMenuProvider((id, inventory, player) -> 
            new ChestMenu(MenuType.GENERIC_9X6, id, inventory, container, 6), 
            Component.literal("§5§lSuperGuard ➔ Список Игроков")
        ));
    }

    public static void openPlayerMenu(ServerPlayer admin, String targetName) {
        SimpleContainer container = new SimpleContainer(27);
        
        ItemStack bg = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        bg.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(" "));
        for (int i = 0; i < 27; i++) container.setItem(i, bg);

        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        head.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§6Управление игроком: §e§l" + targetName));

        // Кнопка просмотра инвентаря
        ItemStack invBtn = new ItemStack(Items.CHEST);
        invBtn.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§a📦 Просмотр инвентаря"));
        invBtn.set(net.minecraft.core.component.DataComponents.LORE, List.of(Component.literal("§7Открыть инвентарь игрока " + targetName)));

        // Кнопка телепортации
        ItemStack tpBtn = new ItemStack(Items.ENDER_PEARL);
        tpBtn.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§b⚡ Телепорт к игроку"));

        // Кнопка кика
        ItemStack kickBtn = new ItemStack(Items.BARRIER);
        kickBtn.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§c👢 Кикнуть игрока"));

        // Кнопка возврата назад
        ItemStack backBtn = new ItemStack(Items.ARROW);
        backBtn.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§e⬅ Назад к списку игроков"));

        container.setItem(4, head);
        container.setItem(10, invBtn);
        container.setItem(12, tpBtn);
        container.setItem(16, kickBtn);
        container.setItem(22, backBtn);

        admin.openMenu(new SimpleMenuProvider((id, inventory, player) -> 
            new ChestMenu(MenuType.GENERIC_9X3, id, inventory, container, 3), 
            Component.literal("§8Игрок: " + targetName)
        ));
    }
}
