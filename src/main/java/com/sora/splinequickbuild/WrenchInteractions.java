package com.sora.splinequickbuild;

import com.sora.splineroads.world.NodeEntity;
import com.sora.splineroads.world.RoadData;
import com.sora.splineroads.world.RoadTool;
import com.sora.splineroads.SplineRoads;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Makes shift + right click on an endpoint remove it, for our wrench and for Create's wrench
 * (Create's own item cannot be extended, so this is handled as an interaction event).
 */
@EventBusSubscriber(modid = QuickBuild.MOD_ID)
public final class WrenchInteractions {
   private WrenchInteractions() {
   }

   @SubscribeEvent
   public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
      if (event.getLevel().isClientSide) {
         return;
      }

      if (!(event.getEntity() instanceof ServerPlayer player) || !player.isShiftKeyDown()) {
         return;
      }

      ItemStack stack = event.getItemStack();
      if (!(stack.getItem() instanceof QuickWrench) && !isCreateWrench(stack)) {
         return;
      }

      ServerLevel level = player.serverLevel();
      BlockPos pos = RoadTool.endpointAtHit(level, event.getPos(), event.getHitVec().getLocation());
      if (!(level.getBlockEntity(pos) instanceof NodeEntity)) {
         return;
      }

      try {
         RoadData.get(level).eraseEndpoint(player, pos);
         if (!player.isCreative()) {
            // Give the endpoint block item back: placing it consumed one.
            player.getInventory().placeItemBackInInventory(new ItemStack(SplineRoads.NODE_ITEM.get()));
         }

         player.displayClientMessage(Component.literal("端点已拆除"), true);
      } catch (IllegalArgumentException error) {
         player.displayClientMessage(Component.literal(error.getMessage()), false);
      }

      event.setCanceled(true);
      event.setCancellationResult(InteractionResult.SUCCESS);
   }

   static boolean isCreateWrench(ItemStack stack) {
      ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
      return id.getNamespace().equals("create") && id.getPath().equals("wrench");
   }
}
