package net.vulkanmod.vulkan;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.framebuffer.RenderPass2;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.PipelineState;
import net.vulkanmod.vulkan.shader.UniformState;
import net.vulkanmod.vulkan.util.ColorUtil;
import net.vulkanmod.vulkan.util.MappedBuffer;
import net.vulkanmod.vulkan.util.VUtil;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static net.vulkanmod.vulkan.framebuffer.AttachmentTypes.*;
import static net.vulkanmod.vulkan.shader.UniformState.MVP;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_VERTEX_BIT;
import static org.lwjgl.vulkan.VK10.vkCmdPushConstants;

public abstract class VRenderSystem {
    private static long window;

    public static boolean depthTest = true;
    public static boolean depthMask = true;
    public static int depthFun = 515;

    public static int colorMask = PipelineState.ColorMask.getColorMask(true, true, true, true);

    public static boolean cull = true;
    private static boolean canApplyClear = false;
    public static boolean logicOp = false;
    public static int logicOpFun = 0;
    public static boolean useLines = false;

    public static final float clearDepth = 1.0f;
    public static FloatBuffer clearColor = MemoryUtil.memCallocFloat(4);
    private static final float[] checkedClearColor = new float[4];
    public static float alphaCutout = 0.0f;

    private static final float[] depthBias = new float[2];
    private static boolean sampleShadingEnable= Initializer.CONFIG.ssaaPreset >0;
    private static int sampleCount= 1 << Initializer.CONFIG.ssaaPreset;
    private static float minSampleShading= 0.01f*Initializer.CONFIG.minSampleShading;
    static boolean renderPassUpdate =true;

    public static void initRenderer()
    {
        RenderSystem.assertInInitPhase();

        Vulkan.initVulkan(window);
    }

    public static MappedBuffer getScreenSize() {
        updateScreenSize();
        return UniformState.ScreenSize.getMappedBufferPtr();
    }

    public static void updateScreenSize() {
        Window window = Minecraft.getInstance().getWindow();

        UniformState.ScreenSize.getMappedBufferPtr().putFloat(0, window.getWidth());
        UniformState.ScreenSize.getMappedBufferPtr().putFloat(4, window.getHeight());
    }

    public static void setWindow(long window) {
        VRenderSystem.window = window;
    }

    public static int maxSupportedTextureSize() {
        return DeviceManager.deviceProperties.limits().maxImageDimension2D();
    }

    public static void applyMVP(Matrix4f MV, Matrix4f P) {
        MV.get(UniformState.ModelViewMat.buffer().asFloatBuffer());//MemoryUtil.memPutFloat(MemoryUtil.memAddress(modelViewMatrix), 1);
        P.get(UniformState.ProjMat.buffer().asFloatBuffer());
        calculateMVP();
    }

    public static void applyModelViewMatrix(Matrix4f mat) {
        mat.get(UniformState.ModelViewMat.buffer().asFloatBuffer());

        //MemoryUtil.memPutFloat(MemoryUtil.memAddress(modelViewMatrix), 1);
    }
    //TODO: SKip if Hashcode Matches
    public static void applyProjectionMatrix(Matrix4f mat) {
        mat.get(UniformState.ProjMat.buffer().asFloatBuffer());
    }

    public static void calculateMVP() {
        Matrix4f MV = new Matrix4f(UniformState.ModelViewMat.buffer().asFloatBuffer());
        Matrix4f P = new Matrix4f(UniformState.ProjMat.buffer().asFloatBuffer());

        final Matrix4f mul = P.mul(MV);
        mul.get(MVP.buffer());
        MVP.needsUpdate(mul.hashCode());
    }

    public static void setTextureMatrix(Matrix4f mat) {



//        if(UniformState.TextureMat.needsUpdate(mat.hashCode()))
//        {
//            mat.getToAddress(UniformState.TextureMat.getMappedBufferPtr().ptr);
//            VK10.nvkCmdPushConstants(Renderer.getCommandBuffer(), Pipeline.getLayout(), VK_SHADER_STAGE_VERTEX_BIT, 0, 32, UniformState.TextureMat.getMappedBufferPtr().ptr);
//            UniformState.TextureMat.resetAndUpdate();
//        }
    }
    public static void setChunkOffset(float f1, float f2, float f3) {
        long ptr = UniformState.ChunkOffset.getMappedBufferPtr().ptr;
        VUtil.UNSAFE.putFloat(ptr, f1);
        VUtil.UNSAFE.putFloat(ptr + 4, f2);
        VUtil.UNSAFE.putFloat(ptr + 8, f3);
    }

    public static boolean isSampleShadingEnable() {
        return sampleShadingEnable;
    }

    public static int getSampleCount() {
        return sampleCount;
    }

    public static void setShaderColor(float f1, float f2, float f3, float f4) {

    }
    //TOOD: Schedule update when actually unique data has been provided
    public static void setShaderFogColor(float f1, float f2, float f3, float f4) {
        ColorUtil.setRGBA_Buffer(UniformState.FogColor.getMappedBufferPtr(), f1, f2, f3, f4);
    }

    public static MappedBuffer getShaderFogColor() {
        return UniformState.FogColor.getMappedBufferPtr();
    }

    public static void clearColor(float f0, float f1, float f2, float f3) {
        //set to true if different color
        if(!(canApplyClear = checkClearColor(f0, f1, f2, f3))) return;
        ColorUtil.setRGBA_Buffer(clearColor, f0, f1, f2, f3);
        checkedClearColor[0]=f0;
        checkedClearColor[1]=f1;
        checkedClearColor[2]=f2;
        checkedClearColor[3]=f3;
    }

    private static boolean checkClearColor(float f0, float f1, float f2, float f3) {
        return checkedClearColor[0] !=f0 | checkedClearColor[1] !=f1 | checkedClearColor[2] !=f2 | checkedClearColor[3] != f3;
    }

    public static void clear(int v) {
        //Skip reapplying the same color over and over per clear
        //Depth clears are much much faster than color clears
        Renderer.clearAttachments(canApplyClear ? v : GL_DEPTH_BUFFER_BIT); //Depth Only Clears needed to fix Chat + Command Elements
        canApplyClear=false;
    }

    // Pipeline state

    public static void disableDepthTest() {
        depthTest = false;
    }

    public static void depthMask(boolean b) {
        depthMask = b;
    }

    public static void colorMask(boolean b, boolean b1, boolean b2, boolean b3) {
        colorMask = PipelineState.ColorMask.getColorMask(b, b1, b2, b3);
    }

    public static int getColorMask() {
        return colorMask;
    }

    public static void enableDepthTest() {
        depthTest = true;
    }

    public static void enableCull() {
        cull = true;
    }

    public static void disableCull() {
        cull = false;
    }

    public static void depthFunc(int depthFun) {
        VRenderSystem.depthFun = depthFun;
    }

    public static void enableBlend() {
        PipelineState.blendInfo.enabled = true;
    }

    public static void disableBlend() {
        PipelineState.blendInfo.enabled = false;
    }

    public static void blendFunc(GlStateManager.SourceFactor sourceFactor, GlStateManager.DestFactor destFactor) {
        PipelineState.blendInfo.setBlendFunction(sourceFactor, destFactor);
    }

    public static void blendFunc(int srcFactor, int dstFactor) {
        PipelineState.blendInfo.setBlendFunction(srcFactor, dstFactor);
    }

    public static void blendFuncSeparate(GlStateManager.SourceFactor p_69417_, GlStateManager.DestFactor p_69418_, GlStateManager.SourceFactor p_69419_, GlStateManager.DestFactor p_69420_) {
        PipelineState.blendInfo.setBlendFuncSeparate(p_69417_, p_69418_, p_69419_, p_69420_);
    }

    public static void blendFuncSeparate(int srcFactorRGB, int dstFactorRGB, int srcFactorAlpha, int dstFactorAlpha) {
        PipelineState.blendInfo.setBlendFuncSeparate(srcFactorRGB, dstFactorRGB, srcFactorAlpha, dstFactorAlpha);
    }

    public static void enableColorLogicOp() {
        logicOp = true;
    }

    public static void disableColorLogicOp() {
        logicOp = false;
    }

    public static void logicOp(GlStateManager.LogicOp logicOp) {
        logicOpFun = logicOp.value;
    }

    public static void polygonOffset(float v, float v1) {
        depthBias[0] = v;
        depthBias[1] = v1;
    }

    public static void enablePolygonOffset() {
        Renderer.setDepthBias(depthBias[0], depthBias[1]);
    }

    public static void disablePolygonOffset() {
        Renderer.setDepthBias(0.0F, 0.0F);
    }

    public static void polygonMode(int i, int j) {
        useLines = j == GL11.GL_LINES;
        /*switch (j)
        {
            case GL11.GL_LINES -> true; *//*VK_PRIMITIVE_TOPOLOGY_LINE_LIST*//*
            case GL11.GL_LINE_STRIP -> true; *//*VK_PRIMITIVE_TOPOLOGY_LINE_STRIP*//*
            case GL11.GL_TRIANGLES, GL11.GL_QUADS -> false; *//*VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST*//*
            case GL11.GL_TRIANGLE_STRIP -> false; *//*VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP*//*
            case GL11.GL_TRIANGLE_FAN -> false; *//*VK_PRIMITIVE_TOPOLOGY_TRIANGLE_FAN*//*
            default -> throw new IllegalStateException("Unexpected value: " + j);
        };*/
    }

    public static void setSkyColor(float f, float g, float h, float i) {
        ColorUtil.setRGBA_Buffer(UniformState.SkyColor.getMappedBufferPtr(), f, g, h, i);
    }

    public static PipelineState.MultiSampleState getMultiSampleState() {
        return new PipelineState.MultiSampleState(sampleShadingEnable, sampleCount, minSampleShading);
    }

    public static void setSampleShadingEnable(boolean sampleShadingEnable1) {
        sampleShadingEnable= sampleShadingEnable1;
    }

    public static void setMinSampleShading(float minSampleShading1) {
        minSampleShading= minSampleShading1;
    }

    public static void setSampleCountFromPreset(int s) {
        sampleCount= 1<<s;
    }

    static RenderPass2 getDefaultRenderPassState() {
        return isSampleShadingEnable() ? new RenderPass2(
                PRESENT_RESOLVE,
                COLOR,
                DEPTH) : new RenderPass2(PRESENT, DEPTH);
    }

    public static void reInit() {
        renderPassUpdate =true;
    }
}
