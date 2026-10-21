package org.redactj.processor;

import com.google.auto.service.AutoService;
import org.redactj.api.Redact;
import org.redactj.api.RedactJMasker;
import org.redactj.api.RedactStrategy;
import org.redactj.api.RedactUtils;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compile-time annotation processor that scans source code for {@link Redact} annotations,
 * validates target member types and visibility, synthesizes type-safe {@link RedactJMasker}
 * implementations via JavaPoet, and registers them dynamically using Java SPI descriptors.
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("org.redactj.api.Redact")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class RedactProcessor extends AbstractProcessor {

    /** Message reporting facility used to emit compilation errors and diagnostic notes. */
    protected Messager messager;

    /** File generator utility used to write physical Java source files and SPI resource descriptors. */
    protected Filer filer;

    /** Element utility functions used to inspect the Abstract Syntax Tree structure. */
    protected Elements elementUtils;

    /** Type utility functions used to analyze, test, and compare compile-time type mirrors. */
    protected Types typeUtils;

    /**
     * Initializes the processor with the compiler's processing environment tools.
     *
     * @param processingEnv the environment capabilities provided by the Java compiler
     */
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.filer = processingEnv.getFiler();
        this.elementUtils = processingEnv.getElementUtils();
        this.typeUtils = processingEnv.getTypeUtils();
    }

    /**
     * Processes a set of annotation types on type elements originating from prior rounds.
     * Evaluates @Redact targets, validates element compatibility, generates companion maskers,
     * and emits the corresponding SPI provider configuration.
     *
     * @param annotations the annotation types requested to be processed
     * @param roundEnv    environment for information about the current and prior rounds
     * @return true indicating that the @Redact annotations are claimed by this processor
     */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver() || annotations.isEmpty()) {
            return false;
        }

        Map<TypeElement, List<Element>> groupedElements = new HashMap<>();

        // 1. Scan and validate annotated elements
        for (Element element : roundEnv.getElementsAnnotatedWith(Redact.class)) {
            // Filter out synthetic fields in Java 21 records to prevent duplicate processing
            if (element.getEnclosingElement().getKind() == ElementKind.RECORD
                    && element.getKind() == ElementKind.FIELD) {
                continue;
            }

            // Validate supported element kinds (Fields and Record Components)
            if (element.getKind() != ElementKind.FIELD && element.getKind() != ElementKind.RECORD_COMPONENT) {
                messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "@Redact annotation can only be applied to fields or record components.",
                        element
                );
                continue;
            }

            // Enforce type compatibility (String only)
            if (!isStringType(element.asType())) {
                messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "RedactJ violation: @Redact can only be applied to java.lang.String types. Field '"
                                + element.getSimpleName() + "' is incompatible.",
                        element
                );
                continue;
            }

            TypeElement parentElement = (TypeElement) element.getEnclosingElement();
            groupedElements.computeIfAbsent(parentElement, k -> new ArrayList<>()).add(element);
        }

        Set<String> generatedMaskers = new HashSet<>();

        // 2. Validate accessors and generate companion masker classes
        for (Map.Entry<TypeElement, List<Element>> entry : groupedElements.entrySet()) {
            TypeElement parentClass = entry.getKey();
            List<Element> fields = entry.getValue();

            boolean hasError = false;
            for (Element field : fields) {
                if (resolveAccessor(parentClass, field) == null) {
                    messager.printMessage(
                            Diagnostic.Kind.ERROR,
                            "RedactJ violation: Cannot access private field '" + field.getSimpleName()
                                    + "' in class '" + parentClass.getSimpleName()
                                    + "'. Please provide a public getter or change field visibility.",
                            field
                    );
                    hasError = true;
                }
            }

            if (!hasError) {
                String generatedClassFqcn = generateMasker(parentClass, fields);
                if (generatedClassFqcn != null) {
                    generatedMaskers.add(generatedClassFqcn);
                }
            }
        }

        // 3. Emit SPI Service Provider configuration file
        if (!generatedMaskers.isEmpty()) {
            writeSpiDescriptor(generatedMaskers);
        }

        return true;
    }

    /**
     * Determines whether the specified type mirror matches java.lang.String.
     *
     * @param typeMirror the compile-time type mirror to examine
     * @return true if the type represents java.lang.String, false otherwise
     */
    private boolean isStringType(TypeMirror typeMirror) {
        return "java.lang.String".equals(typeMirror.toString());
    }

    /**
     * Resolves the appropriate member access syntax for reading the given field's value.
     * Supports canonical component accessors for Java 21 Records, direct field access for non-private
     * members, and standard getter method conventions (getFieldName / isFieldName) for private fields.
     *
     * @param parentClass the enclosing class or record element
     * @param field       the target field or record component element
     * @return the resolved invocation expression (e.g. "email()" or "getEmail()"), or null if inaccessible
     */
    protected String resolveAccessor(TypeElement parentClass, Element field) {
        String fieldName = field.getSimpleName().toString();

        // 1. If it's a Java 21 Record, the accessor name is identical to the component name
        if (parentClass.getKind() == ElementKind.RECORD) {
            return fieldName + "()";
        }

        // 2. If the field is not private, direct access is permissible
        if (!field.getModifiers().contains(Modifier.PRIVATE)) {
            return fieldName;
        }

        // 3. For private fields in standard classes, look for standard getter conventions
        String capitalized = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        String getterName = "get" + capitalized;
        String booleanGetterName = "is" + capitalized;

        boolean hasGetter = parentClass.getEnclosedElements().stream()
                .filter(e -> e.getKind() == ElementKind.METHOD)
                .map(e -> e.getSimpleName().toString())
                .anyMatch(name -> name.equals(getterName) || name.equals(booleanGetterName));

        return hasGetter ? getterName + "()" : null;
    }

    /**
     * Synthesizes the companion [ClassName]RedactMasker Java source file via JavaPoet.
     *
     * @param parentClass the target class or record element being processed
     * @param fields      the list of validated @Redact annotated elements within the parent
     * @return the fully qualified name of the synthesized companion class, or null if writing fails
     */
    private String generateMasker(TypeElement parentClass, List<Element> fields) {
        String packageName = elementUtils.getPackageOf(parentClass).getQualifiedName().toString();
        String simpleClassName = parentClass.getSimpleName().toString();
        String maskerClassName = simpleClassName + "RedactMasker";

        TypeName targetType = TypeName.get(parentClass.asType());
        ParameterizedTypeName spiInterface = ParameterizedTypeName.get(
                ClassName.get(RedactJMasker.class),
                targetType
        );

        // Build redact(TargetClass entity) method
        MethodSpec.Builder redactMethod = MethodSpec.methodBuilder("redact")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class)
                .addParameter(targetType, "entity")
                .beginControlFlow("if (entity == null)")
                .addStatement("return $S", "null")
                .endControlFlow()
                .addStatement("$T sb = new $T($S)", StringBuilder.class, StringBuilder.class, simpleClassName + " {\n");

        for (Element field : fields) {
            String fieldName = field.getSimpleName().toString();
            String accessor = resolveAccessor(parentClass, field);
            Redact redactAnnotation = field.getAnnotation(Redact.class);
            RedactStrategy strategy = (redactAnnotation != null) ? redactAnnotation.strategy() : RedactStrategy.FULL;

            redactMethod.addStatement(
                    "sb.append($S).append($T.apply(entity.$L, $T.$L)).append($S)",
                    "  " + fieldName + "=",
                    RedactUtils.class,
                    accessor,
                    RedactStrategy.class,
                    strategy.name(),
                    "\n"
            );
        }

        redactMethod.addStatement("sb.append($S)", "}");
        redactMethod.addStatement("return sb.toString()");

        // Build getTargetClass() method
        MethodSpec getTargetClassMethod = MethodSpec.methodBuilder("getTargetClass")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(ClassName.get(Class.class), targetType))
                .addStatement("return $T.class", targetType)
                .build();

        // Build final RedactMasker companion class
        TypeSpec maskerClass = TypeSpec.classBuilder(maskerClassName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(spiInterface)
                .addMethod(redactMethod.build())
                .addMethod(getTargetClassMethod)
                .build();

        try {
            JavaFile.builder(packageName, maskerClass)
                    .build()
                    .writeTo(filer);
            return packageName.isEmpty() ? maskerClassName : packageName + "." + maskerClassName;
        } catch (IOException e) {
            messager.printMessage(
                    Diagnostic.Kind.ERROR,
                    "RedactJ failed to write companion source file: " + e.getMessage(),
                    parentClass
            );
            return null;
        }
    }

    /**
     * Emits the META-INF/services SPI registration file declaring all generated companion maskers.
     *
     * @param maskerClassNames set of fully qualified class names to register as RedactJMasker providers
     */
    private void writeSpiDescriptor(Set<String> maskerClassNames) {
        try {
            FileObject resource = filer.createResource(
                    StandardLocation.CLASS_OUTPUT,
                    "",
                    "META-INF/services/" + RedactJMasker.class.getName()
            );
            try (Writer writer = resource.openWriter()) {
                for (String fqcn : maskerClassNames) {
                    writer.write(fqcn);
                    writer.write("\n");
                }
            }
        } catch (IOException e) {
            messager.printMessage(
                    Diagnostic.Kind.ERROR,
                    "RedactJ failed to create SPI service descriptor: " + e.getMessage()
            );
        }
    }
}