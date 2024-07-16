# Maven

## 什么是Maven

> Maven 翻译为"专家"、"内行"，是 Apache 下的一个纯 Java 开发的开源项目。基于项目对象模型（缩写：POM）概念，Maven利用一个中央信息片断能管理一个项目的构建、报告和文档等步骤。
>
> Maven 是一个项目管理工具，可以对 Java 项目进行构建、依赖管理。
>
> Maven 也可被用于构建和管理各种项目，例如 C#，Ruby，Scala 和其他语言编写的项目。Maven 曾是 Jakarta 项目的子项目，现为由 Apache 软件基金会主持的独立 Apache 项目。

Maven 能够帮助开发者完成以下工作：

- 构建
- 文档生成
- 报告
- 依赖
- SCMs
- 发布
- 分发
- 邮件列表

<img src=".\images\image-20240715162633170.png" alt="image-20240715162633170" style="zoom:50%;" />

## 配置

```xml
<!--优先到本地仓库查找依赖，然后是配置的私服配置仓库，然后是镜像仓库，最后是apache的仓库-->

<!--本地仓库配置-->
<localRepository>/path/to/local/repo</localRepository>


<!--镜像仓库配置-->
<mirror>
    <id>aliyunmaven</id>
    <mirrorOf>central</mirrorOf>
    <name>Alibaba Maven Mirror</name>
    <url>https://maven.aliyun.com/repository/central</url>
</mirror>

<!--私服配置的仓库-->
    <server>
        <!--私服配置的仓库id-->
      <id>deploymentRepo</id>
        <!--私服配置的仓库用户名-->
      <username>repouser</username>
        <!--私服配置的仓库密码-->
      <password>repopwd</password>
    </server>

<!--私服配置的仓库-->
  <repositories>
    <repository>
      <id>deploymentRepo</id>
      <name>nexus repository</name>
      <url>https://nexus.xxx.cn/repository/maven-public/</url>
        <!--这个标签用来配置Maven如何处理发布版本的依赖。-->
      <releases>
          <!--这个标签控制是否启用对发布版本的依赖的搜索和使用。-->
        <enabled>true</enabled>
      </releases>
        <!--这个标签用来配置Maven如何处理快照版本的依赖。-->
      <snapshots>
          <!--这个标签控制是否启用对快照版本的依赖的搜索和使用。-->
        <enabled>true</enabled>
          <!--这个标签用于配置Maven如何更新快照版本的依赖 
		always 表示Maven在每次构建时都会检查是否有更新的快照版本，并自动下载最新的版本-->
        <updatePolicy>always</updatePolicy>
          <!--这个标签用于配置Maven如何处理依赖的校验和验证。-->
          <!--fail 表示如果校验和验证失败，Maven构建将会失败。-->
        <checksumPolicy>fail</checksumPolicy>
      </snapshots>
    </repository>
  </repositories>


<!--配置编译的jdk版本-->
    <profile>
      <id>jdk1.8</id>
      <activation>
        <jdk>1.8</jdk>
        <activeByDefault>true</activeByDefault>
      </activation>
    </profile>

<!--配置maven编译器的信息-->
  <properties>
    <maven.compiler.source>8</maven.compiler.source>
    <maven.compiler.target>8</maven.compiler.target>
    <maven.compiler.compilerVersion>8</maven.compiler.compilerVersion>
  </properties>


<!--这个标签用于列出Maven项目可以从中查找和下载Maven插件的仓库列表。-->
  <pluginRepositories>
    <pluginRepository>
      <id>doc-nexus</id>
      <name>doc nexus repository</name>
      <url>https://nexus.doc.xkw.cn/repository/maven-public/</url>
      <releases>
        <enabled>true</enabled>
      </releases>
      <snapshots>
        <enabled>false</enabled>
      </snapshots>
    </pluginRepository>
  </pluginRepositories>




      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
        <version>${spring.boot.version}</version>
        <configuration>
            <!--这个标签用于控制构建过程中是否在一个单独的JVM中运行插件的目标（如spring-boot:run或spring-boot:repackage）。
当设置为true时，插件将在一个新的JVM实例中运行，这意味着插件的运行环境与构建过程的其余部分隔离，可以避免一些类路径冲突问题，但可能会增加构建时间。
当设置为false时，插件将在当前构建的JVM中运行，这样可以减少构建时间，但可能引入类路径冲突的风险。-->
          <fork>true</fork>
            
            <!--这个标签用于控制是否在构建过程中将项目的资源文件（如静态资源、配置文件等）添加到最终的输出中。
当设置为true时，Maven会在构建过程中将项目的资源文件添加到输出的包中，这对于需要访问配置文件或静态资源的应用程序至关重要。
当设置为false时，资源文件将不会被包含在最终的输出中，这可能导致应用程序运行时找不到必要的资源文件而失败。-->
          <addResources>true</addResources>
        </configuration>
      </plugin>







	<plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>flatten-maven-plugin</artifactId>
        <version>1.5.0</version>
        <configuration>
            <!--设置为true时，插件会在执行后更新pom.xml文件，将所有父级和依赖的元数据展平到当前项目中，便于查看和维护。-->
          <updatePomFile>true</updatePomFile>
            <!--指定插件如何处理项目依赖和继承结构。resolveCiFriendliesOnly模式意味着插件只会展平CI友好的依赖，即那些在持续集成环境中常用的依赖。-->
          <flattenMode>resolveCiFriendliesOnly</flattenMode>
        </configuration>
            <!--定义插件的执行周期绑定和目标，可以有多个<execution>元素-->
        <executions>
            <!--描述插件的一次执行，包括执行ID、绑定的构建生命周期阶段和要执行的目标-->
          <execution>
            <id>flatten</id>
              <!--绑定插件执行到Maven生命周期的特定阶段，例如process-resources或clean-->
            <phase>process-resources</phase>
            <goals>
                <!--列出要执行的插件目标，例如flatten或clean-->
              <goal>flatten</goal>
            </goals>
          </execution>
          <execution>
            <id>flatten.clean</id>
            <phase>clean</phase>
            <goals>
              <goal>clean</goal>
            </goals>
          </execution>
        </executions>
      </plugin>






 			<plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-resources-plugin</artifactId>
                <version>3.3.1</version>
                <configuration>
                    <delimiters>
                        <delimiter>@</delimiter>
                    </delimiters>
                    <useDefaultDelimiters>false</useDefaultDelimiters>
                </configuration>
            </plugin>





 		<resources>
            <resource>
                <directory>src/main/resources</directory>
                <!--控制是否对资源文件进行过滤处理。当设置为true时，Maven会在构建过程中对资源文件中的占位符进行替换，通常使用maven-resources-plugin插件完成这一过程。
占位符通常以特定的格式出现，如${property}或@property@（取决于插件配置），并在构建时被替换为实际的值，这允许动态配置资源文件中的属性。-->
                <filtering>true</filtering>
            </resource>
        </resources>
```







## 工程

pom工程是逻辑工程，用在父级工程或者聚合工程中，用于管理jar包 版本控制

jar工程 即可以被打包成jar包的项目

war工程 将会打包成war 用于发布在服务器(tomcat)的工程



## 生命周期

Maven主要有三个生命周期：

**Clean Lifecycle**：负责清理工作目录，移除以前构建产生的文件。它包含以下阶段：

- pre-clean
- clean
- post-clean

**Default Lifecycle**：这是最常用的生命周期，包含了从源代码到可部署包的完整构建过程。它包含以下阶段：

- validate
- compile
- test
- package
- verify
- install
- deploy
- site
- post-site
- site-deploy

**Site Lifecycle**：用于生成项目文档和站点信息。它包含以下阶段：

- pre-site
- site
- post-site
- site-deploy

当你执行一个Maven命令时，Maven会执行该命令所处阶段及其之前的所有阶段。例如，当你运行mvn compile时，Maven会先执行validate阶段，然后才执行compile阶段。
Maven还允许你在pom.xml文件中配置插件目标与生命周期阶段的绑定，这样就可以在特定的阶段自动执行插件的任务。此外，你还可以通过<goals>标签直接调用插件目标，而不必绑定到特定的生命周期阶段。



## 插件

**spring-boot-maven-plugin**

spring-boot-maven-plugin是Spring Boot框架提供的Maven插件，用于简化Spring Boot应用的构建和打包过程。它提供了多种功能来帮助开发者更容易地构建和运行Spring Boot应用，包括：

- 打包: 将应用打包成可执行的JAR或WAR文件，其中包含所有依赖的库，使得应用可以独立运行。
- Repackage: 重新打包应用的主类，使其成为Spring Boot可执行的格式。
- Run: 提供一个目标来运行Spring Boot应用，通常用于开发环境中的快速迭代。
- Starters: 支持Spring Boot的starter依赖，简化了依赖管理。
- Customization: 允许配置各种构建选项，比如是否在构建时启动一个新JVM（fork），以及是否包含资源文件（addResources）。

在上述配置中，<fork>true</fork>表示在运行或测试时，应用将在新的JVM中运行，这有助于避免类路径冲突。<addResources>true</addResources>则表示在构建时将项目资源文件包含在内。

**flatten-maven-plugin**

flatten-maven-plugin是由MojoHaus提供的Maven插件，它的主要功能是简化Maven项目的构建配置。它通过将项目的所有依赖和父POM的配置合并到当前项目的POM中，从而生成一个“扁平化”的POM文件。这样做的好处包括：

- 简化构建: 生成的POM文件包含了所有必要的依赖和配置，使得构建过程更加透明和简单。
- 持续集成: 在持续集成环境中，扁平化的POM可以避免由于网络问题导致的依赖下载失败。
- 易于调试和维护: 所有的配置都在一个地方，便于理解和维护。

在配置中，<updatePomFile>true</updatePomFile>表示插件将更新POM文件，而<flattenMode>resolveCiFriendliesOnly</flattenMode>则指定了展平的模式，这里选择的是只展平CI友好的依赖，即那些在持续集成环境中常用的依赖。



**maven-resources-plugin**

是Maven的标准插件之一，用于处理项目中的资源文件，如属性文件、图像和其他非Java源代码文件。它的主要功能包括：

- Resource Filtering: 通过配置<delimiters>和<useDefaultDelimiters>，插件可以处理资源文件中的占位符，将它们替换为实际的值。例如，使用@作为分隔符，可以在资源文件中使用@property@形式的占位符，然后在构建时将它们替换为真实的值。
- Resource Copying: 插件负责将资源文件从源目录复制到构建目录，确保它们被包含在最终的构建产物中。

在上述配置中，maven-resources-plugin被配置为使用@作为占位符的分隔符，并禁用了默认的分隔符，这意味着所有使用@符号包围的占位符将在构建过程中被替换。



**mybatis-generator-maven-plugin**

是mybatis-generator-maven-plugin，它是MyBatis框架的一个辅助工具，用于自动生成基于数据库表的Java实体类、Mapper接口、XML映射文件以及一些DAO层的基础代码。这个插件利用Maven构建工具的生命周期和插件机制，使得代码生成过程可以自动化地融入到项目的构建流程中。

代码生成：

- 根据数据库表结构自动生成对应的Java实体类（POJOs）。
- 生成Mapper接口和对应的XML映射文件，包括基本的CRUD操作。
- 可以生成DAO层的基础代码，减少手动编写模板代码的工作量。

定制化生成：

- 支持通过配置文件（通常是mybatis-generator.xml）来定制生成的代码，比如指定包名、类名前缀、后缀等。
- 可以通过模板引擎来自定义生成的代码样式和结构。

集成Maven生命周期：

- 可以在Maven的构建生命周期中的某个阶段执行代码生成，如generate-sources阶段，确保每次构建时代码都是最新的。

依赖管理：

- 插件本身可能依赖于数据库驱动或其他组件，这些依赖关系可以通过Maven的dependencies标签管理，确保插件运行所需的所有库都可用。





























