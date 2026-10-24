package dev.andre.modelanchor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class ObjAttachmentLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final Identifier WHITE_TEXTURE =
            Identifier.fromNamespaceAndPath(ModelAnchorClient.MOD_ID, "textures/white.png");
    private static final RenderType MODEL_RENDER_TYPE = RenderTypes.entityCutoutNoCull(WHITE_TEXTURE);

    public ObjAttachmentLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                       AvatarRenderState state, float limbAngle, float limbDistance) {
        if (state.isSpectator) return;
        // These attachments are a local cosmetic configuration, so draw them only on the local avatar.
        if (Minecraft.getInstance().player == null || state.id != Minecraft.getInstance().player.getId()) return;
        List<Attachment> attachments = AttachmentStore.snapshot();
        if (attachments.isEmpty()) return;
        PlayerModel model = getParentModel();

        for (Attachment attachment : attachments) {
            if (attachment == null || !attachment.enabled || attachment.path == null || attachment.path.isBlank()) continue;
            ObjMesh mesh = ObjMeshCache.get(attachment.path);
            if (mesh == null) continue;
            ModelPart anchor = partFor(model, attachment.part);
            if (anchor == null) continue;

            poseStack.pushPose();
            model.root().translateAndRotate(poseStack);
            anchor.translateAndRotate(poseStack);
            // Offsets use sixteenths of a block, matching Minecraft model pixels.
            poseStack.translate(attachment.x / 16.0, attachment.y / 16.0, attachment.z / 16.0);
            poseStack.mulPose(Axis.XP.rotationDegrees((float) attachment.rotateX));
            poseStack.mulPose(Axis.YP.rotationDegrees((float) attachment.rotateY));
            poseStack.mulPose(Axis.ZP.rotationDegrees((float) attachment.rotateZ));
            float scale = (float) Math.max(0.001, Math.min(100.0, attachment.scale));
            // OBJ uses the common Y-up convention; humanoid model geometry uses Y-down coordinates.
            poseStack.scale(scale, -scale, scale);
            collector.submitCustomGeometry(poseStack, MODEL_RENDER_TYPE, (pose, vertexConsumer) -> mesh.emit(pose, vertexConsumer, packedLight));
            poseStack.popPose();
        }
    }

    private static ModelPart partFor(PlayerModel model, String name) {
        if (name == null) return model.head;
        return switch (name.toUpperCase()) {
            case "BODY", "TORSO" -> model.body;
            case "RIGHT_ARM" -> model.rightArm;
            case "LEFT_ARM" -> model.leftArm;
            case "RIGHT_LEG" -> model.rightLeg;
            case "LEFT_LEG" -> model.leftLeg;
            case "HEAD" -> model.head;
            default -> model.head;
        };
    }
}
