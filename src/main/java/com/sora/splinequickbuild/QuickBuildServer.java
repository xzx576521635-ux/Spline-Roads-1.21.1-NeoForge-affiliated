package com.sora.splinequickbuild;

import com.sora.splineroads.ItemNBT;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Mirrors the tool's server-side selection ({@code Start} in the item NBT) to the owning client.
 *
 * <p>Item NBT is never synced back to the client, so after a road is built the base mod chains the
 * selection to the next endpoint but the client would not know. Watching the NBT here keeps the
 * preview line, HUD and glint working across chained builds.</p>
 */
@EventBusSubscriber(modid = QuickBuild.MOD_ID)
public final class QuickBuildServer {
   private static final Map<UUID, Long> LAST_START = new ConcurrentHashMap<>();

   private QuickBuildServer() {
   }

   @SubscribeEvent
   public static void tick(ServerTickEvent.Post event) {
      for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
         ItemStack tool = tool(player);
         if (tool == null) {
            LAST_START.remove(player.getUUID());
            continue;
         }

         CompoundTag tag = ItemNBT.get(tool);
         long start = tag.contains("Start") ? tag.getLong("Start") : 0L;
         String dimension = tag.getString("Dimension");
         Long previous = LAST_START.put(player.getUUID(), start);
         if (previous == null || previous != start) {
            PacketDistributor.sendToPlayer(player, new StartPointPayload(start, dimension));
         }
      }
   }

   private static ItemStack tool(ServerPlayer player) {
      if (player.getMainHandItem().getItem() instanceof QuickBuildTool) {
         return player.getMainHandItem();
      }

      return player.getOffhandItem().getItem() instanceof QuickBuildTool ? player.getOffhandItem() : null;
   }
}
