package ru.warmine.fairwol.wcglow.command;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import ru.warmine.fairwol.wcglow.glow.EntityOutlineController;

import java.util.Arrays;
import java.util.List;

public class GlowCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "wcglow";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/wcglow <on|off|toggle|status>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            sendStatus(sender);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "on":
                if (!OpenGlHelper.shadersSupported) {
                    EntityOutlineController.setEnabled(false);
                    sendMessage(sender, EnumChatFormatting.RED + "Glow outline unavailable: shaders/FBO not supported.");
                    return;
                }
                EntityOutlineController.setEnabled(true);
                sendMessage(sender, EnumChatFormatting.GREEN + "Glow outline enabled.");
                return;

            case "off":
                EntityOutlineController.setEnabled(false);
                sendMessage(sender, EnumChatFormatting.YELLOW + "Glow outline disabled.");
                return;

            case "toggle":
                if (!EntityOutlineController.isEnabled() && !OpenGlHelper.shadersSupported) {
                    sendMessage(sender, EnumChatFormatting.RED + "Glow outline unavailable: shaders/FBO not supported.");
                    return;
                }

                boolean enabled = EntityOutlineController.toggle();
                sendMessage(sender, enabled
                        ? EnumChatFormatting.GREEN + "Glow outline enabled."
                        : EnumChatFormatting.YELLOW + "Glow outline disabled.");
                return;

            case "status":
                sendStatus(sender);
                return;

            default:
                throw new CommandException(getCommandUsage(sender));
        }
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
        return args.length == 1
                ? getListOfStringsMatchingLastWord(args, "on", "off", "toggle", "status")
                : Arrays.<String>asList();
    }

    private void sendStatus(ICommandSender sender) {
        String state = EntityOutlineController.isEnabled()
                ? EnumChatFormatting.GREEN + "enabled"
                : EnumChatFormatting.RED + "disabled";
        String support = OpenGlHelper.shadersSupported
                ? EnumChatFormatting.GRAY + " shaders/FBO: ok"
                : EnumChatFormatting.RED + " shaders/FBO: unsupported";

        sendMessage(sender, EnumChatFormatting.AQUA + "Glow outline " + state
                + EnumChatFormatting.GRAY + " radius: 40 blocks"
                + support);
    }

    private void sendMessage(ICommandSender sender, String message) {
        sender.addChatMessage(new ChatComponentText(
                EnumChatFormatting.DARK_AQUA + "[WCGlow] "
                        + EnumChatFormatting.RESET + message
        ));
    }
}
