import com.android.tools.metalava.model.*;
import com.android.tools.metalava.model.annotation.DefaultAnnotationManager;
import com.android.tools.metalava.model.api.surface.ApiSurfaces;
import com.android.tools.metalava.model.psi.PsiEnvironmentManager;
import com.android.tools.metalava.model.source.SourceParser;
import com.android.tools.metalava.model.source.SourceSet;
import com.android.tools.metalava.reporter.BasicReporter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.file.Path;
import java.util.*;

/** Reports selected modifier accessors from the actual isolated raw-source model. */
public final class RawSourceModifierExtractor {
    private static final String LOG = "android/util/Log.java";
    private static final String SERVICE = "android/app/ServiceStartNotAllowedException.java";
    private static final String BASE = "android/text/SpannableStringInternal.java";
    private static final String SPANNED = "android/text/SpannedString.java";

    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("expected case, sourceRoot, output, jdkHome");
        String selectedCase = args[0];
        File sourceRoot = new File(args[1]).getCanonicalFile();
        List<File> sources = switch (selectedCase) {
            case "native" -> List.of(new File(sourceRoot, LOG));
            case "synchronized" -> List.of(new File(sourceRoot, SERVICE));
            case "inherited_final" -> List.of(new File(sourceRoot, BASE), new File(sourceRoot, SPANNED));
            default -> throw new IllegalArgumentException("unsupported selected case: " + selectedCase);
        };

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("case", selectedCase);
        output.put("sourceRoot", sourceRoot.toString());
        output.put("sourceModelClasspath", List.of());
        output.put("sdkGenerationInputs", List.of());
        List<Map<String, Object>> sourceInventory = new ArrayList<>();
        for (File source : sources) sourceInventory.add(Map.of("path", source.getCanonicalPath()));
        output.put("sources", sourceInventory);

        try (PsiEnvironmentManager manager = new PsiEnvironmentManager(false, false)) {
            Codebase.Config config = new Codebase.Config(new DefaultAnnotationManager(),
                    ApiSurfaces.Companion.getDEFAULT(), new BasicReporter(System.err));
            SourceParser parser = manager.createSourceParser(config, "1.8", "1.9",
                    ModelOptions.Companion.getEmpty(), true, new File(args[3]));
            Codebase codebase = parser.parseSources(new SourceSet(sources, List.of(sourceRoot)),
                    "Bounded raw modifier source-model query", List.of(), null, null, null);
            output.put("parser", parser.getClass().getName());

            if (selectedCase.equals("native")) {
                ClassItem owner = requiredClass(codebase, "android.util.Log");
                output.putAll(ownerRow("owner", owner, sourceRoot, "android/util/Log.java", "COMMAND_LINE"));
                MethodItem selected = requiredMethod(owner, "isLoggable", "(Ljava/lang/String;I)Z");
                output.put("selected", methodRow(selected));
            } else if (selectedCase.equals("synchronized")) {
                ClassItem owner = requiredClass(codebase, "android.app.ServiceStartNotAllowedException");
                output.putAll(ownerRow("owner", owner, sourceRoot,
                        "android/app/ServiceStartNotAllowedException.java", "COMMAND_LINE"));
                MethodItem selected = requiredMethod(owner, "getCause", "()Ljava/lang/Throwable;");
                output.put("selected", methodRow(selected));
            } else {
                ClassItem owner = requiredClass(codebase, "android.text.SpannedString");
                ClassItem base = requiredClass(codebase, "android.text.SpannableStringInternal");
                output.putAll(ownerRow("owner", owner, sourceRoot, "android/text/SpannedString.java",
                        "COMMAND_LINE"));
                output.putAll(ownerRow("base", base, sourceRoot,
                        "android/text/SpannableStringInternal.java", "SOURCE_PATH"));
                MethodItem baseMethod = requiredMethod(base, "length", "()I");
                MethodItem inherited = owner.findMethod(baseMethod, true, true);
                if (inherited == null) throw new AssertionError("SpannedString cannot resolve inherited base length()I");
                output.put("baseMethod", methodRow(baseMethod));
                Map<String, Object> inheritedRow = methodRow(inherited);
                output.put("inheritedLookup", inheritedRow);
                output.put("selected", inheritedRow);
            }
        }
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(Path.of(args[2]).toFile(), output);
    }

    private static ClassItem requiredClass(Codebase codebase, String name) {
        ClassItem item = codebase.resolveClass(name);
        if (item == null) throw new AssertionError("Actual raw source class missing: " + name);
        return item;
    }

    private static MethodItem requiredMethod(ClassItem owner, String name, String descriptor) {
        for (MethodItem method : owner.methods()) {
            if (method.name().equals(name) && descriptor(method).equals(descriptor)) return method;
        }
        throw new AssertionError("Direct raw source method missing: " + owner.qualifiedName()
                + "." + name + descriptor);
    }

    private static String descriptor(MethodItem method) {
        StringBuilder result = new StringBuilder("(");
        for (ParameterItem parameter : method.parameters()) result.append(parameter.type().internalName());
        return result.append(')').append(method.returnType().internalName()).toString();
    }

    private static Map<String, Object> ownerRow(String prefix, ClassItem owner, File sourceRoot,
                                                 String expectedRelativePath,
                                                 String expectedOrigin) throws Exception {
        String actualPath = new File(owner.getFileLocation().getPath().toString()).getCanonicalPath();
        String expectedPath = new File(sourceRoot, expectedRelativePath).getCanonicalPath();
        if (!actualPath.equals(expectedPath)) {
            throw new AssertionError("Unexpected " + prefix + " source association: " + actualPath);
        }
        if (!owner.getOrigin().name().equals(expectedOrigin)) {
            throw new AssertionError(prefix + " origin differs from pinned stock PSI expectation: "
                    + owner.getOrigin() + " (expected " + expectedOrigin + ")");
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put(prefix + "Name", owner.qualifiedName());
        row.put(prefix + "Source", actualPath);
        row.put(prefix + "Origin", owner.getOrigin().name());
        row.put(prefix + "Visibility", owner.getModifiers().getVisibilityString());
        return row;
    }

    private static Map<String, Object> methodRow(MethodItem method) {
        ModifierList modifiers = method.getModifiers();
        TypeStringConfiguration types = TypeStringConfiguration.Companion.getDEFAULT();
        boolean isPublic = modifiers.getVisibilityModifiers().equals("public");
        boolean isStatic = modifiers.isStatic();
        boolean isFinal = modifiers.isFinal();
        boolean isNative = modifiers.isNative();
        boolean isAbstract = modifiers.isAbstract();
        boolean isSynchronized = modifiers.isSynchronized();
        int flags = (isPublic ? 0x0001 : 0)
                | (isStatic ? 0x0008 : 0)
                | (isFinal ? 0x0010 : 0)
                | (isSynchronized ? 0x0020 : 0)
                | (isNative ? 0x0100 : 0);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", method.name());
        row.put("descriptor", descriptor(method));
        row.put("access", modifiers.getVisibilityModifiers());
        row.put("static", isStatic);
        row.put("final", isFinal);
        row.put("native", isNative);
        row.put("abstract", isAbstract);
        row.put("synchronized", isSynchronized);
        row.put("flags", String.format("0x%x", flags));
        row.put("return", method.returnType().toTypeString(types));
        row.put("returnDescriptor", method.returnType().internalName());
        List<Map<String, Object>> parameters = new ArrayList<>();
        for (ParameterItem parameter : method.parameters()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", parameter.name());
            item.put("type", parameter.type().toTypeString(types));
            item.put("descriptor", parameter.type().internalName());
            parameters.add(item);
        }
        row.put("parameters", parameters);
        List<Map<String, Object>> typeParameters = new ArrayList<>();
        for (TypeParameterItem parameter : method.getTypeParameterList()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", parameter.name());
            item.put("bounds", parameter.typeBounds().stream()
                    .map(type -> type.toTypeString(types)).toList());
            item.put("boundDescriptors", parameter.typeBounds().stream()
                    .map(TypeItem::internalName).toList());
            typeParameters.add(item);
        }
        row.put("typeParameters", typeParameters);
        row.put("throws", method.throwsTypes().stream()
                .map(type -> type.toTypeString(types)).toList());
        row.put("containingClass", method.containingClass().qualifiedName());
        row.put("containingOrigin", method.containingClass().getOrigin().name());
        row.put("containingSource", method.containingClass().getFileLocation().getPath().toString());
        return row;
    }
}
