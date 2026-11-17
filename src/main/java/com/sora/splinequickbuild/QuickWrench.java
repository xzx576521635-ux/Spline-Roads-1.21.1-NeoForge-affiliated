package com.sora.splinequickbuild;

import com.sora.splineroads.world.NodeEntity;
import com.sora.splineroads.world.RoadData;
import com.sora.splineroads.world.RoadTool;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Survival helper: shift + right click an endpoint to remove it instantly.
 *
 * <p>Holding the wrench also makes endpoints visible (registered as a node viewer on the base
 * mod). Create's wrench is accepted as well, so players only need one tool on their bar.</p>
 */
public final class QuickWrench extends Item {
   public QuickWrench() {
      super(new Item.Properties().stacksTo(1));
   }

   @Override
   public InteractionResult useOn(UseOnContext context) {
      if (context.getLevel().isClientSide) {
         return InteractionResult.SUCCESS;
      }

      if (!(context.getPlayer() instanceof ServerPlayer player)) {
         return InteractionResult.PASS;
      }

      Level level = player.serverLevel();
      BlockPos pos = RoadTool.endpointAtHit(level, context.getClickedPos(), context.getClickLocation());
      if (!(level.getBlockEntity(pos) instanceof NodeEntity)) {
         player.displayClientMessage(Component.literal("这里没有道路端点"), true);
         return InteractionResult.CONSUME;
      }

      if (!player.isShiftKeyDown()) {
         player.displayClientMessage(Component.literal("Shift + 右键可以拆除这个端点"), true);
         return InteractionResult.CONSUME;
      }

      try {
         RoadData.get((ServerLevel)level).eraseEndpoint(player, pos);
         player.displayClientMessage(Component.literal("端点已拆除"), true);
      } catch (IllegalArgumentException error) {
         player.displayClientMessage(Component.literal(error.getMessage()), false);
      }

      return InteractionResult.CONSUME;
   }
}
