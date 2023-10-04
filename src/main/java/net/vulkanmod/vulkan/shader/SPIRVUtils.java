package net.vulkanmod.vulkan.shader;

import org.lwjgl.system.NativeResource;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.util.shaderc.Shaderc.*;
import static org.lwjgl.vulkan.VK10.*;

public class SPIRVUtils {
    private static final boolean DEBUG = false;
    private static final boolean OPTIMIZATIONS = true;

    private static long compiler;

    public static SPIRV compileShaderAbsoluteFile(String shaderFile, ShaderKind shaderKind) {
        try {
            String source = new String(Files.readAllBytes(Paths.get(new URI(shaderFile))));
            return compileShader(shaderFile, source, shaderKind);
        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static SPIRV compileShader(String filename, String source, ShaderKind shaderKind) {

        if(compiler == 0) compiler = shaderc_compiler_initialize();

        if(compiler == NULL) {
            throw new RuntimeException("Failed to create shader compiler");
        }

        long options = shaderc_compile_options_initialize();

        if(options == NULL) {
            throw new RuntimeException("Failed to create compiler options");
        }

        if(OPTIMIZATIONS)
            shaderc_compile_options_set_optimization_level(options, shaderc_optimization_level_performance);

        if(DEBUG)
            shaderc_compile_options_set_generate_debug_info(options);

        long result = shaderc_compile_into_spv(compiler, source, shaderKind.kind, filename, "main", options);

        if(result == NULL) {
            throw new RuntimeException("Failed to compile shader " + filename + " into SPIR-V");
        }

        if(shaderc_result_get_compilation_status(result) != shaderc_compilation_status_success) {
            throw new RuntimeException("Failed to compile shader " + filename + " into SPIR-V:\n" + shaderc_result_get_error_message(result));
        }

        return new SPIRV(result, shaderKind.shaderStageBit, (int) shaderc_result_get_length(result));
    }

//    private static SPIRV readFromStream(InputStream inputStream) {
//        try {
//            byte[] bytes = inputStream.readAllBytes();
//            ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
//            buffer.put(bytes);
//            buffer.position(0);
//
//            return new SPIRV(MemoryUtil.memAddress(buffer), shaderKind.shaderStageBit, buffer);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        throw new RuntimeException("unable to read inputStream");
//    }

    public enum ShaderKind {
        VERTEX_SHADER(shaderc_glsl_vertex_shader, VK_SHADER_STAGE_VERTEX_BIT),
        GEOMETRY_SHADER(shaderc_glsl_geometry_shader, VK_SHADER_STAGE_GEOMETRY_BIT),
        FRAGMENT_SHADER(shaderc_glsl_fragment_shader, VK_SHADER_STAGE_FRAGMENT_BIT),
        COMPUTE_SHADER(shaderc_glsl_compute_shader, VK_SHADER_STAGE_COMPUTE_BIT);

        private final int kind;
        private final int shaderStageBit;

        ShaderKind(int kind, int shaderStageBit) {
            this.kind = kind;
            this.shaderStageBit = shaderStageBit;
        }
    }

    public record SPIRV(long handle, int shaderStageBit, int size_t) implements NativeResource {

        public ByteBuffer bytecode() {
            return shaderc_result_get_bytes(handle, size_t);
        }

        @Override
        public void free() {
            shaderc_result_release(handle);
        }
    }

}