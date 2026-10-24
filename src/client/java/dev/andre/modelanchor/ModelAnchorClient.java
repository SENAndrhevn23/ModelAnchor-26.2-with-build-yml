package dev.andre.modelanchor;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.layers.RenderLayerParent;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

@SuppressWarnings("unchecked")
public final class ModelAnchorClient implements ClientModInitializer {
    public static final String MOD_ID = "modelanchor";
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "editor")
    );
    private static final KeyMapping OPEN_EDITOR = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.modelanchor.open_editor",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            CATEGORY
    ));

    @Override
    public void onInitializeClient() {
        AttachmentStore.load();
        ObjMeshCache.preload(AttachmentStore.snapshot());

        LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
            if (entityRenderer instanceof AvatarRenderer<?> avatarRenderer) {
                RenderLayerParent<AvatarRenderState, PlayerModel> parent =
                        (RenderLayerParent<AvatarRenderState, PlayerModel>) (Object) avatarRenderer;
                registrationHelper.register(new ObjAttachmentLayer(parent));
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_EDITOR.consumeClick()) {
                if (client.player != null) {
                    ModelAnchorEditor.open();
                }
            }
        });
    }
}
