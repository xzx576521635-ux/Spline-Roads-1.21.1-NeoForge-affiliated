package com.sora.splinequickbuild;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

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

   /** Server-side mirror of the client's "force straight" toggle. */
   private static final Map<UUID, Boolean> STRAIGHT = new ConcurrentHashMap<>();

   public QuickBuild(IEventBus modBus, ModContainer container) {
      ITEMS.register(modBus);
      modBus.addListener(QuickBuild::addToCreativeTab);
      modBus.addListener(QuickBuild::registerPayloads);
   }

   public static boolean straight(Player player) {
      return STRAIGHT.getOrDefault(player.getUUID(), Boolean.FALSE);
   }

   private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
      ResourceLocation key = event.getTabKey().location();
      if (key.getNamespace().equals("splineroads") && key.getPath().equals("roads")) {
         event.accept(QUICK_BUILD_TOOL.get());
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
   }
}
