package me.aleksilassila.litematica.printer.guides.placement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import me.aleksilassila.litematica.printer.SchematicBlockState;
import me.aleksilassila.litematica.printer.actions.Action;
import me.aleksilassila.litematica.printer.actions.PrepareAction;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.implementation.PrinterPlacementContext;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class RotatingBlockGuide extends GeneralPlacementGuide {
    public RotatingBlockGuide(SchematicBlockState state) {
        super(state);
    }

    @Override
    protected List<Direction> getPossibleSides() {
        Block block = state.targetState.getBlock();
        if (block instanceof WallSkullBlock || block instanceof WallSignBlock || block instanceof WallBannerBlock) {
            Optional<Direction> side = getProperty(state.targetState, BlockStateProperties.HORIZONTAL_FACING)
                    .map(Direction::getOpposite);
            return side.map(Collections::singletonList).orElseGet(Collections::emptyList);
        }

        return Collections.singletonList(Direction.DOWN);
    }

    @Override
    public boolean skipOtherGuides() {
        return true;
    }

    /** With rotate on, the printer turns the player to the schematic's rotation right before placing. */
    @Override
    protected boolean isRightResult(BlockState result) {
        if (Configs.ROTATE.getBooleanValue()) {
            return statesEqualIgnoreProperties(result, targetState, BlockStateProperties.ROTATION_16);
        }
        return super.isRightResult(result);
    }

    @Override
    public @Nonnull List<Action> execute(LocalPlayer player) {
        PrinterPlacementContext ctx = getPlacementContext(player);

        if (ctx == null)
            return new ArrayList<>();

        int rotation = getProperty(state.targetState, BlockStateProperties.ROTATION_16).orElse(0);
        if (targetState.getBlock() instanceof BannerBlock || targetState.getBlock() instanceof StandingSignBlock) {
            rotation = (rotation + 8) % 16;
        }

        int distTo0 = rotation > 8 ? 16 - rotation : rotation;
        float yaw = Math.round(distTo0 / 8f * 180f * (rotation > 8 ? -1 : 1));

        List<Action> actions = super.execute(player);
        actions.set(0, new PrepareAction(ctx, yaw, 0));

        return actions;
    }
}
