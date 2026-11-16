package com.example.superguard;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class CommandHandler {

    private static final Map<String, String> registeredUsers = new HashMap<>();
    private static final Set<String> authenticatedUsers = new HashSet<>();
    private static final Set<String> superAdmins = new HashSet<>();
    private static final Set<String> vanishedPlayers = new HashSet<>();
    private static String adminPassword = null;

    private static final File AUTH_FILE = new File("config/superguard_auth.json");
    private static final File ADMIN_FILE = new File("config/superguard_admin.json");

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
                JSONParser parser = new JSONParser();
                JSONObject obj = (JSONObject) parser.parse(new FileReader(AUTH_FILE));
                for (Object key : obj.keySet()) {
                    registeredUsers.put((String) key, (String) obj.get(key));
                }
            }
            if (ADMIN_FILE.exists()) {
                JSONParser parser = new JSONParser();
                JSONObject obj = (JSONObject) parser.parse(new FileReader(ADMIN_FILE));
                adminPassword = (String) obj.get("password");
            }
        } catch (Exception e) {
            SuperGuardMod.LOGGER.error("Failed to load SuperGuard auth data", e);
        }
    }

    private static void saveData() {
        try {
            AUTH_FILE.getParentFile().mkdirs();
            JSONObject authObj = new JSONObject();
            authObj.putAll(registeredUsers);
            try (FileWriter writer = new FileWriter(AUTH_FILE)) {
                writer.write(authObj.toJSONString());
            }

            JSONObject adminObj = new JSONObject();
            if (adminPassword != null) {
                adminObj.put("password", adminPassword);
            }
            try (FileWriter writer = new FileWriter(ADMIN_FILE)) {
                writer.write(adminObj.toJSONString());
            }
        } catch (IOException e) {
            SuperGuardMod.LOGGER.error("Failed to save SuperGuard auth data", e);
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

                    if (adminPassword == null) {
                        adminPassword = pass;
                        saveData();
                        superAdmins.add(player.getName().getString().toLowerCase());
                        player.sendSystemMessage(Component.literal("§a[SuperGuard] Пароль администратора успешно создан и активирован!"));
                        return 1;
                    }

                    if (adminPassword.equals(pass)) {
                        superAdmins.add(player.getName().getString().toLowerCase());
                        player.sendSystemMessage(Component.literal("§a§l[SuperGuard] Режим Super Admin активирован! Полные права OP предоставлены."));
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

                    if (!player.hasPermissions(2) && !isSuperAdmin(name)) {
                        player.sendSystemMessage(Component.literal("§cУ вас нет прав для использования vanish."));
                        return 1;
                    }

                    if (vanishedPlayers.contains(name)) {
                        vanishedPlayers.remove(name);
                        player.setInvisible(false);
                        player.sendSystemMessage(Component.literal("§aVanish выключен. Вы снова видимы."));
                        if (player.getServer() != null) {
                            player.getServer().getPlayerList().broadcastSystemMessage(Component.literal("§e" + player.getName().getString() + " присоединился к игре."), false);
                        }
                    } else {
                        vanishedPlayers.add(name);
                        player.setInvisible(true);
                        player.sendSystemMessage(Component.literal("§aVanish включен. Вы полностью невидимы и скрыты в табе."));
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
                    if (!player.hasPermissions(2) && !isSuperAdmin(name)) {
                        player.sendSystemMessage(Component.literal("§cТолько для администраторов."));
                        return 1;
                    }
                    String msg = StringArgumentType.getString(context, "message");
                    Component chatMsg = Component.literal("§d§l[StaffChat] §f" + player.getName().getString() + " ➔ " + msg);
                    if (player.getServer() != null) {
                        for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
                            if (p.hasPermissions(2) || isSuperAdmin(p.getName().getString().toLowerCase())) {
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
                    if (!player.hasPermissions(2) && !isSuperAdmin(name)) {
                        player.sendSystemMessage(Component.literal("§cТолько для администраторов."));
                        return 1;
                    }
                    AdminMenu.openDashboard(player);
                    return 1;
                })
        );
    }
}
