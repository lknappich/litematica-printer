package me.aleksilassila.litematica.printer.actions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;

/**
 * Uses the item in hand while looking in the given direction. Items like buckets ignore the clicked block and cast
 * their own ray from the player's view, which the server takes from the use packet.
 */
public class UseItemAction extends Action {
    public final float yaw;
    public final float pitch;

    public UseItemAction(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    @Override
    public void send(Minecraft client, LocalPlayer player) {
        if (client.gameMode == null) {
            return;
        }

        // the packet and the client's own prediction read the player's view; the camera is put back right after
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        player.setYRot(this.yaw);
        player.setXRot(this.pitch);
        client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        player.setYRot(yaw);
        player.setXRot(pitch);
    }

    @Override
    public String toString() {
        return "UseItemAction{" +
                "yaw=" + yaw +
                ", pitch=" + pitch +
                '}';
    }
}
