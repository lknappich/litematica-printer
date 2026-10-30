package me.aleksilassila.litematica.printer.guides;

import me.aleksilassila.litematica.printer.SchematicBlockState;
import me.aleksilassila.litematica.printer.actions.Action;
import me.aleksilassila.litematica.printer.actions.PrepareAction;
import me.aleksilassila.litematica.printer.actions.UseItemAction;
import me.aleksilassila.litematica.printer.implementation.PrinterPlacementContext;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A guide that takes a bucket and uses it looking where {@link #canExecute} decided. */
public abstract class BucketGuide extends Guide {
    /**
     * Spots a bucket was just used on. The client does not predict every bucket use (waterlogging happens on the
     * server only), so the spot still looks unchanged for a moment and would get a second bucket.
     */
    private static final Map<BlockPos, Long> USED = new HashMap<>();
    private static final int WAIT_TICKS = 40;

    protected FluidRules.Aim aim;

    public BucketGuide(SchematicBlockState state) {
        super(state);
    }

    /** True while the server has not had the time to answer the last bucket used on this spot. */
    protected boolean waitingForServer() {
        long now = state.world.getGameTime();
        USED.values().removeIf(time -> now - time > WAIT_TICKS || now < time);
        return USED.containsKey(state.blockPos);
    }

    @Override
    public @Nonnull List<Action> execute(LocalPlayer player) {
        List<Action> actions = new ArrayList<>();
        ItemStack bucket = getRequiredItem(player).orElse(ItemStack.EMPTY);
        int slot = getRequiredItemStackSlot(player);

        if (aim == null || slot == -1) {
            return actions;
        }

        USED.put(state.blockPos.immutable(), state.world.getGameTime());

        // only used to pick the bucket; where it goes depends on the view alone
        BlockHitResult hitResult = new BlockHitResult(Vec3.atCenterOf(state.blockPos), Direction.UP, state.blockPos, false);
        actions.add(new PrepareAction(new PrinterPlacementContext(player, hitResult, bucket, slot)));
        actions.add(new UseItemAction(aim.yaw(), aim.pitch()));

        return actions;
    }
}
