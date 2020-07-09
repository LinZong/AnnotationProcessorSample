package com.nemesiss.dev.annotations.processors;

import com.google.auto.service.AutoService;
import com.nemesiss.dev.annotations.BuilderProperty;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@SupportedAnnotationTypes("com.nemesiss.dev.annotations.BuilderProperty")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@AutoService(Processor.class)
public class BuilderPropertyProcessor extends AbstractProcessor {

    static class BuilderFieldProperty {

        String belongToClassQualifiedName;

        String belongToClassSimpleName;

        Element element;

        String fieldName;

        String fullTypeClassName;

        String setterTextName;

        Element setterElement = null;

        public BuilderFieldProperty(Element element) {
            this.element = element;
            fieldName = element.getSimpleName().toString();
            fullTypeClassName = element.asType().toString();

            BuilderProperty bp = element.getAnnotation(BuilderProperty.class);
            setterTextName = bp.setterName().trim();

            if (setterTextName.length() == 0) {
                setterTextName = "set" + firstCharUppercaseIfNeeded(fieldName);
            }

            List<? extends Element> parentClassMethods = ElementFilter.methodsIn(element.getEnclosingElement().getEnclosedElements());

            List<? extends Element> filteredSetterElements = parentClassMethods.stream().filter(this::filterSetterCondition).collect(Collectors.toList());

            if (!filteredSetterElements.isEmpty()) {
                setterElement = filteredSetterElements.get(0);
            }

            belongToClassQualifiedName = ((TypeElement) element.getEnclosingElement()).getQualifiedName().toString();
            int lastDot = belongToClassQualifiedName.lastIndexOf('.');
            belongToClassSimpleName = belongToClassQualifiedName.substring(lastDot + 1);
        }

        public void generateCode(PrintWriter pw, String builderClassName, String setterReceiveTargetName, ProcessingEnvironment processingEnv) {
            if (setterElement == null) {
                notifyErroneouslyAnnotatedElements(element, processingEnv);
                return;
            }
            pw.write("    public " + builderClassName + " set" + firstCharUppercaseIfNeeded(fieldName) + "(" + fullTypeClassName + " value) { \n");
            pw.write("        " + setterReceiveTargetName + "." + setterTextName + "(value);\n");
            pw.write("        return this;\n");
            pw.write("    }\n");
        }

        private boolean filterSetterCondition(Element setterCandidate) {
            boolean isMethod = setterCandidate.getKind() == ElementKind.METHOD;

            List<? extends TypeMirror> candidateMethodParams = ((ExecutableType) setterCandidate.asType()).getParameterTypes();
            boolean methodNameValid = setterCandidate.getSimpleName().toString().equalsIgnoreCase(setterTextName);
            boolean paramValid = candidateMethodParams.size() == 1 && candidateMethodParams.get(0).toString().equals(fullTypeClassName);
            return isMethod && methodNameValid && paramValid;
        }


        private static String firstCharUppercaseIfNeeded(String target) {
            StringBuilder sb = new StringBuilder(target);
            char firstChar = sb.charAt(0);
            firstChar = Character.toUpperCase(firstChar);
            sb.setCharAt(0, firstChar);
            return sb.toString();
        }
    }


    private static void notifyErroneouslyAnnotatedElements(Element errorElement, ProcessingEnvironment processingEnv) {
        processingEnv
                .getMessager()
                .printMessage(Diagnostic.Kind.ERROR,
                        "@BuilderProperty must be applied to a field with setter method "
                                + "has a single argument", errorElement);
    }

    private static void printMessage(String message, ProcessingEnvironment processingEnv, Element element) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, message, element);
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(BuilderProperty.class);

        Set<? extends Element> fieldElements = ElementFilter.fieldsIn(annotatedElements);

        List<BuilderFieldProperty> builderFieldProperties = fieldElements
                .stream()
                .map(BuilderFieldProperty::new)
                .collect(Collectors.toList());
        Map<String, List<BuilderFieldProperty>> files = builderFieldProperties
                .stream()
                .collect(Collectors.groupingBy(x -> x.belongToClassQualifiedName));

        files.values().forEach(x -> {
            try {
                generateSourceFile(x);
            } catch (IOException e) {
                printMessage(e.toString(), processingEnv, null);
            }
        });
        return true;
    }


    private void generateSourceFile(List<BuilderFieldProperty> builderFieldProperties) throws IOException {
        BuilderFieldProperty bfp = builderFieldProperties.get(0);

        // 获取Field的EnclosingElement, 这个就是field所在的类，转型为TypeElement后就能拿到全限定名了。
        String classQualifiedName = ((TypeElement) bfp.element.getEnclosingElement()).getQualifiedName().toString();
        String packageName = null;
        int lastDot = classQualifiedName.lastIndexOf('.');
        String classSimpleName = classQualifiedName.substring(lastDot + 1);
        if (lastDot > 0) {
            packageName = classQualifiedName.substring(0, lastDot);
        }

        String builderQualifiedName = classQualifiedName + "Builder";
        String builderSimpleName = classSimpleName + "Builder";

        String receiveObjectName = "object";

        JavaFileObject jfo = processingEnv.getFiler().createSourceFile(builderQualifiedName);
        try (PrintWriter pw = new PrintWriter(jfo.openWriter())) {
            // Write package name if necessary.
            if (packageName != null) {
                pw.write("package " + packageName + ";\n\n");
            }

            //Write class declaration.

            pw.write("public class " + builderSimpleName + " { \n");

            // Write receive object.
            pw.write("    private " + classSimpleName + " " + receiveObjectName + " = new " + classSimpleName + "();\n");

            builderFieldProperties.forEach(properties -> properties.generateCode(
                    pw,
                    builderSimpleName,
                    receiveObjectName,
                    processingEnv));

            // Write build up object code.

            pw.write("    public " + classSimpleName + " build() { \n");
            pw.write("        return " + receiveObjectName + ";\n");
            pw.write("    }\n");
            pw.write("}");
        }
    }
}
