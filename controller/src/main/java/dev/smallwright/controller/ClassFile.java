package dev.smallwright.controller;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The declarations in one class file, read from its bytes as Java source would write them. The
 * class is never loaded, run or verified as code; a malformed class file is refused whole.
 */
final class ClassFile {
    static final int MAX_BYTES = 1024 * 1024;

    private static final int PUBLIC = 0x0001;
    private static final int PROTECTED = 0x0004;
    private static final int STATIC = 0x0008;
    private static final int FINAL = 0x0010;
    private static final int BRIDGE = 0x0040;
    private static final int VARARGS = 0x0080;
    private static final int INTERFACE = 0x0200;
    private static final int ABSTRACT = 0x0400;
    private static final int SYNTHETIC = 0x1000;
    private static final int ANNOTATION = 0x2000;
    private static final int ENUM = 0x4000;

    private static final int UTF8 = 1;
    private static final int INTEGER = 3;
    private static final int FLOAT = 4;
    private static final int LONG = 5;
    private static final int DOUBLE = 6;
    private static final int CLASS = 7;
    private static final int STRING = 8;

    /** One field, constructor or method. */
    static final class Member {
        final String name, descriptor;
        final boolean method;
        private final int access;
        private final String typeParameters, type;
        private final List<String> parameters, exceptions;
        private final Object constant;
        private final boolean deprecated;

        private Member(
                int access,
                String name,
                String descriptor,
                boolean method,
                String typeParameters,
                String type,
                List<String> parameters,
                List<String> exceptions,
                Object constant,
                boolean deprecated) {
            this.access = access;
            this.name = name;
            this.descriptor = descriptor;
            this.method = method;
            this.typeParameters = typeParameters;
            this.type = type;
            this.parameters = parameters;
            this.exceptions = exceptions;
            this.constant = constant;
            this.deprecated = deprecated;
        }

        boolean constructor() {
            return name.equals("<init>");
        }

        private boolean visible() {
            return (access & (PUBLIC | PROTECTED)) != 0
                    && (access & (SYNTHETIC | BRIDGE)) == 0
                    && !name.equals("<clinit>");
        }
    }

    /** The binary name, such as {@code android/view/View$OnClickListener}. */
    final String name;

    /** The binary name of the superclass; null for {@code java/lang/Object}. */
    final String superName;

    final List<String> interfaceNames;
    private final int access;
    private final String typeParameters, superType;
    private final List<String> interfaceTypes;
    private final boolean deprecated;
    private final List<Member> fields, methods;

    private ClassFile(
            int access,
            String name,
            String superName,
            List<String> interfaceNames,
            String typeParameters,
            String superType,
            List<String> interfaceTypes,
            boolean deprecated,
            List<Member> fields,
            List<Member> methods) {
        this.access = access;
        this.name = name;
        this.superName = superName;
        this.interfaceNames = interfaceNames;
        this.typeParameters = typeParameters;
        this.superType = superType;
        this.interfaceTypes = interfaceTypes;
        this.deprecated = deprecated;
        this.fields = fields;
        this.methods = methods;
    }

    static ClassFile parse(byte[] bytes) throws IOException {
        if (bytes.length > MAX_BYTES) throw new IOException("Class file too large");
        try {
            return new Reader(bytes).read();
        } catch (RuntimeException | IOException malformed) {
            throw new IOException("Not a valid class file");
        }
    }

    /** Public and protected members: constructors, then fields, then methods, each by name. */
    List<Member> members() {
        List<Member> constructors = new ArrayList<>(), named = new ArrayList<>();
        for (Member method : methods)
            if (method.visible()) (method.constructor() ? constructors : named).add(method);
        List<Member> visibleFields = new ArrayList<>();
        for (Member field : fields) if (field.visible()) visibleFields.add(field);
        visibleFields.sort(Comparator.comparing(member -> member.name));
        named.sort(Comparator.comparing(member -> member.name));
        List<Member> members = new ArrayList<>(constructors);
        members.addAll(visibleFields);
        members.addAll(named);
        return members;
    }

    /** The class as its declaration would start, such as {@code public class a.B extends a.C}. */
    String declaration() {
        boolean isInterface = (access & INTERFACE) != 0;
        StringBuilder text = new StringBuilder(deprecated ? "@Deprecated " : "");
        if ((access & PUBLIC) != 0) text.append("public ");
        if ((access & ANNOTATION) != 0) text.append("@interface ");
        else if (isInterface) text.append("interface ");
        else if ((access & ENUM) != 0) text.append("enum ");
        else {
            if ((access & ABSTRACT) != 0) text.append("abstract ");
            if ((access & FINAL) != 0) text.append("final ");
            text.append("class ");
        }
        text.append(Types.display(name)).append(typeParameters);
        boolean plainClass = (access & (INTERFACE | ENUM)) == 0;
        if (plainClass && superType != null && !superType.equals("Object"))
            text.append(" extends ").append(superType);
        List<String> interfaces = new ArrayList<>(interfaceTypes);
        if ((access & ANNOTATION) != 0) interfaces.remove("java.lang.annotation.Annotation");
        if (!interfaces.isEmpty())
            text.append(isInterface ? " extends " : " implements ")
                    .append(String.join(", ", interfaces));
        return text.toString();
    }

    /** The member as Java source declares it, with a constant's value. */
    String declaration(Member member) {
        StringBuilder text = new StringBuilder(member.deprecated ? "@Deprecated " : "");
        int flags = member.access;
        text.append((flags & PUBLIC) != 0 ? "public " : "protected ");
        boolean isInterface = (access & INTERFACE) != 0;
        if (member.method && isInterface && (flags & (ABSTRACT | STATIC)) == 0)
            text.append("default ");
        if ((flags & STATIC) != 0) text.append("static ");
        if ((flags & FINAL) != 0) text.append("final ");
        if ((flags & ABSTRACT) != 0) text.append("abstract ");
        if (!member.method) {
            text.append(member.type).append(' ').append(member.name);
            if (member.constant != null)
                text.append(" = ").append(Types.literal(member.descriptor, member.constant));
            return text.toString();
        }
        if (!member.typeParameters.isEmpty()) text.append(member.typeParameters).append(' ');
        if (member.constructor()) text.append(simpleName());
        else text.append(member.type).append(' ').append(member.name);
        List<String> parameters = new ArrayList<>(member.parameters);
        int last = parameters.size() - 1;
        if ((flags & VARARGS) != 0 && last >= 0 && parameters.get(last).contains("[]")) {
            String varargs = parameters.get(last);
            int brackets = varargs.lastIndexOf("[]");
            parameters.set(
                    last, varargs.substring(0, brackets) + "..." + varargs.substring(brackets + 2));
        }
        text.append('(').append(String.join(", ", parameters)).append(')');
        if (!member.exceptions.isEmpty())
            text.append(" throws ").append(String.join(", ", member.exceptions));
        return text.toString();
    }

    /** The name a constructor has in source, such as {@code OnClickListener}. */
    String simpleName() {
        String display = Types.display(name);
        return display.substring(display.lastIndexOf('.') + 1);
    }

    private static final class Reader {
        private final ByteBuffer in;
        private byte[] tags;
        private Object[] values;

        Reader(byte[] bytes) {
            in = ByteBuffer.wrap(bytes);
        }

        ClassFile read() throws IOException {
            if (in.getInt() != 0xCAFEBABE) throw new IOException("magic");
            in.getShort();
            in.getShort();
            readConstants();
            int access = u2();
            String name = className(u2());
            int superIndex = u2();
            String superName = superIndex == 0 ? null : className(superIndex);
            List<String> interfaceNames = new ArrayList<>();
            for (int count = u2(); count > 0; count--) interfaceNames.add(className(u2()));
            List<Member> fields = members(false);
            List<Member> methods = members(true);
            Attributes attributes = attributes();
            if (in.hasRemaining()) throw new IOException("trailing bytes");
            String typeParameters = "";
            String superType = superName == null ? null : Types.sourceName(superName);
            List<String> interfaceTypes = new ArrayList<>();
            for (String interfaceName : interfaceNames)
                interfaceTypes.add(Types.sourceName(interfaceName));
            if (attributes.signature != null) {
                try {
                    Types signature = new Types(attributes.signature);
                    String parameters = signature.typeParameters();
                    String superSignature = signature.reference();
                    List<String> interfaceSignatures = new ArrayList<>();
                    while (!signature.done()) interfaceSignatures.add(signature.reference());
                    typeParameters = parameters;
                    superType = superSignature;
                    interfaceTypes = interfaceSignatures;
                } catch (RuntimeException ignored) {
                    typeParameters = "";
                }
            }
            return new ClassFile(
                    access,
                    name,
                    superName,
                    interfaceNames,
                    typeParameters,
                    superType,
                    interfaceTypes,
                    attributes.deprecated,
                    fields,
                    methods);
        }

        private void readConstants() throws IOException {
            int count = u2();
            if (count == 0) throw new IOException("constant pool");
            tags = new byte[count];
            values = new Object[count];
            for (int index = 1; index < count; index++) {
                int tag = in.get() & 0xFF;
                tags[index] = (byte) tag;
                switch (tag) {
                    case UTF8:
                        int start = in.position();
                        int length = u2();
                        if (length > in.remaining()) throw new IOException("utf8");
                        values[index] =
                                new DataInputStream(
                                                new ByteArrayInputStream(
                                                        in.array(), start, length + 2))
                                        .readUTF();
                        in.position(start + 2 + length);
                        break;
                    case INTEGER:
                        values[index] = in.getInt();
                        break;
                    case FLOAT:
                        values[index] = in.getFloat();
                        break;
                    case LONG:
                    case DOUBLE:
                        values[index] = tag == LONG ? (Object) in.getLong() : in.getDouble();
                        if (++index >= count) throw new IOException("wide constant");
                        break;
                    case CLASS:
                    case STRING:
                    case 16:
                    case 19:
                    case 20:
                        values[index] = u2();
                        break;
                    case 9:
                    case 10:
                    case 11:
                    case 12:
                    case 17:
                    case 18:
                        in.getInt();
                        break;
                    case 15:
                        in.get();
                        in.getShort();
                        break;
                    default:
                        throw new IOException("constant tag");
                }
            }
        }

        private List<Member> members(boolean method) throws IOException {
            List<Member> members = new ArrayList<>();
            for (int count = u2(); count > 0; count--) {
                int access = u2();
                String name = utf8(u2());
                String descriptor = utf8(u2());
                Attributes attributes = attributes();
                Types erased = new Types(descriptor);
                if (!method) {
                    String type = erased.type(false);
                    if (!erased.done()) throw new IOException("descriptor");
                    String generic = type;
                    if (attributes.signature != null) {
                        try {
                            Types signature = new Types(attributes.signature);
                            String parsed = signature.reference();
                            if (signature.done()) generic = parsed;
                        } catch (RuntimeException ignored) {
                            generic = type;
                        }
                    }
                    members.add(
                            new Member(
                                    access,
                                    name,
                                    descriptor,
                                    false,
                                    "",
                                    generic,
                                    List.of(),
                                    List.of(),
                                    constant(descriptor, attributes.constant),
                                    attributes.deprecated));
                    continue;
                }
                Types.Method shape = erased.method();
                if (!shape.typeParameters.isEmpty() || !shape.exceptions.isEmpty())
                    throw new IOException("descriptor");
                List<String> names =
                        parameterNames(descriptor, (access & STATIC) != 0, attributes.locals);
                List<String> exceptions =
                        attributes.exceptions == null ? List.of() : attributes.exceptions;
                if (attributes.signature != null) {
                    try {
                        Types.Method generic = new Types(attributes.signature).method();
                        if (generic.parameters.size() != shape.parameters.size()) names = null;
                        if (generic.exceptions.isEmpty())
                            generic = generic.withExceptions(exceptions);
                        shape = generic;
                    } catch (RuntimeException ignored) {
                        shape = shape.withExceptions(exceptions);
                    }
                } else shape = shape.withExceptions(exceptions);
                List<String> parameters = new ArrayList<>();
                for (int position = 0; position < shape.parameters.size(); position++)
                    parameters.add(
                            names == null
                                    ? shape.parameters.get(position)
                                    : shape.parameters.get(position) + " " + names.get(position));
                members.add(
                        new Member(
                                access,
                                name,
                                descriptor,
                                true,
                                shape.typeParameters,
                                shape.result,
                                parameters,
                                shape.exceptions,
                                null,
                                attributes.deprecated));
            }
            return members;
        }

        private Object constant(String descriptor, Object value) {
            if (value == null) return null;
            boolean matches;
            switch (descriptor) {
                case "J":
                    matches = value instanceof Long;
                    break;
                case "F":
                    matches = value instanceof Float;
                    break;
                case "D":
                    matches = value instanceof Double;
                    break;
                case "Ljava/lang/String;":
                    matches = value instanceof String;
                    break;
                case "I":
                case "S":
                case "B":
                case "C":
                case "Z":
                    matches = value instanceof Integer;
                    break;
                default:
                    matches = false;
            }
            return matches ? value : null;
        }

        private static List<String> parameterNames(
                String descriptor, boolean isStatic, Map<Integer, String> locals) {
            if (locals.isEmpty()) return null;
            List<String> names = new ArrayList<>();
            int slot = isStatic ? 0 : 1;
            for (int at = 1; descriptor.charAt(at) != ')'; ) {
                String name = locals.get(slot);
                if (name == null) return null;
                names.add(name);
                char kind = descriptor.charAt(at);
                slot += kind == 'J' || kind == 'D' ? 2 : 1;
                while (descriptor.charAt(at) == '[') at++;
                at = descriptor.charAt(at) == 'L' ? descriptor.indexOf(';', at) + 1 : at + 1;
            }
            return names;
        }

        private static final class Attributes {
            String signature;
            Object constant;
            boolean deprecated;
            List<String> exceptions;
            Map<Integer, String> locals = Collections.emptyMap();
        }

        private Attributes attributes() throws IOException {
            Attributes attributes = new Attributes();
            for (int count = u2(); count > 0; count--) {
                String name = utf8(u2());
                int length = in.getInt();
                if (length < 0 || length > in.remaining()) throw new IOException("attribute");
                int end = in.position() + length;
                switch (name) {
                    case "Signature":
                        attributes.signature = utf8(u2());
                        break;
                    case "ConstantValue":
                        attributes.constant = constant(u2());
                        break;
                    case "Deprecated":
                        attributes.deprecated = true;
                        break;
                    case "Exceptions":
                        List<String> exceptions = new ArrayList<>();
                        for (int thrown = u2(); thrown > 0; thrown--)
                            exceptions.add(Types.sourceName(className(u2())));
                        attributes.exceptions = exceptions;
                        break;
                    case "Code":
                        attributes.locals = locals(end);
                        break;
                    default:
                        in.position(end);
                }
                if (in.position() != end) throw new IOException("attribute length");
            }
            return attributes;
        }

        private Map<Integer, String> locals(int end) throws IOException {
            in.getShort();
            in.getShort();
            int codeLength = in.getInt();
            if (codeLength < 0 || codeLength > end - in.position()) throw new IOException("code");
            in.position(in.position() + codeLength);
            int handlers = u2();
            if (handlers * 8 > end - in.position()) throw new IOException("handlers");
            in.position(in.position() + handlers * 8);
            Map<Integer, String> locals = new HashMap<>();
            for (int count = u2(); count > 0; count--) {
                String name = utf8(u2());
                int length = in.getInt();
                if (length < 0 || length > end - in.position()) throw new IOException("attribute");
                int attributeEnd = in.position() + length;
                if (name.equals("LocalVariableTable")) {
                    for (int entries = u2(); entries > 0; entries--) {
                        int startPc = u2();
                        in.getShort();
                        String local = utf8(u2());
                        utf8(u2());
                        int slot = u2();
                        if (startPc == 0) locals.put(slot, local);
                    }
                } else in.position(attributeEnd);
                if (in.position() != attributeEnd) throw new IOException("attribute length");
            }
            return locals;
        }

        private int u2() {
            return in.getShort() & 0xFFFF;
        }

        private String utf8(int index) throws IOException {
            if (index <= 0 || index >= tags.length || tags[index] != UTF8)
                throw new IOException("utf8 index");
            return (String) values[index];
        }

        private String className(int index) throws IOException {
            if (index <= 0 || index >= tags.length || tags[index] != CLASS)
                throw new IOException("class index");
            String name = utf8((Integer) values[index]);
            if (name.isEmpty() || name.startsWith("[") || name.contains(";"))
                throw new IOException("class name");
            return name;
        }

        private Object constant(int index) throws IOException {
            if (index <= 0 || index >= tags.length) throw new IOException("constant index");
            switch (tags[index]) {
                case INTEGER:
                case FLOAT:
                case LONG:
                case DOUBLE:
                    return values[index];
                case STRING:
                    return utf8((Integer) values[index]);
                default:
                    throw new IOException("constant kind");
            }
        }
    }

    /** Descriptors and generic signatures, written as Java source types. */
    private static final class Types {
        static final class Method {
            final String typeParameters, result;
            final List<String> parameters, exceptions;

            Method(
                    String typeParameters,
                    List<String> parameters,
                    String result,
                    List<String> exceptions) {
                this.typeParameters = typeParameters;
                this.parameters = parameters;
                this.result = result;
                this.exceptions = exceptions;
            }

            Method withExceptions(List<String> thrown) {
                return new Method(typeParameters, parameters, result, thrown);
            }
        }

        private static final int MAX_ARRAY_DIMENSIONS = 255;
        private static final int MAX_TYPE_ARGUMENT_DEPTH = 64;

        private final String text;
        private int at;
        private int typeArgumentDepth;

        Types(String text) {
            this.text = text;
        }

        static String display(String binaryName) {
            return binaryName.replace('/', '.').replace('$', '.');
        }

        static String sourceName(String binaryName) {
            String display = display(binaryName);
            String simple = display.startsWith("java.lang.") ? display.substring(10) : display;
            return simple.indexOf('.') < 0 ? simple : display;
        }

        boolean done() {
            return at == text.length();
        }

        Method method() {
            String typeParameters = typeParameters();
            expect('(');
            List<String> parameters = new ArrayList<>();
            while (peek() != ')') parameters.add(type(false));
            expect(')');
            String result = type(true);
            List<String> exceptions = new ArrayList<>();
            while (!done()) {
                expect('^');
                exceptions.add(reference());
            }
            return new Method(typeParameters, parameters, result, exceptions);
        }

        String typeParameters() {
            if (done() || peek() != '<') return "";
            at++;
            List<String> parameters = new ArrayList<>();
            while (peek() != '>') {
                String name = identifier(":");
                expect(':');
                List<String> bounds = new ArrayList<>();
                if (peek() != ':') bounds.add(reference());
                while (peek() == ':') {
                    at++;
                    bounds.add(reference());
                }
                bounds.remove("Object");
                parameters.add(
                        bounds.isEmpty() ? name : name + " extends " + String.join(" & ", bounds));
            }
            at++;
            if (parameters.isEmpty()) throw new IllegalArgumentException("type parameters");
            return "<" + String.join(", ", parameters) + ">";
        }

        String type(boolean voidAllowed) {
            char kind = text.charAt(at++);
            switch (kind) {
                case 'B':
                    return "byte";
                case 'C':
                    return "char";
                case 'D':
                    return "double";
                case 'F':
                    return "float";
                case 'I':
                    return "int";
                case 'J':
                    return "long";
                case 'S':
                    return "short";
                case 'Z':
                    return "boolean";
                case 'V':
                    if (!voidAllowed) throw new IllegalArgumentException("void");
                    return "void";
                default:
                    at--;
                    return reference();
            }
        }

        String reference() {
            if (peek() == '[') {
                int dimensions = 0;
                while (peek() == '[') {
                    at++;
                    if (++dimensions > MAX_ARRAY_DIMENSIONS)
                        throw new IllegalArgumentException("array dimensions");
                }
                return type(false) + "[]".repeat(dimensions);
            }
            char kind = text.charAt(at++);
            if (kind == 'T') {
                String variable = identifier(";");
                expect(';');
                return variable;
            }
            if (kind != 'L') throw new IllegalArgumentException("type");
            StringBuilder type = new StringBuilder(sourceName(identifier("<.;")));
            type.append(typeArguments());
            while (peek() == '.') {
                at++;
                type.append('.')
                        .append(identifier("<.;").replace('$', '.'))
                        .append(typeArguments());
            }
            expect(';');
            return type.toString();
        }

        private String typeArguments() {
            if (peek() != '<') return "";
            at++;
            if (++typeArgumentDepth > MAX_TYPE_ARGUMENT_DEPTH)
                throw new IllegalArgumentException("type arguments too deep");
            List<String> arguments = new ArrayList<>();
            while (peek() != '>') {
                char wildcard = peek();
                if (wildcard == '*') {
                    at++;
                    arguments.add("?");
                } else if (wildcard == '+' || wildcard == '-') {
                    at++;
                    arguments.add((wildcard == '+' ? "? extends " : "? super ") + reference());
                } else arguments.add(reference());
            }
            at++;
            typeArgumentDepth--;
            if (arguments.isEmpty()) throw new IllegalArgumentException("type arguments");
            return "<" + String.join(", ", arguments) + ">";
        }

        private String identifier(String ends) {
            int start = at;
            while (ends.indexOf(peek()) < 0) {
                if ("<>;[:.".indexOf(peek()) >= 0) throw new IllegalArgumentException("identifier");
                at++;
            }
            if (at == start) throw new IllegalArgumentException("identifier");
            return text.substring(start, at);
        }

        private char peek() {
            return text.charAt(at);
        }

        private void expect(char expected) {
            if (text.charAt(at++) != expected) throw new IllegalArgumentException("expected");
        }

        static String literal(String descriptor, Object value) {
            switch (descriptor) {
                case "J":
                    return value + "L";
                case "Z":
                    return ((Integer) value) != 0 ? "true" : "false";
                case "C":
                    return "'" + escape(String.valueOf((char) (int) (Integer) value), '\'') + "'";
                case "F":
                    float number = (Float) value;
                    if (Float.isNaN(number)) return "Float.NaN";
                    if (Float.isInfinite(number))
                        return number > 0 ? "Float.POSITIVE_INFINITY" : "Float.NEGATIVE_INFINITY";
                    return number + "f";
                case "D":
                    double wide = (Double) value;
                    if (Double.isNaN(wide)) return "Double.NaN";
                    if (Double.isInfinite(wide))
                        return wide > 0 ? "Double.POSITIVE_INFINITY" : "Double.NEGATIVE_INFINITY";
                    return String.valueOf(wide);
                case "Ljava/lang/String;":
                    String string = (String) value;
                    return "\""
                            + escape(string.length() > 200 ? string.substring(0, 200) : string, '"')
                            + (string.length() > 200 ? "…" : "")
                            + "\"";
                default:
                    return String.valueOf(value);
            }
        }

        private static String escape(String text, char quote) {
            StringBuilder escaped = new StringBuilder();
            for (char character : text.toCharArray()) {
                if (character == quote || character == '\\') escaped.append('\\').append(character);
                else if (character == '\n') escaped.append("\\n");
                else if (character == '\t') escaped.append("\\t");
                else if (character == '\r') escaped.append("\\r");
                else if (character < 0x20 || character == 0x7F)
                    escaped.append(String.format("\\u%04x", (int) character));
                else escaped.append(character);
            }
            return escaped.toString();
        }
    }
}
