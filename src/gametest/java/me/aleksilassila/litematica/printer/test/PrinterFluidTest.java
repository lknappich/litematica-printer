package me.aleksilassila.litematica.printer.test;

import static me.aleksilassila.litematica.printer.test.TestSchematic.block;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;
import me.aleksilassila.litematica.printer.config.Configs;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.Fluids;

/**
 * Prints, in survival with real items:
 * <ul>
 * <li>a stone basin with a water source and a waterlogged slab in it: the water may only go in once the walls
 * stand, and the slab has to end up waterlogged;</li>
 * <li>a torch next to a stray water source where the schematic wants stone: the torch may only go in once the
 * water is gone, so no torch is washed away;</li>
 * <li>a water source on a pillar at the edge of a second schematic: nothing holds it in, so it must not be poured
 * (keepFluidsInSchematic);</li>
 * <li>a note block on the basin rim that the schematic tunes to note 12: note blocks are in anyStateBlocks, so the
 * printer places it and leaves it at note 0 instead of clicking it 12 times, and Litematica neither marks it as
 * wrong nor counts it as missing.</li>
 * </ul>
 * No water may end up anywhere else.
 */
public class PrinterFluidTest implements FabricClientGameTest {
    private static final int TIMEOUT = 1200;
    private static final int TORCHES = 8;
    private static final int WATER_BUCKETS = 3;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            BlockPos player = ctx.computeOnClient(mc -> mc.player.blockPosition());
            // one layer below the feet so the player can look over the basin walls
            BlockPos o = player.offset(1, -1, -2);
            BlockPos leak = o.offset(-3, 1, 0);

            run(world, "gamemode survival @a");
            run(world, "fill " + at(o.offset(0, 0, 0)) + " " + at(o.offset(3, 1, 4)) + " minecraft:air");
            // a glass pool around the torch row keeps the stray water in; the schematic has air there
            run(world, "fill " + at(o.offset(-1, 0, 3)) + " " + at(o.offset(3, 1, 5)) + " minecraft:glass");
            run(world, "fill " + at(o.offset(0, 0, 4)) + " " + at(o.offset(2, 1, 4)) + " minecraft:air");
            run(world, "setblock " + at(o.offset(2, 1, 4)) + " minecraft:water");
            for (int i = 0; i < WATER_BUCKETS; i++) {
                run(world, "item replace entity @a hotbar." + i + " with minecraft:water_bucket");
            }
            run(world, "item replace entity @a hotbar.3 with minecraft:stone 64");
            run(world, "item replace entity @a hotbar.4 with minecraft:oak_slab 4");
            run(world, "item replace entity @a hotbar.5 with minecraft:torch " + TORCHES);
            run(world, "item replace entity @a hotbar.6 with minecraft:bucket");
            run(world, "item replace entity @a hotbar.7 with minecraft:note_block 2");
            ctx.waitTicks(40);

            TestSchematic schematic = new TestSchematic("Printer Fluids", 4, 2, 5);
            for (int x = 0; x < 4; x++) {
                for (int z = 0; z < 3; z++) {
                    schematic.set(x, 0, z, block("minecraft:stone"));
                    schematic.set(x, 1, z, block("minecraft:stone"));
                }
            }
            schematic.set(0, 1, 0, block("minecraft:note_block", "instrument=basedrum", "note=12", "powered=false"));
            schematic.set(1, 1, 1, block("minecraft:water", "level=0"));
            schematic.set(2, 1, 1, block("minecraft:oak_slab", "type=bottom", "waterlogged=true"));
            for (int x = 0; x < 3; x++) {
                schematic.set(x, 0, 4, block("minecraft:stone"));
            }
            schematic.set(0, 1, 4, block("minecraft:torch"));
            schematic.set(2, 1, 4, block("minecraft:stone"));
            TestSchematic pillar = new TestSchematic("Printer Leak", 1, 2, 1);
            pillar.set(0, 0, 0, block("minecraft:stone"));
            pillar.set(0, 1, 0, block("minecraft:water", "level=0"));
            ctx.runOnClient(mc -> {
                TestSchematic.removeAllPlacements();
                try {
                    pillar.place(mc.gameDirectory.toPath().resolve("schematics"), leak);
                    schematic.place(mc.gameDirectory.toPath().resolve("schematics"), o);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                Configs.PRINT_DEBUG.setBooleanValue(true);
                Configs.PRINT_MODE.setBooleanValue(true);
            });

            String basinWhenWet = null;
            int ticks = 0;
            for (; ticks < TIMEOUT; ticks++) {
                ctx.waitTick();
                String missing = ctx.computeOnClient(mc -> missing(mc, o, leak));
                if (basinWhenWet == null && ctx.computeOnClient(mc -> basinHasWater(mc, o))) {
                    basinWhenWet = ctx.computeOnClient(mc -> rim(mc, o));
                }
                if (missing.isEmpty()) {
                    break;
                }
            }
            ctx.runOnClient(mc -> Configs.PRINT_MODE.setBooleanValue(false));
            ctx.waitTicks(40);
            ctx.takeScreenshot("printer_fluids");

            List<String> problems = new ArrayList<>();
            String missing = ctx.computeOnClient(mc -> missing(mc, o, leak));
            if (!missing.isEmpty()) {
                problems.add("not printed after " + ticks + " ticks: " + missing);
            }
            if (basinWhenWet != null && !basinWhenWet.isEmpty()) {
                problems.add("water went in before the basin was closed, missing then: " + basinWhenWet);
            }
            String stray = ctx.computeOnClient(mc -> strayWater(mc, o));
            if (!stray.isEmpty()) {
                problems.add("water where the schematic has none: " + stray);
            }
            int torches = ctx.computeOnClient(mc -> mc.player.getInventory().countItem(Items.TORCH));
            if (torches != TORCHES - 1) {
                problems.add("expected exactly one torch used, " + (TORCHES - torches) + " gone");
            }
            // one for the source, one for the slab, none for the pillar
            int water = ctx.computeOnClient(mc -> mc.player.getInventory().countItem(Items.WATER_BUCKET));
            if (water != WATER_BUCKETS - 2) {
                problems.add("expected two water buckets used, " + (WATER_BUCKETS - water) + " used");
            }
            int note = ctx.computeOnClient(mc -> mc.level.getBlockState(o.offset(0, 1, 0))
                    .getOptionalValue(BlockStateProperties.NOTE).orElse(-1));
            if (note != 0) {
                problems.add("expected the note block left at note 0, found " + note);
            }

            // Litematica itself: no "wrong state" overlay and nothing missing in the material list for the note block
            BlockState tuned = Blocks.NOTE_BLOCK.defaultBlockState().setValue(BlockStateProperties.NOTE, 12);
            String noteOverlay = overlayType(tuned, Blocks.NOTE_BLOCK.defaultBlockState());
            String slabOverlay = overlayType(Blocks.OAK_SLAB.defaultBlockState(),
                    Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP));
            if (!noteOverlay.equals("NONE") || !slabOverlay.equals("WRONG_STATE")) {
                problems.add("overlay for the note block " + noteOverlay + " (want NONE), for a flipped slab "
                        + slabOverlay + " (want WRONG_STATE)");
            }
            MaterialListBase materials = ctx.computeOnClient(mc -> {
                MaterialListBase list = DataManager.getSchematicPlacementManager().getSelectedSchematicPlacement()
                        .getMaterialList();
                list.reCreateMaterialList();
                return list;
            });
            ctx.waitTicks(60);
            String noteCount = ctx.computeOnClient(mc -> materials.getMaterialsAll().stream()
                    .filter(entry -> entry.getStack().is(Items.NOTE_BLOCK))
                    .map(entry -> entry.getCountTotal() + " total, " + entry.getCountMissing() + " missing")
                    .findFirst().orElse("no entry"));
            if (!noteCount.equals("1 total, 0 missing")) {
                problems.add("material list for note blocks: " + noteCount + " (want 1 total, 0 missing)");
            }
            if (!problems.isEmpty()) {
                throw new AssertionError(String.join("; ", problems));
            }
        }
    }

    /** The blocks that hold the basin's water in: under and beside the water and the slab. */
    private static String rim(Minecraft mc, BlockPos o) {
        List<String> missing = new ArrayList<>();
        for (int x = 1; x <= 2; x++) {
            expect(mc, o.offset(x, 0, 1), Blocks.STONE.defaultBlockState(), missing);
            expect(mc, o.offset(x, 1, 0), Blocks.STONE.defaultBlockState(), missing);
            expect(mc, o.offset(x, 1, 2), Blocks.STONE.defaultBlockState(), missing);
        }
        expect(mc, o.offset(0, 1, 1), Blocks.STONE.defaultBlockState(), missing);
        expect(mc, o.offset(3, 1, 1), Blocks.STONE.defaultBlockState(), missing);
        // once the slab stands, its bottom holds the water in itself
        if (mc.level.getBlockState(o.offset(2, 1, 1)).is(Blocks.OAK_SLAB)) {
            missing.removeIf(spot -> spot.startsWith(o.offset(2, 0, 1).toShortString() + " "));
        }
        return String.join(", ", missing);
    }

    /** What is not yet as in the schematic. */
    private static String missing(Minecraft mc, BlockPos o, BlockPos leak) {
        List<String> missing = new ArrayList<>();
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 3; z++) {
                expect(mc, o.offset(x, 0, z), Blocks.STONE.defaultBlockState(), missing);
                if ((!(x == 1 || x == 2) || z != 1) && (x != 0 || z != 0)) {
                    expect(mc, o.offset(x, 1, z), Blocks.STONE.defaultBlockState(), missing);
                }
            }
        }
        // any note will do
        BlockState noteBlock = mc.level.getBlockState(o.offset(0, 1, 0));
        if (!noteBlock.is(Blocks.NOTE_BLOCK)) {
            missing.add(o.offset(0, 1, 0).toShortString() + " " + noteBlock);
        }
        expect(mc, leak, Blocks.STONE.defaultBlockState(), missing);
        expect(mc, o.offset(1, 1, 1), Blocks.WATER.defaultBlockState(), missing);
        expect(mc, o.offset(2, 1, 1), Blocks.OAK_SLAB.defaultBlockState()
                .setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM)
                .setValue(BlockStateProperties.WATERLOGGED, true), missing);
        for (int x = 0; x < 3; x++) {
            expect(mc, o.offset(x, 0, 4), Blocks.STONE.defaultBlockState(), missing);
        }
        expect(mc, o.offset(2, 1, 4), Blocks.STONE.defaultBlockState(), missing);
        expect(mc, o.offset(0, 1, 4), Blocks.TORCH.defaultBlockState(), missing);
        return String.join(", ", missing);
    }

    private static void expect(Minecraft mc, BlockPos pos, BlockState want, List<String> missing) {
        BlockState found = mc.level.getBlockState(pos);
        if (!found.equals(want)) {
            missing.add(pos.toShortString() + " " + found);
        }
    }

    private static boolean basinHasWater(Minecraft mc, BlockPos o) {
        return mc.level.getFluidState(o.offset(1, 1, 1)).getType().isSame(Fluids.WATER)
                || mc.level.getFluidState(o.offset(2, 1, 1)).getType().isSame(Fluids.WATER);
    }

    /** Water anywhere around the build except the source and the slab in the basin. */
    private static String strayWater(Minecraft mc, BlockPos o) {
        List<String> stray = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(o.offset(-4, -1, -4), o.offset(7, 3, 8))) {
            if (pos.equals(o.offset(1, 1, 1)) || pos.equals(o.offset(2, 1, 1))) {
                continue;
            }
            if (!mc.level.getFluidState(pos).isEmpty()) {
                stray.add(pos.toShortString());
            }
        }
        return String.join(", ", stray);
    }

    /** What Litematica's overlay shows for these states, asked from a bare chunk renderer. */
    private static String overlayType(BlockState schematic, BlockState client) {
        try {
            Field theUnsafe = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Object unsafe = theUnsafe.get(null);
            Object renderer = unsafe.getClass().getMethod("allocateInstance", Class.class)
                    .invoke(unsafe, ChunkRendererSchematicVbo.class);
            Method getOverlayType = ChunkRendererSchematicVbo.class
                    .getDeclaredMethod("getOverlayType", BlockState.class, BlockState.class);
            getOverlayType.setAccessible(true);
            return String.valueOf(getOverlayType.invoke(renderer, schematic, client));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void run(TestSingleplayerContext world, String command) {
        world.getServer().runCommand(command);
    }

    private static String at(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
}
