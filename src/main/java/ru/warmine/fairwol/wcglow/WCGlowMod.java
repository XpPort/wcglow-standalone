package ru.warmine.fairwol.wcglow;

import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import ru.warmine.fairwol.wcglow.command.GlowCommand;

@Mod(
        modid = WCGlowMod.MOD_ID,
        name = WCGlowMod.MOD_NAME,
        version = WCGlowMod.VERSION,
        acceptedMinecraftVersions = WCGlowMod.ACCEPTED_VERSION
)
public class WCGlowMod {

    public static final String MOD_ID = "wcglow";
    public static final String MOD_NAME = "WCGlow Standalone";
    public static final String VERSION = "1.0.0";
    public static final String ACCEPTED_VERSION = "1.8.9";

    @Mod.EventHandler
    public void onPreInit(FMLPreInitializationEvent event) {
        ClientCommandHandler.instance.registerCommand(new GlowCommand());
    }
}
