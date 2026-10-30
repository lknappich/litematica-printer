package me.aleksilassila.litematica.printer.test;

import static me.aleksilassila.litematica.printer.test.TestSchematic.block;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.aleksilassila.litematica.printer.config.Configs;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Prints, in survival, in four steps, and checks that nothing is ever placed wrong or in the wrong spot:
 * <ol>
 * <li>printInAir off: a floating top slab that should become a double slab cannot be clicked from anywhere, so it
 * must stay as it is (the printer used to put a second slab on top of it); stairs on the ground get printed;</li>
 * <li>rotate off: the player looks south, so only stairs facing south may be placed, stairs facing west must wait;</li>
 * <li>printInAir and rotate on: floating stone, slab, stairs and log get printed with the right state, the floating
 * slab becomes a double slab, and the west stairs follow;</li>
 * <li>printingRange 2: a block three blocks away must not be placed until the range is raised.</li>
 * </ol>
 */
public class PrinterPlacementTest implements FabricClientGameTest {
    private static final int TIMEOUT = 600;
    /** Ticks to wait for something that must not happen. */
    private static final int QUIET = 100;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            BlockPos p = ctx.computeOnClient(mc -> mc.player.blockPosition());
            double[] at = ctx.computeOnClient(mc -> new double[]{mc.player.getX(), mc.player.getY(), mc.player.getZ()});
            run(world, "gamemode survival @a");
            // look south and stay there
            run(world, "tp @a " + at[0] + " " + at[1] + " " + at[2] + " 0 0");
            run(world, "item replace entity @a hotbar.0 with minecraft:stone 64");
            run(world, "item replace entity @a hotbar.1 with minecraft:oak_slab 16");
            run(world, "item replace entity @a hotbar.2 with minecraft:oak_stairs 16");
            run(world, "item replace entity @a hotbar.3 with minecraft:oak_log 8");
            ctx.waitTicks(40);

            BlockPos slab = p.offset(-2, 2, 1);
            run(world, "setblock " + at(slab) + " minecraft:oak_slab[type=top]");
            BlockPos eastStairs = p.offset(2, 0, -1);
            BlockPos southStairs = p.offset(-2, 0, -1);
            BlockPos westStairs = p.offset(2, 0, 1);
            Map<BlockPos, BlockState> floating = new LinkedHashMap<>();
            floating.put(p.offset(1, 3, 2), Blocks.STONE.defaultBlockState());
            floating.put(p.offset(-1, 3, 2), Blocks.OAK_SLAB.defaultBlockState());
            floating.put(p.offset(1, 3, -2), stairs(Direction.EAST, Half.TOP));
            floating.put(p.offset(-1, 3, -2), Blocks.OAK_LOG.defaultBlockState()
                    .setValue(BlockStateProperties.AXIS, Direction.Axis.X));
            BlockPos far = p.offset(3, 0, 0);

            ctx.runOnClient(mc -> {
                TestSchematic.removeAllPlacements();
                Configs.PRINT_DEBUG.setBooleanValue(false);
                Configs.PRINTING_INTERVAL.setIntegerValue(2);
                Configs.PRINTING_RANGE.setDoubleValue(5);
                Configs.PRINT_IN_AIR.setBooleanValue(false);
                Configs.ROTATE.setBooleanValue(true);
                Configs.PRINT_MODE.setBooleanValue(true);
            });
            List<String> problems = new ArrayList<>();

            // 1. no air printing
            place(ctx, "Slab", slab, block("minecraft:oak_slab", "type=double", "waterlogged=false"));
            place(ctx, "East Stairs", eastStairs, stairsTag("east", "bottom"));
            String missing = waitFor(ctx, Map.of(eastStairs, stairs(Direction.EAST, Half.BOTTOM)));
            check(problems, "step 1 not printed: " + missing, missing.isEmpty());
            ctx.waitTicks(QUIET);
            check(problems, "step 1: the floating slab changed to " + state(ctx, slab),
                    state(ctx, slab).equals(slab(SlabType.TOP)));
            check(problems, "step 1: something was put on the floating slab: " + state(ctx, slab.above()),
                    state(ctx, slab.above()).isAir());

            // 2. rotate off, the player looks south
            ctx.runOnClient(mc -> Configs.ROTATE.setBooleanValue(false));
            place(ctx, "South Stairs", southStairs, stairsTag("south", "bottom"));
            place(ctx, "West Stairs", westStairs, stairsTag("west", "bottom"));
            missing = waitFor(ctx, Map.of(southStairs, stairs(Direction.SOUTH, Half.BOTTOM)));
            check(problems, "step 2 not printed: " + missing, missing.isEmpty());
            ctx.waitTicks(QUIET);
            check(problems, "step 2: stairs placed although the player does not face west: " + state(ctx, westStairs),
                    state(ctx, westStairs).isAir());

            // 3. air printing and rotate on
            ctx.runOnClient(mc -> {
                Configs.ROTATE.setBooleanValue(true);
                Configs.PRINT_IN_AIR.setBooleanValue(true);
            });
            int i = 0;
            for (Map.Entry<BlockPos, BlockState> entry : floating.entrySet()) {
                place(ctx, "Floating " + i++, entry.getKey(), tag(entry.getValue()));
            }
            Map<BlockPos, BlockState> step3 = new LinkedHashMap<>(floating);
            step3.put(slab, slab(SlabType.DOUBLE));
            step3.put(westStairs, stairs(Direction.WEST, Half.BOTTOM));
            missing = waitFor(ctx, step3);
            check(problems, "step 3 not printed: " + missing, missing.isEmpty());
            check(problems, "step 3: something above the double slab: " + state(ctx, slab.above()),
                    state(ctx, slab.above()).isAir());

            // 4. range
            ctx.runOnClient(mc -> Configs.PRINTING_RANGE.setDoubleValue(2));
            place(ctx, "Far", far, block("minecraft:stone"));
            ctx.waitTicks(QUIET);
            check(problems, "step 4: placed beyond the range: " + state(ctx, far), state(ctx, far).isAir());
            ctx.runOnClient(mc -> Configs.PRINTING_RANGE.setDoubleValue(5));
            missing = waitFor(ctx, Map.of(far, Blocks.STONE.defaultBlockState()));
            check(problems, "step 4 not printed after raising the range: " + missing, missing.isEmpty());

            ctx.runOnClient(mc -> {
                Configs.PRINT_MODE.setBooleanValue(false);
                Configs.PRINT_IN_AIR.setBooleanValue(false);
                Configs.PRINTING_INTERVAL.setIntegerValue(12);
            });
            ctx.waitTicks(20);
            ctx.takeScreenshot("printer_placement");

            // nothing anywhere that no schematic asked for
            List<BlockPos> expected = new ArrayList<>(step3.keySet());
            expected.addAll(List.of(eastStairs, southStairs, far));
            String extra = ctx.computeOnClient(mc -> extraBlocks(mc, p, expected));
            check(problems, "blocks no schematic wants: " + extra, extra.isEmpty());

            if (!problems.isEmpty()) {
                throw new AssertionError(String.join("; ", problems));
            }
        }
    }

    /** Polls until every spot has its state; returns what is still missing. */
    private static String waitFor(ClientGameTestContext ctx, Map<BlockPos, BlockState> want) {
        String missing = "";
        for (int tick = 0; tick < TIMEOUT; tick++) {
            ctx.waitTick();
            missing = ctx.computeOnClient(mc -> {
                List<String> out = new ArrayList<>();
                want.forEach((pos, state) -> {
                    BlockState found = mc.level.getBlockState(pos);
                    if (!found.equals(state)) {
                        out.add(pos.toShortString() + " " + found);
                    }
                });
                return String.join(", ", out);
            });
            if (missing.isEmpty()) {
                break;
            }
        }
        return missing;
    }

    /** Anything standing above the ground around the player other than the printed spots. */
    private static String extraBlocks(Minecraft mc, BlockPos p, List<BlockPos> expected) {
        List<String> extra = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(p.offset(-5, 0, -5), p.offset(5, 5, 5))) {
            if (!mc.level.getBlockState(pos).isAir() && !expected.contains(pos)) {
                extra.add(pos.toShortString() + " " + mc.level.getBlockState(pos));
            }
        }
        return String.join(", ", extra);
    }

    private static void place(ClientGameTestContext ctx, String name, BlockPos pos, CompoundTag state) {
        ctx.runOnClient(mc -> {
            try {
                new TestSchematic("Placement " + name, 1, 1, 1).set(0, 0, 0, state)
                        .place(mc.gameDirectory.toPath().resolve("schematics"), pos);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private static BlockState stairs(Direction facing, Half half) {
        return Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, facing)
                .setValue(StairBlock.HALF, half);
    }

    private static CompoundTag stairsTag(String facing, String half) {
        return block("minecraft:oak_stairs", "facing=" + facing, "half=" + half, "shape=straight", "waterlogged=false");
    }

    private static BlockState slab(SlabType type) {
        return Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, type);
    }

    /** The schematic entry for a state, with all its properties. */
    private static CompoundTag tag(BlockState state) {
        List<String> properties = new ArrayList<>();
        for (Property<?> property : state.getProperties()) {
            properties.add(property.getName() + "=" + valueName(state, property));
        }
        return block(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), properties.toArray(String[]::new));
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private static BlockState state(ClientGameTestContext ctx, BlockPos pos) {
        return ctx.computeOnClient(mc -> mc.level.getBlockState(pos));
    }

    private static void check(List<String> problems, String problem, boolean ok) {
        if (!ok) {
            problems.add(problem);
        }
    }

    private static void run(TestSingleplayerContext world, String command) {
        world.getServer().runCommand(command);
    }

    private static String at(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
}
