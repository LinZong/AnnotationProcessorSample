## Customize a Java Annotation Processor Hand by Hand

Today I had wasted up a lot of time on building up a custom Java annotation processor. It seems that there are few useful docs for a freshman who wants to write annotation processor. Therefore, I am writing this simple guide to help anybody who is working in it.

### 0. Preparing

When there are a huge number of properties that can be configured to build an instance of some type specified, it will provide a builder for user to customize the instance. Just like some code below.

```java
@Data
public class Person {
    private String Name;
    private int Age;
    private boolean Sex;
}

// And we may setup a person using code like below:

Person person = new Person();
person.setName("NemesissLin");
person.setAge(30);
person.setSex(true);

// But we may prefer that way using the fluent chain setter:

Person person = new PersonBuilder()
    				.setName("NemesissLin")
    				.setAge(30)
    				.setSex(true)
    				.build();

// So our goal is implement an annotation @BuilderProperty and generate fluent api automatically.

public class Person {
    
    @BuilderProperty
    private String Name;
    
    @BuilderProperty
    private int Age;
    
    @BuilderProperty
    private boolean Sex;
}
```

### 1. Create a maven project

We will create a maven project with 2 submodule.

Let the project named `annotation-processor-learn`, a submodule named `builder-annotation-processor` to place our annotation processor code, and `person-builder` to place some models and tests for our `@BuilderProperty`.

So, the root project `pom.xml` will look like below:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.nemesiss.dev</groupId>
    <artifactId>annotation-processor-learn</artifactId>
    <packaging>pom</packaging>
    <version>1.0-SNAPSHOT</version>

    <modules>
        <module>builder-annotation-processor</module>
        <module>person-builder</module>
    </modules>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.5.1</version>
                <configuration>
                    <source>1.8</source>
                    <target>1.8</target>
                    <encoding>utf8</encoding>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

The `pom.xml` of `builder-annotation-processor`submodule will look like:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <parent>
        <artifactId>annotation-processor-learn</artifactId>
        <groupId>com.nemesiss.dev</groupId>
        <version>1.0-SNAPSHOT</version>
    </parent>
    <modelVersion>4.0.0</modelVersion>

    <artifactId>builder-annotation-processor</artifactId>

    <properties>
        <auto-service.version>1.0-rc2</auto-service.version>
    </properties>


    <dependencies>
        <dependency>
            <groupId>com.google.auto.service</groupId>
            <artifactId>auto-service</artifactId>
            <version>${auto-service.version}</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>

</project>
```

Here appears an unfamiliar maven dependency: `com.google.auto.service:auto-service`. Don't worry, you will be clear about it soon.

The last `pom.xml` of `person-builder` submodule is here:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <parent>
        <artifactId>annotation-processor-learn</artifactId>
        <groupId>com.nemesiss.dev</groupId>
        <version>1.0-SNAPSHOT</version>
    </parent>
    <modelVersion>4.0.0</modelVersion>

    <artifactId>person-builder</artifactId>
    
    <dependencies>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>1.18.12</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>com.nemesiss.dev</groupId>
            <artifactId>builder-annotation-processor</artifactId>
            <version>1.0-SNAPSHOT</version>
            <scope>compile</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-assembly-plugin</artifactId>
                <version>2.4.1</version>
                <configuration>
                    <descriptorRefs>
                        <descriptorRef>jar-with-dependencies</descriptorRef>
                    </descriptorRefs>
                    <archive>
                        <manifest>
                            <mainClass>com.nemesiss.dev.Main</mainClass>
                        </manifest>
                    </archive>
                </configuration>
                <executions>
                    <execution>
                        <id>make-assembly</id>
                        <phase>package</phase>
                        <goals>
                            <goal>single</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>

</project>
```

Ok. We had set up our project configuration files. Now let' s take a look at our code.

### 2. Define Annotation

In the `builder-annotation-processor`, we can define `@BuilderProperty` mentioned above.

```java
// com.nemesiss.dev.annotations.BuilderProperty

package com.nemesiss.dev.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD) // This annotation can only be annotated on a field.
@Retention(RetentionPolicy.SOURCE) // This annotation only exists in source code. The compiler will discard the annotation while generating class bytes.
public @interface BuilderProperty {
    String setterName() default ""; // Sometimes we may prefer to specify the setter's method name instead of setXXX(...);
}

```

### 3. Complete the annotation processor.

We can create a package `com.nemesiss.dev.annotations.processors` to place our annotation processors' code. Now we only contain one annotation processor named `BuilderPropertyProcessor`.

First, we define a class extends from `AbstractProcessor`. This abstract class had partially completed some key methods of an annotation processor. In most of the time what we need to do is just override its abstract method `process`. 

It's also important to tell the compiler which annotation can it process, and which version of java source code can it support by annotating two annotations on the class level.

```java
// com.nemesiss.dev.annotations.processors.BuilderPropertyProcessor

@SupportedAnnotationTypes("com.nemesiss.dev.annotations.BuilderProperty")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class BuilderPropertyProcessor extends AbstractProcessor {
    ...
}
```

We also need an entry to registry this annotation processor to the compiler in order to let the compiler call our processor in a proper time. 

Java uses a `META-INF` directory to store additional information about a Java program. As for annotation processor, all of them should be registered in the file  `META-INF/services/javax.annotation.processing.Processor`.

![image-20200613222615714](.\tutorials\image-20200613222615714.png)

It must be very annoyed registering all of the annotation processors appeared to such file. Fortunately, Google provides a library named `auto-service` to help us do such work in a handy way. Just annotate your annotation processor class with `AutoService(Processor.class)`, the `auto-service` will help us register our processor to `META-INF`.

Now our processor will look like code snippet below.

```java
@SupportedAnnotationTypes("com.nemesiss.dev.annotations.BuilderProperty")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@AutoService(Processor.class)
public class BuilderPropertyProcessor extends AbstractProcessor {
	
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        return false;
    }
}
```



Now let's implement the very important `process` method.

在继续写代码之前，有必要了解一下一个Java源文件是由哪几种`Element`组成。请看下面的代码。

```java
public class Person { // TypeElement
    
    private String Name; // VariableElement
    
    public void setName(String value) { // ExecutableElement
        this.name = value;
    }
}
```



有必要认识到，整个Java工程的编译阶段会分为若干个轮次。如果在某个编译轮次中，有注解处理器生成了新的Java源文件，那么编译器会在这个轮次结束之后开启下一个轮次，去处理那些新生成的源文件，直到所有轮次结束、所有源文件被编译且不再有新的源文件产生。

相同的注解处理器可能会在多个轮次中发挥作用，编译器会把当前轮次的上下文信息和当前源文件中扫描到的、适合该注解处理器处理的注解`Element`传入`process()`方法。由于这个注解处理器只支持处理一种注解，那么可以不考虑传入的annotations 形参，只使用roundEnv参数去提取我们感兴趣的、有关当前正在处理的注解的元素信息。

```java
Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(BuilderProperty.class);

// 由当前注解处理器仅对Field生效，但是用户可能错误的将注解运用到了别的位置上。因此需要从所有被标注了@BuilderProperty的元素中过滤出Field元素。
// 做得好的注解处理器其实还需要检测出用户错误注解了的元素、并发出错误消息，告知他们注解用错地方了。不过这里先不做这么复杂，直接忽略掉错误标记的Element。
Set<? extends Element> fieldElements = ElementFilter.fieldsIn(annotatedElements);

```

接下来我们需要从带了带了注解的Element实例上进一步提取一些我们想要的信息。为了更好地保存这些信息，我们定义一个类`BuiderFieldProperty`用于提取和存放这些需要的信息。

```java
static class BuilderFieldProperty {

        Element element;

        String fieldName;

        String fullTypeClassName;

        String setterTextName;

        Element setterElement = null;

        public BuilderFieldProperty(Element element) {
            this.element = element;
            fieldName = element.getSimpleName().toString(); // 拿到Field的变量名。
            fullTypeClassName = element.asType().toString(); // 拿到Field的类型全限定名. 如果有一个field的定义为 private String Name; 这一行语句拿到的就是java.lang.String。

            BuilderProperty bp = element.getAnnotation(BuilderProperty.class); // 获取这个Field上的注解实例
            setterTextName = bp.setterName().trim(); // 拿出注解上自定义的setter method name.

            if (setterTextName.length() == 0) { // 如果用户没有自定义setter的名字，那么使用标准的setter命名方式 setXxx(...).
                setterTextName = "set" + firstCharUppercaseIfNeeded(fieldName);
            }

            List<? extends Element> parentClassMethods = ElementFilter.methodsIn(element.getEnclosingElement().getEnclosedElements()); // 拿当前Field所在类的全部元素，准备从中过滤出setter方法。

            List<? extends Element> filteredSetterElements = parentClassMethods.stream().filter(this::filterSetterCondition).collect(Collectors.toList());

            if (!filteredSetterElements.isEmpty()) {
                setterElement = filteredSetterElements.get(0);
            }
        }
    // 根据当前BuilderFieldProperty存储的信息，输出Builder源文件代码的逻辑，比较简单，只是一些机械的字符串拼装工作。
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
    
    // 过滤出setter方法的关键逻辑。一个方法如果是某个field的setter方法，必然满足以下三个条件
    // 1. 这是一个方法
    // 2. 名字符合要求（与自定义名称严格匹配或者是符合setXxx的格式。
    // 3. 形参列表只有一个入参并且参数类型和field的类型保持一致。
    // 具体判断方式看下面的代码。
        private boolean filterSetterCondition(Element setterCandidate) {
            boolean isMethod = setterCandidate.getKind() == ElementKind.METHOD;

            List<? extends TypeMirror> candidateMethodParams = ((ExecutableType) setterCandidate.asType()).getParameterTypes();
            boolean methodNameValid = setterCandidate.getSimpleName().toString().equalsIgnoreCase(setterTextName);
            boolean paramValid = candidateMethodParams.size() == 1 && candidateMethodParams.get(0).toString().equals(fullTypeClassName);
            return isMethod && methodNameValid && paramValid;
        }

    // 工具方法，用于把field的变量名首字母转换成大写。
        private static String firstCharUppercaseIfNeeded(String target) {
            StringBuilder sb = new StringBuilder(target);
            char firstChar = sb.charAt(0);
            firstChar = Character.toUpperCase(firstChar);
            sb.setCharAt(0, firstChar);
            return sb.toString();
        }
    }
```



准备好了`BuilderFieldProperty`类之后，我们就可以继续实现`process`方法了。

```java
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(BuilderProperty.class);

        Set<? extends Element> fieldElements = ElementFilter.fieldsIn(annotatedElements);

        // 把过滤出来的Field逐个构造成BuilderFieldProperty, 提取必要的信息，准备下一步生成源代码用。
        List<BuilderFieldProperty> builderFieldProperties = fieldElements.stream().map(BuilderFieldProperty::new).collect(Collectors.toList());

        // 只有当Field列表不为空的时候才生成源文件。
        if (!builderFieldProperties.isEmpty()) {
            try {
                generateSourceFile(builderFieldProperties);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

```

接下来看看生成源文件的逻辑。主要难点是获取以下几个必须的信息：

1. 当前使用`@BuilderProperty`注解标注了Field的类简单名、类全限定名。
2. 当前使用`@BuilderProperty`注解标注了Field的类所在包名。
3. 计算出对应Builder的类名，Builder生成后所在包的位置

```java
// 1. 获取带注解类的全限定名

        BuilderFieldProperty bfp = builderFieldProperties.get(0);

        // 获取Field的EnclosingElement, 这个就是field所在的类，转型为TypeElement后就能拿到全限定名了。
        String classQualifiedName = ((TypeElement) bfp.element.getEnclosingElement()).getQualifiedName().toString();

// 2. 获取类简单名和包名。
		String packageName = null;
		int lastDot = classQualifiedName.lastIndexOf('.');
        String classSimpleName = classQualifiedName.substring(lastDot + 1);
        if (lastDot > 0) {
            packageName = classQualifiedName.substring(0, lastDot);
        }

// 3. 获取Builder的简单名、全限定名、接收对象的名字。这三个比较简单
// 接受对象就是在builder中通过fluent set api设置属性的对象的变量名。
// 比如下面定义receiveObjectName为 object，那么在builder里面就会有一个private field 类似于
// private Person object = new Person();
// 到时候需要设置属性的时候拼接代码使用的调用对象名字就是'object'. object.setName(value);

		String builderQualifiedName = classQualifiedName + "Builder";
        String builderSimpleName = classSimpleName + "Builder";
        String receiveObjectName = "object";
```



最后就是另外的一些打开writer, 拼接类文件结构的代码。完整的`generateSourceFile`如下所示。

```java
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
```

### 4. Use Annotation Processor

为了使用我们新创建好的注解处理器，我们到`person-builder`项目中简单的创建一个`Person`类.

```java
package com.nemesiss.dev.models;


import com.nemesiss.dev.annotations.BuilderProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class Person {

    @BuilderProperty
    private String name;

    @BuilderProperty
    private int age;

}
```

然后再简单的写一写测试代码

```java
package com.nemesiss.dev;

import com.nemesiss.dev.models.Person;
import com.nemesiss.dev.models.PersonBuilder;

public class Main {
    public static void main(String[] args) {
        Person person = new Person("Hello",20);

        Person person2 = new PersonBuilder()
                .setName("Hello")
                .setAge(20)
                .build();

        Person person3 = new PersonBuilder()
                .setName("Hi!")
                .setAge(30)
                .build();

        System.out.println(person.equals(person2));
        System.out.println(person2.equals(person3));
    }
}

```

最后简单的运行一下`mvn clean package`，进入到`person-builder/target`目录，简单的执行

``java -jar person-builder-1.0-SNAPSHOT-jar-with-dependencies.jar``。可以看到正确的结果。

但是我们的IDEA却未必能正确执行，甚至可能找不到PersonBuilder这个类而爆红。这是因为IDEA很可能没有正确的关联编译过程的注解处理器，以及注解处理器生成的“源文件“。

因此，为了获得最好的编程体验，我们需要对IDEA做进一步的设置，以便支持IDEA的Shift+F10, Ctri+F9这样的内置编译运行方式。

首先打开IDEA的设置，找到Annotation Processor项，把看到的三个项目都打钩上Enable annotation processing。如下图所示

![image-20200613231821073](.\tutorials\image-20200613231821073.png)

然后再打开当前项目的设置，找到`person-builder`(需要提示注解处理器生成的代码的项目)，找到target/generated-sources/annotations文件夹，选中它，再点一下source。这个文件夹会放所有编译过程中生成的源代码文件。我们需要让IDEA认为这个文件夹也是一个“存放源代码”的文件夹，以便IDEA索引文件中的关键词，进行代码高亮。如下图所示：

![image-20200613232102697](.\tutorials\image-20200613232102697.png)



这样一来，我们也可以在IDEA中直接按下Shift+F10运行项目。为了看到效果，可以先在Terminal中执行一句`mvn clean`，把maven编译好的结果全部清掉。这个时候PersonBuilder类提示找不到引用。

![image-20200613232305160](.\tutorials\image-20200613232305160.png)

按下Ctrl+F9编译此项目。等待编译完成后PersonBuilder引用就又找到了。

![image-20200613232327415](.\tutorials\image-20200613232327415.png)

最后再按下Shift+F10运行，正常运行出结果。

![image-20200613232347554](.\tutorials\image-20200613232347554.png)

### 4. Debug your Annotation Processor

人非圣贤，孰能无过？这句话放到编程领域也是非常实用的。很多人都不敢保证自己写的代码一次性bug free。尤其是注解处理器这种涉及到大量属性处理和代码拼接的逻辑。因此在编写注解处理器过程中我们也希望能够单步调试我们写的注解处理器。

但是注解处理器不像正常的Java代码，它不是JVM在运行期执行的，而是Java编译器（Javac）在编译期执行的。因此正常的把Debugger附加到一个JVM上就显得不现实了，需要考虑如何将Debugger挂到Javac上（或者是别的支持注解处理器执行、支持调试注解处理器执行过程的编译器）。

笔者花了非常多的时间去研究如何才能够单步调试注解处理器的代码，以下步骤为使用Javac编译器，在IDEA中编译代码时调试注解处理器的方法。在IDEA Ultimate 2020.1, JDK8上测试成功。



首先需要配置一个IDEA远程调试的Configuration。

![image-20200613232924613](.\tutorials\image-20200613232924613.png)

按照下图所示进行设置：

![image-20200613232950611](.\tutorials\image-20200613232950611.png)

Debugger模式为Attach，选一个你喜欢的端口，这里笔者选了8000，**然后注意一定要把use module classpath设置为你写的注解处理器的模块！**IDEA将会从这个模块中搜索源代码，和调试器中正在执行的字节码相匹配，显示出断点效果。

（网上有一种配置Listen模式的方法，号称可以让调试器等待Javac的执行，自动把Javac停下来，进入断点。但是笔者测试过之后发现这种方式好像是会被IDEA的编译过程打断的。因此最有效的方式是Attach+拼手速点调试按钮）

然后复制IDEA提示的命令行下来。

```
-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8000
```

接下来配置IDEA内置编译器选项

![image-20200613234150772](.\tutorials\image-20200613234150772.png)

找到Build, Execution, Deployment -> Compiler -> Shared build process VM Options: 输入以下内容

```
-Xdebug 刚才复制的命令参数
```

根据上面几个截图中的配置信息，正确的填入内容则为：

```
-Xdebug -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8000
```

完成这些配置之后。在IDEA右上角切换当前调试的配置文件：

![image-20200613234425304](.\tutorials\image-20200613234425304.png)

然后在注解处理器中打下断点，点击Rebuild project之后马上按下Shift+F9 (开始调试的快捷键)，如果配置正确，则当你的注解处理器开始处理源代码文件时，IDEA将会命中断点。此时即可单步调试注解处理器。

不过需要注意的是，在注解处理器的调试过程中，尽量不要去按调试器的Stop按钮，这可能对编译过程造成不可预料的后果。如果在调试器中看到逻辑错了，错就错了，让这个注解处理器执行完。再去改注解处理器的内容然后Rebuild project观察结果。



以上就是自定义注解处理器的踩坑过程了。本过程只是简单的谈及如何做一个最简单的、只能生成额外的源文件的注解处理器。有一些高级的注解处理器是可以支持在编译期动态修改AST，实现字节码注入增强等能力。有关这类注解处理器，笔者将会在日后进行探讨。