package ru.warmine.fairwol.wcglow.glow;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@SideOnly(Side.CLIENT)
public final class EntityOutlineController {

    private static final String[] MAKE_OUTLINE_SHADER_METHODS = {"makeEntityOutlineShader", "func_174966_b"};
    private static final String[] OUTLINE_FRAMEBUFFER_FIELDS = {"entityOutlineFramebuffer", "field_175015_z"};
    private static final String[] OUTLINE_SHADER_FIELDS = {"entityOutlineShader", "field_174991_A"};
    private static final double MAX_DISTANCE_SQ = 40.0D * 40.0D;

    private static boolean enabled;
    private static boolean initializationFailed;

    private EntityOutlineController() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean enabled) {
        EntityOutlineController.enabled = enabled;
        initializationFailed = false;

        if (!enabled) {
            clearOutlineFramebuffer();
            restoreVanillaEntityRenderState(Minecraft.getMinecraft());
        }
    }

    public static boolean toggle() {
        setEnabled(!enabled);
        return enabled;
    }

    public static boolean shouldRenderVanillaEntityOutlines(RenderGlobal renderGlobal) {
        Minecraft mc = Minecraft.getMinecraft();
        if (enabled) {
            return canRenderOutlines(mc, renderGlobal);
        }

        if (mc == null || renderGlobal == null || mc.thePlayer == null || mc.gameSettings == null) {
            return false;
        }

        return getOutlineFramebuffer(renderGlobal) != null
                && getOutlineShader(renderGlobal) != null
                && mc.thePlayer.isSpectator()
                && mc.gameSettings.keyBindSpectatorOutlines.isKeyDown();
    }

    public static boolean renderEntityOutlines(ICamera camera, float partialTicks, double x, double y, double z) {
        Minecraft mc = Minecraft.getMinecraft();
        RenderGlobal renderGlobal = mc != null ? mc.renderGlobal : null;
        boolean shouldRender = canRenderOutlines(mc, renderGlobal);

        if (!shouldRender) {
            clearOutlineFramebuffer(renderGlobal);
            restoreVanillaEntityRenderState(mc);
            return true;
        }

        Framebuffer outlineFramebuffer = getOutlineFramebuffer(renderGlobal);
        ShaderGroup outlineShader = getOutlineShader(renderGlobal);
        if (outlineFramebuffer == null || outlineShader == null) {
            restoreVanillaEntityRenderState(mc);
            return true;
        }

        List<Entity> entitiesToOutline = collectEntities(mc);
        outlineFramebuffer.framebufferClear();

        if (entitiesToOutline.isEmpty()) {
            restoreVanillaEntityRenderState(mc);
            return false;
        }

        RenderManager renderManager = mc.getRenderManager();
        try {
            mc.theWorld.theProfiler.endStartSection("entityOutlines");
            outlineFramebuffer.bindFramebuffer(false);

            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableFog();
            renderManager.setRenderOutlines(true);
            GlStateManager.depthFunc(GL11.GL_ALWAYS);

            for (Entity entity : entitiesToOutline) {
                if (!shouldRender(camera, entity, mc, x, y, z)) {
                    continue;
                }

                try {
                    renderManager.renderEntityStatic(entity, partialTicks, true);
                } catch (Exception ignored) {
                }
            }

            GlStateManager.depthFunc(GL11.GL_LEQUAL);
            GlStateManager.depthMask(false);
            outlineShader.loadShaderGroup(partialTicks);
            GlStateManager.depthMask(true);
        } finally {
            renderManager.setRenderOutlines(false);
            restoreVanillaEntityRenderState(mc);
        }

        return false;
    }

    public static void afterFramebufferDraw() {
        GlStateManager.enableDepth();
    }

    private static List<Entity> collectEntities(Minecraft mc) {
        ArrayList<Entity> entities = new ArrayList<>();
        Entity viewEntity = mc.getRenderViewEntity();

        if (viewEntity == null || mc.theWorld == null) {
            return entities;
        }

        for (Object loadedEntity : mc.theWorld.loadedEntityList) {
            if (!(loadedEntity instanceof Entity)) {
                continue;
            }

            Entity entity = (Entity) loadedEntity;
            if (shouldOutlineEntity(mc, viewEntity, entity)) {
                entities.add(entity);
            }
        }

        return entities;
    }

    private static boolean shouldOutlineEntity(Minecraft mc, Entity viewEntity, Entity entity) {
        if (entity == null || entity.isDead || entity.isInvisible()) {
            return false;
        }

        if ((entity == mc.thePlayer || entity == viewEntity) && mc.gameSettings.thirdPersonView == 0) {
            return false;
        }

        return viewEntity.getDistanceSqToEntity(entity) <= MAX_DISTANCE_SQ;
    }

    private static boolean shouldRender(ICamera camera, Entity entity, Minecraft mc, double x, double y, double z) {
        if (entity == mc.getRenderViewEntity()) {
            boolean playerSleeping = mc.getRenderViewEntity() instanceof EntityLivingBase
                    && ((EntityLivingBase) mc.getRenderViewEntity()).isPlayerSleeping();
            if (!playerSleeping && mc.gameSettings.thirdPersonView == 0) {
                return false;
            }
        }

        return mc.theWorld.isBlockLoaded(new BlockPos(entity))
                && (mc.getRenderManager().shouldRender(entity, camera, x, y, z) || entity.riddenByEntity == mc.thePlayer);
    }

    private static boolean canRenderOutlines(Minecraft mc, RenderGlobal renderGlobal) {
        if (!enabled || initializationFailed || mc == null || renderGlobal == null) {
            return false;
        }

        if (!OpenGlHelper.shadersSupported || !OpenGlHelper.isFramebufferEnabled()) {
            return false;
        }

        if (mc.theWorld == null || mc.thePlayer == null) {
            return false;
        }

        return ensureOutlineShader(renderGlobal);
    }

    private static boolean ensureOutlineShader(RenderGlobal renderGlobal) {
        if (getOutlineFramebuffer(renderGlobal) != null && getOutlineShader(renderGlobal) != null) {
            return true;
        }

        try {
            invokeNoArgs(renderGlobal, MAKE_OUTLINE_SHADER_METHODS);
        } catch (ReflectiveOperationException ex) {
            initializationFailed = true;
            ex.printStackTrace();
            return false;
        }

        boolean initialized = getOutlineFramebuffer(renderGlobal) != null && getOutlineShader(renderGlobal) != null;
        initializationFailed = !initialized;
        return initialized;
    }

    private static void clearOutlineFramebuffer() {
        Minecraft mc = Minecraft.getMinecraft();
        clearOutlineFramebuffer(mc != null ? mc.renderGlobal : null);
    }

    private static void clearOutlineFramebuffer(RenderGlobal renderGlobal) {
        Framebuffer framebuffer = renderGlobal != null ? getOutlineFramebuffer(renderGlobal) : null;
        if (framebuffer != null) {
            framebuffer.framebufferClear();
        }
    }

    private static void restoreVanillaEntityRenderState(Minecraft mc) {
        if (mc == null) {
            return;
        }

        RenderManager renderManager = mc.getRenderManager();
        if (renderManager != null) {
            renderManager.setRenderOutlines(false);
        }

        RenderHelper.enableStandardItemLighting();
        GlStateManager.enableLighting();
        GlStateManager.enableFog();
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.enableCull();
        GlStateManager.depthMask(true);
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.enableTexture2D();
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.enableTexture2D();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);

        mc.getFramebuffer().bindFramebuffer(false);
    }

    private static Framebuffer getOutlineFramebuffer(RenderGlobal renderGlobal) {
        return (Framebuffer) getFieldValue(renderGlobal, OUTLINE_FRAMEBUFFER_FIELDS);
    }

    private static ShaderGroup getOutlineShader(RenderGlobal renderGlobal) {
        return (ShaderGroup) getFieldValue(renderGlobal, OUTLINE_SHADER_FIELDS);
    }

    private static Object getFieldValue(Object instance, String[] names) {
        if (instance == null) {
            return null;
        }

        for (String name : names) {
            try {
                Field field = instance.getClass().getDeclaredField(name);
                field.setAccessible(true);
                return field.get(instance);
            } catch (ReflectiveOperationException ignored) {
            }
        }

        return null;
    }

    private static void invokeNoArgs(Object instance, String[] names) throws ReflectiveOperationException {
        ReflectiveOperationException last = null;

        for (String name : names) {
            try {
                Method method = instance.getClass().getDeclaredMethod(name);
                method.setAccessible(true);
                method.invoke(instance);
                return;
            } catch (ReflectiveOperationException ex) {
                last = ex;
            }
        }

        if (last != null) {
            throw last;
        }
    }
}
