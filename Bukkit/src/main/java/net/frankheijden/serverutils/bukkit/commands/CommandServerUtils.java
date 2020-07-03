package net.frankheijden.serverutils.bukkit.commands;

import static net.frankheijden.serverutils.bukkit.config.Messenger.sendMessage;
import static net.frankheijden.serverutils.bukkit.reflection.BukkitReflection.MINOR;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Dependency;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.frankheijden.serverutils.bukkit.ServerUtils;
import net.frankheijden.serverutils.bukkit.config.Messenger;
import net.frankheijden.serverutils.bukkit.managers.CloseableResult;
import net.frankheijden.serverutils.bukkit.managers.LoadResult;
import net.frankheijden.serverutils.bukkit.managers.PluginManager;
import net.frankheijden.serverutils.bukkit.managers.Result;
import net.frankheijden.serverutils.bukkit.reflection.RCraftServer;
import net.frankheijden.serverutils.bukkit.utils.FormatBuilder;
import net.frankheijden.serverutils.bukkit.utils.ForwardFilter;
import net.frankheijden.serverutils.bukkit.utils.ReloadHandler;
import net.frankheijden.serverutils.common.utils.ListBuilder;
import net.frankheijden.serverutils.common.utils.ListFormat;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;

@CommandAlias("serverutils|su")
public class CommandServerUtils extends BaseCommand {

    private static final Set<String> ALIASES;
    private static final Map<String, ReloadHandler> supportedConfigs;

    static {
        ALIASES = new HashSet<>();
        ALIASES.add("serverutils");
        ALIASES.add("plugins");

        supportedConfigs = new HashMap<>();
        supportedConfigs.put("bukkit", RCraftServer::reloadBukkitConfiguration);
        supportedConfigs.put("commands.yml", RCraftServer::reloadCommandsConfiguration);
        supportedConfigs.put("server-icon.png", RCraftServer::loadIcon);
        supportedConfigs.put("banned-ips.json", RCraftServer::reloadIpBans);
        supportedConfigs.put("banned-players.json", RCraftServer::reloadProfileBans);
    }

    @Dependency
    private ServerUtils plugin;

    public static Set<String> getSupportedConfigs() {
        return supportedConfigs.keySet();
    }

    /**
     * Shows the help page to the sender.
     * @param sender The sender of the command.
     */
    @Default
    @Subcommand("help")
    @CommandPermission("serverutils.help")
    @Description("Shows a help page with a few commands.")
    public void onHelp(CommandSender sender) {
        Messenger.sendMessage(sender, "serverutils.help.header");

        FormatBuilder builder = FormatBuilder.create(Messenger.getMessage("serverutils.help.format"))
                .orderedKeys("%command%", "%subcommand%", "%help%");
        plugin.getCommandManager().getRegisteredRootCommands().stream()
                .filter(c -> !ALIASES.contains(c.getCommandName().toLowerCase()))
                .forEach(rootCommand -> {
                    builder.add(rootCommand.getCommandName(), "", rootCommand.getDescription());
                    rootCommand.getSubCommands().forEach((str, cmd) -> {
                        if (cmd.getPrefSubCommand().isEmpty()) return;
                        builder.add(rootCommand.getCommandName(), " " + cmd.getPrefSubCommand(), cmd.getHelpText());
                    });
                });
        builder.sendTo(sender);
        Messenger.sendMessage(sender, "serverutils.help.footer");
    }

    /**
     * Reloads the configurations of ServerUtils.
     * @param sender The sender of the command.
     */
    @Subcommand("reload")
    @CommandPermission("serverutils.reload")
    @Description("Reloads the ServerUtils plugin.")
    public void onReload(CommandSender sender) {
        plugin.reload();
        sendMessage(sender, "serverutils.success",
                "%action%", "reload",
                "%what%", "ServerUtils configurations");
    }

    /**
     * Reloads a config from a set of configurations of the server.
     * @param sender The sender of the command.
     * @param config The configuration to reload.
     */
    @Subcommand("reloadconfig")
    @CommandCompletion("@supportedConfigs")
    @CommandPermission("serverutils.reloadconfig")
    @Description("Reloads individual Server configurations.")
    public void onReloadCommands(CommandSender sender, String config) {
        ReloadHandler handler = supportedConfigs.get(config);
        if (handler == null) {
            this.doHelp(sender);
            return;
        }

        String[] replacements = new String[]{ "%action%", "reload", "%what%", config };

        ForwardFilter filter = new ForwardFilter(sender);
        filter.start(Bukkit.getLogger());
        try {
            handler.handle();
            filter.stop(Bukkit.getLogger());

            String path = "serverutils." + (filter.hasWarnings() ? "warning" : "success");
            sendMessage(sender, path, replacements);
        } catch (Exception ex) {
            filter.stop(Bukkit.getLogger());

            ex.printStackTrace();
            sendMessage(sender, "serverutils.error", replacements);
        }
    }

    /**
     * Loads the specified plugin on the server.
     * @param sender The sender of the command.
     * @param jarFile The filename of the plugin in the plugins/ directory.
     */
    @Subcommand("loadplugin")
    @CommandCompletion("@pluginJars")
    @CommandPermission("serverutils.loadplugin")
    @Description("Loads the specified jar file as a plugin.")
    public void onLoadPlugin(CommandSender sender, String jarFile) {
        LoadResult loadResult = PluginManager.loadPlugin(jarFile);
        if (!loadResult.isSuccess()) {
            loadResult.getResult().sendTo(sender, "load", jarFile);
            return;
        }

        Result result = PluginManager.enablePlugin(loadResult.getPlugin());
        result.sendTo(sender, "load", jarFile);
    }

    /**
     * Unloads the specified plugin from the server.
     * @param sender The sender of the command.
     * @param pluginName The plugin name.
     */
    @Subcommand("unloadplugin")
    @CommandCompletion("@plugins")
    @CommandPermission("serverutils.unloadplugin")
    @Description("Disables and unloads the specified plugin.")
    public void onUnloadPlugin(CommandSender sender, String pluginName) {
        Result disableResult = PluginManager.disablePlugin(pluginName);
        if (disableResult != Result.SUCCESS && disableResult != Result.ALREADY_DISABLED) {
            disableResult.sendTo(sender, "disabl", pluginName);
            return;
        }

        CloseableResult result = PluginManager.unloadPlugin(pluginName);
        result.getResult().sendTo(sender, "unload", pluginName);
        result.tryClose();
    }

    /**
     * Reloads the specified plugin on the server.
     * @param sender The sender of the command.
     * @param pluginName The plugin name.
     */
    @Subcommand("reloadplugin")
    @CommandCompletion("@plugins")
    @CommandPermission("serverutils.reloadplugin")
    @Description("Reloads a specified plugin.")
    public void onReloadPlugin(CommandSender sender, String pluginName) {
        CloseableResult result = PluginManager.reloadPlugin(pluginName);
        result.getResult().sendTo(sender, "reload", pluginName);
        result.tryClose();
    }

    /**
     * Enables the specified plugin on the server.
     * @param sender The sender of the command.
     * @param pluginName The plugin name.
     */
    @Subcommand("enableplugin")
    @CommandCompletion("@plugins")
    @CommandPermission("serverutils.enableplugin")
    @Description("Enables the loaded plugin.")
    public void onEnablePlugin(CommandSender sender, String pluginName) {
        Result result = PluginManager.enablePlugin(pluginName);
        result.sendTo(sender, "enabl", pluginName);
    }

    /**
     * Disables the specified plugin on the server.
     * @param sender The sender of the command.
     * @param pluginName The plugin name.
     */
    @Subcommand("disableplugin")
    @CommandCompletion("@plugins")
    @CommandPermission("serverutils.disableplugin")
    @Description("Disables the specified plugin.")
    public void onDisablePlugin(CommandSender sender, String pluginName) {
        Result result = PluginManager.disablePlugin(pluginName);
        result.sendTo(sender, "disabl", pluginName);
    }

    /**
     * Shows information about the specified plugin.
     * @param sender The sender of the command.
     * @param pluginName The plugin name.
     */
    @Subcommand("plugininfo")
    @CommandCompletion("@plugins")
    @CommandPermission("serverutils.plugininfo")
    @Description("Shows information about the specified plugin.")
    public void onPluginInfo(CommandSender sender, String pluginName) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
        if (plugin == null) {
            Result.NOT_EXISTS.sendTo(sender, "fetch", pluginName);
            return;
        }

        PluginDescriptionFile description = plugin.getDescription();
        String format = Messenger.getMessage("serverutils.plugininfo.format");
        String listFormatString = Messenger.getMessage("serverutils.plugininfo.list_format");
        String seperator = Messenger.getMessage("serverutils.plugininfo.seperator");
        String lastSeperator = Messenger.getMessage("serverutils.plugininfo.last_seperator");

        ListFormat<String> listFormat = str -> listFormatString.replace("%value%", str);

        Messenger.sendMessage(sender, "serverutils.plugininfo.header");

        FormatBuilder builder = FormatBuilder.create(format)
                .orderedKeys("%key%", "%value%")
                .add("Name", plugin.getName())
                .add("Full Name", description.getFullName())
                .add("Version", description.getVersion());
        if (MINOR >= 13) builder.add("API Version", description.getAPIVersion());
        builder.add("Website", description.getWebsite())
                .add("Authors", ListBuilder.create(description.getAuthors())
                        .format(listFormat)
                        .seperator(seperator)
                        .lastSeperator(lastSeperator)
                        .toString())
                .add("Description", description.getDescription())
                .add("Main", description.getMain())
                .add("Prefix", description.getPrefix())
                .add("Load Order", description.getLoad().name())
                .add("Load Before", ListBuilder.create(description.getLoadBefore())
                        .format(listFormat)
                        .seperator(seperator)
                        .lastSeperator(lastSeperator)
                        .toString())
                .add("Depend", ListBuilder.create(description.getDepend())
                        .format(listFormat)
                        .seperator(seperator)
                        .lastSeperator(lastSeperator)
                        .toString())
                .add("Soft Depend", ListBuilder.create(description.getSoftDepend())
                        .format(listFormat)
                        .seperator(seperator)
                        .lastSeperator(lastSeperator)
                        .toString());
        if (MINOR >= 15) builder.add("Provides", ListBuilder.create(description.getProvides())
                .format(listFormat)
                .seperator(seperator)
                .lastSeperator(lastSeperator)
                .toString());

        builder.sendTo(sender);
        Messenger.sendMessage(sender, "serverutils.plugininfo.footer");
    }

    /**
     * Shows information about a provided command.
     * @param sender The sender of the command.
     * @param command The command to lookup.
     */
    @Subcommand("commandinfo")
    @CommandCompletion("@commands")
    @CommandPermission("serverutils.commandinfo")
    @Description("Shows information about the specified command.")
    public void onCommandInfo(CommandSender sender, String command) {
        Command cmd = PluginManager.getCommand(command);
        if (cmd == null) {
            Messenger.sendMessage(sender, "serverutils.commandinfo.not_exists");
            return;
        }

        String format = Messenger.getMessage("serverutils.commandinfo.format");
        String listFormatString = Messenger.getMessage("serverutils.commandinfo.list_format");
        String seperator = Messenger.getMessage("serverutils.commandinfo.seperator");
        String lastSeperator = Messenger.getMessage("serverutils.commandinfo.last_seperator");

        ListFormat<String> listFormat = str -> listFormatString.replace("%value%", str);

        Messenger.sendMessage(sender, "serverutils.commandinfo.header");
        FormatBuilder builder = FormatBuilder.create(format)
                .orderedKeys("%key%", "%value%")
                .add("Name", cmd.getName());
        if (cmd instanceof PluginIdentifiableCommand) {
            PluginIdentifiableCommand pc = (PluginIdentifiableCommand) cmd;
            builder.add("Plugin", pc.getPlugin().getName());
        }
        builder.add("Usage", cmd.getUsage())
                .add("Description", cmd.getDescription())
                .add("Aliases", ListBuilder.create(cmd.getAliases())
                        .format(listFormat)
                        .seperator(seperator)
                        .lastSeperator(lastSeperator)
                        .toString())
                .add("Label", cmd.getLabel())
                .add("Timings Name", cmd.getTimingName())
                .add("Permission", cmd.getPermission())
                .add("Permission Message", cmd.getPermissionMessage());

        builder.sendTo(sender);
        Messenger.sendMessage(sender, "serverutils.commandinfo.footer");
    }
}