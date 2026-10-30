package me.aleksilassila.litematica.printer.guides;

import me.aleksilassila.litematica.printer.SchematicBlockState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Rules for building with water and lava: what has to stand before a fluid goes in, which blocks a fluid washes
 * away, and where to look with a bucket (buckets cast their own ray from the player's view instead of using the
 * clicked block).
 */
public final class FluidRules {
    /** Where a source spreads to. */
    private static final Direction[] SPREAD = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    /** Where fluid can run into a block from. */
    private static final Direction[] INFLOW = {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    /** Water runs at most 7 blocks from a source, so the rim further away does not matter yet. */
    private static final int FLOW_DISTANCE = 8;
    /** Spots of a pool checked at most, for pools that fall down a long way. */
    private static final int MAX_POOL = 1024;

    public record Aim(float yaw, float pitch) {
    }

    private FluidRules() {
    }

    /**
     * True when the blocks that hold this spot's fluid in already stand as in the schematic. The fluid runs through
     * the spots of the schematic's pool that are still empty, so the pool's rim counts, not just this spot's.
     */
    public static boolean isContained(SchematicBlockState state, Fluid fluid) {
        Set<BlockPos> pool = new HashSet<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        pool.add(state.blockPos);
        open.add(state.blockPos);
        while (!open.isEmpty() && pool.size() < MAX_POOL) {
            BlockPos spot = open.poll();
            for (Direction direction : SPREAD) {
                BlockPos pos = spot.relative(direction);
                BlockState wanted = state.schematic.getBlockState(pos);
                if (wanted.getFluidState().getType().isSame(fluid)) {
                    int distance = Math.abs(pos.getX() - state.blockPos.getX()) + Math.abs(pos.getZ() - state.blockPos.getZ());
                    if (distance <= FLOW_DISTANCE && pool.add(pos)) {
                        open.add(pos);
                    }
                } else if (!wanted.isAir() && state.world.getBlockState(pos).getBlock() != wanted.getBlock()) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Torches, rails, redstone and the like: blocks that flowing water or lava destroys (as FlowingFluid#canHoldAnyFluid). */
    public static boolean washesAway(BlockState state) {
        return !state.isAir() && !(state.getBlock() instanceof LiquidBlockContainer)
                && state.is(BlockTags.WASHED_AWAY_BY_FLUIDS);
    }

    /**
     * True when water or lava that the schematic does not have there stands next to this spot and can run into it,
     * e.g. a river beside the build. Anything that fluid washes away would be gone right after placing.
     */
    public static boolean strayFluidNear(SchematicBlockState state) {
        for (Direction direction : INFLOW) {
            BlockPos pos = state.blockPos.relative(direction);
            BlockState found = state.world.getBlockState(pos);
            if (!(found.getBlock() instanceof LiquidBlock)) {
                continue;
            }
            Fluid fluid = found.getFluidState().getType();
            if (!state.schematic.getBlockState(pos).getFluidState().getType().isSame(fluid)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Where to look so that a filled bucket pours into this spot: at the face of a neighbouring block that touches
     * it, since vanilla puts the fluid in front of the face it hits. Null when no such face can be hit from here.
     */
    @Nullable
    public static Aim aimNextTo(LocalPlayer player, SchematicBlockState state, Fluid fluid) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = state.blockPos.relative(direction);
            BlockState neighborState = state.world.getBlockState(neighbor);
            if (neighborState.getShape(state.world, neighbor).isEmpty()) {
                continue;
            }
            // a block that can take the fluid in (a dry slab, say) would be filled instead of the spot in front of it
            if (neighborState.getBlock() instanceof LiquidBlockContainer container
                    && container.canPlaceLiquid(player, state.world, neighbor, neighborState, fluid)) {
                continue;
            }
            Vec3 face = Vec3.atCenterOf(state.blockPos).add(Vec3.atLowerCornerOf(direction.getUnitVec3i()).scale(0.5));
            Aim aim = aim(player, state.world, facePoints(face, direction), neighbor, direction.getOpposite(),
                    ClipContext.Fluid.NONE);
            if (aim != null) {
                return aim;
            }
        }
        return null;
    }

    /** Where to look to hit this block itself, to fill it with water or to scoop the water out of it. */
    @Nullable
    public static Aim aimAt(LocalPlayer player, Level world, BlockPos pos, ClipContext.Fluid fluidMode) {
        VoxelShape shape = world.getBlockState(pos).getShape(world, pos);
        if (shape.isEmpty()) {
            return null;
        }
        AABB box = shape.bounds().move(pos);
        Vec3 center = box.getCenter();
        List<Vec3> points = new ArrayList<>();
        points.add(center);
        for (Direction direction : Direction.values()) {
            // just inside each side of the outline
            double half = direction.getAxis().choose(box.getXsize(), box.getYsize(), box.getZsize()) / 2;
            points.add(center.add(Vec3.atLowerCornerOf(direction.getUnitVec3i()).scale(Math.max(0, half - 0.05))));
        }
        return aim(player, world, points, pos, null, fluidMode);
    }

    private static List<Vec3> facePoints(Vec3 center, Direction direction) {
        List<Vec3> points = new ArrayList<>();
        points.add(center);
        for (Direction side : Direction.values()) {
            if (side.getAxis() != direction.getAxis()) {
                points.add(center.add(Vec3.atLowerCornerOf(side.getUnitVec3i()).scale(0.3)));
            }
        }
        return points;
    }

    /** The first view towards one of the points whose bucket ray really hits the block (and face). */
    @Nullable
    private static Aim aim(LocalPlayer player, Level world, List<Vec3> points, BlockPos pos, @Nullable Direction face,
                           ClipContext.Fluid fluidMode) {
        Vec3 eye = player.getEyePosition();
        double range = player.blockInteractionRange();
        for (Vec3 point : points) {
            Vec3 to = point.subtract(eye);
            double horizontal = Math.sqrt(to.x * to.x + to.z * to.z);
            float yaw = Mth.wrapDegrees((float) Math.toDegrees(Mth.atan2(to.z, to.x)) - 90f);
            float pitch = Mth.wrapDegrees((float) -Math.toDegrees(Mth.atan2(to.y, horizontal)));
            // the same ray a bucket casts (Item#getPlayerPOVHitResult), from the view the server gets
            Vec3 end = eye.add(Vec3.directionFromRotation(pitch, yaw).scale(range));
            BlockHitResult hit = world.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, fluidMode, player));
            if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)
                    && (face == null || hit.getDirection() == face)) {
                return new Aim(yaw, pitch);
            }
        }
        return null;
    }
}
