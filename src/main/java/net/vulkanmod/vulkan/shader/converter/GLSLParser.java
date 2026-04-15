package net.vulkanmod.vulkan.shader.converter;

import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.Initializer;
import net.vulkanmod.vulkan.shader.descriptor.ImageDescriptor;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
import net.vulkanmod.vulkan.shader.layout.AlignedStruct;
import net.vulkanmod.vulkan.shader.layout.Uniform;
import org.lwjgl.vulkan.VK11;

import java.util.*;

/**
 * Simple parser used to convert GLSL shader code to make it Vulkan compatible
 */
public class GLSLParser {
    private Lexer lexer;
    private List<Token> tokens;
    private int currentTokenIdx;
    private Token currentToken;

    private Stage stage;
    PreprocessorState preprocessorState = PreprocessorState.DEFAULT;
    State state = State.DEFAULT;

    LinkedList<Node> vsStream = new LinkedList<>();
    LinkedList<Node> fsStream = new LinkedList<>();

    Set<String> defines = new HashSet<>();

    int currentUniformLocation = 0;
    List<UniformBlock> uniformBlocks = new ArrayList<>();
    Map<String, UniformBlock> uniformBlockMap = new HashMap<>();
    List<Sampler> samplers = new ArrayList<>();
    Map<String, Sampler> samplerMap = new HashMap<>();

    VertexFormat vertexFormat;
    int currentInAtt = 0, currentOutAtt = 0;
    ArrayList<Attribute> vertInAttributes = new ArrayList<>();
    ArrayList<Attribute> vertOutAttributes = new ArrayList<>();
    ArrayList<Attribute> fragInAttributes = new ArrayList<>();
    ArrayList<Attribute> fragOutAttributes = new ArrayList<>();

    public GLSLParser() {}

    public void setVertexFormat(VertexFormat vertexFormat) {
        this.vertexFormat = vertexFormat;
    }

    public void parse(Lexer lexer, Stage stage) {
        this.stage = stage;
        this.lexer = lexer;
        this.tokens = this.lexer.tokenize();
        this.currentTokenIdx = 0;

        this.currentInAtt = 0;
        this.currentOutAtt = 0;

        advanceToken();

        // Parse version
        parseVersion();

        while (currentToken.type != Token.TokenType.EOF) {
            switch (currentToken.type) {
                case PREPROCESSOR -> parsePreprocessor();
                case COMMENT -> {
                    appendToken(currentToken);
                    advanceToken();
                    continue;
                }
            }

            if (preprocessorState != PreprocessorState.IGNORE) {
                switch (currentToken.type) {
                    case PREPROCESSOR -> parsePreprocessor();

                    case IDENTIFIER -> {
                        switch (currentToken.value) {
                            case "layout" -> parseUniformBlock();
                            case "uniform" -> parseUniform();
                            case "in", "out" -> parseAttribute();
                            default -> appendToken(currentToken);
                        }
                    }

                    case OPERATOR -> {
                        // TODO: need to parse expressions to replace % operator
                        appendToken(currentToken);
                    }

                    default -> appendToken(currentToken);
                }
            }
            else {
                appendToken(currentToken);
            }

            advanceToken();
        }
    }

    private void parseVersion() {
        if (currentToken.type != Token.TokenType.PREPROCESSOR) {
            throw new IllegalStateException("First glsl line must contain #version");
        }

        advanceToken();

        if (!currentToken.value.startsWith("version")) {
            throw new IllegalStateException("First glsl line must contain #version");
        }

        advanceToken();
        while (!currentToken.value.contains("\n")) {
            advanceToken();
        }

        advanceToken();
        appendToken(new Token(Token.TokenType.PREPROCESSOR, "#version 450\n"));
    }

    private void parsePreprocessor()  {
        int startTokenIdx = this.currentTokenIdx - 1;
        boolean appendTokens = true;

        advanceToken(true);
        switch (currentToken.value) {
            case "define" -> {
                advanceToken(true);

                this.defines.add(currentToken.value);
            }
            case "ifdef" -> {
                advanceToken(true);

                if (!this.defines.contains(currentToken.value)) {
                    this.preprocessorState = PreprocessorState.IGNORE;
                }
            }
            case "else" -> {
                if (preprocessorState != PreprocessorState.IGNORE) {
                    preprocessorState = PreprocessorState.IGNORE;
                }
                else {
                    preprocessorState = PreprocessorState.DEFAULT;
                }
            }
            case "endif" -> {
                preprocessorState = PreprocessorState.DEFAULT;
            }
            case "line" -> {
                appendTokens = false;
            }

        }

        this.currentTokenIdx = startTokenIdx;
        this.currentTokenIdx++;
        this.currentToken = this.tokens.get(startTokenIdx);
        do {
            if (appendTokens) {
                appendToken(new Token(Token.TokenType.PREPROCESSOR, currentToken.value));
            }

            advanceToken(false);
        } while (!currentToken.value.contains("\n"));
    }

    private void parseUniform() {
        advanceToken(true);

        if (currentToken.type != Token.TokenType.IDENTIFIER) {
            throw new IllegalStateException();
        }

        switch (currentToken.value) {
            case "sampler2D" -> parseSampler(Sampler.Type.SAMPLER_2D);
            case "samplerCube" -> parseSampler(Sampler.Type.SAMPLER_CUBE);
            case "isamplerBuffer" -> parseSampler(Sampler.Type.I_SAMPLER_BUFFER);

            default -> throw new IllegalStateException("Unrecognized value: %s".formatted(currentToken.value));
        }
        // TODO: parse uniform
    }

    private void parseSampler(Sampler.Type type) {
        advanceToken(true);

        if (currentToken.type != Token.TokenType.IDENTIFIER) {
            throw new IllegalStateException();
        }

        String name = currentToken.value;

        advanceToken(true);
        if (currentToken.type != Token.TokenType.SEMICOLON) {
            throw new IllegalStateException();
        }

        Token next = this.tokens.get(currentTokenIdx);
        if (next.type == Token.TokenType.SPACING) {
            if (Objects.equals(next.value, "\n")) {
                currentTokenIdx++;
            }
            else {
                int i = next.value.indexOf("\n");
                if (i >= 0) {
                    next.value = next.value.substring(i + 1);
                }
            }
        }

        Sampler sampler = new Sampler(type, name);

        if (samplerMap.get(name) != null) {
            sampler = samplerMap.get(name);
        }
        else {
            sampler.setBinding(currentUniformLocation++);
            this.samplerMap.put(name, sampler);
            this.samplers.add(sampler);
        }

        appendNode(sampler.getNode());
    }

    private void parseUniformBlock() {
        this.state = State.LAYOUT;

        advanceToken(true);

        if (currentToken.type != Token.TokenType.LEFT_PARENTHESIS) {
            throw new IllegalStateException();
        }

        do {
            advanceToken(true);
        } while (currentToken.type != Token.TokenType.RIGHT_PARENTHESIS);

        advanceToken(true);

        if (!Objects.equals(this.currentToken.value, "uniform")) {
            throw new IllegalStateException();
        }

        advanceToken(true);
        String name = currentToken.value;

        UniformBlock ub = new UniformBlock(name);

        advanceToken(true);
        if (currentToken.type != Token.TokenType.LEFT_BRACE) {
            throw new IllegalStateException();
        }

        advanceToken(true);

        // Recognize fields
        while (currentToken.type != Token.TokenType.RIGHT_BRACE) {
            if (currentToken.type != Token.TokenType.IDENTIFIER) {
                throw new IllegalStateException();
            }
            String fieldType = this.currentToken.value;

            advanceToken(true);
            if (currentToken.type != Token.TokenType.IDENTIFIER) {
                throw new IllegalStateException();
            }
            String fieldName = this.currentToken.value;

            advanceToken(true);
            if (currentToken.type != Token.TokenType.SEMICOLON) {
                throw new IllegalStateException();
            }

            // Add field
            ub.addField(new UniformBlock.Field(fieldType, fieldName));

            advanceToken(true);
        }

        advanceToken(true);

        switch (currentToken.type) {
            case SEMICOLON -> {}

            case IDENTIFIER -> {
                ub.setAlias(currentToken.value);

                advanceToken(true);
                if (currentToken.type != Token.TokenType.SEMICOLON) {
                    throw new IllegalStateException();
                }
            }

            default -> throw new IllegalStateException();
        }

        Token next = this.tokens.get(currentTokenIdx);
        if (next.type == Token.TokenType.SPACING) {
            if (Objects.equals(next.value, "\n")) {
                currentTokenIdx++;
            }
            else {
                int i = next.value.indexOf("\n");
                if (i >= 0) {
                    next.value = next.value.substring(i + 1);
                }
            }
        }

        if (uniformBlockMap.get(ub.name) != null) {
            ub = uniformBlockMap.get(ub.name);
        }
        else {
            ub.setBinding(this.currentUniformLocation++);
            this.uniformBlockMap.put(ub.name, ub);
            this.uniformBlocks.add(ub);
        }

        appendNode(ub.getNode());
    }

    private void parseAttribute() {
        this.state = State.ATTRIBUTE;

        String ioType = this.currentToken.value;

        advanceToken(true);
        if (currentToken.type != Token.TokenType.IDENTIFIER) {
            throw new IllegalStateException();
        }
        String type = this.currentToken.value;

        advanceToken(true);
        if (currentToken.type != Token.TokenType.IDENTIFIER) {
            throw new IllegalStateException();
        }
        String id = this.currentToken.value;

        advanceToken(true);
        if (currentToken.type != Token.TokenType.SEMICOLON) {
            throw new IllegalStateException();
        }

        Token next = this.tokens.get(currentTokenIdx);
        if (next.type == Token.TokenType.SPACING) {
            if (Objects.equals(next.value, "\n")) {
                currentTokenIdx++;
            }
            else {
                int i = next.value.indexOf("\n");
                if (i >= 0) {
                    next.value = next.value.substring(i + 1);
                }
            }
        }

        Attribute attribute = new Attribute(ioType, type, id);

        switch (this.stage) {
            case VERTEX -> {
                switch (attribute.ioType) {
                    case "in" -> {
                        int attributeLocation;
                        if (this.vertexFormat != null) {
                            var attributeNames = this.vertexFormat.getElementAttributeNames();
                            attributeLocation = attributeNames.indexOf(attribute.id);

                            if (attributeLocation == -1) {
                                Initializer.LOGGER.error("Element %s not found in elements %s".formatted(attribute.id, attributeNames));
                                attributeLocation = currentInAtt;
                            }

                            currentInAtt++;
                        } else {
                            attributeLocation = currentInAtt++;
                        }

                        attribute.setLocation(attributeLocation);
                        vertInAttributes.add(attribute);
                    }
                    case "out" -> {
                        attribute.setLocation(currentOutAtt++);
                        vertOutAttributes.add(attribute);
                    }
                    default -> throw new IllegalStateException();
                }
            }
            case FRAGMENT -> {
                switch (attribute.ioType) {
                    case "in" -> {
                        // Find matching vertex out attribute
                        final var vertAttribute = getVertAttribute(attribute);

                        if (vertAttribute != null) {
                            attribute.setLocation(vertAttribute.location);
                            fragInAttributes.add(attribute);
                        }
                        else {
                            return;
                        }
                    }
                    case "out" -> {
                        if (currentOutAtt > 0) {
                            throw new UnsupportedOperationException("Multiple outputs not currently supported.");
                        }

                        attribute.setLocation(currentOutAtt++);
                        fragOutAttributes.add(attribute);
                    }
                    default -> throw new IllegalStateException();
                }
            }
        }

        this.appendNode(attribute.getNode());
    }

    private Attribute getVertAttribute(Attribute attribute) {
        Attribute vertAttribute = null;
        for (var attribute1 : vertOutAttributes) {
            if (Objects.equals(attribute1.id, attribute.id)) {
                vertAttribute = attribute1;
            }
        }

        if (vertAttribute == null) {
//            throw new IllegalStateException("No match found for attribute %s in vertex attribute outputs.".formatted(attribute.id));
        }
        return vertAttribute;
    }

    private void advanceToken() {
        advanceToken(false);
    }

    private void advanceToken(boolean skipSpace) {
        this.currentToken = this.tokens.get(this.currentTokenIdx++);

        while (skipSpace && this.currentToken.type == Token.TokenType.SPACING) {
            this.currentToken = this.tokens.get(this.currentTokenIdx++);
        }
    }

    private Token nextToken(int i) {
        return this.tokens.get(this.currentTokenIdx + i);
    }

    private void appendToken(Token token) {
        this.appendNode(Node.fromToken(token));
    }

    private void appendNode(Node node) {
        switch (this.stage) {
            case VERTEX -> this.vsStream.add(node);
            case FRAGMENT -> this.fsStream.add(node);
        }
    }

    public String getOutput(Stage stage) {
        StringBuilder stringBuilder = new StringBuilder();

        var stream = switch (stage) {
            case VERTEX -> this.vsStream;
            case FRAGMENT -> this.fsStream;
        };

        // Version
        Node node = stream.getFirst();
        stringBuilder.append(node.value);
        stringBuilder.append("\n");

        switch (stage) {
            case VERTEX -> {
                stringBuilder.append("#define gl_VertexID gl_VertexIndex\n\n");
            }
        }

        // Rename glsl reserved keywords
        stringBuilder.append("#define sampler sampler1\n\n");
        stringBuilder.append("#define sample sample1\n\n");

        for (int i = 1; i < stream.size(); i++) {
            node = stream.get(i);
            stringBuilder.append(node.value);
        }

        return stringBuilder.toString();
    }

    public UBO[] createUBOs() {
        if (this.uniformBlockMap.isEmpty()) {
            return new UBO[0];
        }

        int uboCount = this.uniformBlockMap.size();
        UBO[] ubos = new UBO[uboCount];

        int i = 0;
        for (var uniformBlock : this.uniformBlocks) {
            AlignedStruct.Builder builder = new AlignedStruct.Builder();

            for (var field : uniformBlock.fields) {
                String name = field.name;
                String type = field.type;

                Uniform.Info uniformInfo = Uniform.createUniformInfo(type, name);

                builder.addUniformInfo(uniformInfo);
            }

            ubos[i] = builder.buildUBO(uniformBlock.name, uniformBlock.binding, VK11.VK_SHADER_STAGE_ALL);
            ++i;
        }

        return ubos;
    }

    public List<ImageDescriptor> getSamplerList() {
        List<ImageDescriptor> imageDescriptors = new ObjectArrayList<>();

        int imageIdx = 0;
        for (Sampler sampler : this.samplers) {

            int descriptorType = switch (sampler.type) {
                case SAMPLER_2D, SAMPLER_CUBE -> VK11.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
                case I_SAMPLER_BUFFER -> VK11.VK_DESCRIPTOR_TYPE_UNIFORM_TEXEL_BUFFER;
            };

            imageDescriptors.add(new ImageDescriptor(sampler.binding, "sampler2D", sampler.id, imageIdx, descriptorType));
            imageIdx++;
        }

        return imageDescriptors;
    }

    enum PreprocessorState {
        IGNORE,
        DEFAULT
    }

    enum State {
        LAYOUT,
        UNIFORM,
        UNIFORM_BLOCK,
        ATTRIBUTE,
        DEFAULT
    }

    public enum Stage {
        VERTEX,
        FRAGMENT
    }

    public static class Node {
        String type;
        String value;

        public Node(String type, String value) {
            this.type = type;
            this.value = value;
        }

        public static Node fromToken(Token token) {
            return new Node("token:%s".formatted(token.type), token.value);
        }

        @Override
        public String toString() {
            return "Node{" +
                   "type='" + type + '\'' +
                   ", value='" + value + '\'' +
                   '}';
        }
    }
}


