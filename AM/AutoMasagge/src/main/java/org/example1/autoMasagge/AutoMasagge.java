package org.example1.autoMasagge;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

public class AutoMasagge extends JavaPlugin implements CommandExecutor, TabCompleter {

    private File dataFile;
    private List<String> tasks = new ArrayList<>();
    private List<BukkitTask> activeTasks = new ArrayList<>();
    private Map<Integer, Integer> executionCounts = new HashMap<>();
    private static final Pattern TIME_PATTERN = Pattern.compile("^\\d{2}:\\d{2}:\\d{2}$");

    @Override
    public void onEnable() {
        getCommand("am").setExecutor(this);
        getCommand("am").setTabCompleter(this);
        dataFile = new File("automasagge.txt");
        if (dataFile.exists()) {
            try {
                tasks = Files.readAllLines(dataFile.toPath(), StandardCharsets.UTF_8);
                reloadTasks();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        getLogger().info("AutoMasagge enabled! Tasks: " + tasks.size());
    }

    @Override
    public void onDisable() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        getLogger().info("AutoMasagge disabled!");
    }

    private boolean isOp(CommandSender sender) {
        if (sender instanceof Player) {
            if (!((Player) sender).isOp()) {
                sender.sendMessage("§cЭта команда доступна только операторам сервера!");
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§cИспользование: /am <add|list|delete|stats>");
            return true;
        }

        if (!isOp(sender)) return true;

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "add":
                return handleAdd(sender, args);
            case "list":
                return handleList(sender);
            case "delete":
                return handleDelete(sender, args);
            case "stats":
                return handleStats(sender);
            default:
                sender.sendMessage("§cНеизвестная подкоманда. Используйте: add, list, delete, stats");
                return true;
        }
    }

    private boolean handleAdd(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§cИспользование: /am add <команда> // <время>");
            sender.sendMessage("§eПример: /am add say Привет // 00:00:05");
            return true;
        }

        String full = String.join(" ", args);
        int idx = full.indexOf(" // ");
        if (idx == -1) {
            sender.sendMessage("§cОшибка: разделитель ' // ' не найден");
            return true;
        }

        String before = full.substring(0, idx).trim();
        String timeStr = full.substring(idx + 4).trim();

        if (before.startsWith("add ")) {
            before = before.substring(4).trim();
        }

        if (before.isEmpty()) {
            sender.sendMessage("§cКоманда не может быть пустой");
            return true;
        }

        if (!TIME_PATTERN.matcher(timeStr).matches()) {
            sender.sendMessage("§cОшибка: неверное время");
            sender.sendMessage("§eФормат: ЧЧ:ММ:СС (пример: 00:00:05)");
            return true;
        }

        try {
            String[] t = timeStr.split(":");
            int hours = Integer.parseInt(t[0]);
            int minutes = Integer.parseInt(t[1]);
            int seconds = Integer.parseInt(t[2]);

            if (hours > 23 || minutes > 59 || seconds > 59) {
                sender.sendMessage("§cОшибка: неверное время");
                sender.sendMessage("§eМаксимум: 23:59:59");
                return true;
            }

            long totalSeconds = hours * 3600L + minutes * 60L + seconds;
            if (totalSeconds <= 0) {
                sender.sendMessage("§cОшибка: время должно быть больше 0");
                return true;
            }

            String line = before + " // " + timeStr;
            tasks.add(line);
            Files.write(dataFile.toPath(), (line + "\n").getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            scheduleTask(before, timeStr, tasks.size() - 1);

            sender.sendMessage("§aЗадача добавлена: " + before + " каждые " + timeStr);

        } catch (Exception e) {
            sender.sendMessage("§cОшибка: " + e.getMessage());
        }

        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (tasks.isEmpty()) {
            sender.sendMessage("§eСписок задач пуст");
            return true;
        }

        sender.sendMessage("§6=== Список задач ===");
        for (int i = 0; i < tasks.size(); i++) {
            parseAndSendTask(sender, i, tasks.get(i));
        }
        sender.sendMessage("§6====================");
        return true;
    }

    private boolean handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cИспользование: /am delete <номер>");
            return true;
        }

        try {
            int index = Integer.parseInt(args[1]);
            if (index < 0 || index >= tasks.size()) {
                sender.sendMessage("§cОшибка: номер " + index + " не существует");
                return true;
            }

            String removed = tasks.remove(index);
            sender.sendMessage("§aЗадача удалена: " + removed);

            List<String> updatedTasks = new ArrayList<>(tasks);
            Files.write(dataFile.toPath(), updatedTasks, StandardCharsets.UTF_8);
            reloadTasks();

        } catch (NumberFormatException e) {
            sender.sendMessage("§cОшибка: введите число");
        } catch (Exception e) {
            sender.sendMessage("§cОшибка: " + e.getMessage());
        }

        return true;
    }

    private boolean handleStats(CommandSender sender) {
        if (tasks.isEmpty()) {
            sender.sendMessage("§eНет задач для статистики");
            return true;
        }

        sender.sendMessage("§6=== Статистика ===");
        for (int i = 0; i < tasks.size(); i++) {
            int idx = tasks.get(i).lastIndexOf(" // ");
            if (idx != -1) {
                String cmd = tasks.get(i).substring(0, idx).trim();
                int count = executionCounts.getOrDefault(i, 0);
                sender.sendMessage("§e[" + i + "] §f" + cmd + " §7- выполнено: §b" + count + " раз");
            }
        }
        sender.sendMessage("§6====================");
        return true;
    }

    private void reloadTasks() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();
        executionCounts.clear();
        for (int i = 0; i < tasks.size(); i++) {
            String line = tasks.get(i);
            int idx = line.lastIndexOf(" // ");
            if (idx != -1) {
                String cmdPart = line.substring(0, idx).trim();
                String time = line.substring(idx + 4).trim();
                if (TIME_PATTERN.matcher(time).matches()) {
                    scheduleTask(cmdPart, time, i);
                }
            }
        }
    }

    private void parseAndSendTask(CommandSender sender, int index, String task) {
        int idx = task.lastIndexOf(" // ");
        if (idx != -1) {
            String cmd = task.substring(0, idx).trim();
            String time = task.substring(idx + 4).trim();
            int count = executionCounts.getOrDefault(index, 0);
            sender.sendMessage("§e[" + index + "] §f" + cmd + " §7// §b" + time + " §7(выполнено: " + count + ")");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("add", "list", "delete", "stats");
            String input = args[0].toLowerCase();
            List<String> matches = new ArrayList<>();
            for (String cmd : subCommands) {
                if (cmd.startsWith(input)) matches.add(cmd);
            }
            return matches;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("delete")) {
            List<String> indices = new ArrayList<>();
            for (int i = 0; i < tasks.size(); i++) indices.add(String.valueOf(i));
            return indices;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
            List<String> players = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                players.add(player.getName());
            }
            return players;
        }

        return new ArrayList<>();
    }

    private String applyPlaceholders(String cmd) {
        cmd = cmd.replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
        cmd = cmd.replace("%max_players%", String.valueOf(Bukkit.getMaxPlayers()));
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        cmd = cmd.replace("%time%", sdf.format(new Date()));
        return cmd;
    }

    private void scheduleTask(String cmd, String timeStr, int taskIndex) {
        try {
            String[] t = timeStr.split(":");
            long totalSeconds = Integer.parseInt(t[0]) * 3600L + Integer.parseInt(t[1]) * 60L + Integer.parseInt(t[2]);
            long delayTicks = totalSeconds * 20L;

            BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, () -> {
                String finalCmd = applyPlaceholders(cmd);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd.startsWith("/") ? finalCmd.substring(1) : finalCmd);
                executionCounts.put(taskIndex, executionCounts.getOrDefault(taskIndex, 0) + 1);
            }, delayTicks, delayTicks);

            activeTasks.add(task);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}