package com.sora.splinequickbuild;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Client key handling for the "force straight" toggle (default {@code V}).
 *
 * <p>The toggle is mirrored to the server with {@link StraightModePayload} because the road draft
 * is created server-side and the client's item NBT is not synced back.</p>
 */
@EventBusSubscriber(modid = QuickBuild.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class QuickBuildKeys {
   public static final KeyMapping STRAIGHT = new KeyMapping(
      "key.splinequickbuild.straight", GLFW.GLFW_KEY_V, "key.categories.splinequickbuild"
   );

   private static boolean straight;

   private QuickBuildKeys() {
   }

   public static boolean straight() {
      return straight;
   }

   @SubscribeEvent
   public static void register(RegisterKeyMappingsEvent event) {
      event.register(STRAIGHT);
   }

   /** Game-bus handler, registered from {@link QuickBuildClient}. */
   public static void tick() {
      boolean changed = false;

      while (STRAIGHT.consumeClick()) {
         straight = !straight;
         changed = true;
      }

      if (changed) {
         QuickBuildClient.invalidateCurve();
         PacketDistributor.sendToServer(new StraightModePayload(straight));
         if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance()
               .player
               .displayClientMessage(
                  Component.literal("快速建造：路径模式 = " + (straight ? "强制直线" : "自动曲线")), true
               );
         }
      }
   }
}
