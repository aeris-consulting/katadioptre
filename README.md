# Katadioptre

## Reflection tools for testing in Kotlin

[![Maven Central](https://img.shields.io/maven-central/v/io.aeris-consulting/katadioptre.svg?color=blue&label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.aeris-consulting%22%20AND%20a:%22katadioptre%22)
[![Build](https://github.com/aeris-consulting/katadioptre/actions/workflows/gradle-master.yml/badge.svg)](https://github.com/aeris-consulting/katadioptre/actions/workflows/gradle-master.yml)
[![Scan with Detekt](https://github.com/aeris-consulting/katadioptre/actions/workflows/detekt-analysis.yml/badge.svg)](https://github.com/aeris-consulting/katadioptre/actions/workflows/detekt-analysis.yml)

**Katadioptre** is a lightweight library to work with private members in Kotlin using reflection.

With **Katadioptre**, you can:

* set or get private and protected properties,
* execute private and protected functions,
* generate extension functions at compilation to easily access to the private members in your tests.

**Katadioptre** supports variable, optional and named arguments in functions as well as `suspend` functions.

## Why the name Katadioptre?

In French, a "catadioptre" is a reflector you generally have on bicycles or road security equipments.

_Kotlin + Catadioptre = Katadioptre_

## How to use Katadioptre

### Table of contents

* [Import the dependencies](#import-the-dependencies)
* [Generate extension functions to access your private members in tests](#generate-extension-functions-to-access-your-private-members-in-tests)
* [Setting a private or protected property](#setting-a-private-or-protected-property)
* [Getting a private or protected property](#getting-a-private-or-protected-property)
* [Executing a private or protected function](#executing-a-private-or-protected-function)

### Import the dependencies

You can directly get the dependency from Maven Central.

Gradle - Groovy DSL:

```groovy
testImplementation 'io.aeris-consulting:katadioptre:0.2.0'

// For the code generation.
compileOnly 'io.aeris-consulting:katadioptre-annotations:0.2.0'
kapt 'io.aeris-consulting:katadioptre-annotations:0.2.0'
```

Gradle - Kotlin DSL:

```kotlin
testImplementation("io.aeris-consulting:katadioptre:0.2.0")

// For the code generation.
compileOnly("io.aeris-consulting:katadioptre-annotations:0.2.0")
kapt("io.aeris-consulting:katadioptre-annotations:0.2.0")
```

With Maven:

```
<dependency>
  <groupId>io.aeris-consulting</groupId>
  <artifactId>katadioptre</artifactId>
  <version>0.2.0</version>
  <scope>test</scope>
</dependency>

<!-- For the code generation. -->
<dependency>
  <groupId>io.aeris-consulting</groupId>
  <artifactId>katadioptre-annotations</artifactId>
  <version>0.2.0</version>
  <scope>provided</scope>
</dependency>
  [...]
<execution>
  <id>kapt</id>
  <goals>
    <goal>kapt</goal>
  </goals>
  <configuration>
    <sourceDirs>
      <sourceDir>src/main/kotlin</sourceDir>
      <sourceDir>src/main/java</sourceDir>
    </sourceDirs>
    <annotationProcessorPaths>
      <annotationProcessorPath>
        <groupId>io.aeris-consulting</groupId>
        <artifactId>katadioptre-annotations</artifactId>
        <version>0.2.0</version>
      </annotationProcessorPath>
    </annotationProcessorPaths>
  </configuration>
</execution>
```

You can find more on [Maven Central](https://search.maven.org/artifact/io.aeris-consulting/katadioptre).

### Generate extension functions to access your private members in tests

#### Configure the annotation processor

To facilitate the access to the private members in a test context Katadioptre generates for you extended functions
accessible, that route the calls to the private members using reflection.

Whereas those extensions are meant to be only used in a testing context, you can

Those extensions are generated by an annotation processor and
require [Kapt to be configured](https://kotlinlang.org/docs/kapt.html).

To include the generated source into the test sources, configure your project as follow:

Gradle - Groovy DSL
```groovy
kotlin.sourceSets["test"].kotlin.srcDir("build/generated/source/kaptKotlin/katadioptre")
kapt.useBuildCache = false
```

Gradle - Kotlin DSL
```kotlin
kotlin.sourceSets["test"].kotlin.srcDir("build/generated/source/kaptKotlin/katadioptre")
kapt.useBuildCache = false
```

Maven
```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.codehaus.mojo</groupId>
      <artifactId>build-helper-maven-plugin</artifactId>
      <version>3.2.0</version>
      <executions>
        <execution>
          <id>add-test-source</id>
          <phase>generate-test-sources</phase>
          <goals>
            <goal>add-test-source</goal>
          </goals>
          <configuration>
            <sources>
              <source>target/generated-sources/kaptKotlin/katadioptre</source>
            </sources>
          </configuration>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

#### Annotate the code

Then, simply add the `@Testable` annotation on the private members and compile the class:

```kotlin
class KatadioptreExample {

    @Testable
    private var configuration: Map<String, Double>? = mutableMapOf("any" to 1.0)

    @Testable
    private fun multiplySum(multiplier: Double = 1.0, vararg valuesToSum: Double?): Double {
        return valuesToSum.filterNotNull().sum() * multiplier
    }

}
```

Then in your test, you can do the following:

```kotlin
val instance = KatadioptreExample()

// Work with an annotated property.
val result = instance.configuration() // result is a Map<String, Double>?
instance.configuration(mapOf("other" to 2.0))
instance.clearConfiguration()

// Work with an annotated function.
val result = instance.multiplySum(2.0, arrayOf(1.0, 3.0, 6.0))
```

#### Examples

This repository contains three different folders to demo the full configuration and usage of Katadioptre, using Gradle (
with Grovvy or Kotlin DSL) and Maven. You can run this examples locally to see how the whole is working:

* [Gradle with Kotlin DSL](./katadioptre-kotlin-dsl-example)
* [Gradle with Groovy DSL](./katadioptre-groovy-dsl-example)
* [Maven](./katadioptre-maven-example)

#### Limitations

1. Suspend functions are not supported, because the annotation processor cannot see them as annotated functions.
2. Optional parameter are required in the generated functions.

To bypass those limitations - use suspend functions or verify the behavior or a function with a default parameter value

- you will have to use the utils provided below.

### Setting a private or protected property

Setting a property can be performed with the extension function `setProperty` on any instance or object. The function
takes the name of the property as first argument and the value to set as second.

```kotlin
 instance.setProperty("myProperty", 456)
    .setProperty("myOtherProperty", true)
```

Note that you can chain the calls for a fluent coding.

You can also use the `infix` function `withProperty`

```kotlin
 instance withProperty "myProperty" being 456
```

While you can use `setProperty` or `withProperty` to set a property to null, a more concise option is to use the
function
`clearProperty`:

```kotlin
instance clearProperty "myProperty"
```

### Getting a private or protected property

To get the current value of a property, you can use the function `getProperty` providing the property name as first
argument.

```kotlin
val value: Int = instance getProperty "myProperty"
```

The above example shows how to proceed with the `infix` approach, an alternative is the following:

```kotlin
val value = instance.getProperty<Int>("myProperty")
```

### Executing a private or protected function

#### Functions without parameter

The simplest way to execute a niladic function is to use `invokeNoArgs` providing the function name as argument:

```kotlin
val value: Int = instance invokeNoArgs "calculateRandomInteger"
```

The equivalent function to invoke a `suspend` function without parameter is `coInvokeNoArgs`.

```kotlin
val value: Int = instance coInvokeNoArgs "suspendedCalculateRandomInteger"
```

#### Functions with one or more parameters

The functions `invokeInvisible` (and respectively `coInvokeInvisible` for the `suspend` functions) executes the function
with the name passed as first argument, using the parameters provided in the same order.

The following example executes the function `divide` passing it `12.0` and `6.0` as arguments.

```kotlin
val result: Double = instance.invokeInvisible("divide", 12.0, 6.0)
```

The value of `result` is `2.0`.

Given the richness of the functions declarations in Kotlin - optional parameters, varargs, it is not trivial to resolve
the real function to execute when several ones have the same name.

To help in this resolution, **Katadioptre** requires information about the types or names of the null, omitted optional
or variable arguments.

We provide convenient arguments wrappers to achieve this in a concise way.

#### Passing a null argument

To simply pass a null value as an argument while providing the type of the argument, you can use the wrapper `nullOf`.

```kotlin
val value: Double = instance.invokeInvisible("divideIfNotNull", nullOf<Double>(), named("divider", 6.0))
```

##### Naming an argument

Wrap the argument with the function `named`, giving first the name, then the value. To execute the function

```kotlin
private fun divide(value: Double, divider: Double): Double = value / divider
```

You can use:

```kotlin
val value: Double = instance.invokeInvisible("divide", named("value", 12.0), named("divider", 6.0))
```

When using named arguments, their order in the call no longer matters.

If the value is null, simply use `namedNull<Double>("value")`. The type of the argument is required to match the
function in case of method overloading.

```
val value: Double? = instance.invokeInvisible("divideIfNotNull", namedNull<Double>("value"), named("divider", 6.0))
```

#### Passing a variable argument

To provide all the values of a variable argument, you have to use `vararg`:

```kotlin
val result: Double = instance.invokeInvisible("divideTheSum", 2.0, vararg(1.0, 3.0, 6.0))
```

This will execute the following function summing `1.0`, `3.0` and `6.0` (= `10.0`) and dividing the sum by `2.0`:

```kotlin
private fun divideSum(divider: Double = 1.0, vararg values: Double?): Double {
    return values.filterNotNull().sum() / divider
}
```

#### Omitting an optional argument to use the default value

When you want to execute a function that has a parameter with a default value you want to apply, use `omitted`

This function as a default divider set to `1.0`.

```kotlin
val result: Double = instance.invokeInvisible("divideTheSum", omitted<Double>(), vararg(1.0, 3.0, 6.0))
```

This will execute the following function summing `1.0`, `3.0` and `6.0` (= `10.0`) and dividing the sum by `1.0`, the
default value of the parameter `divider`:

```kotlin
private fun divideSum(divider: Double = 1.0, vararg values: Double?): Double {
    return values.filterNotNull().sum() / divider
}
```

#### Combining wrappers

Last but not least, you can also combine the wrappers to create named variable arguments, or named omitted.

```
val result: Double = instance.invokeInvisible("divideTheSum", named("divider", omitted<Double>()), named("values", vararg(1.0, 3.0, 6.0)))
```

While this is in most cases unnecessary, this might help in resolving to the adequate function to execute when functions of a class are too similar.

