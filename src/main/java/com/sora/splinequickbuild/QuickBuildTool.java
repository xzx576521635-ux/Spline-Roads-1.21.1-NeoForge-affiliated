package com.sora.splinequickbuild;

import com.sora.splineroads.ItemNBT;
import com.sora.splineroads.SplineRoads;
import com.sora.splineroads.world.NodeEntity;
import com.sora.splineroads.world.RoadTool;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Places a road endpoint on the first right click, shows a live line while walking to the
 * next endpoint, and opens the normal road configuration screen on the second click.
 *
 * <p>Extends the base mod's {@link RoadTool} so all of its server-side checks
 * ({@code RoadNetwork.usable(...)}, node highlight, NBT selection) treat this item as a road
 * connector. The only extra step is placing the {@code road_node} endpoint block first, because
 * the base connector only links endpoints that already exist.</p>
 *
 * <p>Survival players need one {@code splineroads:road_node} item per endpoint; creative mode is
 * exempt.</p>
 */
public final class QuickBuildTool extends RoadTool {
   public QuickBuildTool() {
      super(new Item.Properties().stacksTo(1).durability(384));
   }

   @Override
   public InteractionResult useOn(UseOnContext context) {
      if (context.getLevel().isClientSide) {
         if (QuickBuildClient.hasFrozenEnd()) {
            // A draft is already pending (screen closed with Cancel): clicking bare ground must
            // reopen it instead of starting a new selection, exactly like the base mod.
            return super.use(context.getLevel(), context.getPlayer(), context.getHand()).getResult();
         }

         // The server keeps its selection in the stack NBT, which is not synced back to the
         // client; track the start point on the client so the preview has something to draw.
         QuickBuildClient.trackClick(context);
         return InteractionResult.SUCCESS;
      }

      if (!(context.getPlayer() instanceof ServerPlayer player)) {
         return InteractionResult.PASS;
      }

      ServerLevel level = player.serverLevel();
      // Force straight is toggled on the client and mirrored to the server; write it into the
      // tool NBT so the base mod builds its draft in Mode.STRAIGHT.
      ItemNBT.edit(context.getItemInHand()).putBoolean("ForceStraight", QuickBuild.straight(player));
      BlockPos endpoint = RoadTool.endpointAtHit(level, context.getClickedPos(), context.getClickLocation());
      boolean placed = false;
      if (!(level.getBlockEntity(endpoint) instanceof NodeEntity)) {
         if (QuickBuild.pendingDraft(player)) {
            // The client reopened the pending draft; do not place another endpoint on the server.
            return InteractionResult.CONSUME;
         }

         endpoint = placeEndpoint(player, context);
         if (endpoint == null) {
            return InteractionResult.CONSUME;
         }

         placed = true;
      }

      if (placed && !player.isCreative()) {
         ItemStack tool = context.getItemInHand();
         if (tool.isDamageableItem()) {
            tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
         }
      }

      BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(endpoint), Direction.UP, endpoint, false);
      return super.useOn(new UseOnContext(player, context.getHand(), hit));
   }

   @Override
   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      if (level.isClientSide) {
         if (player.isShiftKeyDown()) {
            QuickBuildClient.reset();
            return InteractionResultHolder.success(player.getItemInHand(hand));
         }

         // Keep the base mod's "resume" behaviour: after closing the road screen with Cancel,
         // right clicking again reopens the pending draft instead of starting a new selection.
         return super.use(level, player, hand);
      }

      return super.use(level, player, hand);
   }

   /**
    * Places a {@code road_node} block exactly like the block item would (same placement rules,
    * claim checks and collision handling), consuming one item in survival. Returns the position
    * of the new endpoint, or {@code null} when placement failed.
    */
   private static BlockPos placeEndpoint(ServerPlayer player, UseOnContext context) {
      ServerLevel level = player.serverLevel();
      BlockPos target = new BlockPlaceContext(context).getClickedPos();

      if (!player.isCreative() && nodeSlot(player) < 0) {
         player.displayClientMessage(Component.literal("背包里没有「道路端点」方块，无法放置端点"), true);
         return null;
      }

      BlockHitResult hit = new BlockHitResult(
         context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), context.isInside()
      );
      ItemStack nodeStack = new ItemStack(SplineRoads.NODE_ITEM.get());
      UseOnContext placing = new UseOnContext(player, context.getHand(), hit) {
         @Override
         public ItemStack getItemInHand() {
            return nodeStack;
         }
      };

      Item item = SplineRoads.NODE_ITEM.get();
      if (!(item instanceof BlockItem blockItem)) {
         return null;
      }

      blockItem.useOn(placing);

      if (!(level.getBlockEntity(target) instanceof NodeEntity)) {
         player.displayClientMessage(Component.literal("这个位置无法放置道路端点"), true);
         return null;
      }

      if (!player.isCreative()) {
         int slot = nodeSlot(player);
         if (slot >= 0) {
            player.getInventory().removeItem(slot, 1);
         }
      }

      return target;
   }

   private static int nodeSlot(Player player) {
      return player.getInventory().findSlotMatchingItem(new ItemStack(SplineRoads.NODE_ITEM.get()));
   }
}
