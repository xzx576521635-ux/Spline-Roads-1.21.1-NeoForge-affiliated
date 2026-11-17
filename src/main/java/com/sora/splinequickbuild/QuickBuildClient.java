package com.sora.splinequickbuild;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sora.splineroads.SplineRoads;
import com.sora.splineroads.client.RoadScreen;
import com.sora.splineroads.core.RoadGeometry;
import com.sora.splineroads.world.NodeEntity;
import com.sora.splineroads.world.RoadTool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Client-side preview for the quick build tool: a straight guide line from the placed endpoint
 * to the crosshair-selected block, plus a bottom-left HUD readout (coords, displacement, grade).
 *
 * <p>The start point is tracked client-side: the base mod stores its selection in the tool's NBT
 * on the server, and item NBT changes are not synced back to the client.</p>
 */
@EventBusSubscriber(modid = QuickBuild.MOD_ID, value = Dist.CLIENT)
public final class QuickBuildClient {
   private static BlockPos startPos;
   private static ResourceLocation startDimension;
   private static BlockPos stableEnd;
   private static BlockPos pendingEnd;
   private static BlockPos frozenEnd;
   private static long pendingSince;
   private static BlockPos curveStart;
   private static BlockPos curveEnd;
   private static List<Vec3> curvePoints;

   private QuickBuildClient() {
   }

   /** Polls the "force straight" keybind once per client tick. */
   @SubscribeEvent
   public static void clientTick(ClientTickEvent.Post event) {
      QuickBuildKeys.tick();
      updateGlint(Minecraft.getInstance());
   }

   /** Makes the held tool glint while a start point is selected (visual only, client stack). */
   private static void updateGlint(Minecraft mc) {
      if (mc.player == null) {
         return;
      }

      ItemStack stack = null;
      if (mc.player.getMainHandItem().getItem() instanceof QuickBuildTool) {
         stack = mc.player.getMainHandItem();
      } else if (mc.player.getOffhandItem().getItem() instanceof QuickBuildTool) {
         stack = mc.player.getOffhandItem();
      }

      if (stack == null || stack.isEmpty()) {
         return;
      }

      boolean glow = startPos != null;
      boolean current = Boolean.TRUE.equals(stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
      if (glow && !current) {
         stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE);
      } else if (!glow && current) {
         stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
      }
   }

   /** Called from the item on the client for every block right click. */
   public static void trackClick(UseOnContext context) {
      Level level = context.getLevel();
      if (!(context.getPlayer() instanceof Player player)) {
         return;
      }

      if (startPos != null && (startDimension == null || !startDimension.equals(level.dimension().location()))) {
         reset();
      }

      if (startPos == null) {
         if (!player.isCreative() && !hasNodeItem(player)) {
            return;
         }

         // Starting from an existing endpoint: use the endpoint's own block, not the block next to
         // the clicked face. Otherwise the reported start sits one block high and a flat road shows
         // a fake grade.
         BlockPos endpoint = RoadTool.endpointAtHit(level, context.getClickedPos(), context.getClickLocation());
         if (level.getBlockEntity(endpoint) instanceof NodeEntity) {
            startPos = endpoint;
         } else {
            startPos = new BlockPlaceContext(context).getClickedPos();
         }

         startDimension = level.dimension().location();
         stableEnd = null;
         pendingEnd = null;
      } else {
         // Second click: the draft now has a fixed end. Freeze the preview there (the base mod keeps
         // this draft alive until the road is built or the selection is cancelled).
         BlockPos endpoint = RoadTool.endpointAtHit(level, context.getClickedPos(), context.getClickLocation());
         frozenEnd = level.getBlockEntity(endpoint) instanceof NodeEntity
            ? endpoint
            : new BlockPlaceContext(context).getClickedPos();
         invalidateCurve();
      }
   }

   public static void reset() {
      if (frozenEnd != null) {
         frozenEnd = null;
         PacketDistributor.sendToServer(new DraftStatePayload(false));
      }

      startPos = null;
      startDimension = null;
      stableEnd = null;
      pendingEnd = null;
      curveStart = null;
      curveEnd = null;
      curvePoints = null;
   }

   public static boolean hasFrozenEnd() {
      return frozenEnd != null;
   }

   /** Server-driven selection sync (initial click, chained builds, cancel, rejoin). */
   public static void setStart(long packed, String dimension) {
      if (packed == 0L) {
         reset();
         return;
      }

      BlockPos pos = BlockPos.of(packed);
      ResourceLocation dim = dimension == null || dimension.isEmpty() ? null : ResourceLocation.tryParse(dimension);
      if (!pos.equals(startPos) || dim == null || !dim.equals(startDimension)) {
         startPos = pos;
         startDimension = dim;
         if (frozenEnd != null) {
            frozenEnd = null;
            PacketDistributor.sendToServer(new DraftStatePayload(false));
         }

         invalidateCurve();
      }
   }

   @SubscribeEvent
   public static void renderLine(RenderLevelStageEvent event) {
      if (event.getStage() != Stage.AFTER_TRANSLUCENT_BLOCKS) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      Preview preview = preview(mc);
      if (preview == null) {
         return;
      }

      Vec3 camera = event.getCamera().getPosition();
      PoseStack pose = event.getPoseStack();
      pose.pushPose();
      pose.translate(-camera.x, -camera.y, -camera.z);

      MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
      VertexConsumer line = buffers.getBuffer(RenderType.lines());
      List<Vec3> points = curve(mc, preview);
      for (int i = 1; i < points.size(); i++) {
         thickSegment(pose, line, points.get(i - 1), points.get(i), camera, 0.45F, 0.85F, 1.0F, 1.0F);
      }

      buffers.endBatch(RenderType.lines());

      pose.popPose();
   }

   @SubscribeEvent
   public static void renderHud(RenderGuiEvent.Post event) {
      Minecraft mc = Minecraft.getInstance();
      Preview preview = preview(mc);
      if (preview == null) {
         return;
      }

      GuiGraphics graphics = event.getGuiGraphics();
      Metrics metrics = metrics(preview);

      int x = 8;
      int lines = 8;
      int lineHeight = 11;
      int y = mc.getWindow().getGuiScaledHeight() - 8 - lines * lineHeight;

      ItemStack held = mc.player.getMainHandItem().getItem() instanceof QuickBuildTool
         ? mc.player.getMainHandItem()
         : mc.player.getOffhandItem();
      String durability = held.isDamageableItem()
         ? "  耐久 " + (held.getMaxDamage() - held.getDamageValue()) + "/" + held.getMaxDamage()
         : "";
      graphics.drawString(mc.font, Component.literal("快速建造工具" + durability), x, y, 0x55FFFF, true);
      graphics.drawString(
         mc.font,
         Component.literal("模式  " + (QuickBuildKeys.straight() ? "强制直线" : "自动曲线") + "  (V 切换)"),
         x,
         y + lineHeight,
         QuickBuildKeys.straight() ? 0x7CFC00 : 0xA0A0A0,
         true
      );
      graphics.drawString(mc.font, Component.literal("起点  " + shortPos(preview.startPos())), x, y + lineHeight * 2, 0xFFFFFF, true);
      graphics.drawString(mc.font, Component.literal("终点  " + shortPos(preview.endPos())), x, y + lineHeight * 3, 0xFFFFFF, true);
      graphics.drawString(
         mc.font,
         Component.literal(
            String.format(
               "水平位移  ΔX %d  ΔZ %d  (直线 %.1f / 曲线 %.1f 格)",
               metrics.dx(),
               metrics.dz(),
               metrics.horizontal(),
               curveLength(curve(mc, preview))
            )
         ),
         x,
         y + lineHeight * 4,
         0xC8C8C8,
         true
      );
      graphics.drawString(
         mc.font,
         Component.literal(String.format("高差  %+d 格   坡度  %+.1f%%", metrics.dy(), metrics.grade())),
         x,
         y + lineHeight * 5,
         Math.abs(metrics.grade()) > 12.0 ? 0xFFAA55 : 0xE0E0E0,
         true
      );
      graphics.drawString(mc.font, Component.literal("右键：设置终点并打开道路配置"), x, y + lineHeight * 6, 0x909090, true);
      graphics.drawString(mc.font, Component.literal("Shift+右键空气：取消选择"), x, y + lineHeight * 7, 0x909090, true);
   }

   /** Material bill for the road currently configured in the road screen. */
   @SubscribeEvent
   public static void screenClosing(ScreenEvent.Closing event) {
      if (event.getScreen() instanceof RoadScreen && frozenEnd != null) {
         // The draft is now pending on the server as well, so clicking bare ground reopens it
         // instead of placing another endpoint.
         PacketDistributor.sendToServer(new DraftStatePayload(true));
      }
   }

   /** Material bill for the road currently configured in the road screen. */
   @SubscribeEvent
   public static void screenRender(ScreenEvent.Render.Post event) {
      if (!(event.getScreen() instanceof RoadScreen road)) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) {
         return;
      }

      RoadGeometry.Settings settings = road.currentSettingsOrNull();
      if (settings == null) {
         return;
      }

      CompoundTag draft = road.draftTag();
      if (!draft.contains("A") || !draft.contains("B")) {
         return;
      }

      BlockPos a = BlockPos.of(draft.getLong("A"));
      BlockPos b = BlockPos.of(draft.getLong("B"));
      Map<Item, Integer> bill = RoadMaterialCost.bill(a, b, settings);
      if (bill.isEmpty()) {
         return;
      }

      GuiGraphics graphics = event.getGuiGraphics();
      int panelX = road.panelRight();
      int panelY = road.panelTop();
      int panelHeight = road.panelHeight();
      int panelWidth = Math.max(96, Math.min(140, event.getScreen().width - panelX - 2));

      // Same panel colour as the road screen, with the accent bar reused as the divider.
      graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, -334356180);
      graphics.fill(panelX, panelY, panelX + 2, panelY + panelHeight, -11477293);
      graphics.drawString(mc.font, Component.literal("材料需求"), panelX + 10, panelY + 8, 0xFFFFC24A, true);
      graphics.fill(panelX + 8, panelY + 20, panelX + panelWidth - 8, panelY + 21, 0x40FFFFFF);

      int maxRows = Math.max(1, (panelHeight - 44) / 20);
      int rows = Math.min(bill.size(), maxRows);

      boolean creative = mc.player.isCreative();
      int y = panelY + 26;
      int drawn = 0;

      for (Map.Entry<Item, Integer> entry : bill.entrySet()) {
         if (drawn++ >= rows) {
            break;
         }

         graphics.renderItem(new ItemStack(entry.getKey()), panelX + 8, y);
         int have = creative ? entry.getValue() : countInInventory(mc.player, entry.getKey());
         boolean enough = have >= entry.getValue();
         String text = entry.getValue() + "  (" + have + ")";
         graphics.drawString(mc.font, text, panelX + 30, y + 4, enough ? 0x55FF55 : 0xFF5555, true);
         y += 20;
      }

      if (bill.size() > rows) {
         graphics.drawString(mc.font, Component.literal("+" + (bill.size() - rows) + " 项…"), panelX + 10, y + 2, 0x909090, true);
      }
   }

   private static int countInInventory(Player player, Item item) {
      int total = 0;

      for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
         ItemStack stack = player.getInventory().getItem(i);
         if (stack.is(item)) {
            total += stack.getCount();
         }
      }

      return total;
   }

   /** Drops the cached centre line (the straight/curve mode or endpoints changed). */
   public static void invalidateCurve() {
      curveStart = null;
      curveEnd = null;
      curvePoints = null;
   }

   /**
    * Sampled centre line of the road the base mod would build for the current endpoints, so the
    * guide line shows the real curve (AUTO planning) or a straight line in force-straight mode.
    */
   private static List<Vec3> curve(Minecraft mc, Preview preview) {
      BlockPos start = preview.startPos();
      BlockPos end = preview.endPos();
      if (start.equals(curveStart) && end.equals(curveEnd) && curvePoints != null) {
         return curvePoints;
      }

      List<Vec3> points;
      try {
         RoadGeometry.Node a = nodeAt(mc, start, mc.player == null ? 0.0 : mc.player.getYRot());
         RoadGeometry.Node b = nodeAt(mc, end, mc.player == null ? 0.0 : mc.player.getYRot());
         RoadGeometry.Mode mode = QuickBuildKeys.straight() ? RoadGeometry.Mode.STRAIGHT : RoadGeometry.Mode.AUTO;
         RoadGeometry.Settings settings = new RoadGeometry.Settings(
            mode, RoadGeometry.Style.O2_YELLOW, 9.0, 1.0, 0.35, 90.0
         );
         RoadGeometry.Mesh mesh = RoadGeometry.build(a, b, settings);
         List<Vec3> sampled = new ArrayList<>(mesh.samples().size());

         for (RoadGeometry.Sample sample : mesh.samples()) {
            RoadGeometry.V center = sample.center();
            sampled.add(new Vec3(center.x(), center.y(), center.z()));
         }

         points = sampled.size() < 2 ? List.of(preview.start(), preview.end()) : sampled;
      } catch (Throwable ignored) {
         points = List.of(preview.start(), preview.end());
      }

      curveStart = start;
      curveEnd = end;
      curvePoints = points;
      return points;
   }

   private static RoadGeometry.Node nodeAt(Minecraft mc, BlockPos pos, double fallbackYaw) {
      if (mc.level != null && mc.level.getBlockEntity(pos) instanceof NodeEntity entity) {
         return entity.constructionNode();
      }

      return new RoadGeometry.Node(new RoadGeometry.V(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5), fallbackYaw, 0.0);
   }

   private static double curveLength(List<Vec3> points) {
      double length = 0.0;

      for (int i = 1; i < points.size(); i++) {
         length += points.get(i - 1).distanceTo(points.get(i));
      }

      return length;
   }

   private static Preview preview(Minecraft mc) {
      if (mc.player == null || mc.level == null) {
         return null;
      }

      InteractionHand hand = hand(mc.player);
      if (hand == null) {
         return null;
      }

      if (startPos == null || startDimension == null || !startDimension.equals(mc.level.dimension().location())) {
         return null;
      }

      BlockPos endPos;
      if (frozenEnd != null) {
         // Pending draft (screen closed with Cancel): keep the end fixed.
         endPos = frozenEnd;
      } else {
         BlockHitResult hit = hit(mc);
         if (hit == null) {
            return null;
         }

         endPos = RoadTool.endpointAtHit(mc.level, hit.getBlockPos(), hit.getLocation());
         if (!(mc.level.getBlockEntity(endPos) instanceof NodeEntity)) {
            endPos = new BlockPlaceContext(new UseOnContext(mc.player, hand, hit)).getClickedPos();
         }

         endPos = stabilize(endPos);
      }

      return new Preview(nodePosition(mc, startPos), anchor(endPos), startPos, endPos);
   }

   /**
    * Keeps the selected end block for a moment before switching: when the crosshair sits on a
    * block edge the ray can flip between two neighbours every frame, which made the line and the
    * HUD numbers jitter back and forth.
    */
   private static BlockPos stabilize(BlockPos candidate) {
      long now = System.currentTimeMillis();
      if (stableEnd == null) {
         stableEnd = candidate;
         pendingEnd = null;
         return stableEnd;
      }

      if (candidate.equals(stableEnd)) {
         pendingEnd = null;
         return stableEnd;
      }

      // A clear jump (more than ~2.8 blocks) switches immediately.
      if (candidate.distSqr(stableEnd) > 8.0) {
         stableEnd = candidate;
         pendingEnd = null;
         return stableEnd;
      }

      if (!candidate.equals(pendingEnd)) {
         pendingEnd = candidate;
         pendingSince = now;
         return stableEnd;
      }

      if (now - pendingSince >= 120L) {
         stableEnd = candidate;
         pendingEnd = null;
      }

      return stableEnd;
   }

   /**
    * Metrics use whole block coordinates, so a flat road reads exactly 0% even when the crosshair
    * is on a block face or the node entity carries a small vertical offset.
    */
   private static Metrics metrics(Preview preview) {
      BlockPos start = preview.startPos();
      BlockPos end = preview.endPos();
      int dx = end.getX() - start.getX();
      int dy = end.getY() - start.getY();
      int dz = end.getZ() - start.getZ();
      double horizontal = Math.sqrt((double)(dx * dx + dz * dz));
      double grade = horizontal > 1.0E-4 ? (double)dy / horizontal * 100.0 : 0.0;
      return new Metrics(dx, dy, dz, horizontal, grade);
   }

   private static InteractionHand hand(Player player) {
      if (tool(player.getMainHandItem()) != null) {
         return InteractionHand.MAIN_HAND;
      }

      return tool(player.getOffhandItem()) != null ? InteractionHand.OFF_HAND : null;
   }

   /** Node anchor point for a block: centred in X/Z, sitting on the block's bottom face. */
   private static Vec3 anchor(BlockPos pos) {
      return new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
   }

   private static boolean hasNodeItem(Player player) {
      return player.getInventory().findSlotMatchingItem(new ItemStack(SplineRoads.NODE_ITEM.get())) >= 0;
   }

   private static ItemStack tool(ItemStack stack) {
      return stack.getItem() instanceof QuickBuildTool ? stack : null;
   }

   private static Vec3 nodePosition(Minecraft mc, BlockPos pos) {
      BlockEntity entity = mc.level.getBlockEntity(pos);
      if (entity instanceof NodeEntity node) {
         var point = node.node().position();
         return new Vec3(point.x(), point.y(), point.z());
      }

      return anchor(pos);
   }

   private static BlockHitResult hit(Minecraft mc) {
      return mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK ? hit : null;
   }

   private static String shortPos(BlockPos pos) {
      return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
   }

   /**
    * Draws the guide line as several parallel 1px lines offset perpendicular to the view, which
    * looks like a ~5px thick line without relying on {@code glLineWidth} (not supported above 1px
    * on core profiles).
    */
   private static void thickSegment(
      PoseStack pose, VertexConsumer buffer, Vec3 from, Vec3 to, Vec3 camera, float red, float green, float blue, float alpha
   ) {
      Vec3 delta = to.subtract(from);
      double length = delta.length();
      if (length < 1.0E-6) {
         return;
      }

      Vec3 direction = delta.scale(1.0 / length);
      Vec3 view = camera.subtract(from);
      if (view.lengthSqr() < 1.0E-6) {
         return;
      }

      Vec3 side = direction.cross(view.normalize());
      if (side.lengthSqr() < 1.0E-8) {
         return;
      }

      side = side.normalize();
      // Scale with distance so the on-screen width stays roughly constant.
      double width = Math.max(0.004, from.distanceTo(camera) * 0.0018);

      for (int i = -2; i <= 2; i++) {
         Vec3 offset = side.scale(width * (double)i);
         segment(pose, buffer, from.add(offset), to.add(offset), red, green, blue, alpha);
      }
   }

   private static void segment(
      PoseStack pose, VertexConsumer buffer, Vec3 from, Vec3 to, float red, float green, float blue, float alpha
   ) {
      Vec3 delta = to.subtract(from);
      double length = delta.length();
      float nx = 0.0F;
      float ny = 0.0F;
      float nz = 0.0F;
      if (length > 1.0E-6) {
         nx = (float)(delta.x / length);
         ny = (float)(delta.y / length);
         nz = (float)(delta.z / length);
      }

      PoseStack.Pose last = pose.last();
      buffer.addVertex(last, (float)from.x, (float)from.y, (float)from.z)
         .setColor(red, green, blue, alpha)
         .setNormal(last, nx, ny, nz);
      buffer.addVertex(last, (float)to.x, (float)to.y, (float)to.z)
         .setColor(red, green, blue, alpha)
         .setNormal(last, nx, ny, nz);
   }

   private record Preview(Vec3 start, Vec3 end, BlockPos startPos, BlockPos endPos) {
   }

   private record Metrics(int dx, int dy, int dz, double horizontal, double grade) {
   }
}
