package com.sora.splinequickbuild;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.sora.splineroads.world.RoadCostHooks;
import com.sora.splineroads.world.RoadTool;

/**
 * Spline Roads: Quick Build - add-on that adds a single "quick build tool".
 *
 * <p>The tool delegates the actual road endpoint / draft logic to the base mod's
 * {@code RoadTool}; this add-on only contributes the item, the client-side preview and the
 * "force straight" toggle.</p>
 */
@Mod(QuickBuild.MOD_ID)
public final class QuickBuild {
   public static final String MOD_ID = "splinequickbuild";
   public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MOD_ID);
   public static final DeferredHolder<Item, QuickBuildTool> QUICK_BUILD_TOOL = ITEMS.register(
      "quick_build_tool", QuickBuildTool::new
   );
   public static final DeferredHolder<Item, QuickWrench> QUICK_WRENCH = ITEMS.register(
      "quick_wrench", QuickWrench::new
   );

   /** Server-side mirror of the client's "force straight" toggle. */
   private static final Map<UUID, Boolean> STRAIGHT = new ConcurrentHashMap<>();
   /** Server-side mirror of "a road draft is pending" (screen closed with Cancel). */
   private static final Map<UUID, Boolean> PENDING_DRAFT = new ConcurrentHashMap<>();

   public QuickBuild(IEventBus modBus, ModContainer container) {
      ITEMS.register(modBus);
      modBus.addListener(QuickBuild::addToCreativeTab);
      modBus.addListener(QuickBuild::registerPayloads);
      // Survival balance only exists while this add-on is installed.
      RoadCostHooks.register(new RoadMaterialCost());
      // Endpoints stay visible while holding our wrench (or Create's).
      RoadTool.registerNodeViewer(QuickBuild::viewsEndpoints);
   }

   private static boolean viewsEndpoints(ItemStack stack) {
      if (stack.getItem() instanceof QuickWrench) {
         return true;
      }

      ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
      return id.getNamespace().equals("create") && id.getPath().equals("wrench");
   }

   public static boolean straight(Player player) {
      return STRAIGHT.getOrDefault(player.getUUID(), Boolean.FALSE);
   }

   public static boolean pendingDraft(Player player) {
      return PENDING_DRAFT.getOrDefault(player.getUUID(), Boolean.FALSE);
   }

   private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
      ResourceLocation key = event.getTabKey().location();
      if (key.getNamespace().equals("splineroads") && key.getPath().equals("roads")) {
         event.accept(QUICK_BUILD_TOOL.get());
         event.accept(QUICK_WRENCH.get());
      }
   }

   private static void registerPayloads(RegisterPayloadHandlersEvent event) {
      PayloadRegistrar registrar = event.registrar("1");
      registrar.playToServer(
         StraightModePayload.TYPE,
         StraightModePayload.STREAM_CODEC,
         (payload, context) -> context.enqueueWork(() -> {
            if (context.player() != null) {
               STRAIGHT.put(context.player().getUUID(), payload.straight());
            }
         })
      );
      registrar.playToClient(
         StartPointPayload.TYPE,
         StartPointPayload.STREAM_CODEC,
         (payload, context) -> context.enqueueWork(() -> QuickBuildClient.setStart(payload.pos(), payload.dimension()))
      );
      registrar.playToServer(
         DraftStatePayload.TYPE,
         DraftStatePayload.STREAM_CODEC,
         (payload, context) -> context.enqueueWork(() -> {
            if (context.player() != null) {
               PENDING_DRAFT.put(context.player().getUUID(), payload.pending());
            }
         })
      );
   }
}
