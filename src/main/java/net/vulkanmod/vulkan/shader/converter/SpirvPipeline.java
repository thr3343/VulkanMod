package net.vulkanmod.vulkan.shader.converter;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.Initializer;
import net.vulkanmod.vulkan.shader.PipelineConfig;
import net.vulkanmod.vulkan.shader.descriptor.ImageDescriptor;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
import net.vulkanmod.vulkan.shader.layout.AlignedStruct;
import net.vulkanmod.vulkan.shader.layout.Uniform;
import org.lwjgl.vulkan.VK11;

import java.util.*;

public class SpirvPipeline {
    final SpirvShader vertexShader;
    final SpirvShader fragShader;

    List<UniformBuffer> uniformBuffers = new ObjectArrayList<>();
    List<Sampler> samplers = new ObjectArrayList<>();

    private final List<DescriptorBinding> bindings = new ObjectArrayList<>();
    private final Map<String, DescriptorBinding> bindingMap = new Object2ObjectOpenHashMap<>();

    public SpirvPipeline(SpirvShader vertexShader, SpirvShader fragShader) {
        this.vertexShader = vertexShader;
        this.fragShader = fragShader;

        this.resolveBindings();
    }

    /***
     * Resolve descriptor bindings from shaders
     */
    private void resolveBindings() {
        // Collect all bindings with their original values and stages
        this.collectBindings(vertexShader.uniformBuffers(), fragShader.uniformBuffers());
        this.collectBindings(vertexShader.samplers(), fragShader.samplers());

        // Populate lists
        updateBindingLists();

        // compact bindings
        compactBindings();

        // Propagate final bindings back to SPIRV
        updateShaderBindings();
    }

    private void collectBindings(List<? extends SpirvShader.SpvBinding> vertexResources,
                                 List<? extends SpirvShader.SpvBinding> fragResources) {
        for (var resource : vertexResources) {
            var descriptorBinding = extractDescriptor(resource, VK11.VK_SHADER_STAGE_VERTEX_BIT);
            bindingMap.put(descriptorBinding.name(), descriptorBinding);
        }

        for (var resource : fragResources) {
            var binding = bindingMap.get(resource.name());
            if (binding == null) {
                var descriptorBinding = extractDescriptor(resource, VK11.VK_SHADER_STAGE_FRAGMENT_BIT);
                bindingMap.put(descriptorBinding.name(), descriptorBinding);
            }
            else {
                binding.addStage(VK11.VK_SHADER_STAGE_FRAGMENT_BIT);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private DescriptorBinding extractDescriptor(SpirvShader.SpvBinding resource, int stage) {
        return switch (resource) {
            case SpirvShader.SpvUniformBuffer ub -> new UniformBuffer(ub.name(), ub.getBinding(), ub.size(), stage);
            case SpirvShader.SpvSampler s -> new Sampler(s.name(), s.getBinding(), stage);
            default -> throw new IllegalStateException("Unexpected value: " + resource.getClass());
        };

    }

    private void updateBindingLists() {
        this.bindings.addAll(this.bindingMap.values()
                                            .stream()
                                            .sorted(Comparator.comparingInt(DescriptorBinding::binding))
                                            .toList());

        for (var b : this.bindings) {
            switch (b) {
                case UniformBuffer ub -> this.uniformBuffers.add(ub);
                case Sampler s -> this.samplers.add(s);
                default -> throw new IllegalStateException("Unexpected value: " + b.getClass());
            }
        }
    }

    private void compactBindings() {
        int nextBinding = 0;
        for (var ub : bindings) {
            int binding =  ub.binding();

            if (binding != nextBinding) {
                ub.setBinding(nextBinding);
            }
            nextBinding++;
        }
    }

    private void updateShaderBindings() {
        for (var ub : vertexShader.uniformBuffers()) {
            var binding = bindingMap.get(ub.name());
            if (binding != null)
                ub.setBinding(binding.binding());
        }
        for (var ub : fragShader.uniformBuffers()) {
            var binding = bindingMap.get(ub.name());
            if (binding != null)
                ub.setBinding(binding.binding());
        }

        for (var sampler : vertexShader.samplers()) {
            var binding = bindingMap.get(sampler.name());
            if (binding != null)
                sampler.setBinding(binding.binding());
        }
        for (var sampler : fragShader.samplers()) {
            var binding = bindingMap.get(sampler.name());
            if (binding != null)
                sampler.setBinding(binding.binding());
        }
    }

    public void updateLocations(List<String> vertexAttributes) {
        int attribLocation = 0;

        List<String> remainingAttributes = new ArrayList<>(vertexAttributes);

        for (int i = 0; i < vertexAttributes.size(); i++) {
            String variableName = vertexAttributes.get(i);
            SpirvShader.SpvVariable inputVariable = vertexShader.getInputVariable(variableName);
            if (inputVariable != null) {
                inputVariable.setLocation(attribLocation);
                remainingAttributes.remove(variableName);
                attribLocation++;
            }
        }

        if (!remainingAttributes.isEmpty()) {
            Initializer.LOGGER.error("Missing attributes: {}", remainingAttributes);
        }

        for (int i = 0; i < vertexShader.outputs().size(); i++) {
            vertexShader.outputs().get(i).setLocation(i);
        }

        // Fragment shader
        List<String> vertexOutputNames = new ArrayList<>();

        for (SpirvShader.SpvVariable output : vertexShader.outputs()) {
            vertexOutputNames.add(output.name());
        }

        List<String> remainingInputs = new ArrayList<>(vertexOutputNames);
        int inputLocation = 0;

        for (String variableName : vertexOutputNames) {
            SpirvShader.SpvVariable inputVariable = fragShader.getInputVariable(variableName);
            if (inputVariable != null) {
                inputVariable.setLocation(inputLocation);
                remainingInputs.remove(variableName);
                inputLocation++;
            }
        }

        if (!remainingInputs.isEmpty()) {
            Initializer.LOGGER.error("Shader expects inputs which are not being provided: {}", remainingInputs);
        }
    }

    public List<ImageDescriptor> getSamplerList() {
        List<ImageDescriptor> imageDescriptors = new ObjectArrayList<>();

        int imageIdx = 0;
        for (var sampler : this.samplers) {

            int descriptorType = VK11.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;

            int binding = sampler.binding();
            imageDescriptors.add(new ImageDescriptor(binding, "sampler2D", sampler.name(), imageIdx, descriptorType));
            imageIdx++;
        }

        return imageDescriptors;
    }

    public List<ImageDescriptor> getSamplerList(PipelineConfig config) {
        List<ImageDescriptor> imageDescriptors = new ObjectArrayList<>();

        var descriptorInfos = config.imageDescriptors;

        for (var descriptorInfo : descriptorInfos) {
            int descriptorType = VK11.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;

            int binding;
            var sampler = (Sampler) this.bindingMap.get(descriptorInfo.name());

            if (sampler != null) {
                binding = sampler.binding();
            }
            else {
                binding = descriptorInfo.binding();
            }

            imageDescriptors.add(new ImageDescriptor(binding, "sampler2D", descriptorInfo.name(), descriptorInfo.imageIdx(), descriptorType));
        }

        return imageDescriptors;
    }

    public List<UBO> createUBOs() {
        List<UBO> ubos = new ObjectArrayList<>();
        int uboCount = this.uniformBuffers.size();

        int i = 0;

        for (var uniformBuffer : this.uniformBuffers) {
            AlignedStruct.Builder builder = new AlignedStruct.Builder();

            // TODO: add uniforms info

            int binding = uniformBuffer.binding;

            ubos.add(builder.buildUBO(uniformBuffer.name(), binding, uniformBuffer.stage(), uniformBuffer.size()));
            ++i;
        }

        return ubos;
    }

    public List<UBO> createUBOs(PipelineConfig config) {
        List<UBO> ubos = new ObjectArrayList<>();
        int uboCount = config.ubs.size();

        for (int i = 0; i < config.ubs.size(); ++i) {
            var ubInfo = config.ubs.get(i);

            var ub = (UniformBuffer) this.bindingMap.get(ubInfo.name);

            int binding;
            if (ub != null) {
                binding = ub.binding;
            }
            else {
                binding = ubInfo.binding;
            }

            int stages = ubInfo.stage;
            AlignedStruct.Builder builder = new AlignedStruct.Builder();

            UBO ubo;
            if (!ubInfo.uniforms.isEmpty()) {
                for (var field : ubInfo.uniforms) {
                    String name = field.name();
                    String type = field.type();

                    Uniform.Info uniformInfo = Uniform.createUniformInfo(type, name);
                    uniformInfo.setupSupplier();

                    if (!uniformInfo.hasSupplier()) {
                        throw new IllegalStateException("No uniform supplier found for uniform: (%s:%s)".formatted(type, name));
                    }

                    builder.addUniform(uniformInfo);
                }

                ubo = builder.buildUBO(ubInfo.name, binding, stages);
            }
            else {
                int size = ubInfo.size;

                if (size <= 0) {
                    throw new IllegalStateException("Manual UBO has size <= 0");
                }

                ubo = new UBO(ubInfo.name, binding, stages, size, null);
                ubo.setUseGlobalBuffer(false);
            }

            ubos.add(ubo);
        }

        return ubos;
    }

    public Sampler getSampler(String name) {
        return (Sampler) bindingMap.get(name);
    }

    public static abstract class DescriptorBinding {
        String name;
        int binding;
        int stage;

        public DescriptorBinding(String name, int binding, int stage) {
            this.name = name;
            this.binding = binding;
            this.stage = stage;
        }

        public void addStage(int stage) {
            this.stage |= stage;
        }

        public String name() {
            return name;
        }

        public int binding() {
            return this.binding;
        }

        public void setBinding(int binding) {
            this.binding = binding;
        }

        public int stage() {
            return stage;
        }

        @Override
        public String toString() {
            return "DescriptorBinding{" +
                   "name='" + name + '\'' +
                   ", binding=" + binding +
                   ", stage=" + stage +
                   '}';
        }
    }

    public static class UniformBuffer extends DescriptorBinding {
        final int size;

        public UniformBuffer(String name, int binding, int size, int stage) {
            super(name, binding, stage);

            this.size = size;
        }

        public int size() {
            return this.size;
        }

        @Override
        public String toString() {
            return "UniformBuffer{" +
                   "size=" + size +
                   ", name='" + name + '\'' +
                   ", binding=" + binding +
                   ", stage=" + stage +
                   '}';
        }
    }

    public static class Sampler extends DescriptorBinding {

        public Sampler(String name, int binding, int stage) {
            super(name, binding, stage);
        }

        @Override
        public String toString() {
            return "Sampler{" +
                   "stage=" + stage +
                   ", binding=" + binding +
                   ", name='" + name + '\'' +
                   '}';
        }
    }
}
