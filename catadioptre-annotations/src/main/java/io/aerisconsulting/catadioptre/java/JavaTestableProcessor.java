/*
 * Copyright 2021 AERIS-Consulting e.U.
 *
 * AERIS-Consulting e.U. licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.aerisconsulting.catadioptre.java;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeSpec.Builder;
import com.squareup.javapoet.TypeVariableName;
import io.aerisconsulting.catadioptre.ReflectionFieldUtils;
import io.aerisconsulting.catadioptre.ReflectionMethodUtils;
import io.aerisconsulting.catadioptre.Testable;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;

/**
 * Processor that generates source code to provide indirect access to private fields and methods.
 *
 * @author Eric Jessé
 */
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes("io.aerisconsulting.catadioptre.Testable")
public class JavaTestableProcessor extends AbstractProcessor {

	private static final String INSTANCE_PARAM_TYPE = "INSTANCE";

	private Elements elementUtils;

	private Path generatedDirPath;

	private JavaSpecificationUtils specificationUtils;

	@Override
	public synchronized void init(final ProcessingEnvironment processingEnv) {
		super.init(processingEnv);
		elementUtils = processingEnv.getElementUtils();
		specificationUtils = new JavaSpecificationUtils();

		try {
			// Finds out the folder where generated sources are written.
			final JavaFileObject builderFile = processingEnv.getFiler().createSourceFile("CatadioptreLocationTest");
			generatedDirPath = Paths.get(Paths.get(builderFile.toUri()).getParent().getParent().toUri().getPath(),
					"catadioptre");
			builderFile.openWriter().close();
			builderFile.delete();
			generatedDirPath.toFile().mkdirs();
		} catch (IOException e) {
			processingEnv.getMessager()
					.printMessage(Kind.ERROR, "Could not detect the generation folder: " + e.getMessage());
		}
	}

	@Override
	public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
		final Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(Testable.class);
		if (annotatedElements.isEmpty() || generatedDirPath == null) {
			return false;
		}

		// Groups the annotated elements by declaring class.
		final Map<TypeElement, Set<Element>> annotatedElementsByDeclaringType = new HashMap<>();
		annotatedElements.stream()
				.forEach(element -> annotatedElementsByDeclaringType
						.computeIfAbsent((TypeElement) element.getEnclosingElement(), k -> new HashSet<>())
						.add(element));

		annotatedElementsByDeclaringType.forEach((declaringType, elements) -> {

			final Set<Modifier> classModifiers = declaringType.getModifiers();
			final Modifier visibility;
			if (classModifiers.contains(Modifier.PUBLIC)) {
				visibility = Modifier.PUBLIC;
			} else if (classModifiers.contains(Modifier.PRIVATE)) {
				visibility = Modifier.PRIVATE;
			} else {
				visibility = null;
			}

			// If the declaring class is private, no code is generated.
			if (visibility != Modifier.PRIVATE) {
				generateProxyMethods(declaringType, elements, visibility);
			}
		});
		return true;
	}

	/**
	 * Generates all the proxy methods for the annotated members of the class.
	 *
	 * @param declaringType the class declaring the members to proxy
	 * @param elements the annotated elements
	 * @param visibility the visibility to apply on the proxy methods
	 */
	private void generateProxyMethods(final TypeElement declaringType, final Set<Element> elements,
			final Modifier visibility) {
		final String packageName = elementUtils.getPackageOf(declaringType).toString();
		final String testableClassName = "Testable" + declaringType.getSimpleName().toString();
		final Builder testableTypeSpec = TypeSpec.classBuilder(testableClassName);
		testableTypeSpec.addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());

		elements.forEach(element -> {
			if (element instanceof ExecutableElement) {
				addTestableMethod(testableTypeSpec, declaringType, (ExecutableElement) element, visibility);
			} else if (element instanceof VariableElement) {
				addTestableField(testableTypeSpec, declaringType, (VariableElement) element, visibility);
			}
		});

		// Then writes the content of the generated class to the file.
		try {
			final JavaFile testableClassFile = JavaFile.builder(packageName, testableTypeSpec.build()).build();
			testableClassFile.writeTo(generatedDirPath);
		} catch (IOException e) {
			processingEnv.getMessager()
					.printMessage(Kind.ERROR,
							"Could not generate the testable source for class " + packageName + "."
									+ declaringType.getSimpleName().toString() + ": " + e.getMessage());
		}
	}

	/**
	 * Builds all the proxy methods to access to a field.
	 *
	 * @param typeSpecBuilder the builder for the class declaring the proxy method
	 * @param declaringType the class declaring the annotated field
	 * @param element the field to access behind the proxy method
	 * @param visibility the visibility of the proxy method
	 */
	private void addTestableField(final TypeSpec.Builder typeSpecBuilder, final TypeElement declaringType,
			final VariableElement element, final Modifier visibility) {
		final Testable annotation = element.getAnnotation(Testable.class);

		if (annotation.getter()) {
			buildGetterMethod(typeSpecBuilder, declaringType, element, visibility);
		}
		if (annotation.setter()) {
			buildSetterMethod(typeSpecBuilder, declaringType, element, visibility);
		}
		if (annotation.clearer()) {
			buildClearerMethod(typeSpecBuilder, declaringType, element, visibility);
		}
	}

	/**
	 * Builds a proxy method to read the content of a field using reflection.
	 *
	 * @param typeSpecBuilder the builder for the class declaring the proxy method
	 * @param declaringType the class declaring the annotated field
	 * @param element the field to access behind the proxy method
	 * @param visibility the visibility of the proxy method
	 */
	private void buildGetterMethod(final Builder typeSpecBuilder, final TypeElement declaringType,
			final VariableElement element, final Modifier visibility) {
		final MethodSpec.Builder methodBuilder = prepareProxyMethod(declaringType,
				MethodSpec.methodBuilder(element.getSimpleName().toString()), visibility, false)
				.addStatement("return $T.getField(instance, $S)", ClassName.get(ReflectionFieldUtils.class),
						element.getSimpleName())
				.returns(TypeVariableName.get(element.asType()));
		typeSpecBuilder.addMethod(methodBuilder.build());
	}

	/**
	 * Builds a proxy method to write the content of a field using reflection.
	 *
	 * @param typeSpecBuilder the builder for the class declaring the proxy method
	 * @param declaringType the class declaring the annotated field
	 * @param element the field to access behind the proxy method
	 * @param visibility the visibility of the proxy method
	 */
	private void buildSetterMethod(final Builder typeSpecBuilder, final TypeElement declaringType,
			final VariableElement element, final Modifier visibility) {
		final MethodSpec.Builder methodBuilder = prepareProxyMethod(declaringType,
				MethodSpec.methodBuilder(element.getSimpleName().toString()), visibility, true)
				.addParameter(TypeVariableName.get(element.asType()), "value")
				.addStatement("$T.setField(instance, $S, value)", ClassName.get(ReflectionFieldUtils.class),
						element.getSimpleName())
				.addStatement("return instance");
		typeSpecBuilder.addMethod(methodBuilder.build());
	}

	/**
	 * Builds a proxy method to set the content of a field to null using reflection.
	 *
	 * @param typeSpecBuilder the builder for the class declaring the proxy method
	 * @param declaringType the class declaring the annotated field
	 * @param element the field to access behind the proxy method
	 * @param visibility the visibility of the proxy method
	 */
	private void buildClearerMethod(final Builder typeSpecBuilder, final TypeElement declaringType,
			final VariableElement element, final Modifier visibility) {
		final String capitalizedName = capitalize(element.getSimpleName().toString());
		final MethodSpec.Builder methodBuilder = prepareProxyMethod(declaringType,
				MethodSpec.methodBuilder("clear" + capitalizedName), visibility, true)
				.addStatement("$T.clearField(instance, $S)", ClassName.get(ReflectionFieldUtils.class),
						element.getSimpleName())
				.addStatement("return instance");
		typeSpecBuilder.addMethod(methodBuilder.build());
	}

	private String capitalize(final String value) {
		final char[] chars = value.toCharArray();
		chars[0] = Character.toUpperCase(chars[0]);
		return new String(chars);
	}

	/**
	 * Builds a proxy method to execute another method using reflection.
	 *
	 * @param typeSpecBuilder the builder for the class declaring the proxy method
	 * @param declaringType the class declaring the annotated method
	 * @param element the method to access behind the proxy method
	 * @param visibility the visibility of the proxy method
	 */
	private void addTestableMethod(final TypeSpec.Builder typeSpecBuilder, final TypeElement declaringType,
			final ExecutableElement element, final Modifier visibility) {
		final MethodSpec.Builder methodBuilder = prepareProxyMethod(declaringType,
				MethodSpec.methodBuilder(element.getSimpleName().toString()), visibility, false)
				.returns(TypeVariableName.get(element.getReturnType()));
		element.getTypeParameters().forEach(e -> methodBuilder.addTypeVariable(TypeVariableName.get(e)));
		element.getParameters().forEach(p -> methodBuilder.addParameter(
				ParameterSpec.builder(TypeVariableName.get(p.asType()), p.getSimpleName().toString()).build()));
		String params = element.getParameters().stream().map(p -> p.getSimpleName().toString())
				.collect(Collectors.joining(","));
		if (!params.isEmpty()) {
			params = ", " + params;
		}
		methodBuilder.addStatement("return $T.executeInvisible(instance, $S" + params + ")",
				ClassName.get(ReflectionMethodUtils.class),
				element.getSimpleName().toString());
		typeSpecBuilder.addMethod(methodBuilder.build());
	}

	/**
	 * Generally configures the proxy method.
	 *
	 * @param declaringType the class declaring the field or method to proxy
	 * @param methodBuilder the builder for the proxy method to complete
	 * @param visibility the visibility of the proxy method
	 * @param returnsInstanceType specifies if the returned type is the same as declaringType.
	 */
	private MethodSpec.Builder prepareProxyMethod(final TypeElement declaringType,
			final MethodSpec.Builder methodBuilder, final Modifier visibility, final boolean returnsInstanceType) {
		methodBuilder
				.addTypeVariable(
						TypeVariableName.get(INSTANCE_PARAM_TYPE, specificationUtils.createTypeName(declaringType)))
				.addModifiers(Modifier.STATIC)
				.addParameter(TypeVariableName.get(INSTANCE_PARAM_TYPE), "instance");
		declaringType.getTypeParameters().forEach(e -> methodBuilder.addTypeVariable(TypeVariableName.get(e)));
		if (visibility != null) {
			methodBuilder.addModifiers(visibility);
		}
		if (returnsInstanceType) {
			methodBuilder.returns(TypeVariableName.get(INSTANCE_PARAM_TYPE));
		}
		return methodBuilder;
	}
}
