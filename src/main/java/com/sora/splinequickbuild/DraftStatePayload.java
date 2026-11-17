package com.sora.splinequickbuild;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server: whether the player currently has a pending road draft (endpoint A and B chosen,
 * road screen closed with Cancel).
 *
 * <p>Needed because the server cannot tell "second endpoint not chosen yet" apart from "draft
 * pending"; in the latter case right clicking bare ground must reopen the draft instead of placing
 * a new endpoint.</p>
 */
public record DraftStatePayload(boolean pending) implements CustomPacketPayload {
   public static final CustomPacketPayload.Type<DraftStatePayload> TYPE = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(QuickBuild.MOD_ID, "draft_state")
   );
   public static final StreamCodec<ByteBuf, DraftStatePayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.BOOL, DraftStatePayload::pending, DraftStatePayload::new
   );

   @Override
   public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
