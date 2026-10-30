package me.aleksilassila.litematica.printer;

import fi.dy.masa.litematica.util.SchematicWorldRefresher;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.event.KeyCallbacks;
import net.fabricmc.api.ModInitializer;
import net.minecraft.client.Minecraft;

public class LitematicaMixinMod implements ModInitializer {
    public static Printer printer;

    @Override
    public void onInitialize() {
        KeyCallbacks.init(Minecraft.getInstance());
        // redraw the schematic overlay so the blocks in the list lose (or get) their "wrong state" marks
        Configs.ANY_STATE_BLOCKS.setValueChangeCallback(config -> SchematicWorldRefresher.INSTANCE.updateAll());
        Printer.LOGGER.info("{} initialized.", PrinterReference.MOD_STRING);
    }
}
