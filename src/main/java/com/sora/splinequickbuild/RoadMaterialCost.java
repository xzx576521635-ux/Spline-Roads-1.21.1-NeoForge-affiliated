package com.sora.splinequickbuild;

import com.sora.splineroads.core.RoadGeometry;
import com.sora.splineroads.world.RoadCostHooks;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Survival balance: new roads (not edits) cost the materials listed by {@link RoadBill}, but only
 * while this add-on is installed.
 *
 * <p>Two phases: {@link #reserve} aborts the build when the player cannot pay (so nothing is
 * written), {@link #charge} deducts after the road was written successfully.</p>
 */
public final class RoadMaterialCost implements RoadCostHooks.Listener {
   @Override
   public void reserve(ServerPlayer player, ServerLevel level, BlockPos a, BlockPos b, RoadGeometry.Settings settings) {
      if (player.isCreative()) {
         return;
      }

      Map<Item, Integer> bill = bill(a, b, settings);
      for (Map.Entry<Item, Integer> entry : bill.entrySet()) {
         int have = count(player, entry.getKey());
         if (have < entry.getValue()) {
            throw new IllegalArgumentException(
               "材料不足：" + entry.getKey().getDescription().getString() + " 需要 " + entry.getValue() + "，背包里有 " + have
            );
         }
      }
   }

   @Override
   public void charge(ServerPlayer player, ServerLevel level, BlockPos a, BlockPos b, RoadGeometry.Settings settings) {
      if (player.isCreative()) {
         return;
      }

      bill(a, b, settings).forEach((item, amount) -> remove(player, item, amount));
      player.getInventory().setChanged();
      player.containerMenu.broadcastChanges();
   }

   static Map<Item, Integer> bill(BlockPos a, BlockPos b, RoadGeometry.Settings settings) {
      double dx = (double)(a.getX() - b.getX());
      double dz = (double)(a.getZ() - b.getZ());
      return RoadBill.compute(settings, Math.sqrt(dx * dx + dz * dz));
   }

   static int count(ServerPlayer player, Item item) {
      int total = 0;

      for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
         ItemStack stack = player.getInventory().getItem(i);
         if (stack.is(item)) {
            total += stack.getCount();
         }
      }

      return total;
   }

   private static void remove(ServerPlayer player, Item item, int amount) {
      int left = amount;

      for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
         ItemStack stack = player.getInventory().getItem(i);
         if (stack.is(item)) {
            int take = Math.min(left, stack.getCount());
            stack.shrink(take);
            left -= take;
         }
      }
   }
}
