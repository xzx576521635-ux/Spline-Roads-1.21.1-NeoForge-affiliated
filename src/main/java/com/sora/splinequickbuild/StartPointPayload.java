package com.sora.splinequickbuild;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client: the tool's current start endpoint (or {@code 0} when nothing is selected).
 *
 * <p>Used after a road is built: the base mod chains the selection to the next endpoint by writing
 * {@code Start} in the item NBT, which the client never sees. This payload keeps the preview, the
 * glint and the HUD in sync with the server.</p>
 */
public record StartPointPayload(long pos, String dimension) implements CustomPacketPayload {
   public static final CustomPacketPayload.Type<StartPointPayload> TYPE = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(QuickBuild.MOD_ID, "start_point")
   );
   public static final StreamCodec<ByteBuf, StartPointPayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_LONG, StartPointPayload::pos,
      ByteBufCodecs.STRING_UTF8, StartPointPayload::dimension,
      StartPointPayload::new
   );

   @Override
   public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
