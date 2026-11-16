package com.example.superguard;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CommandHandler {

    private static final Map<String, String> registeredUsers = new HashMap<>();
    private static final Set<String> authenticatedUsers = new HashSet<>();
    private static final Set<String> superAdmins = new HashSet<>();
    private static final Set<String> vanishedPlayers = new HashSet<>();
    private static String adminPassword = null;

    private static final File AUTH_FILE = new File("config/superguard_auth.txt");
    private static final File ADMIN_FILE = new File("config/superguard_admin.txt");

    static {
        loadData();
    }

    public static boolean isAuthenticated(String name) {
        return !SuperGuardConfig.ENABLE_AUTH.get() || authenticatedUsers.contains(name.toLowerCase());
    }

    public static boolean isVanished(String name) {
        return vanishedPlayers.contains(name.toLowerCase());
    }

    public static boolean isSuperAdmin(String name) {
        return superAdmins.contains(name.toLowerCase());
    }

    private static void loadData() {
        try {
            if (AUTH_FILE.exists()) {
                List<String> lines = Files.readAllLines(AUTH_FILE.toPath());
                for (String line : lines) {
                    String[] parts = line.split(":", 2);
                    if (parts.length == 2) {
                        registeredUsers.put(parts[0].trim(), parts[1].trim());
                    }
                }
            }
            if (ADMIN_FILE.exists()) {
                List<String> lines = Files.readAllLines(ADMIN_FILE.toPath());
                if (!lines.isEmpty()) {
                    adminPassword = lines.get(0).trim();
                }
            }
        } catch (Exception e) {
            SuperGuardMod.LOGGER.error("Failed to load SuperGuard auth data", e);
        }
    }

    private static void saveData() {
        try {
            AUTH_FILE.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(AUTH_FILE)) {
                for (Map.Entry<String, String> entry : registeredUsers.entrySet()) {
                    writer.write(entry.getKey() + ":" + entry.getValue() + "\n");
                }
            }

            if (adminPassword != null) {
                try (FileWriter writer = new FileWriter(ADMIN_FILE)) {
                    writer.write(adminPassword + "\n");
                }
            }
        } catch (IOException e) {
            SuperGuardMod.LOGGER.error("Failed to save SuperGuard auth data", e);
        }
    }

    // Заморозка движения и черный экран для неавторизованных / неразрешенных админов
    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String name = player.getName().getString().toLowerCase();

            boolean needsAuth = SuperGuardConfig.ENABLE_AUTH.get() && !isAuthenticated(name);
            boolean isOp = player.getServer() != null && player.getServer().getPlayerList().isOp(player.getGameProfile());
            boolean needsAdmin = isOp && !isSuperAdmin(name);

            if (needsAuth || needsAdmin) {
                // Полная остановка движения (обнуляем скорость по X и Z)
                player.setDeltaMovement(0, player.getDeltaMovement().y, 0);

                // Черный экран (Слепота) + Замедление + Запрет прыжков
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 255, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.JUMP, 40, 128, false, false));
            }
        }
    }

    // Перехват и блокировка команд
    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        if (event.getParseResults().getContext().getSource().getPlayer() == null) {
            return;
        }
        ServerPlayer player = event.getParseResults().getContext().getSource().getPlayer();
        String name = player.getName().getString().toLowerCase();
        String commandLine = event.getParseResults().getReader().getString().toLowerCase().trim();

        if (!isAuthenticated(name)) {
            if (!commandLine.startsWith("register") && !commandLine.startsWith("login")) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("§c[Auth] Вы не вошли в систему! Используйте /login <пароль>"));
                return;
            }
        }

        boolean isOp = player.getServer() != null && player.getServer().getPlayerList().isOp(player.getGameProfile());
        if (isOp && !isSuperAdmin(name)) {
            String cmdRoot = commandLine.split(" ")[0];
            List<String> restrictedCmds = List.of("ven", "sc", "sg", "op", "deop", "ban", "ban-ip", "pardon", "pardon-ip", "kick", "stop", "reload", "whitelist", "gamemode", "give", "clear");
            
            if (restrictedCmds.contains(cmdRoot)) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("§c[SuperGuard] Ваши OP-права заблокированы! Введите /admin <пароль> для разблокировки."));
            }
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("register")
                .then(Commands.argument("password", StringArgumentType.word())
                .then(Commands.argument("confirm", StringArgumentType.word())
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    String name = player.getName().getString().toLowerCase();
                    String pass = StringArgumentType.getString(context, "password");
                    String confirm = StringArgumentType.getString(context, "confirm");

                    if (registeredUsers.containsKey(name)) {
                        player.sendSystemMessage(Component.literal("§c[Auth] Вы уже зарегистрированы! Используйте /login <пароль>"));
                        return 1;
                    }
                    if (!pass.equals(confirm)) {
                        player.sendSystemMessage(Component.literal("§c[Auth] Пароли не совпадают!"));
                        return 1;
                    }

                    registeredUsers.put(name, pass);
                    authenticatedUsers.add(name);
                    saveData();
                    player.sendSystemMessage(Component.literal("§a[Auth] Успешная регистрация и вход в игру!"));
                    return 1;
                })))
        );

        event.getDispatcher().register(
            Commands.literal("login")
                .then(Commands.argument("password", StringArgumentType.word())
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    String name = player.getName().getString().toLowerCase();
                    String pass = StringArgumentType.getString(context, "password");

                    if (!registeredUsers.containsKey(name)) {
                        player.sendSystemMessage(Component.literal("§c[Auth] Вы не зарегистрированы! Используйте /register <пароль> <пароль>"));
                        return 1;
                    }
                    if (authenticatedUsers.contains(name)) {
                        player.sendSystemMessage(Component.literal("§e[Auth] Вы уже вошли в систему!"));
                        return 1;
                    }
                    if (!registeredUsers.get(name).equals(pass)) {
                        player.sendSystemMessage(Component.literal("§c[Auth] Неверный пароль!"));
                        return 1;
                    }

                    authenticatedUsers.add(name);
                    player.sendSystemMessage(Component.literal("§a[Auth] Успешный вход в систему! Добро пожаловать."));
                    return 1;
                }))
        );

        event.getDispatcher().register(
            Commands.literal("admin")
                .then(Commands.argument("password", StringArgumentType.word())
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    String pass = StringArgumentType.getString(context, "password");
                    String name = player.getName().getString().toLowerCase();

                    if (adminPassword == null) {
                        adminPassword = pass;
                        saveData();
                        superAdmins.add(name);
                        player.sendSystemMessage(Component.literal("§a[SuperGuard] Пароль администратора успешно создан и активирован!"));
                        return 1;
                    }

                    if (adminPassword.equals(pass)) {
                        superAdmins.add(name);
                        player.sendSystemMessage(Component.literal("§a§l[SuperGuard] Режим Super Admin активирован! OP-команды разблокированы."));
                    } else {
                        player.sendSystemMessage(Component.literal("§c[SuperGuard] Неверный пароль администратора!"));
                    }
                    return 1;
                }))
        );

        event.getDispatcher().register(
            Commands.literal("ven")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    String name = player.getName().getString().toLowerCase();

                    if (!isSuperAdmin(name)) {
                        player.sendSystemMessage(Component.literal("§cУ вас нет прав (введите /admin)."));
                        return 1;
                    }

                    if (vanishedPlayers.contains(name)) {
                        vanishedPlayers.remove(name);
                        player.setInvisible(false);
                        player.sendSystemMessage(Component.literal("§aVanish выключен."));
                        if (player.getServer() != null) {
                            player.getServer().getPlayerList().broadcastSystemMessage(Component.literal("§e" + player.getName().getString() + " присоединился к игре."), false);
                        }
                    } else {
                        vanishedPlayers.add(name);
                        player.setInvisible(true);
                        player.sendSystemMessage(Component.literal("§aVanish включен."));
                        if (player.getServer() != null) {
                            player.getServer().getPlayerList().broadcastSystemMessage(Component.literal("§e" + player.getName().getString() + " вышел с сервера"), false);
                        }
                    }
                    return 1;
                })
        );

        event.getDispatcher().register(
            Commands.literal("sc")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    String name = player.getName().getString().toLowerCase();
                    if (!isSuperAdmin(name)) {
                        player.sendSystemMessage(Component.literal("§cТолько для администраторов."));
                        return 1;
                    }
                    String msg = StringArgumentType.getString(context, "message");
                    Component chatMsg = Component.literal("§d§l[StaffChat] §f" + player.getName().getString() + " ➔ " + msg);
                    if (player.getServer() != null) {
                        for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
                            if (isSuperAdmin(p.getName().getString().toLowerCase())) {
                                p.sendSystemMessage(chatMsg);
                            }
                        }
                    }
                    return 1;
                }))
        );

        event.getDispatcher().register(
            Commands.literal("sg")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    String name = player.getName().getString().toLowerCase();
                    if (!isSuperAdmin(name)) {
                        player.sendSystemMessage(Component.literal("§cТолько для администраторов."));
                        return 1;
                    }
                    AdminMenu.openDashboard(player);
                    return 1;
                })
        );
    }
}
