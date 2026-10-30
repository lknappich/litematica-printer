package me.aleksilassila.litematica.printer.guides.placement;

import me.aleksilassila.litematica.printer.Printer;
import me.aleksilassila.litematica.printer.SchematicBlockState;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.implementation.PrinterPlacementContext;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * An old school guide where there are defined specific conditions
 * for player state depending on the block being placed.
 */
public class GeneralPlacementGuide extends PlacementGuide {
    /**
     * Which side of the target the click comes from. In the air there is no block on that side, so the target
     * spot itself is clicked, at the same point and on the same face as the neighbour would be.
     */
    private record Click(Direction side, boolean inAir) {
    }

    public GeneralPlacementGuide(SchematicBlockState state) {
        super(state);
    }

    protected List<Direction> getPossibleSides() {
        return Arrays.asList(Direction.values());
    }

    protected Optional<Direction> getLookDirection() {
        return Optional.empty();
    }

    protected boolean getRequiresSupport() {
        return false;
    }

    protected boolean getRequiresExplicitShift() {
        return false;
    }

    protected Vec3 getHitModifier(Direction validSide) {
        return new Vec3(0, 0, 0);
    }

    private Optional<Click> getClick(SchematicBlockState state) {
        List<Direction> sides = getPossibleSides();

        if (sides.isEmpty()) {
            return Optional.empty();
        }

        List<Direction> validSides = new ArrayList<>();
        for (Direction side : sides) {
            SchematicBlockState neighborState = state.offset(side);

            if (getProperty(neighborState.currentState, SlabBlock.TYPE).orElse(null) == SlabType.DOUBLE) {
                validSides.add(side);
                continue;
            }

            if (canBeClicked(neighborState.world, neighborState.blockPos) && // Handle unclickable grass for example
                    !neighborState.currentState.canBeReplaced())
                validSides.add(side);
        }

        for (Direction validSide : validSides) {
            if (!isInteractive(state.offset(validSide).currentState.getBlock())) {
                return Optional.of(new Click(validSide, false));
            }
        }

        if (!validSides.isEmpty()) {
            return Optional.of(new Click(validSides.getFirst(), false));
        }

        // nothing to click on: with printInAir, click the empty spot itself
        if (Configs.PRINT_IN_AIR.getBooleanValue() && !getRequiresSupport()) {
            return Optional.of(new Click(sides.getFirst(), true));
        }

        return Optional.empty();
    }

    protected boolean getUseShift(SchematicBlockState state) {
        if (getRequiresExplicitShift())
            return true;

        Click click = getClick(state).orElse(null);
        if (click == null || click.inAir())
            return false;
        return isInteractive(state.offset(click.side()).currentState.getBlock());
    }

    @Nullable
    public PrinterPlacementContext getPlacementContext(LocalPlayer player) {
        try {
            Optional<Click> click = getClick(state);
            Optional<ItemStack> requiredItem = getRequiredItem(player);
            int requiredSlot = getRequiredItemStackSlot(player);

            if (click.isEmpty() || requiredItem.isEmpty() || requiredSlot == -1)
                return null;

            if (!click.get().inAir()) {
                return context(player, click.get(), requiredItem.get(), requiredSlot);
            }
            // in the air any side will do, but which one decides how some blocks come out (a slab added to another)
            for (Direction side : getPossibleSides()) {
                PrinterPlacementContext context = context(player, new Click(side, true), requiredItem.get(), requiredSlot);
                if (placesRightBlock(player, context)) {
                    return context;
                }
            }
            return null;
        } catch (Exception e) {
            Printer.LOGGER.error("getPlacementContext(): Exception caught: {}", e.getMessage());
            //e.printStackTrace();
            return null;
        }
    }

    private PrinterPlacementContext context(LocalPlayer player, Click click, ItemStack item, int slot) {
        Direction side = click.side();
        Vec3 hitVec = Vec3.atCenterOf(state.blockPos)
                .add(Vec3.atLowerCornerOf(side.getUnitVec3i()).scale(0.5))
                .add(getHitModifier(side));
        BlockPos clicked = click.inAir() ? state.blockPos : state.blockPos.relative(side);
        BlockHitResult blockHitResult = new BlockHitResult(hitVec, side.getOpposite(), clicked, false);

        return new PrinterPlacementContext(player, blockHitResult, item, slot,
                getLookDirection().orElse(null), getUseShift(state));
    }
}
