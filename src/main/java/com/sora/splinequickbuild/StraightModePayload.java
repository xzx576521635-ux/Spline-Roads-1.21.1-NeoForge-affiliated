package com.sora.splinequickbuild;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server: whether the quick build tool should force {@code Mode.STRAIGHT}.
 *
 * <p>The toggle has to live on the server because the road draft is created server-side; the
 * client's item stack NBT is never sent back.</p>
 */
public record StraightModePayload(boolean straight) implements CustomPacketPayload {
   public static final CustomPacketPayload.Type<StraightModePayload> TYPE = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(QuickBuild.MOD_ID, "straight_mode")
   );
   public static final StreamCodec<ByteBuf, StraightModePayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.BOOL, StraightModePayload::straight, StraightModePayload::new
   );

   @Override
   public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
