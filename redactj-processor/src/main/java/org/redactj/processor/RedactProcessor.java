package org.redactj.processor;

import com.google.auto.service.AutoService;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.Set;

/**
 * Compile-time annotation processor that detects {@link org.redactj.api.Redact} annotations
 * and coordinates code generation for sensitive data masking.
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("org.redactj.api.Redact")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class RedactProcessor extends AbstractProcessor {
    protected Messager messager;
    protected Filer filer;
    protected Elements elementUtils;
    protected Types typeUtils;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        messager = processingEnv.getMessager();
        filer = processingEnv.getFiler();
        elementUtils = processingEnv.getElementUtils();
        typeUtils = processingEnv.getTypeUtils();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if(roundEnv.processingOver() || annotations.isEmpty()){
            return false;
        }

        messager.printMessage(Diagnostic.Kind.NOTE, "RedactJ processor initialized");
        return false;
    }
}
