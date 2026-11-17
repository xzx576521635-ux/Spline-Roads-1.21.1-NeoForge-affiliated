package com.sora.splinequickbuild;

import com.sora.splineroads.core.RoadGeometry;
import com.sora.splineroads.core.RoadInfrastructure;
import com.sora.splineroads.core.RoadProfile;
import com.sora.splineroads.core.RoadSidewalks;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Bill of materials for a new road, derived from the settings chosen in the road screen.
 *
 * <p>Rules of thumb (the add-on cannot count every generated part without rebuilding the whole
 * geometry):</p>
 *
 * <ul>
 *   <li>pavement: 1 asphalt block per block of deck area (length x width)</li>
 *   <li>sidewalk: 1 chosen finish block per block of sidewalk area</li>
 *   <li>median rail: 1 iron bar per 2 blocks; median green: dirt/leaves</li>
 *   <li>gantries: iron ingots + iron bars per gantry</li>
 *   <li>bridges: smooth stone + iron ingots for supports; tunnels: stone bricks</li>
 *   <li>lamps: 1 iron bar every 12 blocks</li>
 *   <li>trees: 2 saplings + 4 leaves every 12 blocks</li>
 * </ul>
 */
public final class RoadBill {
   private static final ResourceLocation ASPHALT = ResourceLocation.fromNamespaceAndPath("createdieselgenerators", "asphalt_block");
   private static final ResourceLocation ASPHALT_FALLBACK = ResourceLocation.fromNamespaceAndPath("splineroads", "road_block");

   public static Map<Item, Integer> compute(RoadGeometry.Settings settings, double length) {
      Map<Item, Integer> bill = new LinkedHashMap<>();
      double width = Math.max(1.0, settings.width());
      double deck = Math.max(1.0, length * width);
      add(bill, asphalt(), (int)Math.ceil(deck));

      RoadSidewalks.Config walk = settings.options().sidewalk();
      if (walk != null && walk.enabled()) {
         int sides = walk.side() == RoadSidewalks.Side.BOTH ? 2 : 1;
         int blocks = (int)Math.ceil(length * Math.max(1, walk.width()) * sides);
         add(bill, finishItem(walk.material()), Math.max(1, blocks));
      }

      RoadProfile.Median median = RoadProfile.catalog(settings).median();
      if (median == RoadProfile.Median.RAIL) {
         add(bill, Items.IRON_BARS, (int)Math.max(1, Math.ceil(length / 2.0)));
      } else if (median == RoadProfile.Median.GREEN) {
         int rows = (int)Math.max(1, Math.ceil(length / 4.0));
         add(bill, Items.DIRT, rows);
         add(bill, Items.OAK_LEAVES, rows * 2);
      }

      RoadInfrastructure.Gantry gantry = settings.options().infrastructure().gantry();
      if (gantry != RoadInfrastructure.Gantry.OFF) {
         double spacing = Math.max(8.0, settings.options().infrastructure().spacing());
         int count = (int)Math.max(1, Math.ceil(length / spacing));
         add(bill, Items.IRON_INGOT, count * 4);
         add(bill, Items.IRON_BARS, count * 2);
      }

      RoadGeometry.Structure structure = settings.structure();
      if (structure == RoadGeometry.Structure.BRIDGE) {
         add(bill, Items.SMOOTH_STONE, (int)Math.max(1, Math.ceil(deck * 0.35)));
         add(bill, Items.IRON_INGOT, (int)Math.max(1, Math.ceil(length / 12.0)));
      } else if (structure == RoadGeometry.Structure.TUNNEL) {
         add(bill, Items.STONE_BRICKS, (int)Math.max(1, Math.ceil(length * 6.0)));
      }

      int lamps = (int)Math.max(1, Math.ceil(length / 12.0));
      add(bill, Items.IRON_BARS, lamps);

      int trees = (int)Math.max(0, Math.ceil(length / 12.0) - 1);
      if (trees > 0) {
         add(bill, Items.OAK_SAPLING, trees * 2);
         add(bill, Items.OAK_LEAVES, trees * 4);
      }

      return bill;
   }

   private static void add(Map<Item, Integer> bill, Item item, int count) {
      if (item != null && item != Items.AIR && count > 0) {
         bill.merge(item, count, Integer::sum);
      }
   }

   private static Item asphalt() {
      Item block = item(ASPHALT.getNamespace(), ASPHALT.getPath(), null);
      return block != null ? block : item(ASPHALT_FALLBACK.getNamespace(), ASPHALT_FALLBACK.getPath(), Items.BLACK_CONCRETE);
   }

   /** Sidewalk finishes store a full id ("minecraft:stone_bricks"), so do not prefix a namespace. */
   private static Item finishItem(String material) {
      ResourceLocation id = material.contains(":")
         ? ResourceLocation.tryParse(material)
         : ResourceLocation.fromNamespaceAndPath("minecraft", material);
      if (id == null) {
         return Items.STONE_BRICKS;
      }

      return item(id.getNamespace(), id.getPath(), Items.STONE_BRICKS);
   }

   private static Item item(String namespace, String path, Item fallback) {
      ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, path);
      for (Item candidate : BuiltInRegistries.ITEM) {
         if (BuiltInRegistries.ITEM.getKey(candidate).equals(id)) {
            return candidate;
         }
      }

      return fallback;
   }

   private RoadBill() {
   }
}
