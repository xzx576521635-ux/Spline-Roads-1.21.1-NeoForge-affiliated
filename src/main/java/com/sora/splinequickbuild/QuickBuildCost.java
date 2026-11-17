package com.sora.splinequickbuild;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Material cost model shared by the server-side charge and the client-side preview, so both always
 * agree on the numbers.
 */
public final class QuickBuildCost {
   /** Material charged per new road. */
   public static final Item MATERIAL = Items.GRAVEL;
   /** Blocks of road footprint (length x width) per one material item. */
   public static final double BLOCKS_PER_ITEM = 4.0;
   /** Width assumed by the preview before the player picks a style in the road screen. */
   public static final double PREVIEW_WIDTH = 9.0;

   public static int cost(double length, double width) {
      double area = Math.max(1.0, length * Math.max(1.0, width));
      return Math.max(1, (int)Math.ceil(area / BLOCKS_PER_ITEM));
   }

   private QuickBuildCost() {
   }
}
