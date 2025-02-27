# Spring源码



## 整体概述

![image-20250213102918979](.\images\image-20250213102918979.png)

**核心方法**

**AbstractApplicationContext**中的refresh方法

- 初始化ApplicationContext
- 创建并且初始化DefaultListableBeanFactory，加载BeanDefinitions
- 配置DefaultListableBeanFactory
- 扩展方法postProcessBeanFactory()
- 调用所有的BeanFactoryPostProcessor
- 将所有的BeanPostProcessor注册到DefaultListableBeanFactory
- initMessageSource() 多语言初始化
- 注册事件多播器 ApplicationEventMulticaster
- 扩展方法onRefresh()
- 注册监听器 ApplicationListener
- 实例化并且初始化所有的单例Bean
- 完成刷新 finishRefresh()

```java
@Override
public void refresh() throws BeansException, IllegalStateException {
    synchronized (this.startupShutdownMonitor) {
       // Prepare this context for refreshing.
       prepareRefresh();

       // Tell the subclass to refresh the internal bean factory.
       ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();

       // Prepare the bean factory for use in this context.
       prepareBeanFactory(beanFactory);

       try {
          // Allows post-processing of the bean factory in context subclasses.
          postProcessBeanFactory(beanFactory);

          // Invoke factory processors registered as beans in the context.
          invokeBeanFactoryPostProcessors(beanFactory);

          // Register bean processors that intercept bean creation.
          registerBeanPostProcessors(beanFactory);

          // Initialize message source for this context.
          initMessageSource();

          // Initialize event multicaster for this context.
          initApplicationEventMulticaster();

          // Initialize other special beans in specific context subclasses.
          onRefresh();

          // Check for listener beans and register them.
          registerListeners();

          // Instantiate all remaining (non-lazy-init) singletons.
          finishBeanFactoryInitialization(beanFactory);

          // Last step: publish corresponding event.
          finishRefresh();
       }

       catch (BeansException ex) {
          if (logger.isWarnEnabled()) {
             logger.warn("Exception encountered during context initialization - " +
                   "cancelling refresh attempt: " + ex);
          }

          // Destroy already created singletons to avoid dangling resources.
          destroyBeans();

          // Reset 'active' flag.
          cancelRefresh(ex);

          // Propagate exception to caller.
          throw ex;
       }

       finally {
          // Reset common introspection caches in Spring's core, since we
          // might not ever need metadata for singleton beans anymore...
          resetCommonCaches();
       }
    }
}
```









### 常见重要接口、BeanFactory和FactoryBean的区别

![image-20250213103145924](.\images\image-20250213103145924.png)

BeanFactory是用于管理整个spring中的bean的容器，而FactoryBean则是用于使得某个bean跳过spring繁杂的创建流程，将实例化bean和初始化bean的权利交到自己手上，是一种快速创建bean的方法。

```java

//当需要Test的bean对象时，就会使用MyFactoryBean的getObject快速获得
@Component("myFactoryBean")
public class MyFactoryBean implements FactoryBean<Test> {
    
    //等同于getBean(myFactoryBean);
    @Resource(name = "myFactoryBean")
    private Test test;
    
    //等同于getBean(&myFactoryBean);
    @Resource(name = "&myFactoryBean")
    private MyFactoryBean myFactoryBean;
    
    
    @Override
    public Test getObject() throws Exception {
        return new Test();
    }

    @Override
    public Class<?> getObjectType() {
        return Test.class;
    }
}
```



### ApplicationContext和BeanFactory的接口继承差异

以核心类**AbstractApplicationContext**为例，ApplicationContext更像是一个包含着BeanFactory的外部包装，具有加载资源、事件发布和管理BeanFactory的能力，为BeanFactory提供支持。

<img src=".\images\image-20250213105127872.png" alt="image-20250213105127872" style="zoom: 33%;" />

- 继承了ResourceLoader和ResourcePatternResolver，拥有了资源加载和资源路径解析的能力
- 继承了ApplicationEventPublisher，具有应用事件推送的能力
- 继承了MessageSource，具有国际化参数的能力
- 继承了BeanFactory，在ApplicationContext中具有了控制和管理BeanFactory的能力



以核心类**DefaultListableBeanFactory**为例，其主要作用只有两个，管理Bean和BeanDefinition

<img src=".\images\image-20250213105246688.png" alt="image-20250213105246688" style="zoom: 50%;" />

- 继承了SingletonBeanRegistry，具有增删改查单例Bean的能力
- 继承了BeanDefinitionRegistry，具有增上改查BeanDefinition的能力

## ApplicationContext创建

以常见的new某种类型的ApplicationContext启动

```java
ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("tx.xml");

//支持Ant风格的路径
ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("/*.xml");
```

### ClassPathXmlApplicationContext

```java
public class ClassPathXmlApplicationContext extends AbstractXmlApplicationContext {
    
    	public ClassPathXmlApplicationContext(
			String[] configLocations, boolean refresh, @Nullable ApplicationContext parent)
			throws BeansException {

		super(parent);
		setConfigLocations(configLocations);
		if (refresh) {
			refresh();
		}
	}
    
}
```





### AbstractApplicationContext

```java
public abstract class AbstractApplicationContext extends DefaultResourceLoader
       implements ConfigurableApplicationContext {
    
    //资源路径解析器
    private ResourcePatternResolver resourcePatternResolver;

    //默认构造方法
    public AbstractApplicationContext() {
		this.resourcePatternResolver = getResourcePatternResolver();
	}
    
    
    protected ResourcePatternResolver getResourcePatternResolver() {
		return new PathMatchingResourcePatternResolver(this);
	}
    
    
    //获取环境
    @Override
	public ConfigurableEnvironment getEnvironment() {
		if (this.environment == null) {
			this.environment = createEnvironment();
		}
		return this.environment;
	}
    
    protected ConfigurableEnvironment createEnvironment() {
		return new StandardEnvironment();
	}

}
```



### PathMatchingResourcePatternResolver

```java
public class PathMatchingResourcePatternResolver implements ResourcePatternResolver {
    
    //资源加载器 这里使用的是ApplicationContext作为资源加载器
	private final ResourceLoader resourceLoader;

    //ant风格的路径匹配器
	private PathMatcher pathMatcher = new AntPathMatcher();
    
    String CLASSPATH_ALL_URL_PREFIX = "classpath*:";
    

    @Override
	public Resource[] getResources(String locationPattern) throws IOException {
		Assert.notNull(locationPattern, "Location pattern must not be null");
        //如果文件路径是以classpath*:开头的
		if (locationPattern.startsWith(CLASSPATH_ALL_URL_PREFIX)) {
			// a class path resource (multiple resources for same name possible)
            //去掉classpath*:前缀，如果剩下的路径是以Ant风格的 如 com/*.xml等
			if (getPathMatcher().isPattern(locationPattern.substring(CLASSPATH_ALL_URL_PREFIX.length()))) {
				// a class path resource pattern
                 //使用Ant风格解析匹配文件
				return findPathMatchingResources(locationPattern);
			}
			else {
                  //不是Ant风格的路径 是一个普通的绝对路径/相对路径 如 xxx.yml 或 /META-INFO/xxx.xml
				// all class path resources with the given name
                 // 使用类加载器加载相对路径的文件
				return findAllClassPathResources(locationPattern.substring(CLASSPATH_ALL_URL_PREFIX.length()));
			}
		}
		else {
			// Generally only look for a pattern after a prefix here,
			// and on Tomcat only after the "*/" separator for its "war:" protocol.
			int prefixEnd = (locationPattern.startsWith("war:") ? locationPattern.indexOf("*/") + 1 :
					locationPattern.indexOf(':') + 1);
			if (getPathMatcher().isPattern(locationPattern.substring(prefixEnd))) {
				// a file pattern
				return findPathMatchingResources(locationPattern);
			}
			else {
				// a single resource with the given name
				return new Resource[] {getResourceLoader().getResource(locationPattern)};
			}
		}
	}

}
```

> Ant风格
>
> <img src=".\images\image-20250213111616906.png" alt="image-20250213111616906" style="zoom: 50%;" />



### StandardEnvironment

```java
public class StandardEnvironment extends AbstractEnvironment {
    
    /** System environment property source name: {@value}. */
	public static final String SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME = "systemEnvironment";

	/** JVM system properties property source name: {@value}. */
	public static final String SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME = "systemProperties";
    

    //将System.getProperties()和System.getenv()装载到StandardEnvironment中
    @Override
	protected void customizePropertySources(MutablePropertySources propertySources) {
		propertySources.addLast(
				new PropertiesPropertySource(SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME, getSystemProperties()));
		propertySources.addLast(
				new SystemEnvironmentPropertySource(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, getSystemEnvironment()));
	}


}
```



**解析传入的xml路径**

```java
//AbstractRefreshableConfigApplicationContext
protected String resolvePath(String path) {
    return getEnvironment().resolveRequiredPlaceholders(path);
}
```

```java
//AbstractEnvironment

@Override
public String resolveRequiredPlaceholders(String text) throws IllegalArgumentException {
    return this.propertyResolver.resolveRequiredPlaceholders(text);
}
```

```java
//AbstractPropertyResolver
@Override
public String resolveRequiredPlaceholders(String text) throws IllegalArgumentException {
	if (this.strictHelper == null) {
        //创建占位符解析器
		this.strictHelper = createPlaceholderHelper(false);
	}
	return doResolvePlaceholders(text, this.strictHelper);
}

private String doResolvePlaceholders(String text, PropertyPlaceholderHelper helper) {
	return helper.replacePlaceholders(text, this::getPropertyAsRawString);
}
```

### PropertyPlaceholderHelper 

```java
//${}占位符属性替换解析器 工具类类型
public class PropertyPlaceholderHelper {
    
	public String replacePlaceholders(String value, PlaceholderResolver placeholderResolver) {
		Assert.notNull(value, "'value' must not be null");
		return parseStringValue(value, placeholderResolver, null);
	}
    

    //解析并且替换${}占位符的内容
    protected String parseStringValue(
			String value, PlaceholderResolver placeholderResolver, @Nullable Set<String> visitedPlaceholders) {

        //找到$的下标
		int startIndex = value.indexOf(this.placeholderPrefix);
		if (startIndex == -1) {
			return value;
		}

		StringBuilder result = new StringBuilder(value);
		while (startIndex != -1) {
            //找到}的下标
			int endIndex = findPlaceholderEndIndex(result, startIndex);
			if (endIndex != -1) {
                //获取${}占位符中间的内容
				String placeholder = result.substring(startIndex + this.placeholderPrefix.length(), endIndex);
				String originalPlaceholder = placeholder;
				if (visitedPlaceholders == null) {
					visitedPlaceholders = new HashSet<>(4);
				}
				if (!visitedPlaceholders.add(originalPlaceholder)) {
					throw new IllegalArgumentException(
							"Circular placeholder reference '" + originalPlaceholder + "' in property definitions");
				}
                 //递归一下 避免占位符嵌套
				// Recursive invocation, parsing placeholders contained in the placeholder key.
				placeholder = parseStringValue(placeholder, placeholderResolver, visitedPlaceholders);
                
                  //通过systemEnvironment和systemProperties中的key去匹配占位符中间的内容 替换成value
				// Now obtain the value for the fully resolved key...
				String propVal = placeholderResolver.resolvePlaceholder(placeholder);
				if (propVal == null && this.valueSeparator != null) {
					int separatorIndex = placeholder.indexOf(this.valueSeparator);
					if (separatorIndex != -1) {
						String actualPlaceholder = placeholder.substring(0, separatorIndex);
						String defaultValue = placeholder.substring(separatorIndex + this.valueSeparator.length());
						propVal = placeholderResolver.resolvePlaceholder(actualPlaceholder);
						if (propVal == null) {
							propVal = defaultValue;
						}
					}
				}
				if (propVal != null) {
					// Recursive invocation, parsing placeholders contained in the
					// previously resolved placeholder value.
					propVal = parseStringValue(propVal, placeholderResolver, visitedPlaceholders);
					result.replace(startIndex, endIndex + this.placeholderSuffix.length(), propVal);
					if (logger.isTraceEnabled()) {
						logger.trace("Resolved placeholder '" + placeholder + "'");
					}
					startIndex = result.indexOf(this.placeholderPrefix, startIndex + propVal.length());
				}
				else if (this.ignoreUnresolvablePlaceholders) {
					// Proceed with unprocessed value.
					startIndex = result.indexOf(this.placeholderPrefix, endIndex + this.placeholderSuffix.length());
				}
				else {
					throw new IllegalArgumentException("Could not resolve placeholder '" +
							placeholder + "'" + " in value \"" + value + "\"");
				}
				visitedPlaceholders.remove(originalPlaceholder);
			}
			else {
				startIndex = -1;
			}
		}
		return result.toString();
	}

}
```

<img src=".\images\image-20250213113417680.png" alt="image-20250213113417680" style="zoom:50%;" />

### PropertySourcesPropertyResolver

```java

public class PropertySourcesPropertyResolver extends AbstractPropertyResolver {

	@Override
	@Nullable
	protected String getPropertyAsRawString(String key) {
		return getProperty(key, String.class, false);
	}

	@Nullable
	protected <T> T getProperty(String key, Class<T> targetValueType, boolean resolveNestedPlaceholders) {
        //this.propertySources就是包含了systemEnvironment和systemProperties的列表集合
		if (this.propertySources != null) {
			for (PropertySource<?> propertySource : this.propertySources) {
				if (logger.isTraceEnabled()) {
					logger.trace("Searching for key '" + key + "' in PropertySource '" +
							propertySource.getName() + "'");
				}
                //通过systemEnvironment和systemProperties的key查找value
				Object value = propertySource.getProperty(key);
				if (value != null) {
					if (resolveNestedPlaceholders && value instanceof String) {
						value = resolveNestedPlaceholders((String) value);
					}
					logKeyFound(key, propertySource, value);
					return convertValueIfNecessary(value, targetValueType);
				}
			}
		}
		if (logger.isTraceEnabled()) {
			logger.trace("Could not find key '" + key + "' in any property source");
		}
		return null;
	}
}
```





## prepareRefresh()

```java
//AbstractApplicationContext

protected void prepareRefresh() {
    
    //从关闭状态切换成开启状态
    // Switch to active.
    this.startupDate = System.currentTimeMillis();
    this.closed.set(false);
    this.active.set(true);

    if (logger.isDebugEnabled()) {
       if (logger.isTraceEnabled()) {
          logger.trace("Refreshing " + this);
       }
       else {
          logger.debug("Refreshing " + getDisplayName());
       }
    }

    //扩展点 可以设置必须验证的属性 
    // Initialize any placeholder property sources in the context environment.
    initPropertySources();

    //校验环境
    // Validate that all properties marked as required are resolvable:
    // see ConfigurablePropertyResolver#setRequiredProperties
    getEnvironment().validateRequiredProperties();

    //存储预先监听器 在spring都是空的 等mvc才有对象
    // Store pre-refresh ApplicationListeners...
    if (this.earlyApplicationListeners == null) {
       this.earlyApplicationListeners = new LinkedHashSet<>(this.applicationListeners);
    }
    else {
       // Reset local application listeners to pre-refresh state.
       this.applicationListeners.clear();
       this.applicationListeners.addAll(this.earlyApplicationListeners);
    }

    // Allow for the collection of early ApplicationEvents,
    // to be published once the multicaster is available...
    this.earlyApplicationEvents = new LinkedHashSet<>();
}
```



## 创建BeanFactory并加载BeanDefinition

```java
//AbstractApplicationContext

protected ConfigurableListableBeanFactory obtainFreshBeanFactory() {
    refreshBeanFactory();
    return getBeanFactory();
}
```



```java
//AbstractRefreshableApplicationContext

@Override
protected final void refreshBeanFactory() throws BeansException {
    if (hasBeanFactory()) {
       destroyBeans();
       closeBeanFactory();
    }
    try {
       //创建DefaultListableBeanFactory
       DefaultListableBeanFactory beanFactory = createBeanFactory();
       //设置beanFactory的Id org.springframework.context.support.ClassPathXmlApplicationContext@e6ea0c6
       beanFactory.setSerializationId(getId());
       //个性化DefaultListableBeanFactory
       customizeBeanFactory(beanFactory);
       //加载BeanDefinitions
       loadBeanDefinitions(beanFactory);
       this.beanFactory = beanFactory;
    }
    catch (IOException ex) {
       throw new ApplicationContextException("I/O error parsing bean definition source for " + getDisplayName(), ex);
    }
}
```

### 创建BeanFactory

```java
//AbstractRefreshableApplicationContext
protected DefaultListableBeanFactory createBeanFactory() {
    return new DefaultListableBeanFactory(getInternalParentBeanFactory());
}
```

```java
//AbstractRefreshableApplicationContext
//扩展点 可以修改下面两个属性的值
protected void customizeBeanFactory(DefaultListableBeanFactory beanFactory) {
    //设置是否允许 默认允许
    if (this.allowBeanDefinitionOverriding != null) {
       beanFactory.setAllowBeanDefinitionOverriding(this.allowBeanDefinitionOverriding);
    }
    //设置是否允许循环依赖 默认允许 不允许的情况下在发现循环依赖就报错
    if (this.allowCircularReferences != null) {
       beanFactory.setAllowCircularReferences(this.allowCircularReferences);
    }
}
```

### 加载BeanDefinitions

#### 以xml方式加载beanDefinitions

```java
//AbstractXmlApplicationContext

@Override
protected void loadBeanDefinitions(DefaultListableBeanFactory beanFactory) throws BeansException, IOException {
    // Create a new XmlBeanDefinitionReader for the given BeanFactory.
    XmlBeanDefinitionReader beanDefinitionReader = new XmlBeanDefinitionReader(beanFactory);

    // Configure the bean definition reader with this context's
    // resource loading environment.
    beanDefinitionReader.setEnvironment(this.getEnvironment());
    beanDefinitionReader.setResourceLoader(this);
    //这里设置了针对xsd和dtd两种xml格式的解析器
    beanDefinitionReader.setEntityResolver(new ResourceEntityResolver(this));

    // Allow a subclass to provide custom initialization of the reader,
    // then proceed with actually loading the bean definitions.
    initBeanDefinitionReader(beanDefinitionReader);
    loadBeanDefinitions(beanDefinitionReader);
}
```

##### ResourceEntityResolver

```java
//针对xsd和dtd两种xml格式的解析器
public class ResourceEntityResolver extends DelegatingEntityResolver {

    public ResourceEntityResolver(ResourceLoader resourceLoader) {
        //核心在于其父类
		super(resourceLoader.getClassLoader());
		this.resourceLoader = resourceLoader;
	}
}


public class DelegatingEntityResolver implements EntityResolver {

	/** Suffix for DTD files. */
	public static final String DTD_SUFFIX = ".dtd";

	/** Suffix for schema definition files. */
	public static final String XSD_SUFFIX = ".xsd";

	//dtd格式的解析器
	private final EntityResolver dtdResolver;
	//xsd格式的解析器
	private final EntityResolver schemaResolver;

    public DelegatingEntityResolver(@Nullable ClassLoader classLoader) {
		this.dtdResolver = new BeansDtdResolver();
		this.schemaResolver = new PluggableSchemaResolver(classLoader);
	}
}



```

###### xsd格式

META-INF/spring.schemas文件中存储了对应spring-beans.xsd格式文件的本地映射路径，在网络不畅通时可以使用本地xsd格式文件解析bean标签

<img src=".\images\image-20250225111422617.png" alt="image-20250225111422617" style="zoom:50%;" />

<img src=".\images\image-20250225111650181.png" alt="image-20250225111650181" style="zoom: 33%;" />

<img src="D:\doc\my\studymd\LearningNotes\framework\spring\images\image-20250225112032475.png" alt="image-20250225112032475" style="zoom:33%;" />

```java
//xsd格式的解析器 读取META-INF/spring.schemas中的xsd文件作为xsd格式的bean标签规则
public class PluggableSchemaResolver implements EntityResolver {

	/**
	 * The location of the file that defines schema mappings.
	 * Can be present in multiple JAR files.
	 */
	public static final String DEFAULT_SCHEMA_MAPPINGS_LOCATION = "META-INF/spring.schemas";
}
```



###### dtd格式

<img src="D:\doc\my\studymd\LearningNotes\framework\spring\images\image-20250225112055062.png" alt="image-20250225112055062" style="zoom:33%;" />

```java
//dtd格式的解析器 读取当前目录下的spring-beans.dtd文件作为dtd格式的bean标签规则
public class BeansDtdResolver implements EntityResolver {

	private static final String DTD_EXTENSION = ".dtd";

	private static final String DTD_NAME = "spring-beans";
}
```



##### doLoadBeanDefinitions

```java
//XmlBeanDefinitionReader

protected int doLoadBeanDefinitions(InputSource inputSource, Resource resource)
       throws BeanDefinitionStoreException {

    try {
        //解析xml文件成为对象Document
       Document doc = doLoadDocument(inputSource, resource);
        //注册BeanDefinitions
       int count = registerBeanDefinitions(doc, resource);
       if (logger.isDebugEnabled()) {
          logger.debug("Loaded " + count + " bean definitions from " + resource);
       }
       return count;
    }
    catch (BeanDefinitionStoreException ex) {
       throw ex;
    }
    catch (SAXParseException ex) {
       throw new XmlBeanDefinitionStoreException(resource.getDescription(),
             "Line " + ex.getLineNumber() + " in XML document from " + resource + " is invalid", ex);
    }
    catch (SAXException ex) {
       throw new XmlBeanDefinitionStoreException(resource.getDescription(),
             "XML document from " + resource + " is invalid", ex);
    }
    catch (ParserConfigurationException ex) {
       throw new BeanDefinitionStoreException(resource.getDescription(),
             "Parser configuration exception parsing XML from " + resource, ex);
    }
    catch (IOException ex) {
       throw new BeanDefinitionStoreException(resource.getDescription(),
             "IOException parsing XML document from " + resource, ex);
    }
    catch (Throwable ex) {
       throw new BeanDefinitionStoreException(resource.getDescription(),
             "Unexpected exception parsing XML document from " + resource, ex);
    }
}

//注册BeanDefinitions
public int registerBeanDefinitions(Document doc, Resource resource) throws BeanDefinitionStoreException {
	BeanDefinitionDocumentReader documentReader = createBeanDefinitionDocumentReader();
	int countBefore = getRegistry().getBeanDefinitionCount();
     //createReaderContext中创建了对应默认namespace的处理器
	documentReader.registerBeanDefinitions(doc, createReaderContext(resource));
	return getRegistry().getBeanDefinitionCount() - countBefore;
}

```

##### doRegisterBeanDefinitions

```java
//DefaultBeanDefinitionDocumentReader

protected void doRegisterBeanDefinitions(Element root) {
    // Any nested <beans> elements will cause recursion in this method. In
    // order to propagate and preserve <beans> default-* attributes correctly,
    // keep track of the current (parent) delegate, which may be null. Create
    // the new (child) delegate with a reference to the parent for fallback purposes,
    // then ultimately reset this.delegate back to its original (parent) reference.
    // this behavior emulates a stack of delegates without actually necessitating one.
    BeanDefinitionParserDelegate parent = this.delegate;
    this.delegate = createDelegate(getReaderContext(), root, parent);

    if (this.delegate.isDefaultNamespace(root)) {
       String profileSpec = root.getAttribute(PROFILE_ATTRIBUTE);
       if (StringUtils.hasText(profileSpec)) {
          String[] specifiedProfiles = StringUtils.tokenizeToStringArray(
                profileSpec, BeanDefinitionParserDelegate.MULTI_VALUE_ATTRIBUTE_DELIMITERS);
          // We cannot use Profiles.of(...) since profile expressions are not supported
          // in XML config. See SPR-12458 for details.
          if (!getReaderContext().getEnvironment().acceptsProfiles(specifiedProfiles)) {
             if (logger.isDebugEnabled()) {
                logger.debug("Skipped XML bean definition file due to specified profiles [" + profileSpec +
                      "] not matching: " + getReaderContext().getResource());
             }
             return;
          }
       }
    }

    preProcessXml(root);
    //解析bean标签 注册beanDefinitions
    parseBeanDefinitions(root, this.delegate);
    postProcessXml(root);

    this.delegate = parent;
}




	protected void parseBeanDefinitions(Element root, BeanDefinitionParserDelegate delegate) {
        //如果当前标签属于默认命名空间http://www.springframework.org/schema/beans
		if (delegate.isDefaultNamespace(root)) {
			NodeList nl = root.getChildNodes();
			for (int i = 0; i < nl.getLength(); i++) {
				Node node = nl.item(i);
				if (node instanceof Element) {
					Element ele = (Element) node;
					if (delegate.isDefaultNamespace(ele)) {
                        //按照默认命名空间解析元素
						parseDefaultElement(ele, delegate);
					}
					else {
                        //按照自定义命名空间解析元素
						delegate.parseCustomElement(ele);
					}
				}
			}
		}
		else {
			delegate.parseCustomElement(root);
		}
	}



	//解析默认元素
	private void parseDefaultElement(Element ele, BeanDefinitionParserDelegate delegate) {
        //标签是<import>
		if (delegate.nodeNameEquals(ele, IMPORT_ELEMENT)) {
			importBeanDefinitionResource(ele);
		}
        //标签是<alias>
		else if (delegate.nodeNameEquals(ele, ALIAS_ELEMENT)) {
			processAliasRegistration(ele);
		}
        //标签是<bean>
		else if (delegate.nodeNameEquals(ele, BEAN_ELEMENT)) {
			processBeanDefinition(ele, delegate);
		}
        //标签是<beans>
		else if (delegate.nodeNameEquals(ele, NESTED_BEANS_ELEMENT)) {
			// recurse
			doRegisterBeanDefinitions(ele);
		}
	}

	protected void processBeanDefinition(Element ele, BeanDefinitionParserDelegate delegate) {
        //解析设置BeanDefinitionHolder
		BeanDefinitionHolder bdHolder = delegate.parseBeanDefinitionElement(ele);
		if (bdHolder != null) {
			bdHolder = delegate.decorateBeanDefinitionIfRequired(ele, bdHolder);
			try {
                 //注册BeanDefinition
				// Register the final decorated instance.
				BeanDefinitionReaderUtils.registerBeanDefinition(bdHolder, getReaderContext().getRegistry());
			}
			catch (BeanDefinitionStoreException ex) {
				getReaderContext().error("Failed to register bean definition with name '" +
						bdHolder.getBeanName() + "'", ele, ex);
			}
			// Send registration event.
			getReaderContext().fireComponentRegistered(new BeanComponentDefinition(bdHolder));
		}
	}
```

##### parseCustomElement

```java
@Nullable
public BeanDefinition parseCustomElement(Element ele, @Nullable BeanDefinition containingBd) {
    //获取元素标签的命名空间
    String namespaceUri = getNamespaceURI(ele);
    if (namespaceUri == null) {
       return null;
    }
    //根据命名空间寻找加载创建对应的Handler
    NamespaceHandler handler = this.readerContext.getNamespaceHandlerResolver().resolve(namespaceUri);
    if (handler == null) {
       error("Unable to locate Spring NamespaceHandler for XML schema namespace [" + namespaceUri + "]", ele);
       return null;
    }
    //解析
    return handler.parse(ele, new ParserContext(this.readerContext, this, containingBd));
}


@Override
@Nullable
public BeanDefinition parse(Element element, ParserContext parserContext) {
    //根据元素标签名称查找对应的Parser
	BeanDefinitionParser parser = findParserForElement(element, parserContext);
    //使用Parser解析
	return (parser != null ? parser.parse(element, parserContext) : null);
}
```





#### 以注解方式加载beanDefinitions

@todo



### 自定义标签解析

标签解析的流程

- 初始化ResourceEntityResolver，初始化dtd和xsd的解析器，读取META-INF/spring.schemas中的所有xsd路径
- 利用xsd规则文件解析xml文件，封装成Document对象
- 加载META-INF/spring.handlers下的所有handler路径
- 匹配自定义的命名空间，实例化对应命名空间的handler，调用init方法创建Parser
- 根据对应元素标签匹配Parser，利用Parser解析标签的属性

> - 一个命名空间对应一个xsd文件的网络路径
> - 在spring.schemas中，一个xsd文件的网路路径对应一个本地路径
> - 在spring.handlers中，一个命名空间对应一个Handler



#### 自定义xsd格式文件并指定本地路径

**自定义xsd格式文件**

```xsd
<?xml version="1.0" encoding="UTF-8"?>
<schema xmlns="http://www.w3.org/2001/XMLSchema"
       targetNamespace="http://www.mashibing.com/schema/user"
       xmlns:tns="http://www.mashibing.com/schema/user"
       elementFormDefault="qualified">
    <element name="user">
       <complexType>
          <attribute name ="id" type = "string"/>
          <attribute name ="userName" type = "string"/>
          <attribute name ="email" type = "string"/>
          <attribute name ="password" type="string"/>
       </complexType>
    </element>
</schema>
```

**创建META-INF/spring.schemas**

```schemas
http\://www.mashibing.com/schema/user.xsd=META-INF/user.xsd
```



#### 指定处理器和元素解析器

**创建META-INF/spring.handlers**

```handlers
http\://www.mashibing.com/schema/user=com.zcq.demo.test.myXml.UserNamespaceHandler
```

```java
//http://www.mashibing.com/schema/user命名空间对应的处理器
public class UserNamespaceHandler extends NamespaceHandlerSupport {
    @Override
    public void init() {
        //user标签对应的解析器
        registerBeanDefinitionParser("user", new UserBeanDefinitionParser());
    }
}
```

```java

//user标签对应的解析器
public class UserBeanDefinitionParser implements BeanDefinitionParser {

    @Override
    public BeanDefinition parse(Element element, ParserContext parserContext) {
        String id = element.getAttribute("id");
        String name = element.getAttribute("userName");
        String email = element.getAttribute("email");
        String password = element.getAttribute("password");

        AbstractBeanDefinition beanDefinition = BeanDefinitionBuilder.genericBeanDefinition(User.class)
                .addPropertyValue("id", id)
                .addPropertyValue("name", name)
                .addPropertyValue("email", email)
                .addPropertyValue("password", password)
                .getBeanDefinition();

        //注册一个名为user的beanDefinition
        parserContext.getRegistry().registerBeanDefinition("user", beanDefinition);

        return null;
    }
}
```







## prepareBeanFactory()

```java
//配置BeanFactory

protected void prepareBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    // Tell the internal bean factory to use the context's class loader etc.
    beanFactory.setBeanClassLoader(getClassLoader());
    //设置spel表达式解析器
    beanFactory.setBeanExpressionResolver(new StandardBeanExpressionResolver(beanFactory.getBeanClassLoader()));
    //设置属性编辑器注册器
    beanFactory.addPropertyEditorRegistrar(new ResourceEditorRegistrar(this, getEnvironment()));

    // Configure the bean factory with context callbacks.
    //设置自定义Aware的处理器
    beanFactory.addBeanPostProcessor(new ApplicationContextAwareProcessor(this));
    beanFactory.ignoreDependencyInterface(EnvironmentAware.class);
    beanFactory.ignoreDependencyInterface(EmbeddedValueResolverAware.class);
    beanFactory.ignoreDependencyInterface(ResourceLoaderAware.class);
    beanFactory.ignoreDependencyInterface(ApplicationEventPublisherAware.class);
    beanFactory.ignoreDependencyInterface(MessageSourceAware.class);
    beanFactory.ignoreDependencyInterface(ApplicationContextAware.class);

    // BeanFactory interface not registered as resolvable type in a plain factory.
    // MessageSource registered (and found for autowiring) as a bean.
	//指定优先注入指定的对象 类似@Primary的功能
    beanFactory.registerResolvableDependency(BeanFactory.class, beanFactory);
    beanFactory.registerResolvableDependency(ResourceLoader.class, this);
    beanFactory.registerResolvableDependency(ApplicationEventPublisher.class, this);
    beanFactory.registerResolvableDependency(ApplicationContext.class, this);

    // Register early post-processor for detecting inner beans as ApplicationListeners.
    beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(this));

    // Detect a LoadTimeWeaver and prepare for weaving, if found.
    if (beanFactory.containsBean(LOAD_TIME_WEAVER_BEAN_NAME)) {
       beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
       // Set a temporary ClassLoader for type matching.
       beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
    }

    //注册环境变量到beanfactroy中
    // Register default environment beans.
    if (!beanFactory.containsLocalBean(ENVIRONMENT_BEAN_NAME)) {
       beanFactory.registerSingleton(ENVIRONMENT_BEAN_NAME, getEnvironment());
    }
    if (!beanFactory.containsLocalBean(SYSTEM_PROPERTIES_BEAN_NAME)) {
       beanFactory.registerSingleton(SYSTEM_PROPERTIES_BEAN_NAME, getEnvironment().getSystemProperties());
    }
    if (!beanFactory.containsLocalBean(SYSTEM_ENVIRONMENT_BEAN_NAME)) {
       beanFactory.registerSingleton(SYSTEM_ENVIRONMENT_BEAN_NAME, getEnvironment().getSystemEnvironment());
    }
}
```



### StandardBeanExpressionResolver

```java
//Spring EL表达式解析器

public class StandardBeanExpressionResolver implements BeanExpressionResolver {
	/** Default expression prefix: "#{". */
	public static final String DEFAULT_EXPRESSION_PREFIX = "#{";

	/** Default expression suffix: "}". */
	public static final String DEFAULT_EXPRESSION_SUFFIX = "}";
    
    
    public StandardBeanExpressionResolver(@Nullable ClassLoader beanClassLoader) {
        //核心解析器SpelExpressionParser
		this.expressionParser = new SpelExpressionParser(new SpelParserConfiguration(null, beanClassLoader));
	}


}
```

#### Spring EL表达式

> **字面量表达式**    
>
> - **整数**：`#{100}`    
> - **小数**：`#{3.14}`   
> - **字符串**：`#{'Hello, Spring EL'}`
> - **布尔值**：`#{true}` 
>
> **属性访问表达式**   
>
> - **对象属性访问**：`#{user.name}`    
> - **嵌套属性访问**：如果 `User` 类中有一个 `Address` 类型的属性 `address`，且 `Address` 类有 `city` 属性，则 `#{user.address.city}` 
>
> **方法调用表达式** 
>
> 假设 `user` 对象有一个 `getFullName()` 方法。    
>
> - **无参方法调用**：`#{user.getFullName()}`    
> - **有参方法调用**：如果有 `sayHello(String message)` 方法，则 `#{user.sayHello('Hi')}` 
>
> **数组、List、Map 访问表达式**    
>
> - **数组访问**：`#{myArray[0]}`，假设 `myArray` 是一个数组。    
> - **List 访问**：`#{myList[2]}`，假设 `myList` 是一个 `List`。    
> - **Map 访问**：`#{myMap['key']}`，假设 `myMap` 是一个 `Map`。
>
> **关系表达式**    
>
> - **比较**：`#{user.age > 18}`    
>
> - **相等判断**：`#{user.name == 'John'}` 
>
> **逻辑表达式**    
>
> - **与**：`#{user.age > 18 && user.isActive}`   
> - **或**：`#{user.age > 18 || user.isSpecial}` 
>
> **三元运算符表达式** 
>
> `#{user.age > 18? 'Adult' : 'Minor'}` 
>
> **定义变量**
>
> 如 `<bean id="user" class="com.example.User">    <property name="name" value="#{T(java.lang.System).getProperty('user.name')}"/> </bean>` 
>
> 这里通过 `T(java.lang.System).getProperty('user.name')` 获取系统属性 `user.name` 作为变量。 
>
> **使用变量** 
>
> 假设在 EL 上下文中定义了变量 `myVar`，可在表达式中使用 `#{myVar}` 。 

#### @Value中${}和#{}的区别

```java
@Component
public class ValueTest {
    
    //以#{}的是Spring EL表达式
    
    @Value("#{systemProperties['os.name']}")
    private String name;
    
    @Value("#{T(java.lang.Math).random() * 100.0}")
    private double randomNumber;
    
    @Value("#{new java.lang.String('Hello World').toUpperCase()}")
    private String message;
    
    @Value("#{new java.util.Date()}")
    private String date;
    
    @Value("#{new com.zcq.demo.test.myXml.User('1','zcq','zcq@163.com','123456')}")
    private User user;
    
    //以${}的是属性占位符，会被PropertyPlaceholderHelper解析替换成Environment中的属性
    //而且以.yml和.properties的配置文件工作原理就是所有配置属性被加载到Environment 然后再被${}替换成配置属性
    
    @Value("${user.name}")
    private String userName;
    
}
```



### 自定义属性编辑器

- 创建特定属性编辑器
- 创建属性编辑器的注册器，指定特殊属性的编辑采用特定属性编辑器进行转换
- 将属性编辑器的注册器注册到beanFactory中

```java
//创建特定属性编辑器
public class MyPropertyEditor extends PropertyEditorSupport {

    @Override
    public void setAsText(String text) throws IllegalArgumentException {
        //针对MyProperty类型的属性进行编辑
        MyProperty myProperty = new MyProperty();
        String[] split = text.split("_");
        myProperty.setName(split[0]);
        myProperty.setAge(split[1]);
        setValue(myProperty);
    }
}
```

```java

//创建属性编辑器的注册器，指定特殊属性的编辑采用特定属性编辑器
public class MyPropertyEditorRegistrar implements PropertyEditorRegistrar {
    @Override
    public void registerCustomEditors(PropertyEditorRegistry registry) {
        //在处理MyProperty类型的属性值时，使用MyPropertyEditor编辑器进行转换
        registry.registerCustomEditor(MyProperty.class, new MyPropertyEditor());
    }
}
```

```java
//将属性编辑器的注册器注册到beanFactory中
public class MyApplicationContext extends ClassPathXmlApplicationContext {

    public MyApplicationContext(String configLocations) {
        super(configLocations);
    }

    @Override
    protected void customizeBeanFactory(DefaultListableBeanFactory beanFactory) {
        //注册MyPropertyEditorRegistrar到beanFactroy中
        beanFactory.addPropertyEditorRegistrar(new MyPropertyEditorRegistrar());
        super.customizeBeanFactory(beanFactory);
    }
}
```



### 自定义Aware

仿照ApplicationContextAwareProcessor，可以在bean实例化以后对自定义的Aware属性进行注入工作

```java
class ApplicationContextAwareProcessor implements BeanPostProcessor {
    
	@Override
	@Nullable
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		if (!(bean instanceof EnvironmentAware || bean instanceof EmbeddedValueResolverAware ||
				bean instanceof ResourceLoaderAware || bean instanceof ApplicationEventPublisherAware ||
				bean instanceof MessageSourceAware || bean instanceof ApplicationContextAware)){
			return bean;
		}

		AccessControlContext acc = null;

		if (System.getSecurityManager() != null) {
			acc = this.applicationContext.getBeanFactory().getAccessControlContext();
		}

		if (acc != null) {
			AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
				invokeAwareInterfaces(bean);
				return null;
			}, acc);
		}
		else {
			invokeAwareInterfaces(bean);
		}

		return bean;
	}
}
```

**自定义Aware**

```java
public interface MyAware extends Aware {
    void setMyAware(Object object);
}
```

**自定义BeanPostProcesssor**

```java
public class MyAwareBeanPostProcessor implements BeanPostProcessor {
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        //如果bean实现了对应的Aware接口 那么就可以对属性进行设置
        if (bean instanceof MyAware){
            ((MyAware) bean).setMyAware(new Object());
        }
        return bean;
    }
}
```



## invokeBeanFactoryPostProcessors()



### BeanNameGenerator

### BeanDefinition

```
GenericBeanDefinition ScannedGenericBeanDefinition AnnotatedGenericBeanDefinition
```







## 配置文件加载替换流程解析

在spring中，配置文件加载替换流程如下

- 负责文件加载的类 **PathMatchingResourcePatternResolver**
- 读取文件，将文件中的所有属性配置加入 **Environment**
- 负责属性占位符`${}`替换的类 **PropertyPlaceholderHelper ** 将`${server.port}` 中的属性值替换成 **Environment**中记录的value

### 文件加载的原理

在spring中，ResourceLoader接口负责资源的加载获取功能

```java
public interface ResourceLoader {
    
    String CLASSPATH_URL_PREFIX = ResourceUtils.CLASSPATH_URL_PREFIX;
    
	Resource getResource(String location);
    
    @Nullable
	ClassLoader getClassLoader();
}
```

ApplicationContext本身也实现了此接口，但实际工作的是内部的PathMatchingResourcePatternResolver

```java
public abstract class AbstractApplicationContext extends DefaultResourceLoader
       implements ConfigurableApplicationContext {
    
    //资源路径解析器
    private ResourcePatternResolver resourcePatternResolver;

    //默认构造方法
    public AbstractApplicationContext() {
		this.resourcePatternResolver = getResourcePatternResolver();
	}
    
    protected ResourcePatternResolver getResourcePatternResolver() {
		return new PathMatchingResourcePatternResolver(this);
	}

    
    //加载资源的方法 实际工作的是内部的PathMatchingResourcePatternResolver
	@Override
	public Resource[] getResources(String locationPattern) throws IOException {
		return this.resourcePatternResolver.getResources(locationPattern);
	}

    
    //获取环境
    @Override
	public ConfigurableEnvironment getEnvironment() {
		if (this.environment == null) {
			this.environment = createEnvironment();
		}
		return this.environment;
	}
    
    protected ConfigurableEnvironment createEnvironment() {
		return new StandardEnvironment();
	}

}
```



#### 类加载器加载文件原理

类加载器加载文件采用**双亲委派原理**，逐级向上委派给父类加载器加载文件，每个类加载器都负责不同的加载范围

```java
package java.lang;

public abstract class ClassLoader {
    
    public Enumeration<URL> getResources(String name) throws IOException {
        @SuppressWarnings("unchecked")
        Enumeration<URL>[] tmp = (Enumeration<URL>[]) new Enumeration<?>[2];
        //委派给父类进行加载资源
        if (parent != null) {
            tmp[0] = parent.getResources(name);
        } else {
            //使用启动类加载器加载
            tmp[0] = getBootstrapResources(name);
        }
        //根据自己的负责范围加载资源
        tmp[1] = findResources(name);

        return new CompoundEnumeration<>(tmp);
    }
}
```

类加载器加载**类和资源**的负责范围如下

- 启动类加载器（Bootstrap ClassLoader）

  **启动类加载器**负责**加载 Java 的核心类库**，通常是 JDK 安装目录下的 `jre/lib` 目录，启动类加载器一般不会直接被 Java 代码获取到，因为在 Java 代码中调用 `getClassLoader()` 对于核心类库的类会返回 `null`

```java
	//负责范围是jdk下的核心类和资源的加载
	//因为获取不到启动类加载器 所以会产生null异常
		Enumeration<URL> resources3 = Test.class.getClassLoader().getParent().getParent().getResources("a.yml");
        while (resources3.hasMoreElements()) {
            URL url = resources3.nextElement();
            File file = new File(url.getFile());
            File absoluteFile = file.getAbsoluteFile();
            System.out.println(absoluteFile);
        }
```

- 扩展类加载器（Extension ClassLoader）

  扩展类加载器负责加载 **JDK 扩展目录**下的类和资源，通常是 `jre/lib/ext` 目录。如果 `Test` 类是由扩展类加载器加载的，那么 `getResources("")` 查找的根路径就是 `jre/lib/ext` 目录及其子目录。

```java
//查找路径就是 jdk扩展目录jre/lib/ext 下去找相对于当前目录下的a.yml文件
//先委托给父类启动类加载器找a.yml 再自己找
Enumeration<URL> resources5 = Test.class.getClassLoader().getParent().getResources("a.yml");
while (resources5.hasMoreElements()) {
    URL url = resources5.nextElement();
    //转换成File对象
    File file = new File(url.getFile());
    File absoluteFile = file.getAbsoluteFile();
    System.out.println(absoluteFile);
}
```

- 应用程序类加载器（Application ClassLoader）

  应用程序类加载器负责**加载用户类路径（`classpath`）上的类和资源**。

  classpath 包括**当前项目的类和资源路径** + **依赖Jar包的类和资源路径**

在不同的开发环境下，根路径有所不同：

- 开发环境下(IDE)：当前项目的类和资源路径 （`target/classes`）+ 依赖Jar包的类和资源路径 （`D:\softwares\LocalRepository\kkk.jar/`）

  这里因为在开发环境中，并没有实际将依赖Jar包和当前项目文件打包，类加载器还能找到对应的依赖是因为IDEA在启动项目时在命令行加上了参数

  ```shell
  java -Dfile.encoding=UTF-8 -classpath D:\softwares\LocalRepository\kkk.jar;D:\softwares\LocalRepository\fff.jar;
  ```

- 打成Jar包下：当前项目的类和资源路径 (`xxx.jar/BOOT-INF/classes/`) + 依赖Jar包的类和资源路径 （`xxx.jar/BOOT-INF/lib/kkkk.jar/`）

```java
//查找路径就是所有的classpath路径下 找相对于每一条classpath下的a.yml文件
//先去委托给父类扩展类加载器再找a.yml + 先委托给启动类加载器去找 + 再自己找
Enumeration<URL> resources = Test.class.getClassLoader().getResources("com");
while (resources.hasMoreElements()) {
    URL url = resources.nextElement();
    //转换成File对象
    File file = new File(url.getFile());
    File absoluteFile = file.getAbsoluteFile();
    System.out.println(absoluteFile);
}


//结果如下 ：
//在扩展类加载器找到的
D:\doc\my\studymd\LearningNotes\file:\C:\Users\Administrator\.jdks\corretto-1.8.0_412\jre\lib\ext\jfxrt.jar!\com
//在应用类加载器找到的
//当前项目的类和资源路径下的com文件
D:\doc\my\studymd\LearningNotes\framework\spring\springstudycode\target\classes\com
//假设当前依赖两个jar包，那么每个jar包的路径都可以看作一个独立的查找路径，在每个jar包下查找对应的文件
//依赖Jar包的类和资源路径的com文件
D:\doc\my\studymd\LearningNotes\file:\D:\softwares\LocalRepository\com\fasterxml\jackson\core\jackson-databind\2.11.0\jackson-databind-2.11.0.jar!\com
//依赖Jar包的类和资源路径的com文件
D:\doc\my\studymd\LearningNotes\file:\D:\softwares\LocalRepository\com\fasterxml\jackson\core\jackson-annotations\2.11.0\jackson-annotations-2.11.0.jar!\com
```

当前项目的类和资源路径 （`target/classes`）的目录结构 ： **顶级类包** + **开发时resources目录下的所有文件**

<img src=".\images\image-20250227140553290.png" alt="image-20250227140553290" style="zoom:50%;" />

依赖Jar包的类和资源路径 （`D:\softwares\LocalRepository\kkk.jar/`）的目录结构 ：和上面基本一致  **顶级类包** + **所有文件资源文件**

<img src=".\images\image-20250227140835971.png" alt="image-20250227140835971" style="zoom:50%;" />

#### PathMatchingResourcePatternResolver的工作原理

- 以classpath*:开头的文件路径
  1. Ant风格的文件路径`META-INFO/*.xml`，使用类加载器获取指定前缀文件`META-INFO`下的资源，遍历指定文件下的所有资源，使用AntPathMatcher匹配Ant风格路径，匹配上的资源才加载
  2. 以绝对路径`META-INFO/test.xml`的文件路径，直接使用类加载器进行加载指定路径下的文件
- 不以classpath*:开头的文件路径
  1. Ant风格的文件路径`META-INFO/*.xml`，使用类加载器获取指定前缀文件`META-INFO`下的资源，遍历指定文件下的所有资源，使用AntPathMatcher匹配Ant风格路径，匹配上的资源才加载
  2. 以绝对路径`META-INFO/test.xml`的文件路径，直接使用类加载器进行加载指定路径下的单个文件

```java
public class PathMatchingResourcePatternResolver implements ResourcePatternResolver {
    
    //资源加载器 这里使用的是ApplicationContext作为资源加载器
	private final ResourceLoader resourceLoader;

    //ant风格的路径匹配器
	private PathMatcher pathMatcher = new AntPathMatcher();
    
    String CLASSPATH_ALL_URL_PREFIX = "classpath*:";
    

    @Override
	public Resource[] getResources(String locationPattern) throws IOException {
		Assert.notNull(locationPattern, "Location pattern must not be null");
        //如果文件路径是以classpath*:开头的
		if (locationPattern.startsWith(CLASSPATH_ALL_URL_PREFIX)) {
			// a class path resource (multiple resources for same name possible)
            //去掉classpath*:前缀，如果剩下的路径是以Ant风格的 如 com/*.xml等
			if (getPathMatcher().isPattern(locationPattern.substring(CLASSPATH_ALL_URL_PREFIX.length()))) {
				// a class path resource pattern
                 //使用Ant风格解析匹配文件
				return findPathMatchingResources(locationPattern);
			}
			else {
                  //不是Ant风格的路径 是一个普通的绝对路径/相对路径 如 xxx.yml 或 /META-INFO/xxx.xml
				// all class path resources with the given name
                 // 使用类加载器加载绝对路径/相对路径的文件
				return findAllClassPathResources(locationPattern.substring(CLASSPATH_ALL_URL_PREFIX.length()));
			}
		}
		else {
			// Generally only look for a pattern after a prefix here,
			// and on Tomcat only after the "*/" separator for its "war:" protocol.
			int prefixEnd = (locationPattern.startsWith("war:") ? locationPattern.indexOf("*/") + 1 :
					locationPattern.indexOf(':') + 1);
			if (getPathMatcher().isPattern(locationPattern.substring(prefixEnd))) {
				// a file pattern
				return findPathMatchingResources(locationPattern);
			}
			else {
				// a single resource with the given name
				return new Resource[] {getResourceLoader().getResource(locationPattern)};
			}
		}
	}

}
```









### 读取配置和占位符替换原理

即@Value的工作原理













## PropertySourcesPlaceholderConfigurer

继承BeanFactoryPostProcessor 是

```xml
 <context:property-placeholder location="classpath:dbconfig.properties"></context:property-placeholder>
```

这个标签的解析结果 用于在BFPP环节处理beanDefinition中的${}占位符替换工作

### 





## BeanFactoryPostProcessor和BeanDefinitionRegistyPostProcessor



## AnnotationConfigUtils

```java

public abstract class AnnotationConfigUtils {

	/**
	 * The bean name of the internally managed Configuration annotation processor.
	 */
	public static final String CONFIGURATION_ANNOTATION_PROCESSOR_BEAN_NAME =
			"org.springframework.context.annotation.internalConfigurationAnnotationProcessor";

	/**
	 * The bean name of the internally managed BeanNameGenerator for use when processing
	 * {@link Configuration} classes. Set by {@link AnnotationConfigApplicationContext}
	 * and {@code AnnotationConfigWebApplicationContext} during bootstrap in order to make
	 * any custom name generation strategy available to the underlying
	 * {@link ConfigurationClassPostProcessor}.
	 * @since 3.1.1
	 */
	public static final String CONFIGURATION_BEAN_NAME_GENERATOR =
			"org.springframework.context.annotation.internalConfigurationBeanNameGenerator";

	/**
	 * The bean name of the internally managed Autowired annotation processor.
	 */
	public static final String AUTOWIRED_ANNOTATION_PROCESSOR_BEAN_NAME =
			"org.springframework.context.annotation.internalAutowiredAnnotationProcessor";

	/**
	 * The bean name of the internally managed Required annotation processor.
	 * @deprecated as of 5.1, since no Required processor is registered by default anymore
	 */
	@Deprecated
	public static final String REQUIRED_ANNOTATION_PROCESSOR_BEAN_NAME =
			"org.springframework.context.annotation.internalRequiredAnnotationProcessor";

	/**
	 * The bean name of the internally managed JSR-250 annotation processor.
	 */
	public static final String COMMON_ANNOTATION_PROCESSOR_BEAN_NAME =
			"org.springframework.context.annotation.internalCommonAnnotationProcessor";

	/**
	 * The bean name of the internally managed JPA annotation processor.
	 */
	public static final String PERSISTENCE_ANNOTATION_PROCESSOR_BEAN_NAME =
			"org.springframework.context.annotation.internalPersistenceAnnotationProcessor";

	private static final String PERSISTENCE_ANNOTATION_PROCESSOR_CLASS_NAME =
			"org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor";

	/**
	 * The bean name of the internally managed @EventListener annotation processor.
	 */
	public static final String EVENT_LISTENER_PROCESSOR_BEAN_NAME =
			"org.springframework.context.event.internalEventListenerProcessor";

	/**
	 * The bean name of the internally managed EventListenerFactory.
	 */
	public static final String EVENT_LISTENER_FACTORY_BEAN_NAME =
			"org.springframework.context.event.internalEventListenerFactory";

    //加入<context:component-scan base-package="com.example.demo.factoryBean"/> 标签后注册的内部类
	public static Set<BeanDefinitionHolder> registerAnnotationConfigProcessors(
			BeanDefinitionRegistry registry, @Nullable Object source) {

		// 获取beanFactory
		DefaultListableBeanFactory beanFactory = unwrapDefaultListableBeanFactory(registry);
		if (beanFactory != null) {
			if (!(beanFactory.getDependencyComparator() instanceof AnnotationAwareOrderComparator)) {
				// //设置依赖比较器
				beanFactory.setDependencyComparator(AnnotationAwareOrderComparator.INSTANCE);
			}
			if (!(beanFactory.getAutowireCandidateResolver() instanceof ContextAnnotationAutowireCandidateResolver)) {
				// //设置自动装配解析器
				beanFactory.setAutowireCandidateResolver(new ContextAnnotationAutowireCandidateResolver());
			}
		}

		// 创建BeanDefinitionHolder集合
		Set<BeanDefinitionHolder> beanDefs = new LinkedHashSet<>(8);

		// 注册内部管理的用于处理@configuration注解的后置处理器的bean
		if (!registry.containsBeanDefinition(CONFIGURATION_ANNOTATION_PROCESSOR_BEAN_NAME)) {
			RootBeanDefinition def = new RootBeanDefinition(ConfigurationClassPostProcessor.class);
			def.setSource(source);
			// 注册BeanDefinition到注册表中
			beanDefs.add(registerPostProcessor(registry, def, CONFIGURATION_ANNOTATION_PROCESSOR_BEAN_NAME));
		}

		// 注册内部管理的用于处理@Autowired，@Value,@Inject以及@Lookup注解的后置处理器bean
		if (!registry.containsBeanDefinition(AUTOWIRED_ANNOTATION_PROCESSOR_BEAN_NAME)) {
			RootBeanDefinition def = new RootBeanDefinition(AutowiredAnnotationBeanPostProcessor.class);
			def.setSource(source);
			beanDefs.add(registerPostProcessor(registry, def, AUTOWIRED_ANNOTATION_PROCESSOR_BEAN_NAME));
		}

		// Check for JSR-250 support, and if present add the CommonAnnotationBeanPostProcessor.
		// 注册内部管理的用于处理JSR-250注解，例如@Resource,@PostConstruct,@PreDestroy的后置处理器bean
		if (jsr250Present && !registry.containsBeanDefinition(COMMON_ANNOTATION_PROCESSOR_BEAN_NAME)) {
			RootBeanDefinition def = new RootBeanDefinition(CommonAnnotationBeanPostProcessor.class);
			def.setSource(source);
			beanDefs.add(registerPostProcessor(registry, def, COMMON_ANNOTATION_PROCESSOR_BEAN_NAME));
		}

		// Check for JPA support, and if present add the PersistenceAnnotationBeanPostProcessor.
		// 注册内部管理的用于处理JPA注解的后置处理器bean
		if (jpaPresent && !registry.containsBeanDefinition(PERSISTENCE_ANNOTATION_PROCESSOR_BEAN_NAME)) {
			RootBeanDefinition def = new RootBeanDefinition();
			try {
				def.setBeanClass(ClassUtils.forName(PERSISTENCE_ANNOTATION_PROCESSOR_CLASS_NAME,
						AnnotationConfigUtils.class.getClassLoader()));
			}
			catch (ClassNotFoundException ex) {
				throw new IllegalStateException(
						"Cannot load optional framework class: " + PERSISTENCE_ANNOTATION_PROCESSOR_CLASS_NAME, ex);
			}
			def.setSource(source);
			beanDefs.add(registerPostProcessor(registry, def, PERSISTENCE_ANNOTATION_PROCESSOR_BEAN_NAME));
		}

		// 注册内部管理的用于处理@EventListener注解的后置处理器的bean
		if (!registry.containsBeanDefinition(EVENT_LISTENER_PROCESSOR_BEAN_NAME)) {
			RootBeanDefinition def = new RootBeanDefinition(EventListenerMethodProcessor.class);
			def.setSource(source);
			beanDefs.add(registerPostProcessor(registry, def, EVENT_LISTENER_PROCESSOR_BEAN_NAME));
		}

		// 注册内部管理用于生产ApplicationListener对象的EventListenerFactory对象
		if (!registry.containsBeanDefinition(EVENT_LISTENER_FACTORY_BEAN_NAME)) {
			RootBeanDefinition def = new RootBeanDefinition(DefaultEventListenerFactory.class);
			def.setSource(source);
			beanDefs.add(registerPostProcessor(registry, def, EVENT_LISTENER_FACTORY_BEAN_NAME));
		}

		return beanDefs;
	}
}

```



## ConfigrationClassPostProcessor





## 事件广播机制





## ConversionService

## BeanNameGenerator



## Bean的创建流程

getBean->doGetBean->getSingleton->createBean->doCreateBean

先大概阐述每个方法里面做的事，然后再做细化







## lookup和replace

如果你有一个抽象类，那么也可以把它加进入spring容器中 但是spring并不会为它创建实例bean对象 因为抽象类不能实例化 只是会生成一个BeanDefinition

```java
public abstract class Fruit {
    public Fruit getFruit(){
        System.out.println("获得水果");
        return this;
    }
}
```

```xml
<bean id="fruit" class="com.example.demo.lookup.Fruit" abstract="true" ></bean>
```

如上所示 必须要标注为abstract="true"为抽象 因为spring会基于这个状态过滤 否则就会抛出不能创建抽象类的异常

```java
// Trigger initialization of all non-lazy singleton beans...
		for (String beanName : beanNames) {
			RootBeanDefinition bd = getMergedLocalBeanDefinition(beanName);
			if (!bd.isAbstract() && bd.isSingleton() && !bd.isLazyInit()) {
				if (isFactoryBean(beanName)) {
					Object bean = getBean(FACTORY_BEAN_PREFIX + beanName);
					if (bean instanceof FactoryBean) {
						final FactoryBean<?> factory = (FactoryBean<?>) bean;
						boolean isEagerInit;
						if (System.getSecurityManager() != null && factory instanceof SmartFactoryBean) {
							isEagerInit = AccessController.doPrivileged((PrivilegedAction<Boolean>)
											((SmartFactoryBean<?>) factory)::isEagerInit,
									getAccessControlContext());
						}
						else {
							isEagerInit = (factory instanceof SmartFactoryBean &&
									((SmartFactoryBean<?>) factory).isEagerInit());
						}
						if (isEagerInit) {
							getBean(beanName);
						}
					}
				}
				else {
					getBean(beanName);
				}
			}
		}
```

```java
Exception in thread "main" org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'fruit' defined in class path resource [applicationContext.xml]: Instantiation of bean failed; nested exception is org.springframework.beans.BeanInstantiationException: Failed to instantiate [com.example.demo.lookup.Fruit]: Is it an abstract class?; 
```

但是有lookup-method和replaced-method两种情况除外 可以不用标注abstract="true" 因为在处理这两种情况时 会在实例化时产生Cglib的代理对象 

```java
@Override
	public Object instantiate(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner) {
		// Don't override the class with CGLIB if no overrides.
        //如果没有要进行方法覆盖 即 lookup-method和replaced-method的情况
		if (!bd.hasMethodOverrides()) {
			Constructor<?> constructorToUse;
			synchronized (bd.constructorArgumentLock) {
				constructorToUse = (Constructor<?>) bd.resolvedConstructorOrFactoryMethod;
				if (constructorToUse == null) {
					final Class<?> clazz = bd.getBeanClass();
					if (clazz.isInterface()) {
						throw new BeanInstantiationException(clazz, "Specified class is an interface");
					}
					try {
						if (System.getSecurityManager() != null) {
							constructorToUse = AccessController.doPrivileged(
									(PrivilegedExceptionAction<Constructor<?>>) clazz::getDeclaredConstructor);
						}
						else {
							constructorToUse = clazz.getDeclaredConstructor();
						}
						bd.resolvedConstructorOrFactoryMethod = constructorToUse;
					}
					catch (Throwable ex) {
						throw new BeanInstantiationException(clazz, "No default constructor found", ex);
					}
				}
			}
            //直接通过反射构造函数创建一个新对象返回
			return BeanUtils.instantiateClass(constructorToUse);
		}
		else {
            //有lookup-method和replaced-method的情况 就需要代理处理
			// Must generate CGLIB subclass.
			return instantiateWithMethodInjection(bd, beanName, owner);
		}
	}
		
		//代理处理的实例化方法
		public Object instantiate(@Nullable Constructor<?> ctor, Object... args) {
            //这里就已经产生了当前类的子类
			Class<?> subclass = createEnhancedSubclass(this.beanDefinition);
			Object instance;
			if (ctor == null) {
                //一般是进到这里
				instance = BeanUtils.instantiateClass(subclass);
			}
			else {
				try {
					Constructor<?> enhancedSubclassConstructor = subclass.getConstructor(ctor.getParameterTypes());
					instance = enhancedSubclassConstructor.newInstance(args);
				}
				catch (Exception ex) {
					throw new BeanInstantiationException(this.beanDefinition.getBeanClass(),
							"Failed to invoke constructor for CGLIB enhanced subclass [" + subclass.getName() + "]", ex);
				}
			}
			// SPR-10785: set callbacks directly on the instance instead of in the
			// enhanced class (via the Enhancer) in order to avoid memory leaks.
            //最后对回调做设置 返回生成的子类代理
			Factory factory = (Factory) instance;
			factory.setCallbacks(new Callback[] {NoOp.INSTANCE,
					new LookupOverrideMethodInterceptor(this.beanDefinition, this.owner),
					new ReplaceOverrideMethodInterceptor(this.beanDefinition, this.owner)});
			return instance;
		}
```

常规的@Autowired依赖注入的方式 因为整个对象是单例只创建一次 所以内部的多例对象属性也只会被赋值一次 后续走的是一级缓存 所以每次获取的多例对象是同一个。

### lookup-method

lookup更多用于单例对象引用多例对象时 每次调用方法都可以生成一个新的spring管理的对象

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns:context="http://www.springframework.org/schema/context"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns="http://www.springframework.org/schema/beans"
       xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd
        http://www.springframework.org/schema/context  http://www.springframework.org/schema/context/spring-context.xsd
        ">
    <context:component-scan base-package="com.example.demo"/>
    <bean id="fruit" class="com.example.demo.lookup.Fruit">
        <!--这里就代表以名称为apple的bean对象替换getFruit的返回值-->
        <lookup-method name="getFruit" bean="apple"></lookup-method>
    </bean>
    <bean id="apple" class="com.example.demo.lookup.Apple" scope="prototype"></bean>
</beans>
```

```java
public abstract class Fruit {
    //@Lookup(value = "apple") 也可以采用这样的注解方式 
    //标注该方法返回beanName为apple的多例对象
    public Object getFruit(){
        return null;
    }
}

public class Apple {
   
}

public class SpringTestDemo {
    public static void main(String[] args) {
        System.getProperties().setProperty("zcq", "applicationContext");
        //读取刚才的applicationContext.xml的配置文件
        MyApplicationContext applicationContext = new MyApplicationContext("${zcq}.xml");
        //虽然是抽象类 这里获取到的是单例的代理对象
        Fruit bean = (Fruit) applicationContext.getBean("fruit");
        //这里的getFruit就会通过spring创建不同的apple对象返回
        Fruit fruit = bean.getFruit();
        Fruit fruit2 = bean.getFruit();
        System.out.println(fruit);//com.example.demo.lookup.Apple@30af7377
        System.out.println(fruit2);//com.example.demo.lookup.Apple@67a056f1
    }
}
```

原理讲解 由于代理过程还不熟悉 所以更细致的源码todo 这里是调用了Fruit fruit = bean.getFruit();以后debug进来的方法

```java
		//可以看到并没有进入我们父类Fruit的getFruit()方法 而是进入了代理对象的拦截
		@Override
		public Object intercept(Object obj, Method method, Object[] args, MethodProxy mp) throws Throwable {
			// Cast is safe, as CallbackFilter filters are used selectively.
			LookupOverride lo = (LookupOverride) getBeanDefinition().getMethodOverrides().getOverride(method);
			Assert.state(lo != null, "LookupOverride not found");
			Object[] argsToUse = (args.length > 0 ? args : null);  // if no-arg, don't insist on args at all
			//这里做两种判断
             //如果有beanName 那么就按照beanName去spring取
            //<lookup-method name="getFruit" bean="apple"></lookup-method> 就是这里指定的bean
             if (StringUtils.hasText(lo.getBeanName())) {
                 //看到getBean就很熟悉了 单例从缓存取 多例立刻创建
				return (argsToUse != null ? this.owner.getBean(lo.getBeanName(), argsToUse) :
						this.owner.getBean(lo.getBeanName()));
			}
            //如果没有指定beanName 那么就按照父类Fruit的getFruit()方法的返回值类型去spring中取
			else {
				return (argsToUse != null ? this.owner.getBean(method.getReturnType(), argsToUse) :
						this.owner.getBean(method.getReturnType()));
			}
		}
	}
```

### replace-method

replace-method用于指定替换某个方法 

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns:context="http://www.springframework.org/schema/context"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns="http://www.springframework.org/schema/beans"
       xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd
        http://www.springframework.org/schema/context  http://www.springframework.org/schema/context/spring-context.xsd
        ">
    <context:component-scan base-package="com.example.demo"/>

    <bean id="fruit" class="com.example.demo.lookup.Fruit">
        <!--这里的意思是 利用apple这个bean作为替换者 替换getFruit方法--> 
        <!--replaced-method我没有找到注解的实现方法 目前只有xml 不过肯定也可以通过修改BeanDefinition来完成-->
        <replaced-method name="getFruit" replacer="apple"/>
    </bean>
    <bean id="apple" class="com.example.demo.lookup.Apple" scope="prototype"></bean>
</beans>
```

```java
public abstract class Fruit {
    public Fruit getFruit(){
        return null;
    }
}
public class Apple implements MethodReplacer {

    @Resource
    ApplicationContext applicationContext;

    @Override
    public Object reimplement(Object obj, Method method, Object[] args) throws Throwable {
        System.out.println("替换");
        return applicationContext.getBean("apple");
    }
}

public class SpringTestDemo {
    public static void main(String[] args) {
        System.getProperties().setProperty("zcq", "applicationContext");
        MyApplicationContext applicationContext = new MyApplicationContext("${zcq}.xml");
        Fruit bean = (Fruit) applicationContext.getBean("fruit");
        //当getFruit的时候实际上也是进入了代理对象 调用被替换的方法
        Fruit fruit = bean.getFruit();
        Fruit fruit2 = bean.getFruit();
        System.out.println(fruit);
        System.out.println(fruit2);
        //替换
        //替换
        //com.example.demo.lookup.Apple@6df7988f
        //com.example.demo.lookup.Apple@27b22f74
    }
}
```

可以看到replace-method也可以实现单例对于多例对象的引用 只不过要借助bean容器进行重新创建，**replace-method更多的被用于替换方法，lookup-method更多被用于替换返回对象**。

```java
		//当getFruit的时候实际上也是进入了代理对象 调用被替换的方法
		@Override
		public Object intercept(Object obj, Method method, Object[] args, MethodProxy mp) throws Throwable {
			ReplaceOverride ro = (ReplaceOverride) getBeanDefinition().getMethodOverrides().getOverride(method);
			Assert.state(ro != null, "ReplaceOverride not found");
			// TODO could cache if a singleton for minor performance optimization
			//这里通过容器找到我们指定的那个bean 也就是前面提到的替换者
             MethodReplacer mr = this.owner.getBean(ro.getMethodReplacerBeanName(), MethodReplacer.class);
			//然后调用实现的替换方法 也就完成了对getFruit的拦截替换
             return mr.reimplement(obj, method, args);
		}
```



## 创建Bean的几种方式

![image-20231211090141402](.\images\image-20231211090141402.png)

### 通过FactoryBean创建对象

```java
//例子

@Component
public class MyFactoryBean implements FactoryBean<String> {

    //返回创建的对象
    @Override
    public String getObject() throws Exception {
        return new String("xxxxxxxxx");
    }

    //返回创建对象的类型
    @Override
    public Class<?> getObjectType() {
        return String.class;
    }
    
	//是否是单例
    @Override
    public boolean isSingleton() {
        return false;
    }
}
-------------------------------------
//测试
public class Test {
    public static void main(String[] args) {
        ApplicationContext applicationContext = new ClassPathXmlApplicationContext("applicationContext4.xml");
        //直接通过beanName获得的是实际创建的对象
        Object myFactoryBean1 = applicationContext.getBean("myFactoryBean");
        //带上取地址符号的beanName取到的是myFactoryBean这个对象
        Object myFactoryBean2 = applicationContext.getBean("&myFactoryBean");
        //通过类型也是直接取到myFactoryBean这个对象
        Object myFactoryBean3 = applicationContext.getBean(MyFactoryBean.class);
        //通过类型可以取到实际创建的对象
        Object myFactoryBean4 = applicationContext.getBean(String.class);

        System.out.println(myFactoryBean1);
        System.out.println(myFactoryBean2);
        System.out.println(myFactoryBean3);
        System.out.println(myFactoryBean4);
    }
}
----------------------------------------------------------------------
xxxxxxxxx
com.example.demo.factoryBean.MyFactoryBean@76b07f29
com.example.demo.factoryBean.MyFactoryBean@76b07f29
xxxxxxxxx
```

**原理**

缓存由factoryBean创建的对象是在factoryBeanObjectCache 而不是一级缓存singletonObjects中

所以singletonObjects和factoryBeanObjectCache 中其实存在两个beanName相同的对象 一个是beanFactory 一个是 创建的对象

通过&来决定取哪个

```java
//首先 factoryBean的创建的对象并不会在容器启动时像其他bean一样创建和管理 而factoryBean会被容器进行管理
//启动过程的逻辑 refresh->finishBeanFactoryInitialization->preInstantiateSingletons
//处理片段
	@Override
	public void preInstantiateSingletons() throws BeansException {
		if (logger.isTraceEnabled()) {
			logger.trace("Pre-instantiating singletons in " + this);
		}

		// Iterate over a copy to allow for init methods which in turn register new bean definitions.
		// While this may not be part of the regular factory bootstrap, it does otherwise work fine.
		List<String> beanNames = new ArrayList<>(this.beanDefinitionNames);

		// Trigger initialization of all non-lazy singleton beans...
		for (String beanName : beanNames) {
			RootBeanDefinition bd = getMergedLocalBeanDefinition(beanName);
			if (!bd.isAbstract() && bd.isSingleton() && !bd.isLazyInit()) {
                //如果这个对象是factoryBean
				if (isFactoryBean(beanName)) {
                      //带着FACTORY_BEAN_PREFIX也就是&取地址进行创建 那么实际创建的是factoryBean的对象
					Object bean = getBean(FACTORY_BEAN_PREFIX + beanName);
					if (bean instanceof FactoryBean) {
						final FactoryBean<?> factory = (FactoryBean<?>) bean;
						boolean isEagerInit;
						if (System.getSecurityManager() != null && factory instanceof SmartFactoryBean) {
							isEagerInit = AccessController.doPrivileged((PrivilegedAction<Boolean>)
											((SmartFactoryBean<?>) factory)::isEagerInit,
									getAccessControlContext());
						}
						else {
                            //如果继承了SmartFactoryBean这个接口 设置了EagerInit 那么实际的对象就会在容器启动期间进行实例化
							isEagerInit = (factory instanceof SmartFactoryBean &&
									((SmartFactoryBean<?>) factory).isEagerInit());
						}
						if (isEagerInit) {
							getBean(beanName);
						}
					}
				}
				else {
					getBean(beanName);
				}
			}
		}
    }
---------------------------------------------------------------------
//启动完成后 实际上进行调用factoryBean实例化对象的地方是在第一次使用这个对象的时候
//例如 Object myFactoryBean1 = applicationContext.getBean("myFactoryBean");
//     Object myFactoryBean4 = applicationContext.getBean(String.class);
//实际处理fatoryBean的逻辑在getBean->doGetBean

	protected <T> T doGetBean(final String name, @Nullable final Class<T> requiredType,
			@Nullable final Object[] args, boolean typeCheckOnly) throws BeansException {
        
        final String beanName = transformedBeanName(name);
            Object bean;

		// Eagerly check singleton cache for manually registered singletons.
        //这里拿到的是factoryBean对象 存放在一级缓存singletonFactories中 所以可以拿到
		Object sharedInstance = getSingleton(beanName);
		if (sharedInstance != null && args == null) {
			if (logger.isTraceEnabled()) {
				if (isSingletonCurrentlyInCreation(beanName)) {
					logger.trace("Returning eagerly cached instance of singleton bean '" + beanName +
							"' that is not fully initialized yet - a consequence of a circular reference");
				}
				else {
					logger.trace("Returning cached instance of singleton bean '" + beanName + "'");
				}
			}
			bean = getObjectForBeanInstance(sharedInstance, name, beanName, null);
		}

		else {
			// Fail if we're already creating this bean instance:
			// We're assumably within a circular reference.
			if (isPrototypeCurrentlyInCreation(beanName)) {
				throw new BeanCurrentlyInCreationException(beanName);
			}

			// Check if bean definition exists in this factory.
			BeanFactory parentBeanFactory = getParentBeanFactory();
			if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
				// Not found -> check parent.
				String nameToLookup = originalBeanName(name);
				if (parentBeanFactory instanceof AbstractBeanFactory) {
					return ((AbstractBeanFactory) parentBeanFactory).doGetBean(
							nameToLookup, requiredType, args, typeCheckOnly);
				}
				else if (args != null) {
					// Delegation to parent with explicit args.
					return (T) parentBeanFactory.getBean(nameToLookup, args);
				}
				else if (requiredType != null) {
					// No args -> delegate to standard getBean method.
					return parentBeanFactory.getBean(nameToLookup, requiredType);
				}
				else {
					return (T) parentBeanFactory.getBean(nameToLookup);
				}
			}
    
    }
------------------------------------------------------------------
  		//继续深入getObjectForBeanInstance
protected Object getObjectForBeanInstance(
			Object beanInstance, String name, String beanName, @Nullable RootBeanDefinition mbd) {

		// Don't let calling code try to dereference the factory if the bean isn't a factory.
    	//判断要获取的beanName是不是以&的符号开头
    	/**
    	*	public static boolean isFactoryDereference(@Nullable String name) {
				return (name != null && name.startsWith(BeanFactory.FACTORY_BEAN_PREFIX));
			}
    	*/
		if (BeanFactoryUtils.isFactoryDereference(name)) {
			if (beanInstance instanceof NullBean) {
				return beanInstance;
			}
			if (!(beanInstance instanceof FactoryBean)) {
				throw new BeanIsNotAFactoryException(beanName, beanInstance.getClass());
			}
			if (mbd != null) {
				mbd.isFactoryBean = true;
			}
            //如果要获取&myFactoryBean这样的对象 那么就直接返回factoryBean
			return beanInstance;
		}

		// Now we have the bean instance, which may be a normal bean or a FactoryBean.
		// If it's a FactoryBean, we use it to create a bean instance, unless the
		// caller actually wants a reference to the factory.
    	//如果这个对象不是fatoryBean 返回
		if (!(beanInstance instanceof FactoryBean)) {
			return beanInstance;
		}
 		//开始处理fatoryBean的逻辑
		Object object = null;
		if (mbd != null) {
			mbd.isFactoryBean = true;
		}
		else {
           	//从缓存中获取一下要创建的bean
            /**
            *	@Nullable
                protected Object getCachedObjectForFactoryBean(String beanName) {
                    return this.factoryBeanObjectCache.get(beanName);
                }
            */
			object = getCachedObjectForFactoryBean(beanName);
		}
    	//没获取到 开始创建
		if (object == null) {
			// Return bean instance from factory.
			FactoryBean<?> factory = (FactoryBean<?>) beanInstance;
			// Caches object obtained from FactoryBean if it is a singleton.
             //合并beanDefinition
			if (mbd == null && containsBeanDefinition(beanName)) {
				mbd = getMergedLocalBeanDefinition(beanName);
			}
			boolean synthetic = (mbd != null && mbd.isSynthetic());
			object = getObjectFromFactoryBean(factory, beanName, !synthetic);
		}
		return object;
	}
-------------------------------------------------------------------------
    //深入getObjectFromFactoryBean
    protected Object getObjectFromFactoryBean(FactoryBean<?> factory, String beanName, boolean shouldPostProcess) {
		//如果这个要创建的对象是单例的
        if (factory.isSingleton() && containsSingleton(beanName)) {
			synchronized (getSingletonMutex()) {
                //那么还是先从缓存中取一下 
				Object object = this.factoryBeanObjectCache.get(beanName);
				if (object == null) {
                      //取不到的话就去创建
					object = doGetObjectFromFactoryBean(factory, beanName);
					// Only post-process and store if not put there already during getObject() call above
					// (e.g. because of circular reference processing triggered by custom getBean calls)
					Object alreadyThere = this.factoryBeanObjectCache.get(beanName);
					if (alreadyThere != null) {
						object = alreadyThere;
					}
					else {
                        
						if (shouldPostProcess) {
							if (isSingletonCurrentlyInCreation(beanName)) {
								// Temporarily return non-post-processed object, not storing it yet..
								return object;
							}
							beforeSingletonCreation(beanName);
							try {
                                   //实际创建对象的地方
								object = postProcessObjectFromFactoryBean(object, beanName);
							}
							catch (Throwable ex) {
								throw new BeanCreationException(beanName,
										"Post-processing of FactoryBean's singleton object failed", ex);
							}
							finally {
								afterSingletonCreation(beanName);
							}
						}
						if (containsSingleton(beanName)) {
                            	//创建完对象放入缓存中 
							this.factoryBeanObjectCache.put(beanName, object);
						}
					}
				}
				return object;
			}
		}
        //如果不是单例 是原型
		else {
            //那么直接创建对象
			Object object = doGetObjectFromFactoryBean(factory, beanName);
			if (shouldPostProcess) {
				try {
                    //看起来原型对象才会进行
                    //创建完成调用BeanPostProcessor 初始化after处理
					object = postProcessObjectFromFactoryBean(object, beanName);
				}
				catch (Throwable ex) {
					throw new BeanCreationException(beanName, "Post-processing of FactoryBean's object failed", ex);
				}
			}
			return object;
		}
	}
--------------------------------------------------------------------------
//创建bean的实际方法
private Object doGetObjectFromFactoryBean(final FactoryBean<?> factory, final String beanName)
			throws BeanCreationException {

		Object object;
		try {
			if (System.getSecurityManager() != null) {
				AccessControlContext acc = getAccessControlContext();
				try {
					object = AccessController.doPrivileged((PrivilegedExceptionAction<Object>) factory::getObject, acc);
				}
				catch (PrivilegedActionException pae) {
					throw pae.getException();
				}
			}
			else {
                //通过调用getObject方法获取对象
				object = factory.getObject();
			}
		}
		catch (FactoryBeanNotInitializedException ex) {
			throw new BeanCurrentlyInCreationException(beanName, ex.toString());
		}
		catch (Throwable ex) {
			throw new BeanCreationException(beanName, "FactoryBean threw exception on object creation", ex);
		}

		// Do not accept a null value for a FactoryBean that's not fully
		// initialized yet: Many FactoryBeans just return null then.
		if (object == null) {
			if (isSingletonCurrentlyInCreation(beanName)) {
				throw new BeanCurrentlyInCreationException(
						beanName, "FactoryBean which is currently in creation returned null from getObject");
			}
			object = new NullBean();
		}
		return object;
	}        
```











### 通过InstantiationAwareBeanPostProcessor生成对象

![image-20231211105324341](.\images\image-20231211105324341.png)

**接口**

```java
    public interface InstantiationAwareBeanPostProcessor extends BeanPostProcessor {
        //首先是自带的四个方法
        //实例化之前执行
        //这个方法就是用于在容器实例化之前 给代理对象一个机会去提前实例化对象返回
        @Nullable
        default Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) throws BeansException {
            return null;
        }
        //实例化之后执行
        default boolean postProcessAfterInstantiation(Object bean, String beanName) throws BeansException {
            return true;
        }

        @Nullable
        default PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName)
                throws BeansException {

            return null;
        }

        @Deprecated
        @Nullable
        default PropertyValues postProcessPropertyValues(
                PropertyValues pvs, PropertyDescriptor[] pds, Object bean, String beanName) throws BeansException {

            return pvs;
        }
        //接下来两个方法继承自BeanPostProcessor
        //在bean初始化之前执行
        @Nullable
        default Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
            return bean;
        }
		//初始化之后处理
        @Nullable
        default Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            return bean;
        }
    }
```

**执行流程**

```java
//getBean-->doGetBean-->createBean
	@Override
	protected Object createBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)
			throws BeanCreationException {

		if (logger.isTraceEnabled()) {
			logger.trace("Creating instance of bean '" + beanName + "'");
		}
		RootBeanDefinition mbdToUse = mbd;

		// Make sure bean class is actually resolved at this point, and
		// clone the bean definition in case of a dynamically resolved Class
		// which cannot be stored in the shared merged bean definition.
		Class<?> resolvedClass = resolveBeanClass(mbd, beanName);
		if (resolvedClass != null && !mbd.hasBeanClass() && mbd.getBeanClassName() != null) {
			mbdToUse = new RootBeanDefinition(mbd);
			mbdToUse.setBeanClass(resolvedClass);
		}

		// Prepare method overrides.
		try {
            //这个是标记lookup和replace-method的地方
			mbdToUse.prepareMethodOverrides();
		}
		catch (BeanDefinitionValidationException ex) {
			throw new BeanDefinitionStoreException(mbdToUse.getResourceDescription(),
					beanName, "Validation of method overrides failed", ex);
		}

		try {
			// Give BeanPostProcessors a chance to return a proxy instead of the target bean instance.
            //根据注释 给BeanPostProcessors一个机会去创建一个bean的代理对象返回
            //这里返回以后就直接回到getSingleton了 跳过了后续的容器bean创建过程
			Object bean = resolveBeforeInstantiation(beanName, mbdToUse);
			if (bean != null) {
				return bean;
			}
		}
		catch (Throwable ex) {
			throw new BeanCreationException(mbdToUse.getResourceDescription(), beanName,
					"BeanPostProcessor before instantiation of bean failed", ex);
		}

		try {
			Object beanInstance = doCreateBean(beanName, mbdToUse, args);
			if (logger.isTraceEnabled()) {
				logger.trace("Finished creating instance of bean '" + beanName + "'");
			}
			return beanInstance;
		}
		catch (BeanCreationException | ImplicitlyAppearedSingletonException ex) {
			// A previously detected exception with proper bean creation context already,
			// or illegal singleton state to be communicated up to DefaultSingletonBeanRegistry.
			throw ex;
		}
		catch (Throwable ex) {
			throw new BeanCreationException(
					mbdToUse.getResourceDescription(), beanName, "Unexpected exception during bean creation", ex);
		}
	}
```

```java
    @Nullable
	protected Object resolveBeforeInstantiation(String beanName, RootBeanDefinition mbd) {
		Object bean = null;
		if (!Boolean.FALSE.equals(mbd.beforeInstantiationResolved)) {
			// Make sure bean class is actually resolved at this point.
            //逻辑也比较简单 容器里面只要有InstantiationAwareBeanPostProcessor就会执行 每个bean都会执行
			if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
				Class<?> targetType = determineTargetType(beanName, mbd);
				if (targetType != null) {
                    	//调用实例化前的方法
					bean = applyBeanPostProcessorsBeforeInstantiation(targetType, beanName);
					if (bean != null) {
                        	//调用初始化后的方法
						bean = applyBeanPostProcessorsAfterInitialization(bean, beanName);
					}
				}
			}
			mbd.beforeInstantiationResolved = (bean != null);
		}
		return bean;
	}
```

**例子**

```java
@Component
public class MyInstantiationAwareBeanPostProcessor implements InstantiationAwareBeanPostProcessor {

    @Override
    public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) throws BeansException {
        System.out.println("执行实例化前置方法");
        if (beanName.equals("xxxBean")){
            return new Object();
        }
        return null;
    }

    @Override
    public boolean postProcessAfterInstantiation(Object bean, String beanName) throws BeansException {
        System.out.println("执行实例化后置方法");
        return InstantiationAwareBeanPostProcessor.super.postProcessAfterInstantiation(bean, beanName);
    }

    @Override
    public PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName) throws BeansException {
        System.out.println("执行处理属性方法");
        return InstantiationAwareBeanPostProcessor.super.postProcessProperties(pvs, bean, beanName);
    }

    @Override
    public PropertyValues postProcessPropertyValues(PropertyValues pvs, PropertyDescriptor[] pds, Object bean, String beanName) throws BeansException {
        System.out.println("执行处理属性方法2");
        return InstantiationAwareBeanPostProcessor.super.postProcessPropertyValues(pvs, pds, bean, beanName);
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        System.out.println("执行初始化前方法");
        return InstantiationAwareBeanPostProcessor.super.postProcessBeforeInitialization(bean, beanName);
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        System.out.println("执行初始化后方法");
        return InstantiationAwareBeanPostProcessor.super.postProcessAfterInitialization(bean, beanName);
    }
    
    
```

```java
11:17:47.127 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'testController'
执行实例化前置方法
执行初始化后方法
11:17:50.614 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'testService'
执行实例化前置方法
执行初始化后方法
//如果控制台出现了其他bean 所有方法都执行了 请不要慌张 因为BeanPostProcessor是为全体bean服务的 所有bean都会进行调用 而不单单是你提前创建的bean单独调用
```

### 通过FactoryMethod生成对象

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns:context="http://www.springframework.org/schema/context"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns="http://www.springframework.org/schema/beans"
       xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd
        http://www.springframework.org/schema/context  http://www.springframework.org/schema/context/spring-context.xsd
        ">
    <context:component-scan base-package="com.example.demo"/>

<!--    通过静态factoryMethod创建对象 需要将class设置为静态工厂类 指定factoryMethod方法和参数-->
    <bean name="user" class="com.example.demo.factorymethod.UserStaticFactoryMethod"
          factory-method="getUserFactoryMethod">
        <constructor-arg value="lisi"/>
    </bean>

    <!--    通过实例factoryMethod创建对象 需要将实例factory交给spring管理 指定factory-bean 指定factoryMethod方法和参数-->
    <bean name="userFactoryMethod" class="com.example.demo.factorymethod.UserFactoryMethod"></bean>
    <bean name="user2" factory-bean="userFactoryMethod" factory-method="getUserFactoryMethod">
        <constructor-arg value="zhangsan"></constructor-arg>
    </bean>


</beans>
```

```java
public class User {
    private String username;
    private Integer age;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }
}
------------------------------------------------------------------------------------------
//实例工厂
public class UserFactoryMethod {

    public User getUserFactoryMethod(String name) {
        User user = new User();
        user.setAge(1);
        user.setUsername(name);
        return user;
    }
}
------------------------------------------------------------------------------------------
//静态工厂
public class UserStaticFactoryMethod {
    public static User getUserFactoryMethod(String name) {
        User user = new User();
        user.setAge(1);
        user.setUsername(name);
        return user;
    }
}

```

**剖析原理**

```java
//实际上处理fatory-method的逻辑在 getBean->doGetBean->createBean->doCreateBean->createBeanInstance

protected BeanWrapper createBeanInstance(String beanName, RootBeanDefinition mbd, @Nullable Object[] args) {
		// Make sure bean class is actually resolved at this point.
		Class<?> beanClass = resolveBeanClass(mbd, beanName);

		if (beanClass != null && !Modifier.isPublic(beanClass.getModifiers()) && !mbd.isNonPublicAccessAllowed()) {
			throw new BeanCreationException(mbd.getResourceDescription(), beanName,
					"Bean class isn't public, and non-public access not allowed: " + beanClass.getName());
		}
		//这里是supplier支持的创建bean的方式 等下讲
		Supplier<?> instanceSupplier = mbd.getInstanceSupplier();
		if (instanceSupplier != null) {
			return obtainFromSupplier(instanceSupplier, beanName);
		}
		//这里就是处理beanDefinition中factory-method的地方
		if (mbd.getFactoryMethodName() != null) {
			return instantiateUsingFactoryMethod(beanName, mbd, args);
		}

		// Shortcut when re-creating the same bean...
		boolean resolved = false;
		boolean autowireNecessary = false;
		if (args == null) {
			synchronized (mbd.constructorArgumentLock) {
				if (mbd.resolvedConstructorOrFactoryMethod != null) {
					resolved = true;
					autowireNecessary = mbd.constructorArgumentsResolved;
				}
			}
		}
		if (resolved) {
			if (autowireNecessary) {
				return autowireConstructor(beanName, mbd, null, null);
			}
			else {
				return instantiateBean(beanName, mbd);
			}
		}

		// Candidate constructors for autowiring?
		Constructor<?>[] ctors = determineConstructorsFromBeanPostProcessors(beanClass, beanName);
		if (ctors != null || mbd.getResolvedAutowireMode() == AUTOWIRE_CONSTRUCTOR ||
				mbd.hasConstructorArgumentValues() || !ObjectUtils.isEmpty(args)) {
			return autowireConstructor(beanName, mbd, ctors, args);
		}

		// Preferred constructors for default construction?
		ctors = mbd.getPreferredConstructors();
		if (ctors != null) {
			return autowireConstructor(beanName, mbd, ctors, null);
		}

		// No special handling: simply use no-arg constructor.
		return instantiateBean(beanName, mbd);
	}
------------------------------------------------------------------------------------------
//深入instantiateUsingFactoryMethod方法
	protected BeanWrapper instantiateUsingFactoryMethod(
			String beanName, RootBeanDefinition mbd, @Nullable Object[] explicitArgs) {

		return new ConstructorResolver(this).instantiateUsingFactoryMethod(beanName, mbd, explicitArgs);
	}
------------------------------------------------------------------------------------------
//继续深入instantiateUsingFactoryMethod 这个方法比较复杂 我们截取几个片段来看
public BeanWrapper instantiateUsingFactoryMethod(
			String beanName, RootBeanDefinition mbd, @Nullable Object[] explicitArgs) {

		//首先就是如何判断静态还是实例工厂    	
    	BeanWrapperImpl bw = new BeanWrapperImpl();
		this.beanFactory.initBeanWrapper(bw);

		Object factoryBean;
		Class<?> factoryClass;
		boolean isStatic;
		//这里就是通过factoryBeanName来决定是否是实例工厂
		String factoryBeanName = mbd.getFactoryBeanName();
		if (factoryBeanName != null) {
			if (factoryBeanName.equals(beanName)) {
				throw new BeanDefinitionStoreException(mbd.getResourceDescription(), beanName,
						"factory-bean reference points back to the same bean definition");
			}
             //会从容器中取出实例 所以要交给spring管理
			factoryBean = this.beanFactory.getBean(factoryBeanName);
			if (mbd.isSingleton() && this.beanFactory.containsSingleton(beanName)) {
				throw new ImplicitlyAppearedSingletonException();
			}
			factoryClass = factoryBean.getClass();
			isStatic = false;
		}
		else {
			// It's a static factory method on the bean class.
			if (!mbd.hasBeanClass()) {
				throw new BeanDefinitionStoreException(mbd.getResourceDescription(), beanName,
						"bean definition declares neither a bean class nor a factory-bean reference");
			}
			factoryBean = null;
             //静态工厂取得是beanClass 这也是为什么xml的class位置要写factory的class
			factoryClass = mbd.getBeanClass();
			isStatic = true;
		}
------------------------------------------------------------------------------------------
    //接下来这部分是通过方法参数的个数 和传参的个数 类型做匹配寻找对应的fatory-method 因为可能会有重载
    if (factoryMethodToUse == null || argsToUse == null) {
			// Need to determine the factory method...
			// Try all methods with this name to see if they match the given arguments.
			factoryClass = ClassUtils.getUserClass(factoryClass);

			List<Method> candidates = null;
			if (mbd.isFactoryMethodUnique) {
				if (factoryMethodToUse == null) {
					factoryMethodToUse = mbd.getResolvedFactoryMethod();
				}
				if (factoryMethodToUse != null) {
					candidates = Collections.singletonList(factoryMethodToUse);
				}
			}
			if (candidates == null) {
				candidates = new ArrayList<>();
                 //获取这个工厂类及其父类的所有方法
				Method[] rawCandidates = getCandidateMethods(factoryClass, mbd);
				for (Method candidate : rawCandidates) {
					if (Modifier.isStatic(candidate.getModifiers()) == isStatic && mbd.isFactoryMethod(candidate)) {
						candidates.add(candidate);
					}
				}
			}
			//如果没有参数并且只有一个fatoryMethod 没有重载直接调用实例化返回
			if (candidates.size() == 1 && explicitArgs == null && !mbd.hasConstructorArgumentValues()) {
				Method uniqueCandidate = candidates.get(0);
				if (uniqueCandidate.getParameterCount() == 0) {
					mbd.factoryMethodToIntrospect = uniqueCandidate;
					synchronized (mbd.constructorArgumentLock) {
						mbd.resolvedConstructorOrFactoryMethod = uniqueCandidate;
						mbd.constructorArgumentsResolved = true;
						mbd.resolvedConstructorArguments = EMPTY_ARGS;
					}
					bw.setBeanInstance(instantiate(beanName, mbd, factoryBean, uniqueCandidate, EMPTY_ARGS));
					return bw;
				}
			}

			if (candidates.size() > 1) {  // explicitly skip immutable singletonList
				candidates.sort(AutowireUtils.EXECUTABLE_COMPARATOR);
			}

			ConstructorArgumentValues resolvedValues = null;
			boolean autowiring = (mbd.getResolvedAutowireMode() == AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR);
			int minTypeDiffWeight = Integer.MAX_VALUE;
			Set<Method> ambiguousFactoryMethods = null;

			int minNrOfArgs;
			if (explicitArgs != null) {
				minNrOfArgs = explicitArgs.length;
			}
			else {
				// We don't have arguments passed in programmatically, so we need to resolve the
				// arguments specified in the constructor arguments held in the bean definition.
                //如果没有通过getBean的重载方法指定外部参数 那么就解析beanDefinition中携带的constructor参数
				if (mbd.hasConstructorArgumentValues()) {
					ConstructorArgumentValues cargs = mbd.getConstructorArgumentValues();
					resolvedValues = new ConstructorArgumentValues();
                      //解析出携带的constructor参数的数量封装对象
					minNrOfArgs = resolveConstructorArguments(beanName, mbd, bw, cargs, resolvedValues);
				}
				else {
					minNrOfArgs = 0;
				}
			}
        
        //否则有重载 就是通过参数个数 类型 进行判断寻找最匹配的方法
        for (Method candidate : candidates) {
				int parameterCount = candidate.getParameterCount();
				//首先是这个方法的参数个数肯定要大于等于最小传参的数量
				if (parameterCount >= minNrOfArgs) {
					ArgumentsHolder argsHolder;

					Class<?>[] paramTypes = candidate.getParameterTypes();
					if (explicitArgs != null) {
						// Explicit arguments given -> arguments length must match exactly.
						if (paramTypes.length != explicitArgs.length) {
							continue;
						}
						argsHolder = new ArgumentsHolder(explicitArgs);
					}
					else {
						// Resolved constructor arguments: type conversion and/or autowiring necessary.
						try {
							String[] paramNames = null;
							ParameterNameDiscoverer pnd = this.beanFactory.getParameterNameDiscoverer();
							if (pnd != null) {
                                   //解析出这个方法的所有参数名称
								paramNames = pnd.getParameterNames(candidate);
							}
                            	//创建方法参数名称和方法参数值的映射
							argsHolder = createArgumentArray(beanName, mbd, resolvedValues, bw,
									paramTypes, paramNames, candidate, autowiring, candidates.size() == 1);
						}
						catch (UnsatisfiedDependencyException ex) {
							if (logger.isTraceEnabled()) {
								logger.trace("Ignoring factory method [" + candidate + "] of bean '" + beanName + "': " + ex);
							}
							// Swallow and try next overloaded factory method.
							if (causes == null) {
								causes = new LinkedList<>();
							}
							causes.add(ex);
							continue;
						}
					}
					//计算权重 根据方法参数的类型和参数的类型的比较 逐级寻找父类完全匹配的类型 寻找过程中加和权重
                    //权重越低 代表该方法匹配度越高 
					int typeDiffWeight = (mbd.isLenientConstructorResolution() ?
							argsHolder.getTypeDifferenceWeight(paramTypes) : argsHolder.getAssignabilityWeight(paramTypes));
					// Choose this factory method if it represents the closest match.
					if (typeDiffWeight < minTypeDiffWeight) {
						factoryMethodToUse = candidate;
						argsHolderToUse = argsHolder;
						argsToUse = argsHolder.arguments;
						minTypeDiffWeight = typeDiffWeight;
						ambiguousFactoryMethods = null;
					}
					// Find out about ambiguity: In case of the same type difference weight
					// for methods with the same number of parameters, collect such candidates
					// and eventually raise an ambiguity exception.
					// However, only perform that check in non-lenient constructor resolution mode,
					// and explicitly ignore overridden methods (with the same parameter signature).
					else if (factoryMethodToUse != null && typeDiffWeight == minTypeDiffWeight &&
							!mbd.isLenientConstructorResolution() &&
							paramTypes.length == factoryMethodToUse.getParameterCount() &&
							!Arrays.equals(paramTypes, factoryMethodToUse.getParameterTypes())) {
						if (ambiguousFactoryMethods == null) {
							ambiguousFactoryMethods = new LinkedHashSet<>();
							ambiguousFactoryMethods.add(factoryMethodToUse);
						}
						ambiguousFactoryMethods.add(candidate);
					}
				}
			}

    
------------------------------------------------------------------------------------------
        //最后实例化对象 实际上是调用fatoryMethod
        bw.setBeanInstance(instantiate(beanName, mbd, factoryBean, factoryMethodToUse, argsToUse));
	    return bw;
}
------------------------------------------------------------------------------------------
    //实例化方法
    private Object instantiate(String beanName, RootBeanDefinition mbd,
			@Nullable Object factoryBean, Method factoryMethod, Object[] args) {

		try {
			if (System.getSecurityManager() != null) {
				return AccessController.doPrivileged((PrivilegedAction<Object>) () ->
						this.beanFactory.getInstantiationStrategy().instantiate(
								mbd, beanName, this.beanFactory, factoryBean, factoryMethod, args),
						this.beanFactory.getAccessControlContext());
			}
			else {
                //拿到默认的实例化策略 一般是SimpleInstantiationStrategy
				return this.beanFactory.getInstantiationStrategy().instantiate(
						mbd, beanName, this.beanFactory, factoryBean, factoryMethod, args);
			}
		}
		catch (Throwable ex) {
			throw new BeanCreationException(mbd.getResourceDescription(), beanName,
					"Bean instantiation via factory method failed", ex);
		}
	}
------------------------------------------------------------------------------------------
    //真正实例化的方法
    	@Override
	public Object instantiate(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner,
			@Nullable Object factoryBean, final Method factoryMethod, Object... args) {

		try {
			if (System.getSecurityManager() != null) {
				AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
					ReflectionUtils.makeAccessible(factoryMethod);
					return null;
				});
			}
			else {
				ReflectionUtils.makeAccessible(factoryMethod);
			}

			Method priorInvokedFactoryMethod = currentlyInvokedFactoryMethod.get();
			try {
				currentlyInvokedFactoryMethod.set(factoryMethod);
                 //invoke调用执行
				Object result = factoryMethod.invoke(factoryBean, args);
				if (result == null) {
					result = new NullBean();
				}
				return result;
			}
			finally {
				if (priorInvokedFactoryMethod != null) {
					currentlyInvokedFactoryMethod.set(priorInvokedFactoryMethod);
				}
				else {
					currentlyInvokedFactoryMethod.remove();
				}
			}
		}
		catch (IllegalArgumentException ex) {
			throw new BeanInstantiationException(factoryMethod,
					"Illegal arguments to factory method '" + factoryMethod.getName() + "'; " +
					"args: " + StringUtils.arrayToCommaDelimitedString(args), ex);
		}
		catch (IllegalAccessException ex) {
			throw new BeanInstantiationException(factoryMethod,
					"Cannot access factory method '" + factoryMethod.getName() + "'; is it public?", ex);
		}
		catch (InvocationTargetException ex) {
			String msg = "Factory method '" + factoryMethod.getName() + "' threw exception";
			if (bd.getFactoryBeanName() != null && owner instanceof ConfigurableBeanFactory &&
					((ConfigurableBeanFactory) owner).isCurrentlyInCreation(bd.getFactoryBeanName())) {
				msg = "Circular reference involving containing bean '" + bd.getFactoryBeanName() + "' - consider " +
						"declaring the factory method as static for independence from its containing instance. " + msg;
			}
			throw new BeanInstantiationException(factoryMethod, msg, ex.getTargetException());
		}
	}


```

### 通过supplier创建对象

```java
//例子
//实现BeanFactoryPostProcessor来进行修改beanDefinition
@Component
public class MySupplier implements BeanFactoryPostProcessor {
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        BeanDefinition beanDefinition = beanFactory.getBeanDefinition("user");
        //setInstanceSupplier这个方法在AbstractBeanDefinition里面 需要做一下强转
        AbstractBeanDefinition abstractBeanDefinition = (AbstractBeanDefinition) beanDefinition;
        //然后是对supplier进行一下设置
        abstractBeanDefinition.setInstanceSupplier(new Supplier<User>() {
            @Override
            public User get() {
                return new User();
            }
        });
    }
}
```

**原理**

```java
//处理supplier的逻辑在 getBean->doGetBean->createBean->doCreateBean->createBeanInstance

protected BeanWrapper createBeanInstance(String beanName, RootBeanDefinition mbd, @Nullable Object[] args) {
		// Make sure bean class is actually resolved at this point.
		Class<?> beanClass = resolveBeanClass(mbd, beanName);

		if (beanClass != null && !Modifier.isPublic(beanClass.getModifiers()) && !mbd.isNonPublicAccessAllowed()) {
			throw new BeanCreationException(mbd.getResourceDescription(), beanName,
					"Bean class isn't public, and non-public access not allowed: " + beanClass.getName());
		}
		//这里就是处理supplier逻辑的地方
    	//如果beanDefinition中包含supplier 那么就按照supplier的方式返回
		Supplier<?> instanceSupplier = mbd.getInstanceSupplier();
		if (instanceSupplier != null) {
			return obtainFromSupplier(instanceSupplier, beanName);
		}

		if (mbd.getFactoryMethodName() != null) {
			return instantiateUsingFactoryMethod(beanName, mbd, args);
		}

		// Shortcut when re-creating the same bean...
		boolean resolved = false;
		boolean autowireNecessary = false;
		if (args == null) {
			synchronized (mbd.constructorArgumentLock) {
				if (mbd.resolvedConstructorOrFactoryMethod != null) {
					resolved = true;
					autowireNecessary = mbd.constructorArgumentsResolved;
				}
			}
		}
		if (resolved) {
			if (autowireNecessary) {
				return autowireConstructor(beanName, mbd, null, null);
			}
			else {
				return instantiateBean(beanName, mbd);
			}
		}

		// Candidate constructors for autowiring?
		Constructor<?>[] ctors = determineConstructorsFromBeanPostProcessors(beanClass, beanName);
		if (ctors != null || mbd.getResolvedAutowireMode() == AUTOWIRE_CONSTRUCTOR ||
				mbd.hasConstructorArgumentValues() || !ObjectUtils.isEmpty(args)) {
			return autowireConstructor(beanName, mbd, ctors, args);
		}

		// Preferred constructors for default construction?
		ctors = mbd.getPreferredConstructors();
		if (ctors != null) {
			return autowireConstructor(beanName, mbd, ctors, null);
		}

		// No special handling: simply use no-arg constructor.
		return instantiateBean(beanName, mbd);
	}
------------------------------------------------------------------------------------------
//实现也比较简单 

protected BeanWrapper obtainFromSupplier(Supplier<?> instanceSupplier, String beanName) {
		Object instance;

		String outerBean = this.currentlyCreatedBean.get();
		this.currentlyCreatedBean.set(beanName);
		try {
            //直接调用我们自己设置的supplier进行创建对象 返回
			instance = instanceSupplier.get();
		}
		finally {
			if (outerBean != null) {
				this.currentlyCreatedBean.set(outerBean);
			}
			else {
				this.currentlyCreatedBean.remove();
			}
		}

		if (instance == null) {
			instance = new NullBean();
		}
		BeanWrapper bw = new BeanWrapperImpl(instance);
		initBeanWrapper(bw);
		return bw;
	}
```



## 循环依赖





## 梳理各个类的接口继承关系





## AOP

### @EnableAspectJAutoProxy

![image-20240110095544889](.\images\image-20240110095544889.png)

不论是xml方式还是注解方式 都需要注册一个AspectAutoProxyCreator 而xml注册的是

![image-20240110095705893](.\images\image-20240110095705893.png)

xml是在解析<aop>标签的时候进行注册的 

而在注解情况下 想要@Aspect等注解生效 就需要使用@EnableAspectJAutoProxy 注册

![image-20240110095817749](.\images\image-20240110095817749.png)

也就是说 不论使用任何方式 都需要先创建AspectAutoProxyCreator 

![image-20240110100526692](.\images\image-20240110100526692.png)

- 如果是纯xml文件来进行aop的代理工作 那么就采用config标签 内部会注册AspectJAwareAdvisorAutoProxyCreator 这个类来处理 

- 如果是xml+注解搭配的形式 那么就采用 config标签+aspectj-autoproxy标签 内部会注册 AspectJAwareAdvisorAutoProxyCreator 这个类和AnnotationAwareAspectJAutoProxyCreator这两个类 但是beanDefinitionMap的key都是org.springframework.aop.config.internalAutoProxyCreator 这个name 所以第二个会替换第一个 只剩下AnnotationAwareAspectJAutoProxyCreator

  ![image-20240110101210570](.\images\image-20240110101210570.png)

- 如果是平时就用注解来进行aop 那么就使用@EnableAspectJAutoProxy来导入注册AnnotationAwareAspectJAutoProxyCreator处理

```xml
<aop:config>
        <aop:aspect ref="logUtil">
            <aop:pointcut id="myPoint" expression="execution( Integer com.example.demo.aop.xml.service.MyCalculator.*  (..))"/>
            <aop:around method="around" pointcut-ref="myPoint"></aop:around>
            <aop:before method="start" pointcut-ref="myPoint"></aop:before>
            <aop:after method="logFinally" pointcut-ref="myPoint"></aop:after>
            <aop:after-returning method="stop" pointcut-ref="myPoint" returning="result"></aop:after-returning>
            <aop:after-throwing method="logException" pointcut-ref="myPoint" throwing="e"></aop:after-throwing>
        </aop:aspect>
    </aop:config>
<aop:aspectj-autoproxy></aop:aspectj-autoproxy>
```

### 拓扑排序

> 一个较大的工程往往被划分成许多子工程，我们把这些子工程称作**活动**(activity)。在整个工程中，有些子工程(活动)必须在其它有关子工程完成之后才能开始，也就是说，一个子工程的开始是以它的所有前序子工程的结束为先决条件的，但有些子工程没有先决条件，可以安排在任何时间开始。为了形象地反映出整个工程中各个子工程(活动)之间的先后关系，可用一个有向图来表示，图中的顶点代表活动(子工程)，图中的有向边代表活动的先后关系，即有向边的起点的活动是终点活动的前序活动，只有当起点活动完成之后，其终点活动才能进行。通常，我们把这种顶点表示活动、边表示活动间先后关系的有向图称做**顶点活动网**(Activity On Vertex network)，简称**AOV**网。
>

![image-20240117174717970](.\images\image-20240117174717970.png)



### JDK和CgLib动态代理的原理

#### JDK动态代理

以下都是基于jdk1.8的实现，其他版本实现并不一致

jdk动态代理的方式是基于生成被代理类的接口的子类 也就是被代理的兄弟类来实现相应的代理增强功能

核心原理代码： Proxy.newProxyInstance(loader, interfaces, h);

```java
@CallerSensitive
    public static Object newProxyInstance(ClassLoader loader,
                                          Class<?>[] interfaces,
                                          InvocationHandler h)
        throws IllegalArgumentException
    {
        Objects.requireNonNull(h);

        final Class<?>[] intfs = interfaces.clone();
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            checkProxyAccess(Reflection.getCallerClass(), loader, intfs);
        }

        /*
         * Look up or generate the designated proxy class.
         */
        //这里是实际生成字节码文件并且加载到jvm的方法
        Class<?> cl = getProxyClass0(loader, intfs);

        /*
         * Invoke its constructor with the designated invocation handler.
         */
        try {
            if (sm != null) {
                checkNewProxyPermission(Reflection.getCallerClass(), cl);
            }
		    //获取生成的代理类的带有InvocationHandler类型的构造方法 为什么有这个方法下面说
            final Constructor<?> cons = cl.getConstructor(constructorParams);
            final InvocationHandler ih = h;
            if (!Modifier.isPublic(cl.getModifiers())) {
                AccessController.doPrivileged(new PrivilegedAction<Void>() {
                    public Void run() {
                        cons.setAccessible(true);
                        return null;
                    }
                });
            }
            //根据传进来的InvocationHandler参数 构造出代理对象
            return cons.newInstance(new Object[]{h});
        } catch (IllegalAccessException|InstantiationException e) {
            throw new InternalError(e.toString(), e);
        } catch (InvocationTargetException e) {
            Throwable t = e.getCause();
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            } else {
                throw new InternalError(t.toString(), t);
            }
        } catch (NoSuchMethodException e) {
            throw new InternalError(e.toString(), e);
        }
    }
```

核心是在Proxy内部类的ProxyClassFactory中

```java
private static final class ProxyClassFactory
        implements BiFunction<ClassLoader, Class<?>[], Class<?>>
    {
        // prefix for all proxy class names
        private static final String proxyClassNamePrefix = "$Proxy";

        // next number to use for generation of unique proxy class names
        private static final AtomicLong nextUniqueNumber = new AtomicLong();
		//apply方法是实现生成字节码文件和加载内存的实际方法
        @Override
        public Class<?> apply(ClassLoader loader, Class<?>[] interfaces) {

            Map<Class<?>, Boolean> interfaceSet = new IdentityHashMap<>(interfaces.length);
            //对被代理的接口做校验工作
            for (Class<?> intf : interfaces) {
                /*
                 * Verify that the class loader resolves the name of this
                 * interface to the same Class object.
                 */
                Class<?> interfaceClass = null;
                try {
                    interfaceClass = Class.forName(intf.getName(), false, loader);
                } catch (ClassNotFoundException e) {
                }
                if (interfaceClass != intf) {
                    throw new IllegalArgumentException(
                        intf + " is not visible from class loader");
                }
                /*
                 * Verify that the Class object actually represents an
                 * interface.
                 */
                if (!interfaceClass.isInterface()) {
                    throw new IllegalArgumentException(
                        interfaceClass.getName() + " is not an interface");
                }
                /*
                 * Verify that this interface is not a duplicate.
                 */
                if (interfaceSet.put(interfaceClass, Boolean.TRUE) != null) {
                    throw new IllegalArgumentException(
                        "repeated interface: " + interfaceClass.getName());
                }
            }

            String proxyPkg = null;     // package to define proxy class in
            int accessFlags = Modifier.PUBLIC | Modifier.FINAL;

            /*
             * Record the package of a non-public proxy interface so that the
             * proxy class will be defined in the same package.  Verify that
             * all non-public proxy interfaces are in the same package.
             */
            //这里是记录非公共的接口包名称 确保生成的代理类在非公共接口的包中 因为如果不在同一个包 那么是无法进行调用使用的
            //并且还会检查所有非公共的接口是不是在一个包里面 不在一个包里面就报错 因为如果不在同一个包 那么是无法进行调用使用的
            for (Class<?> intf : interfaces) {
                int flags = intf.getModifiers();
                if (!Modifier.isPublic(flags)) {
                    accessFlags = Modifier.FINAL;
                    String name = intf.getName();
                    int n = name.lastIndexOf('.');
                    String pkg = ((n == -1) ? "" : name.substring(0, n + 1));
                    if (proxyPkg == null) {
                        proxyPkg = pkg;
                    } else if (!pkg.equals(proxyPkg)) {
                        throw new IllegalArgumentException(
                            "non-public interfaces from different packages");
                    }
                }
            }
			//没有非公共的接口 就采用默认的前置名称
            if (proxyPkg == null) {
                // if no non-public proxy interfaces, use com.sun.proxy package
                proxyPkg = ReflectUtil.PROXY_PACKAGE + ".";
            }

            /*
             * Choose a name for the proxy class to generate.
             */
            long num = nextUniqueNumber.getAndIncrement();
            //默认的名称 com.sun.proxy.$Proxy0 数字自增
            String proxyName = proxyPkg + proxyClassNamePrefix + num;

            /*
             * Generate the specified proxy class.
             	//生成字节码文件的方法 
             */
            byte[] proxyClassFile = ProxyGenerator.generateProxyClass(
                proxyName, interfaces, accessFlags);
            try {
                return defineClass0(loader, proxyName,
                                    proxyClassFile, 0, proxyClassFile.length);
            } catch (ClassFormatError e) {
                /*
                 * A ClassFormatError here means that (barring bugs in the
                 * proxy class generation code) there was some other
                 * invalid aspect of the arguments supplied to the proxy
                 * class creation (such as virtual machine limitations
                 * exceeded).
                 */
                throw new IllegalArgumentException(e.toString());
            }
        }
    }
```

接下来是ProxyGenerator.generateProxyClass

```java
public static byte[] generateProxyClass(final String name,
                                            Class<?>[] interfaces,
                                            int accessFlags)
    {
    	//创建代理类生成器
        ProxyGenerator gen = new ProxyGenerator(name, interfaces, accessFlags);
    	//生成字节码文件
        final byte[] classFile = gen.generateClassFile();
		//基于配置决定是否将生成的字节码文件保存下来 而不是单单存在内存中
        if (saveGeneratedFiles) {
            java.security.AccessController.doPrivileged(
            new java.security.PrivilegedAction<Void>() {
                public Void run() {
                    try {
                        int i = name.lastIndexOf('.');
                        Path path;
                        if (i > 0) {
                            Path dir = Paths.get(name.substring(0, i).replace('.', File.separatorChar));
                            Files.createDirectories(dir);
                            path = dir.resolve(name.substring(i+1, name.length()) + ".class");
                        } else {
                            path = Paths.get(name + ".class");
                        }
                        Files.write(path, classFile);
                        return null;
                    } catch (IOException e) {
                        throw new InternalError(
                            "I/O exception saving generated file: " + e);
                    }
                }
            });
        }

        return classFile;
    }
```

生成字节码文件的核心方法

```java

    /**
     * Generate a class file for the proxy class.  This method drives the
     * class file generation process.
     */
    private byte[] generateClassFile() {

        /* ============================================================
         * Step 1: Assemble ProxyMethod objects for all methods to
         * generate proxy dispatching code for.
         */

        /*
         * Record that proxy methods are needed for the hashCode, equals,
         * and toString methods of java.lang.Object.  This is done before
         * the methods from the proxy interfaces so that the methods from
         * java.lang.Object take precedence over duplicate methods in the
         * proxy interfaces.
         */
        //添加要代理生成的方法
        addProxyMethod(hashCodeMethod, Object.class);
        addProxyMethod(equalsMethod, Object.class);
        addProxyMethod(toStringMethod, Object.class);

        /*
         * Now record all of the methods from the proxy interfaces, giving
         * earlier interfaces precedence over later ones with duplicate
         * methods.
         */
        //把所有接口的方法都加进来
        for (Class<?> intf : interfaces) {
            for (Method m : intf.getMethods()) {
                addProxyMethod(m, intf);
            }
        }

        /*
         * For each set of proxy methods with the same signature,
         * verify that the methods' return types are compatible.
         */
        //检验返回值
        for (List<ProxyMethod> sigmethods : proxyMethods.values()) {
            checkReturnTypes(sigmethods);
        }

        /* ============================================================
         * Step 2: Assemble FieldInfo and MethodInfo structs for all of
         * fields and methods in the class we are generating.
         */
        try {
            //添加一个构造方法 就是我们上面提到的那个只有一个InvocationHandler参数的构造方法
            methods.add(generateConstructor());
			
            for (List<ProxyMethod> sigmethods : proxyMethods.values()) {
                for (ProxyMethod pm : sigmethods) {
				   //把所有被代理的方法写成静态的字段
                    // add static field for method's Method object
                    fields.add(new FieldInfo(pm.methodFieldName,
                        "Ljava/lang/reflect/Method;",
                         ACC_PRIVATE | ACC_STATIC));
				  //加入代理方法	
                    // generate code for proxy method and add it
                    methods.add(pm.generateMethod());
                }
            }
			//加入静态代码块
            methods.add(generateStaticInitializer());

        } catch (IOException e) {
            throw new InternalError("unexpected I/O Exception", e);
        }

        if (methods.size() > 65535) {
            throw new IllegalArgumentException("method limit exceeded");
        }
        if (fields.size() > 65535) {
            throw new IllegalArgumentException("field limit exceeded");
        }

        /* ============================================================
         * Step 3: Write the final class file.
           //生成字节码文件 
         */

        /*
         * Make sure that constant pool indexes are reserved for the
         * following items before starting to write the final class file.
         */
        cp.getClass(dotToSlash(className));
        cp.getClass(superclassName);
        for (Class<?> intf: interfaces) {
            cp.getClass(dotToSlash(intf.getName()));
        }

        /*
         * Disallow new constant pool additions beyond this point, since
         * we are about to write the final constant pool table.
         */
        cp.setReadOnly();

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(bout);

        try {
            /*
             * Write all the items of the "ClassFile" structure.
             * See JVMS section 4.1.
             */
                                        // u4 magic;
            dout.writeInt(0xCAFEBABE);
                                        // u2 minor_version;
            dout.writeShort(CLASSFILE_MINOR_VERSION);
                                        // u2 major_version;
            dout.writeShort(CLASSFILE_MAJOR_VERSION);

            cp.write(dout);             // (write constant pool)

                                        // u2 access_flags;
            dout.writeShort(accessFlags);
                                        // u2 this_class;
            dout.writeShort(cp.getClass(dotToSlash(className)));
                                        // u2 super_class;
            dout.writeShort(cp.getClass(superclassName));

                                        // u2 interfaces_count;
            dout.writeShort(interfaces.length);
                                        // u2 interfaces[interfaces_count];
            for (Class<?> intf : interfaces) {
                dout.writeShort(cp.getClass(
                    dotToSlash(intf.getName())));
            }

                                        // u2 fields_count;
            dout.writeShort(fields.size());
                                        // field_info fields[fields_count];
            for (FieldInfo f : fields) {
                f.write(dout);
            }

                                        // u2 methods_count;
            dout.writeShort(methods.size());
                                        // method_info methods[methods_count];
            for (MethodInfo m : methods) {
                m.write(dout);
            }

                                         // u2 attributes_count;
            dout.writeShort(0); // (no ClassFile attributes for proxy classes)

        } catch (IOException e) {
            throw new InternalError("unexpected I/O Exception", e);
        }
		//转换成byte数组返回
        return bout.toByteArray();
    }
```

最后 看一下测试的类 和生成的字节码文件

```java
package com.xkw.proxy.jdk;

public class Test {
    public static void main(String[] args) {
        //开启这个 就可以把生成的字节码文件保存下来 而不仅仅在内存中
        System.getProperties().put("sun.misc.ProxyGenerator.saveGeneratedFiles", "true");
        Calculator proxy = CalculatorProxy.getProxy(new MyCalculator());
        proxy.add(1,1);
        System.out.println(proxy.getClass());
    }
}
//---------------------------------------------------------
package com.xkw.proxy.jdk;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class CalculatorProxy {
    public static Calculator getProxy(final Calculator calculator){
        ClassLoader loader = calculator.getClass().getClassLoader();
        Class<?>[] interfaces = calculator.getClass().getInterfaces();
        InvocationHandler h = new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                Object result = null;
                try {
                    result = method.invoke(calculator, args);
                } catch (Exception e) {
                } finally {
                }
                return result;
            }
        };
        //测试生成代理类
        Object proxy = Proxy.newProxyInstance(loader, interfaces, h);
        return (Calculator) proxy;
    }
}
//-----------------------生成的字节码文件

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.sun.proxy;

import com.xkw.proxy.jdk.Calculator;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.UndeclaredThrowableException;

public final class $Proxy0 extends Proxy implements Calculator {
    //所有被代理的方法其实都变成了静态的字段 然后在静态代码块中赋值
    private static Method m1;
    private static Method m2;
    private static Method m6;
    private static Method m3;
    private static Method m4;
    private static Method m5;
    private static Method m0;
	//继承Proxy生成的构造方法 用于接收InvocationHandler的参数
    public $Proxy0(InvocationHandler var1) throws  {
        super(var1);
    }


    public final boolean equals(Object var1) throws  {
        try {
            //代理逻辑实现的关键其实就是在这里
            //利用自定义实现的InvocationHandler 把原来的method方法传进去 产生代理的效果
            return (Boolean)super.h.invoke(this, m1, new Object[]{var1});
        } catch (RuntimeException | Error var3) {
            throw var3;
        } catch (Throwable var4) {
            throw new UndeclaredThrowableException(var4);
        }
    }

    public final String toString() throws  {
        try {
            return (String)super.h.invoke(this, m2, (Object[])null);
        } catch (RuntimeException | Error var2) {
            throw var2;
        } catch (Throwable var3) {
            throw new UndeclaredThrowableException(var3);
        }
    }

    public final int add(int var1, int var2) throws  {
        try {
            return (Integer)super.h.invoke(this, m6, new Object[]{var1, var2});
        } catch (RuntimeException | Error var4) {
            throw var4;
        } catch (Throwable var5) {
            throw new UndeclaredThrowableException(var5);
        }
    }

    public final int sub(int var1, int var2) throws  {
        try {
            return (Integer)super.h.invoke(this, m3, new Object[]{var1, var2});
        } catch (RuntimeException | Error var4) {
            throw var4;
        } catch (Throwable var5) {
            throw new UndeclaredThrowableException(var5);
        }
    }

    public final int mult(int var1, int var2) throws  {
        try {
            return (Integer)super.h.invoke(this, m4, new Object[]{var1, var2});
        } catch (RuntimeException | Error var4) {
            throw var4;
        } catch (Throwable var5) {
            throw new UndeclaredThrowableException(var5);
        }
    }

    public final int div(int var1, int var2) throws  {
        try {
            return (Integer)super.h.invoke(this, m5, new Object[]{var1, var2});
        } catch (RuntimeException | Error var4) {
            throw var4;
        } catch (Throwable var5) {
            throw new UndeclaredThrowableException(var5);
        }
    }

    public final int hashCode() throws  {
        try {
            return (Integer)super.h.invoke(this, m0, (Object[])null);
        } catch (RuntimeException | Error var2) {
            throw var2;
        } catch (Throwable var3) {
            throw new UndeclaredThrowableException(var3);
        }
    }

    static {
        try {
            //给所有被代理的方法赋值 
            m1 = Class.forName("java.lang.Object").getMethod("equals", Class.forName("java.lang.Object"));
            m2 = Class.forName("java.lang.Object").getMethod("toString");
            m6 = Class.forName("com.xkw.proxy.jdk.Calculator").getMethod("add", Integer.TYPE, Integer.TYPE);
            m3 = Class.forName("com.xkw.proxy.jdk.Calculator").getMethod("sub", Integer.TYPE, Integer.TYPE);
            m4 = Class.forName("com.xkw.proxy.jdk.Calculator").getMethod("mult", Integer.TYPE, Integer.TYPE);
            m5 = Class.forName("com.xkw.proxy.jdk.Calculator").getMethod("div", Integer.TYPE, Integer.TYPE);
            m0 = Class.forName("java.lang.Object").getMethod("hashCode");
        } catch (NoSuchMethodException var2) {
            throw new NoSuchMethodError(var2.getMessage());
        } catch (ClassNotFoundException var3) {
            throw new NoClassDefFoundError(var3.getMessage());
        }
    }
}


```

总结一下，实质上jdk的代理方式就是生成一个被代理类的接口的子类 将被代理的方法变成静态字段，利用静态代码块赋值，然后通过继承Proxy类生成的构造方法 将自定义的InvocationHandler对象保存 在调用被代理类的时候通过调用super.h.invoke(this, m0, (Object[])null);方法，进入到用户自定义的逻辑中，并且把原来的方法和参数进行传递，完成代理的工作。

#### CgLib动态代理

支持cglib代理的核心类就是Enhancer

> - Enhancer会生成能够实现方法拦截的动态子类。Enhancer类最初是为了作为JDK Proxy代理的替代方案，但是Enhancer能够代理具体实现类，而不仅仅是实现接口。生成的子类会重写所有被代理类非final方法，并且使用者可以自定义方法拦截器实现方法拦截。
>
> - 其中，最原始、最常用的回调对象类型是MethodInterceptor。在切面（AOP）领域中，MethodInterceptor能够实现环绕通知的能力。基于环绕通知能力，你可以在调用被代理方法的前后穿插执行自定义行为。不仅如此，你也可以改变方法的入参数值，甚至根本不调用被代理方法。
> - 尽管MethodInterceptor已经足够通用且能满足任何代理的需要，回调对象还有其他可用类型，比如LazyLoader，目的是为了更加简化或具备更好的性能。常常一个增强子类只会使用一个回调对象，但是你也可以通过CallbackFilter来控制不同方法使用不同回调对象。
> - 在CGLIB包下很多类都有类似设计。这些类会提供一些静态方法提供给外界使用，但是为了更好的控制对象，比如控制Enhancer生成子类的自定义类加载器，就需要使用Enhancer的构造器。
> - 所有的Enhancer生成的增强子类都会实现Factory接口，当然你也可以通过setUseFactory方法使得子类不实现Factory接口。Factory提供了一些API，可用于修改代理对象的回调对象，也提供了更快、更容易的方式生成一个代理对象。

> Callback接口：Callback接口是CGLIB中的回调接口，用于定义方法拦截逻辑。CGLIB提供了多个Callback接口的实现，其中最常用的是MethodInterceptor接口。MethodInterceptor接口定义了一个intercept()方法，用于实现代理逻辑：
>
> - MethodInterceptor（方法拦截器）：MethodInterceptor接口是CGLIB中最常用的Callback接口实现类，用于定义方法拦截逻辑。它定义了一个intercept()方法，用于在代理对象的方法执行前后插入自定义逻辑
> - NoOp（空操作）：NoOp是Callback接口的一个实现，它不进行任何操作，即空操作。当不需要对方法进行拦截和修改时，可以使用NoOp来避免不必要的开销和性能损耗
> - FixedValue（固定返回值）：FixedValue是Callback接口的另一个实现，它可以指定一个固定的返回值。当代理对象的方法被调用时，FixedValue会返回指定的固定值，而不会执行目标方法
> - Dispatcher（分派器）：Dispatcher是Callback接口的实现，用于根据方法签名将方法调用分派给不同的实现。它可以根据方法名称、参数类型等来选择具体的方法实现
> - LazyLoader（延迟加载）：LazyLoader是Callback接口的实现，用于实现延迟加载的功能。当代理对象的方法被调用时，LazyLoader会延迟加载目标对象，直到真正需要时才进行加载
> - InvocationHandler（调用处理器）：InvocationHandler是CGLIB中的一个Callback接口实现类，与Java标准库中的InvocationHandler接口相似。它定义了一个invoke()方法，用于在代理对象的方法执行前后插入自定义逻辑
> - LazyLoaderAdapter（延迟加载适配器）：LazyLoaderAdapter是Callback接口的实现，用于实现延迟加载的功能。它可以将LazyLoader接口的实现适配为Callback接口，以便在生成代理类时使用

Enhancer内部的核心属性

```java
private Class[] interfaces;			// 增强子类实现的接口列表
private CallbackFilter filter;		// 根据filter根据方法选择不同回调对象
private Callback[] callbacks;		// 回调对象列表
private Type[] callbackTypes;		// 回调对象类型列表
private boolean validateCallbackTypes;	// 是否已确定回调对象类型列表标识
private boolean classOnly;				// 是否仅返回Class对象，而不是实例化对象
private Class superclass;			// 增强子类继承的父类	
private Class[] argumentTypes;		// 父类构造器类型列表
private Object[] arguments;			// 父类构造器入参值
private boolean useFactory = true;	// 增强子类是否实现Factory接口
private Long serialVersionUID;		// 是否支持序列化操作
private boolean interceptDuringConstruction = true;	// 是否拦截构造方法
```

实际生成class的方法

```java
private Object createHelper() {
    preValidate();	// 前置校验
    // 通过KeyFactory机制根据父类、接口等关键特征生成Key，这个也是后续作为缓存得Key.
    Object key = KEY_FACTORY.newInstance((superclass != null) ? superclass.getName() : null,
            ReflectUtils.getNames(interfaces),
            filter == ALL_ZERO ? null : new WeakCacheKey<CallbackFilter>(filter),
            callbackTypes,
            useFactory,
            interceptDuringConstruction,
            serialVersionUID);
    this.currentKey = key;	// 赋值到ACG#key属性，后续缓存类会使用这个属性
    // 通过其父类的create()模版方法创建并返回生成类实例。
    Object result = super.create(key);
    return result;
}
```

ClassLoaderData是为AbstractClassGenerator的静态内部类，其主要维护了指定类加载器(classLoader)的生成类缓存。

```java
protected static class ClassLoaderData {
		/**
		*
		*每一个类加载器下生成类的类名不能重复。使用reservedClassNames存储所有生成类类名，并提供getUniqueNamePredicate()方法判定传入类名是否和之前冲突。
		*/
		private final Set<String> reservedClassNames = new HashSet<String>();

		/**
		 * {@link AbstractClassGenerator} here holds "cache key" (e.g. {@link org.springframework.cglib.proxy.Enhancer}
		 * configuration), and the value is the generated class plus some additional values
		 * (see {@link #unwrapCachedValue(Object)}.
		 * <p>The generated classes can be reused as long as their classloader is reachable.</p>
		 * <p>Note: the only way to access a class is to find it through generatedClasses cache, thus
		 * the key should not expire as long as the class itself is alive (its classloader is alive).</p>
		 generatedClasses就是缓存了所有当前类加载器下动态的生成类。LoadingCache和JDK代理中WeakCache功能类似。
		 */
		private final LoadingCache<AbstractClassGenerator, Object, Object> generatedClasses;

		/**
		 * Note: ClassLoaderData object is stored as a value of {@code WeakHashMap<ClassLoader, ...>} thus
		 * this classLoader reference should be weak otherwise it would make classLoader strongly reachable
		 * and alive forever.
		 * Reference queue is not required since the cleanup is handled by {@link WeakHashMap}.
		 类加载器。由于外层ACG#CACHE使用WeakHashMap（以classLoader为Key，以ClassLoaderData为Value）进行缓存，而WeakHashMap是将Key使用弱引用包装，但是Value仍是强引用。因此这里的ClassLoaderData#classLoader必须使用弱引用包装，否则就无法GC清楚。
		 */
		private final WeakReference<ClassLoader> classLoader;

		private final Predicate uniqueNamePredicate = new Predicate() {
			public boolean evaluate(Object name) {
				return reservedClassNames.contains(name);
			}
		};
		// 获取key的函数逻辑 通过AbstractClassGenerator的实现类获取key
		private static final Function<AbstractClassGenerator, Object> GET_KEY = new Function<AbstractClassGenerator, Object>() {
			public Object apply(AbstractClassGenerator gen) {
				return gen.key;
			}
		};

		public ClassLoaderData(ClassLoader classLoader) {
                if (classLoader == null) {		 // 必须传入有效类类加载器
                    throw new IllegalArgumentException("classLoader == null is not yet supported");
                }
                // 初始化classLoader属性
                this.classLoader = new WeakReference<ClassLoader>(classLoader);
                /**
                * 这里的load就是真正生成类逻辑的函数式变量。
                * load会被传入LoadingCache中，在合适的地方触发load内部逻辑获取生成类并缓存起来！
                * 
                **/
                Function<AbstractClassGenerator, Object> load =
                        new Function<AbstractClassGenerator, Object>() {
                            public Object apply(AbstractClassGenerator gen) {
                                // 重点！AbstractClassGenerator的实现类来负责生成逻辑
                                Class klass = gen.generate(ClassLoaderData.this);
                                return gen.wrapCachedClass(klass);
                            }
                        };
                // 初始化generatedClasses属性，即创建LoadingCache缓存实例
                generatedClasses = new LoadingCache<AbstractClassGenerator, Object, Object>(GET_KEY, load);
		}
}
```

核心的类是Enhancer的父类AbstractClassGenerator

```java
/**
 * Abstract class for all code-generating CGLIB utilities.
 * In addition to caching generated classes for performance, it provides hooks for
 * customizing the <code>ClassLoader</code>, name of the generated class, and transformations
 * applied before generation.
 * 所有代码生成 CGLIB 实用程序的抽象类。除了缓存生成的类以提高性能外，它还提供了用于自定义 ClassLoader生成类的钩子，以及生成	之前应用的转换。
 */
//AbstractClassGenerator继承了ClassGenerator但没有实现
//AbstractClassGenerator只是一个模板类 具体实现交给子类
abstract public class AbstractClassGenerator<T> implements ClassGenerator {
    //核心方法 生成代理对象
   protected Object create(Object key) {
    try {
    	// 1. 获取生成类的类加载器 （内部支持子类自定义默认类加载器）
        ClassLoader loader = getClassLoader();
        // 2. 缓存 Key为类加载器 value为ClassLoaderData对象
        //	（内部维护有generatedClasses缓存）
        Map<ClassLoader, ClassLoaderData> cache = CACHE;
        ClassLoaderData data = cache.get(loader);
        if (data == null) {		
        	// 3. 当前类加载器缓存不存在时，DCL初始化对应的ClassLoaderData对象
            synchronized (AbstractClassGenerator.class) {
                cache = CACHE;
                data = cache.get(loader);
                if (data == null) {
                    Map<ClassLoader, ClassLoaderData> newCache = new WeakHashMap<ClassLoader, ClassLoaderData>(cache);
                    data = new ClassLoaderData(loader);
                    newCache.put(loader, data);
                    CACHE = newCache;
                }
            }
        }
        this.key = key;				// 4. 将key赋值属性key上
        // 5. 通过ClassLoaderData获取生成类对象
        Object obj = data.get(this, getUseCache());	
        if (obj instanceof Class) {
        	// 6. 如果是Class对象，就创建实例。具体创建过程由子类实现
            return firstInstance((Class) obj);
        }
        // 7. 如果是真实实例对象，就创建另外一个实例。具体创建过程由子类实现。
        return nextInstance(obj);
    } catch (RuntimeException e) {
        throw e;
    } catch (Error e) {
        throw e;
    } catch (Exception e) {
        throw new CodeGenerationException(e);
    }
}
}
//---------------------------------------------------------
//ClassLoaderData的get方法
//key 是Enhancer 也就是AbstractClassGenerator的实现类
  public V get(K key) {
        //调用的是之前新建ClassLoaderData的时候的函数接口GET_KEY
        //实际上就是取AbstractClassGenerator的实现类Enhancer的属性key
        KK cacheKey = this.keyMapper.apply(key);
        //通过取到的key去map里拿生成类
        Object v = this.map.get(cacheKey);
        //拿不到就自己生成
        return v != null && !(v instanceof FutureTask) ? v : this.createEntry(key, cacheKey, v);
    }
//---------------------------------------------------------
//生成 代理类的实际方法
//key是AbstractClassGenerator的实现类 cacheKey是属性key v是取到的生成类
    protected V createEntry(final K key, KK cacheKey, Object v) {
        boolean creator = false;
        FutureTask task;
        Object result;
        if (v != null) {
            task = (FutureTask)v;
        } else {
            //如果v == null 没有取到 那么就创建一个task
            task = new FutureTask(new Callable<V>() {
                public V call() throws Exception {
                    //调用loader那个函数方法 key是Enhancer AbstractClassGenerator的实现类
                    return LoadingCache.this.loader.apply(key);
                }
            });
            //map 里放的是 属性key 和 task
            result = this.map.putIfAbsent(cacheKey, task);
            if (result == null) {
                creator = true;
                task.run();
            } else {
                if (!(result instanceof FutureTask)) {
                    return result;
                }

                task = (FutureTask)result;
            }
        }

        try {
            result = task.get();
        } catch (InterruptedException var9) {
            throw new IllegalStateException("Interrupted while loading cache item", var9);
        } catch (ExecutionException var10) {
            Throwable cause = var10.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException)cause;
            }

            throw new IllegalStateException("Unable to load cache item", cause);
        }

        if (creator) {
            this.map.put(cacheKey, result);
        }

        return result;
    }
//---------------------------------------------------
//位于Enhancer的实际生成类的逻辑
	@Override
	protected Class generate(ClassLoaderData data) {
        //校验一下属性参数 
		validate();
        //如果父类不为null
		if (superclass != null) {
            //将父类的全路径名称设置为生成类的前缀
			setNamePrefix(superclass.getName());
		}
		else if (interfaces != null) {
			setNamePrefix(interfaces[ReflectUtils.findPackageProtected(interfaces)].getName());
		}
        // 实际生成方法 调回到AbstractClassGenarate
		return super.generate(data);
	}
//-----------------------------------------------------
//位于AbstractClassGenarate 的generate方法
protected Class generate(ClassLoaderData data) {
		Class gen;
    	//从ThreadLocal中获取AbstractClassGenarate实现类的对象
		Object save = CURRENT.get();
         //把这个AbstractClassGenarate实现类存进去 也就是Enhancer
		CURRENT.set(this);
		try {
             //从ClassLoaderData中获取类加载器
			ClassLoader classLoader = data.getClassLoader();
			if (classLoader == null) {
				throw new IllegalStateException("ClassLoader is null while trying to define class " +
						getClassName() + ". It seems that the loader has been expired from a weak reference somehow. " +
						"Please file an issue at cglib's issue tracker.");
			}
			synchronized (classLoader) {
                //生成类的名称
                //简而言之 生成逻辑是 父类全类名+$$+ACG实现类名称+Tag+hashcode
                //  String base = prefix + "$$" + source.substring(source.lastIndexOf(46) + 1) + this.getTag() + "$$" + Integer.toHexString(STRESS_HASH_CODE ? 0 : key.hashCode());
				String name = generateClassName(data.getUniqueNamePredicate());
                 //ClassLoaderData存储生成类的名称
				data.reserveName(name);
                //Enhancer设置类名
				this.setClassName(name);
			}
            //如果尝试加载 那么就直接去类加载器加载这个class
			if (attemptLoad) {
				try {
					gen = classLoader.loadClass(getClassName());
					return gen;
				}
				catch (ClassNotFoundException e) {
					// ignore
				}
			}
            //根据策略生成class字节码数组
			byte[] b = strategy.generate(this);
			String className = ClassNameReader.getClassName(new ClassReader(b));
			ProtectionDomain protectionDomain = getProtectionDomain();
			synchronized (classLoader) { // just in case
				// SPRING PATCH BEGIN
				gen = ReflectUtils.defineClass(className, b, classLoader, protectionDomain, contextClass);
				// SPRING PATCH END
			}
			return gen;
		}
		catch (RuntimeException | Error ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new CodeGenerationException(ex);
		}
		finally {
			CURRENT.set(save);
		}
	}
//--------------------------------------------------------------------
// ClassLoaderAwareGeneratorStrategy 里面的生成字节码方法
@Override
	public byte[] generate(ClassGenerator cg) throws Exception {
		if (this.classLoader == null) {
			return super.generate(cg);
		}
		//获取当前线程
		Thread currentThread = Thread.currentThread();
		ClassLoader threadContextClassLoader;
		try {
             //获得当前线程的类加载器
			threadContextClassLoader = currentThread.getContextClassLoader();
		}
		catch (Throwable ex) {
			// Cannot access thread context ClassLoader - falling back...
			return super.generate(cg);
		}
		//看看指定的类加载器是否被覆盖
		boolean overrideClassLoader = !this.classLoader.equals(threadContextClassLoader);
		if (overrideClassLoader) {
             //如果被覆盖 那么就需要对当前线程进行重新赋值
			currentThread.setContextClassLoader(this.classLoader);
		}
		try {
             //调用父类DefaultGeneratorStrategy的生成方法
			return super.generate(cg);
		}
		finally {
			if (overrideClassLoader) {
				// Reset original thread context ClassLoader.
				currentThread.setContextClassLoader(threadContextClassLoader);
			}
		}
	}
//--------------------------------------------------------
//DefaultGeneratorStrategy的生成方法
 public byte[] generate(ClassGenerator cg) throws Exception {
        //DebuggingClassWriter是ClassVisitor的子类 getClassVisitor就是new DebuggingClassWriter(2);
        // ClassVisitor是操作字节码的核心类
        DebuggingClassWriter cw = this.getClassVisitor();
        // transform 就是把Enhancer强转成了ClassGenerator 调用generateClass(cw)方法
        this.transform(cg).generateClass(cw);
        return this.transform(cw.toByteArray());
    }
//-----------------------------------------------------------
//Enhancer内部的生成方法

public void generateClass(ClassVisitor v) throws Exception {
         //拿到superclass 的class对象
		Class sc = (superclass == null) ? Object.class : superclass;
		//看看是不是final关键字修饰
		if (TypeUtils.isFinal(sc.getModifiers()))
			throw new IllegalArgumentException("Cannot subclass final class " + sc.getName());
         //获取父类的默认构造方法
		List constructors = new ArrayList(Arrays.asList(sc.getDeclaredConstructors()));
         //获取父类所有的声明构造器，且默认过滤掉私有构造器
		filterConstructors(sc, constructors);

		// Order is very important: must add superclass, then
		// its superclass chain, then each interface and
		// its superinterfaces.
     	/**
        * 获取需要实现或重写的所有的方法actualMethods
        * interfaceMethods所有接口方法，
          forcePublic存储Public方法的签名Key
        * getMethods内部会过滤掉静态、私有、final修饰的方法，也会根据方法签名Key去重(KEY_FACTORY)
        **/
		List actualMethods = new ArrayList();
		List interfaceMethods = new ArrayList();
		final Set forcePublic = new HashSet();
		getMethods(sc, interfaces, actualMethods, interfaceMethods, forcePublic);

		List methods = CollectionUtils.transform(actualMethods, new Transformer() {
			public Object transform(Object value) {
				Method method = (Method) value;
				int modifiers = Constants.ACC_FINAL
						| (method.getModifiers()
						& ~Constants.ACC_ABSTRACT
						& ~Constants.ACC_NATIVE
						& ~Constants.ACC_SYNCHRONIZED);
				if (forcePublic.contains(MethodWrapper.create(method))) {
					modifiers = (modifiers & ~Constants.ACC_PROTECTED) | Constants.ACC_PUBLIC;
				}
				return ReflectUtils.getMethodInfo(method, modifiers);
			}
		});

		        ClassEmitter e = new ClassEmitter(v);
        if (currentData == null) {
        	/**
        	* 生成类的版本、访问权限、类名、父类类型、接口列表，来源
        	* useFactory属性决定了生成类是否继承Factory接口
        	**/
        	e.begin_class(Constants.V1_8,
                      Constants.ACC_PUBLIC,
                      getClassName(),
                      Type.getType(sc),
                      (useFactory ?
                       TypeUtils.add(TypeUtils.getTypes(interfaces), FACTORY) :
                       TypeUtils.getTypes(interfaces)),
                      Constants.SOURCE_FILE);
        } else {
            e.begin_class(Constants.V1_8,
                    Constants.ACC_PUBLIC,
                    getClassName(),
                    null,
                    new Type[]{FACTORY},
                    Constants.SOURCE_FILE);
        }
        // 将构造方法转换为了MethodInfo对象
        List constructorInfo = CollectionUtils.transform(constructors, MethodInfoTransformer.getInstance());

		// 声明动态类中私有CGLIB$BOUND布尔类型属性
        e.declare_field(Constants.ACC_PRIVATE, BOUND_FIELD, Type.BOOLEAN_TYPE, null);		
        // 声明动态类中公共静态CGLIB$FACTORY_DATA类型为Object属性
        e.declare_field(Constants.ACC_PUBLIC | Constants.ACC_STATIC, FACTORY_DATA_FIELD, OBJECT_TYPE, null);
        if (!interceptDuringConstruction) {
        	// 如果设置interceptDuringConstruction属性为false，会声明动态类中私有CGLIB$CONSTRUCTED属性
            e.declare_field(Constants.ACC_PRIVATE, CONSTRUCTED_FIELD, Type.BOOLEAN_TYPE, null);
        }
        // 声明动态类中私有静态final的CGLIB$THREAD_CALLBACKS类型为ThreadLocal属性
        e.declare_field(Constants.PRIVATE_FINAL_STATIC, THREAD_CALLBACKS_FIELD, THREAD_LOCAL, null);
        // 声明动态类中私有静态final的CGLIB$STATIC_CALLBACKS类型为Callback属性
        e.declare_field(Constants.PRIVATE_FINAL_STATIC, STATIC_CALLBACKS_FIELD, CALLBACK_ARRAY, null);
        if (serialVersionUID != null) {
        	// 声明动态类中私有静态final的serialVersionUID类型为Long属性，并赋值serialVersionUID
            e.declare_field(Constants.PRIVATE_FINAL_STATIC, Constants.SUID_FIELD_NAME, Type.LONG_TYPE, serialVersionUID);
        }

        for (int i = 0; i < callbackTypes.length; i++) {
       	 	// 声明动态类中私有的CGLIB$CALLBACK_{index}回调类型属性
            e.declare_field(Constants.ACC_PRIVATE, getCallbackField(i), callbackTypes[i], null);
        }
        // 声明动态类中静态私有的CGLIB$CALLBACK_FILTER属性，类型为Object
        e.declare_field(Constants.ACC_PRIVATE | Constants.ACC_STATIC, CALLBACK_FILTER_FIELD, OBJECT_TYPE, null);

        if (currentData == null) {
			// 产生生成类中所有方法
            emitMethods(e, methods, actualMethods);
            // 产生生成类中所有构造方法
            emitConstructors(e, constructorInfo);
        } else {
            emitDefaultConstructor(e);
        }
        // 生成公开静态方法CGLIB$SET_THREAD_CALLBACKS()方法
        emitSetThreadCallbacks(e);
        // 生成公开静态的CGLIB$SET_STATIC_CALLBACKS()方法
        emitSetStaticCallbacks(e);
        // 生成私有静态final的CGLIB$BIND_CALLBACKS()方法
        emitBindCallbacks(e);

		/**
		* 下面是生成Factory的实现方法
		**/
        if (useFactory || currentData != null) {
            int[] keys = getCallbackKeys();
            emitNewInstanceCallbacks(e);
            emitNewInstanceCallback(e);
            emitNewInstanceMultiarg(e, constructorInfo);
            emitGetCallback(e, keys);
            emitSetCallback(e, keys);
            emitGetCallbacks(e);
            emitSetCallbacks(e);
        }

        e.end_class();
	}
```

看一下生成的字节码文件 

> System.setProperty ( DebuggingClassWriter.DEBUG_LOCATION_PROPERTY,"/Users/spl/own/mavenTest" );
> 配置DebuggingClassWriter.DEBUG_LOCATION_PROPERTY属性，cglib会将动态生成的类保存在指定目录下。
>
> - Student$$EnhancerByCGLIB$86327ae0
>
>   生成并写入文件的增强代理类。
>
> - Student$$EnhancerByCGLIB$2838b21a$FastClassByCGLIB$594c36f
>
>   前缀和增强代理类相同，后面是FastClass类的标志，因此该文件是调用代理方法后生成的代理类的FastClass子类。
>
>   FastClass是为了避免反射获取类方法而设计的，内部维护索引到各个方法的映射，加速了CGLIB代理方法的执行速度。
>
> - Student$$FastClassByCGLIB$53ab8f74
>
>   如上规律，该文件时调用代理方法后生成的被代理类的FastClass子类。功能如上。
>
> FastClass子类及其对象都是在调用对应代理方法时才会被创建、实例化。

贴一张Factory接口的图片

<img src=".\images\image-20240131094507649.png" alt="image-20240131094507649" style="zoom: 80%;" />

```java
/*
* SpringProxy和Advised接口是加上去的？
* 代理类直接继承了被代理类，而且实现了Factory接口。所有被Enhancer类返回的增强代理类均会实现Factory接口。Factory接口提供了一	系列创建新增强代理对象的newInstance()方法，且支持替换之前指定的Callbacks列表
*
*/
public class BookDao$$EnhancerBySpringCGLIB$$59fb3d0c extends BookDao implements SpringProxy, Advised, Factory {
    //标识当前增强代理对象是否已绑定回调对象（Callback）。增强代理类支持修改回调对象，且具备有限线程上下文中的回调对象，因此当调用任何公开方法时，都会根据此表示判断回调对象是否已绑定在下面这个回调对象属性上。
    private boolean CGLIB$BOUND;
    
    public static Object CGLIB$FACTORY_DATA;
    
    //存放在ThreadLocal里面的callback列表
    private static final ThreadLocal CGLIB$THREAD_CALLBACKS;
    //存放在静态属性中的callback列表
    private static final Callback[] CGLIB$STATIC_CALLBACKS;
    
    //回调对象。允许有多个，这个属性指向了第index个回调对象。
    //MethodInterceptor，NoOp，Dispatcher都是Callback的子接口
    private MethodInterceptor CGLIB$CALLBACK_0;
    private MethodInterceptor CGLIB$CALLBACK_1;
    private NoOp CGLIB$CALLBACK_2;
    private Dispatcher CGLIB$CALLBACK_3;
    private Dispatcher CGLIB$CALLBACK_4;
    private MethodInterceptor CGLIB$CALLBACK_5;
    private MethodInterceptor CGLIB$CALLBACK_6;
    
     //当方法参数为空时，会传递该空数组给回调方法(intecept)上。
    private static final Object[] CGLIB$emptyArgs;
    
    private static Object CGLIB$CALLBACK_FILTER;
    
    //(n+4)组类方法，n为被代理对象方法个数 +4是equals toString hashCode 和 clone
    //被代理方法对象以及代理方法对象会作为第二、第四个参数传给回调函数的intercep(…)方法。
    private static final Method CGLIB$updateStock$0$Method; // 被代理方法
    private static final MethodProxy CGLIB$updateStock$0$Proxy; //代理方法
    private static final Method CGLIB$getJdbcTemplate$1$Method;
    private static final MethodProxy CGLIB$getJdbcTemplate$1$Proxy;
    private static final Method CGLIB$setJdbcTemplate$2$Method;
    private static final MethodProxy CGLIB$setJdbcTemplate$2$Proxy;
    private static final Method CGLIB$updateBalance$3$Method;
    private static final MethodProxy CGLIB$updateBalance$3$Proxy;
    private static final Method CGLIB$getPrice$4$Method;
    private static final MethodProxy CGLIB$getPrice$4$Proxy;
    private static final Method CGLIB$equals$5$Method;
    private static final MethodProxy CGLIB$equals$5$Proxy;
    private static final Method CGLIB$toString$6$Method;
    private static final MethodProxy CGLIB$toString$6$Proxy;
    private static final Method CGLIB$hashCode$7$Method;
    private static final MethodProxy CGLIB$hashCode$7$Proxy;
    private static final Method CGLIB$clone$8$Method;
    private static final MethodProxy CGLIB$clone$8$Proxy;

    static void CGLIB$STATICHOOK1() {
        CGLIB$THREAD_CALLBACKS = new ThreadLocal();
        CGLIB$emptyArgs = new Object[0];
        Class var0 = Class.forName("com.example.demo.tx.xml.dao.BookDao$$EnhancerBySpringCGLIB$$59fb3d0c");
        Class var1;
        Method[] var10000 = ReflectUtils.findMethods(new String[]{"equals", "(Ljava/lang/Object;)Z", "toString", "()Ljava/lang/String;", "hashCode", "()I", "clone", "()Ljava/lang/Object;"}, (var1 = Class.forName("java.lang.Object")).getDeclaredMethods());
        CGLIB$equals$5$Method = var10000[0];
        CGLIB$equals$5$Proxy = MethodProxy.create(var1, var0, "(Ljava/lang/Object;)Z", "equals", "CGLIB$equals$5");
        CGLIB$toString$6$Method = var10000[1];
        CGLIB$toString$6$Proxy = MethodProxy.create(var1, var0, "()Ljava/lang/String;", "toString", "CGLIB$toString$6");
        CGLIB$hashCode$7$Method = var10000[2];
        CGLIB$hashCode$7$Proxy = MethodProxy.create(var1, var0, "()I", "hashCode", "CGLIB$hashCode$7");
        CGLIB$clone$8$Method = var10000[3];
        CGLIB$clone$8$Proxy = MethodProxy.create(var1, var0, "()Ljava/lang/Object;", "clone", "CGLIB$clone$8");
        var10000 = ReflectUtils.findMethods(new String[]{"updateStock", "(I)V", "getJdbcTemplate", "()Lorg/springframework/jdbc/core/JdbcTemplate;", "setJdbcTemplate", "(Lorg/springframework/jdbc/core/JdbcTemplate;)V", "updateBalance", "(Ljava/lang/String;I)V", "getPrice", "(I)I"}, (var1 = Class.forName("com.example.demo.tx.xml.dao.BookDao")).getDeclaredMethods());
        CGLIB$updateStock$0$Method = var10000[0];
        CGLIB$updateStock$0$Proxy = MethodProxy.create(var1, var0, "(I)V", "updateStock", "CGLIB$updateStock$0");
        CGLIB$getJdbcTemplate$1$Method = var10000[1];
        CGLIB$getJdbcTemplate$1$Proxy = MethodProxy.create(var1, var0, "()Lorg/springframework/jdbc/core/JdbcTemplate;", "getJdbcTemplate", "CGLIB$getJdbcTemplate$1");
        CGLIB$setJdbcTemplate$2$Method = var10000[2];
        CGLIB$setJdbcTemplate$2$Proxy = MethodProxy.create(var1, var0, "(Lorg/springframework/jdbc/core/JdbcTemplate;)V", "setJdbcTemplate", "CGLIB$setJdbcTemplate$2");
        CGLIB$updateBalance$3$Method = var10000[3];
        CGLIB$updateBalance$3$Proxy = MethodProxy.create(var1, var0, "(Ljava/lang/String;I)V", "updateBalance", "CGLIB$updateBalance$3");
        CGLIB$getPrice$4$Method = var10000[4];
        CGLIB$getPrice$4$Proxy = MethodProxy.create(var1, var0, "(I)I", "getPrice", "CGLIB$getPrice$4");
    }

    final void CGLIB$updateStock$0(int var1) {
        super.updateStock(var1);
    }

    public final void updateStock(int var1) {
        MethodInterceptor var10000 = this.CGLIB$CALLBACK_0;
        if (var10000 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_0;
        }

        if (var10000 != null) {
            var10000.intercept(this, CGLIB$updateStock$0$Method, new Object[]{new Integer(var1)}, CGLIB$updateStock$0$Proxy);
        } else {
            super.updateStock(var1);
        }
    }

    final JdbcTemplate CGLIB$getJdbcTemplate$1() {
        return super.getJdbcTemplate();
    }

    public final JdbcTemplate getJdbcTemplate() {
        MethodInterceptor var10000 = this.CGLIB$CALLBACK_0;
        if (var10000 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_0;
        }

        return var10000 != null ? (JdbcTemplate)var10000.intercept(this, CGLIB$getJdbcTemplate$1$Method, CGLIB$emptyArgs, CGLIB$getJdbcTemplate$1$Proxy) : super.getJdbcTemplate();
    }

    final void CGLIB$setJdbcTemplate$2(JdbcTemplate var1) {
        super.setJdbcTemplate(var1);
    }

    public final void setJdbcTemplate(JdbcTemplate var1) {
        MethodInterceptor var10000 = this.CGLIB$CALLBACK_0;
        if (var10000 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_0;
        }

        if (var10000 != null) {
            var10000.intercept(this, CGLIB$setJdbcTemplate$2$Method, new Object[]{var1}, CGLIB$setJdbcTemplate$2$Proxy);
        } else {
            super.setJdbcTemplate(var1);
        }
    }

    final void CGLIB$updateBalance$3(String var1, int var2) {
        super.updateBalance(var1, var2);
    }

    public final void updateBalance(String var1, int var2) {
        MethodInterceptor var10000 = this.CGLIB$CALLBACK_0;
        if (var10000 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_0;
        }

        if (var10000 != null) {
            var10000.intercept(this, CGLIB$updateBalance$3$Method, new Object[]{var1, new Integer(var2)}, CGLIB$updateBalance$3$Proxy);
        } else {
            super.updateBalance(var1, var2);
        }
    }

    final int CGLIB$getPrice$4(int var1) {
        return super.getPrice(var1);
    }

    public final int getPrice(int var1) {
        MethodInterceptor var10000 = this.CGLIB$CALLBACK_0;
        if (var10000 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_0;
        }

        if (var10000 != null) {
            Object var2 = var10000.intercept(this, CGLIB$getPrice$4$Method, new Object[]{new Integer(var1)}, CGLIB$getPrice$4$Proxy);
            return var2 == null ? 0 : ((Number)var2).intValue();
        } else {
            return super.getPrice(var1);
        }
    }

    final boolean CGLIB$equals$5(Object var1) {
        return super.equals(var1);
    }

    public final boolean equals(Object var1) {
        MethodInterceptor var10000 = this.CGLIB$CALLBACK_5;
        if (var10000 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_5;
        }

        if (var10000 != null) {
            Object var2 = var10000.intercept(this, CGLIB$equals$5$Method, new Object[]{var1}, CGLIB$equals$5$Proxy);
            return var2 == null ? false : (Boolean)var2;
        } else {
            return super.equals(var1);
        }
    }

    final String CGLIB$toString$6() {
        return super.toString();
    }

    public final String toString() {
        MethodInterceptor var10000 = this.CGLIB$CALLBACK_0;
        if (var10000 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_0;
        }

        return var10000 != null ? (String)var10000.intercept(this, CGLIB$toString$6$Method, CGLIB$emptyArgs, CGLIB$toString$6$Proxy) : super.toString();
    }

    final int CGLIB$hashCode$7() {
        return super.hashCode();
    }

    public final int hashCode() {
        MethodInterceptor var10000 = this.CGLIB$CALLBACK_6;
        if (var10000 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_6;
        }

        if (var10000 != null) {
            Object var1 = var10000.intercept(this, CGLIB$hashCode$7$Method, CGLIB$emptyArgs, CGLIB$hashCode$7$Proxy);
            return var1 == null ? 0 : ((Number)var1).intValue();
        } else {
            return super.hashCode();
        }
    }

    final Object CGLIB$clone$8() throws CloneNotSupportedException {
        return super.clone();
    }

    protected final Object clone() throws CloneNotSupportedException {
        MethodInterceptor var10000 = this.CGLIB$CALLBACK_0;
        if (var10000 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_0;
        }

        return var10000 != null ? var10000.intercept(this, CGLIB$clone$8$Method, CGLIB$emptyArgs, CGLIB$clone$8$Proxy) : super.clone();
    }

    public static MethodProxy CGLIB$findMethodProxy(Signature var0) {
        String var10000 = var0.toString();
        switch (var10000.hashCode()) {
            case -2136695756:
                if (var10000.equals("getPrice(I)I")) {
                    return CGLIB$getPrice$4$Proxy;
                }
                break;
            case -1822721157:
                if (var10000.equals("updateStock(I)V")) {
                    return CGLIB$updateStock$0$Proxy;
                }
                break;
            case -1245458157:
                if (var10000.equals("setJdbcTemplate(Lorg/springframework/jdbc/core/JdbcTemplate;)V")) {
                    return CGLIB$setJdbcTemplate$2$Proxy;
                }
                break;
            case -508378822:
                if (var10000.equals("clone()Ljava/lang/Object;")) {
                    return CGLIB$clone$8$Proxy;
                }
                break;
            case 145057057:
                if (var10000.equals("getJdbcTemplate()Lorg/springframework/jdbc/core/JdbcTemplate;")) {
                    return CGLIB$getJdbcTemplate$1$Proxy;
                }
                break;
            case 1023777399:
                if (var10000.equals("updateBalance(Ljava/lang/String;I)V")) {
                    return CGLIB$updateBalance$3$Proxy;
                }
                break;
            case 1826985398:
                if (var10000.equals("equals(Ljava/lang/Object;)Z")) {
                    return CGLIB$equals$5$Proxy;
                }
                break;
            case 1913648695:
                if (var10000.equals("toString()Ljava/lang/String;")) {
                    return CGLIB$toString$6$Proxy;
                }
                break;
            case 1984935277:
                if (var10000.equals("hashCode()I")) {
                    return CGLIB$hashCode$7$Proxy;
                }
        }

        return null;
    }

    public final int indexOf(Advice var1) {
        Dispatcher var10000 = this.CGLIB$CALLBACK_4;
        if (var10000 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_4;
        }

        return ((Advised)var10000.loadObject()).indexOf(var1);
    }

    public final int indexOf(Advisor var1) {
        Dispatcher var10000 = this.CGLIB$CALLBACK_4;
        if (var10000 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_4;
        }

        return ((Advised)var10000.loadObject()).indexOf(var1);
    }

    public final boolean isFrozen() {
        Dispatcher var10000 = this.CGLIB$CALLBACK_4;
        if (var10000 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_4;
        }

        return ((Advised)var10000.loadObject()).isFrozen();
    }

    public final void setTargetSource(TargetSource var1) {
        Dispatcher var10000 = this.CGLIB$CALLBACK_4;
        if (var10000 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_4;
        }

        ((Advised)var10000.loadObject()).setTargetSource(var1);
    }

    public final void setPreFiltered(boolean var1) {
        Dispatcher var10000 = this.CGLIB$CALLBACK_4;
        if (var10000 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_4;
        }

        ((Advised)var10000.loadObject()).setPreFiltered(var1);
    }

    public final boolean isProxyTargetClass() {
        Dispatcher var10000 = this.CGLIB$CALLBACK_4;
        if (var10000 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_4;
        }

        return ((Advised)var10000.loadObject()).isProxyTargetClass();
    }

    public final void setExposeProxy(boolean var1) {
        Dispatcher var10000 = this.CGLIB$CALLBACK_4;
        if (var10000 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_4;
        }

        ((Advised)var10000.loadObject()).setExposeProxy(var1);
    }

    public final boolean isExposeProxy() {
        Dispatcher var10000 = this.CGLIB$CALLBACK_4;
        if (var10000 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_4;
        }

        return ((Advised)var10000.loadObject()).isExposeProxy();
    }

    public final TargetSource getTargetSource() {
        Dispatcher var10000 = this.CGLIB$CALLBACK_4;
        if (var10000 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_4;
        }

        return ((Advised)var10000.loadObject()).getTargetSource();
    }

    public final Class[] getProxiedInterfaces() {
        Dispatcher var10000 = this.CGLIB$CALLBACK_4;
        if (var10000 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_4;
        }

        return ((Advised)var10000.loadObject()).getProxiedInterfaces();
    }

    public final String toProxyConfigString() {
        Dispatcher var10000 = this.CGLIB$CALLBACK_4;
        if (var10000 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_4;
        }

        return ((Advised)var10000.loadObject()).toProxyConfigString();
    }

    public final Advisor[] getAdvisors() {
        Dispatcher var10000 = this.CGLIB$CALLBACK_4;
        if (var10000 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_4;
        }

        return ((Advised)var10000.loadObject()).getAdvisors();
    }

    public final void addAdvisor(Advisor var1) throws AopConfigException {
        Dispatcher var10000 = this.CGLIB$CALLBACK_4;
        if (var10000 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_4;
        }

        ((Advised)var10000.loadObject()).addAdvisor(var1);
    }

    public final void addAdvisor(int var1, Advisor var2) throws AopConfigException {
        Dispatcher var10000 = this.CGLIB$CALLBACK_4;
        if (var10000 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_4;
        }

        ((Advised)var10000.loadObject()).addAdvisor(var1, var2);
    }

    public final boolean removeAdvice(Advice var1) {
        Dispatcher var10000 = this.CGLIB$CALLBACK_4;
        if (var10000 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_4;
        }

        return ((Advised)var10000.loadObject()).removeAdvice(var1);
    }

    public final boolean isInterfaceProxied(Class var1) {
        Dispatcher var10000 = this.CGLIB$CALLBACK_4;
        if (var10000 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_4;
        }

        return ((Advised)var10000.loadObject()).isInterfaceProxied(var1);
    }

    public final boolean isPreFiltered() {
        Dispatcher var10000 = this.CGLIB$CALLBACK_4;
        if (var10000 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_4;
        }

        return ((Advised)var10000.loadObject()).isPreFiltered();
    }

    public final void removeAdvisor(int var1) throws AopConfigException {
        Dispatcher var10000 = this.CGLIB$CALLBACK_4;
        if (var10000 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_4;
        }

        ((Advised)var10000.loadObject()).removeAdvisor(var1);
    }

    public final boolean removeAdvisor(Advisor var1) {
        Dispatcher var10000 = this.CGLIB$CALLBACK_4;
        if (var10000 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_4;
        }

        return ((Advised)var10000.loadObject()).removeAdvisor(var1);
    }

    public final boolean replaceAdvisor(Advisor var1, Advisor var2) throws AopConfigException {
        Dispatcher var10000 = this.CGLIB$CALLBACK_4;
        if (var10000 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_4;
        }

        return ((Advised)var10000.loadObject()).replaceAdvisor(var1, var2);
    }

    public final void addAdvice(int var1, Advice var2) throws AopConfigException {
        Dispatcher var10000 = this.CGLIB$CALLBACK_4;
        if (var10000 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_4;
        }

        ((Advised)var10000.loadObject()).addAdvice(var1, var2);
    }

    public final void addAdvice(Advice var1) throws AopConfigException {
        Dispatcher var10000 = this.CGLIB$CALLBACK_4;
        if (var10000 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_4;
        }

        ((Advised)var10000.loadObject()).addAdvice(var1);
    }

    public final Class getTargetClass() {
        Dispatcher var10000 = this.CGLIB$CALLBACK_4;
        if (var10000 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_4;
        }

        return ((TargetClassAware)var10000.loadObject()).getTargetClass();
    }

    public BookDao$$EnhancerBySpringCGLIB$$59fb3d0c() {
        CGLIB$BIND_CALLBACKS(this);
    }

    public static void CGLIB$SET_THREAD_CALLBACKS(Callback[] var0) {
        CGLIB$THREAD_CALLBACKS.set(var0);
    }

    public static void CGLIB$SET_STATIC_CALLBACKS(Callback[] var0) {
        CGLIB$STATIC_CALLBACKS = var0;
    }

    private static final void CGLIB$BIND_CALLBACKS(Object var0) {
        BookDao$$EnhancerBySpringCGLIB$$59fb3d0c var1 = (BookDao$$EnhancerBySpringCGLIB$$59fb3d0c)var0;
        if (!var1.CGLIB$BOUND) {
            var1.CGLIB$BOUND = true;
            Object var10000 = CGLIB$THREAD_CALLBACKS.get();
            if (var10000 == null) {
                var10000 = CGLIB$STATIC_CALLBACKS;
                if (var10000 == null) {
                    return;
                }
            }

            Callback[] var10001 = (Callback[])var10000;
            var1.CGLIB$CALLBACK_6 = (MethodInterceptor)((Callback[])var10000)[6];
            var1.CGLIB$CALLBACK_5 = (MethodInterceptor)var10001[5];
            var1.CGLIB$CALLBACK_4 = (Dispatcher)var10001[4];
            var1.CGLIB$CALLBACK_3 = (Dispatcher)var10001[3];
            var1.CGLIB$CALLBACK_2 = (NoOp)var10001[2];
            var1.CGLIB$CALLBACK_1 = (MethodInterceptor)var10001[1];
            var1.CGLIB$CALLBACK_0 = (MethodInterceptor)var10001[0];
        }

    }

    public Object newInstance(Callback[] var1) {
        CGLIB$SET_THREAD_CALLBACKS(var1);
        BookDao$$EnhancerBySpringCGLIB$$59fb3d0c var10000 = new BookDao$$EnhancerBySpringCGLIB$$59fb3d0c();
        CGLIB$SET_THREAD_CALLBACKS((Callback[])null);
        return var10000;
    }

    public Object newInstance(Callback var1) {
        throw new IllegalStateException("More than one callback object required");
    }

    public Object newInstance(Class[] var1, Object[] var2, Callback[] var3) {
        CGLIB$SET_THREAD_CALLBACKS(var3);
        BookDao$$EnhancerBySpringCGLIB$$59fb3d0c var10000 = new BookDao$$EnhancerBySpringCGLIB$$59fb3d0c;
        switch (var1.length) {
            case 0:
                var10000.<init>();
                CGLIB$SET_THREAD_CALLBACKS((Callback[])null);
                return var10000;
            default:
                throw new IllegalArgumentException("Constructor not found");
        }
    }

    public Callback getCallback(int var1) {
        CGLIB$BIND_CALLBACKS(this);
        Object var10000;
        switch (var1) {
            case 0:
                var10000 = this.CGLIB$CALLBACK_0;
                break;
            case 1:
                var10000 = this.CGLIB$CALLBACK_1;
                break;
            case 2:
                var10000 = this.CGLIB$CALLBACK_2;
                break;
            case 3:
                var10000 = this.CGLIB$CALLBACK_3;
                break;
            case 4:
                var10000 = this.CGLIB$CALLBACK_4;
                break;
            case 5:
                var10000 = this.CGLIB$CALLBACK_5;
                break;
            case 6:
                var10000 = this.CGLIB$CALLBACK_6;
                break;
            default:
                var10000 = null;
        }

        return (Callback)var10000;
    }

    public void setCallback(int var1, Callback var2) {
        switch (var1) {
            case 0:
                this.CGLIB$CALLBACK_0 = (MethodInterceptor)var2;
                break;
            case 1:
                this.CGLIB$CALLBACK_1 = (MethodInterceptor)var2;
                break;
            case 2:
                this.CGLIB$CALLBACK_2 = (NoOp)var2;
                break;
            case 3:
                this.CGLIB$CALLBACK_3 = (Dispatcher)var2;
                break;
            case 4:
                this.CGLIB$CALLBACK_4 = (Dispatcher)var2;
                break;
            case 5:
                this.CGLIB$CALLBACK_5 = (MethodInterceptor)var2;
                break;
            case 6:
                this.CGLIB$CALLBACK_6 = (MethodInterceptor)var2;
        }

    }

    public Callback[] getCallbacks() {
        CGLIB$BIND_CALLBACKS(this);
        return new Callback[]{this.CGLIB$CALLBACK_0, this.CGLIB$CALLBACK_1, this.CGLIB$CALLBACK_2, this.CGLIB$CALLBACK_3, this.CGLIB$CALLBACK_4, this.CGLIB$CALLBACK_5, this.CGLIB$CALLBACK_6};
    }

    public void setCallbacks(Callback[] var1) {
        this.CGLIB$CALLBACK_0 = (MethodInterceptor)var1[0];
        this.CGLIB$CALLBACK_1 = (MethodInterceptor)var1[1];
        this.CGLIB$CALLBACK_2 = (NoOp)var1[2];
        this.CGLIB$CALLBACK_3 = (Dispatcher)var1[3];
        this.CGLIB$CALLBACK_4 = (Dispatcher)var1[4];
        this.CGLIB$CALLBACK_5 = (MethodInterceptor)var1[5];
        this.CGLIB$CALLBACK_6 = (MethodInterceptor)var1[6];
    }

    static {
        CGLIB$STATICHOOK1();
    }
}

```





### 剖析AOP的整个过程

AOP的整个过程，可以分为三个过程

#### 解析创建切面的BeanDefinition

基于xml或者注解方式的结果是相同的，都是注入beanDefinition，只不过注入beanDefinition的方式不同，

##### xml方式

xml的方式是在refresh()方法的obtainFreshBeanFactory()里面对xml文件进行解析和loadBeanDefinitions()

一些关键的解析代码如下

```java
//此部分属于ioc的代码 Element封装了整个xml的所有元素标签 BeanDefinitionParserDelegate则是对应标签的代理解析类
protected void parseBeanDefinitions(Element root, BeanDefinitionParserDelegate delegate) {
		if (delegate.isDefaultNamespace(root)) {
			NodeList nl = root.getChildNodes();
			for (int i = 0; i < nl.getLength(); i++) {
				Node node = nl.item(i);
                //可能会出现换行符 这里就需要进行校验node的类型
				if (node instanceof Element) {
					Element ele = (Element) node;
                    //默认的namespace是spring自带的http://www.springframework.org/schema/beans这个
					if (delegate.isDefaultNamespace(ele)) {
						parseDefaultElement(ele, delegate);
					}
                    //其他的比如context、aop的namespace在spring眼里都属于自定义的
					else {
						delegate.parseCustomElement(ele);
					}
				}
			}
		}
		else {
			delegate.parseCustomElement(root);
		}
	}
```

找到了aop的标签 接下来要去找对应的aop标签的解析类

![image-20240119090132037](.\images\image-20240119090132037.png)

![image-20240119090154559](.\images\image-20240119090154559.png)

```java
	@Nullable
	public BeanDefinition parseCustomElement(Element ele, @Nullable BeanDefinition containingBd) {
        //获得这个标签对应的namespace aop对应的是http://www.springframework.org/schema/aop
		String namespaceUri = getNamespaceURI(ele);
		if (namespaceUri == null) {
			return null;
		}
        //从META-INF/spring.handlers里面找到对应namespace的解析类
		NamespaceHandler handler = this.readerContext.getNamespaceHandlerResolver().resolve(namespaceUri);
		if (handler == null) {
			error("Unable to locate Spring NamespaceHandler for XML schema namespace [" + namespaceUri + "]", ele);
			return null;
		}
		return handler.parse(ele, new ParserContext(this.readerContext, this, containingBd));
	}
```

aop的对应标签解析类是

```java
public class AopNamespaceHandler extends NamespaceHandlerSupport {

	/**
	 * Register the {@link BeanDefinitionParser BeanDefinitionParsers} for the
	 * '{@code config}', '{@code spring-configured}', '{@code aspectj-autoproxy}'
	 * and '{@code scoped-proxy}' tags.
	 */
	@Override
	public void init() {
        
        //对于aop的xml方式来说 最重要的标签解析就是ConfigBeanDefinitionParser 这个类负责对切面和通知的解析
        //并且还进行了AspectJAwareAdvisorAutoProxyCreator的注册工作
        //对于aop的xml+注解混用的方式来说 AspectJAutoProxyBeanDefinitionParser 这个类负责解析aspectj-autoproxy标签 可实际的解析@Aspect等注解的工作并不在这里 而是注册了 AnnotationAwareAspectJAutoProxyCreator 后在后续BPP环节执行
        
        
		// In 2.0 XSD as well as in 2.5+ XSDs
        //注册了对<aop:config>标签的解析器
		registerBeanDefinitionParser("config", new ConfigBeanDefinitionParser());
        //注册了对<aop:aspectj-autoproxy>标签的解析器
		registerBeanDefinitionParser("aspectj-autoproxy", new AspectJAutoProxyBeanDefinitionParser());
        //注册了对<aop:scoped-proxy>标签的解析器
		registerBeanDefinitionDecorator("scoped-proxy", new ScopedProxyBeanDefinitionDecorator());
		 //注册了对<aop:spring-configured>标签的解析器
		// Only in 2.0 XSD: moved to context namespace in 2.5+
		registerBeanDefinitionParser("spring-configured", new SpringConfiguredBeanDefinitionParser());
	}

}

```

进入ConfigBeanDefinitionParser的解析类中

```java

	@Override
	@Nullable
	public BeanDefinition parse(Element element, ParserContext parserContext) {
        //创建一个复合组件定义
		CompositeComponentDefinition compositeDef =
				new CompositeComponentDefinition(element.getTagName(), parserContext.extractSource(element));
		//进行压栈操作
        parserContext.pushContainingComponent(compositeDef);
		//配置AutoProxyCreator
		configureAutoProxyCreator(parserContext, element);
		//获取下一级所有子标签 也就是  <aop:aspect ref="logUtil">
		List<Element> childElts = DomUtils.getChildElements(element);
		for (Element elt: childElts) {
			String localName = parserContext.getDelegate().getLocalName(elt);
			if (POINTCUT.equals(localName)) {
				parsePointcut(elt, parserContext);
			}
			else if (ADVISOR.equals(localName)) {
				parseAdvisor(elt, parserContext);
			}
            //对切面标签做解析
			else if (ASPECT.equals(localName)) {
				parseAspect(elt, parserContext);
			}
		}
		//进行出栈操作
		parserContext.popAndRegisterContainingComponent();
		return null;
	}

//---------------------------------
//这里是configureAutoProxyCreator()方法的实际调用

public static void registerAspectJAutoProxyCreatorIfNecessary(
			ParserContext parserContext, Element sourceElement) {
   		 //注册内部类org.springframework.aop.config.internalAutoProxyCreator
		//这里封装注册了AspectJAwareAdvisorAutoProxyCreator.class的beanDefinition
		BeanDefinition beanDefinition = AopConfigUtils.registerAspectJAutoProxyCreatorIfNecessary(
				parserContext.getRegistry(), parserContext.extractSource(sourceElement));
    	//处理一些标签的属性
		useClassProxyingIfNecessary(parserContext.getRegistry(), sourceElement);
    	
		registerComponentIfNecessary(beanDefinition, parserContext);
	}
//-----------------------------
//useClassProxyingIfNecessary()内部

private static void useClassProxyingIfNecessary(BeanDefinitionRegistry registry, @Nullable Element sourceElement) {
	//	sourceElement指的是<aop:config>的标签
    if (sourceElement != null) {
        //这里是查看标签是否配置了proxy-target-class属性并作解析 
        // <aop:config proxy-target-class="true"> 代表代理目标类 也就是cglib代理 
        // 如果不设置默认为false 并且此类没有接口 则基于jdk代理
        //高版本spring自动根据运行类是否有接口选择JDK或CGLIB代理，我们无需设置proxy-target-class属性
			boolean proxyTargetClass = Boolean.parseBoolean(sourceElement.getAttribute(PROXY_TARGET_CLASS_ATTRIBUTE));
			if (proxyTargetClass) {
				AopConfigUtils.forceAutoProxyCreatorToUseClassProxying(registry);
			}
         //这里是查看标签是否配置了expose-proxy属性并作解析
        // <aop:config expose-proxy="true"> 
        // 设置为true的时候会采用ThreadLocal 也就是在代理方法A内部调用——>代理方法B的时候 也会执行额外的代理逻辑
			boolean exposeProxy = Boolean.parseBoolean(sourceElement.getAttribute(EXPOSE_PROXY_ATTRIBUTE));
			if (exposeProxy) {
				AopConfigUtils.forceAutoProxyCreatorToExposeProxy(registry);
			}
		}
	}
```

解析aspect标签的流程

```java
//解析<aop:aspect>的流程
private void parseAspect(Element aspectElement, ParserContext parserContext) {
		String aspectId = aspectElement.getAttribute(ID);
    	//获取切面对象的引用   <aop:aspect ref="logUtil">
		String aspectName = aspectElement.getAttribute(REF);

		try {
            //压栈
			this.parseState.push(new AspectEntry(aspectId, aspectName));
			List<BeanDefinition> beanDefinitions = new ArrayList<>();
			List<BeanReference> beanReferences = new ArrayList<>();
            //以下标签属于胡写的 declare-parents是aspect标签的子标签 用法是为被代理的类增加一个新接口和新接口的功能方法 并且提供一个默认功能方法的实现
//  <aop:declare-parents types-matching="Long" implement-interface="templates.MyCalculator" default-impl="templates.MyCalculator"></aop:declare-parents>
       
			List<Element> declareParents = DomUtils.getChildElementsByTagName(aspectElement, DECLARE_PARENTS);
			for (int i = METHOD_INDEX; i < declareParents.size(); i++) {
				Element declareParentsElement = declareParents.get(i);
				beanDefinitions.add(parseDeclareParents(declareParentsElement, parserContext));
			}

			// We have to parse "advice" and all the advice kinds in one loop, to get the
			// ordering semantics right.
            //找到所有的advice通知 获取子标签集合
			NodeList nodeList = aspectElement.getChildNodes();
			boolean adviceFoundAlready = false;
			for (int i = 0; i < nodeList.getLength(); i++) {
				Node node = nodeList.item(i);
                //isAdviceNode的核心判断逻辑如下 这个子标签的名字只要是这其中之一 那么就是advice
                  //return (BEFORE.equals(name) || AFTER.equals(name) || AFTER_RETURNING_ELEMENT.equals(name) ||AFTER_THROWING_ELEMENT.equals(name) || AROUND.equals(name));
				if (isAdviceNode(node, parserContext)) {
                    //找到advice后 
					if (!adviceFoundAlready) {
						adviceFoundAlready = true;
						if (!StringUtils.hasText(aspectName)) {
							parserContext.getReaderContext().error(
									"<aspect> tag needs aspect bean reference via 'ref' attribute when declaring advices.",
									aspectElement, this.parseState.snapshot());
							return;
						}
                          //会把切面包装成一个RuntimeBeanReference放起来
						beanReferences.add(new RuntimeBeanReference(aspectName));
					}
                    //开始解析advice标签 封装成beanDefinition
					AbstractBeanDefinition advisorDefinition = parseAdvice(
							aspectName, i, aspectElement, (Element) node, parserContext, beanDefinitions, beanReferences);
                    //包装好的advisor装入beanDefinitions的集合
					beanDefinitions.add(advisorDefinition);
				}
			}
			//获得所有<aop:pointcut>的子标签
			AspectComponentDefinition aspectComponentDefinition = createAspectComponentDefinition(
					aspectElement, aspectId, beanDefinitions, beanReferences, parserContext);
			parserContext.pushContainingComponent(aspectComponentDefinition);

			List<Element> pointcuts = DomUtils.getChildElementsByTagName(aspectElement, POINTCUT);
			for (Element pointcutElement : pointcuts) {
				parsePointcut(pointcutElement, parserContext);
			}

			parserContext.popAndRegisterContainingComponent();
		}
		finally {
            //出栈
			this.parseState.pop();
		}
	}

//---------------------------------------
//解析advice标签的parseAdvice()方法
	private AbstractBeanDefinition parseAdvice(
			String aspectName, int order, Element aspectElement, Element adviceElement, ParserContext parserContext,
			List<BeanDefinition> beanDefinitions, List<BeanReference> beanReferences) {

		try {
			this.parseState.push(new AdviceEntry(parserContext.getDelegate().getLocalName(adviceElement)));
			
			// create the method factory bean
            //封装advice的第一个参数MethodLocatingFactoryBean
			RootBeanDefinition methodDefinition = new RootBeanDefinition(MethodLocatingFactoryBean.class);
            //添加属性 切面的名称
			methodDefinition.getPropertyValues().add("targetBeanName", aspectName);
            //添加属性<aop:around method="around"></aop:around> 标签method属性 代表扩展的方法逻辑
			methodDefinition.getPropertyValues().add("methodName", adviceElement.getAttribute("method"));
            //设置这个beanDefinition是一个合成的 也就是容器内部的 不是用户定义的
			methodDefinition.setSynthetic(true);

			// create instance factory definition
             //封装advice的第三个参数SimpleBeanFactoryAwareAspectInstanceFactory
			RootBeanDefinition aspectFactoryDef =
					new RootBeanDefinition(SimpleBeanFactoryAwareAspectInstanceFactory.class);
			aspectFactoryDef.getPropertyValues().add("aspectBeanName", aspectName);
			aspectFactoryDef.setSynthetic(true);

             //由上面两个参数封装advice的beanDefinition
			// register the pointcut
			AbstractBeanDefinition adviceDef = createAdviceDefinition(
					adviceElement, parserContext, aspectName, order, methodDefinition, aspectFactoryDef,
					beanDefinitions, beanReferences);

			// configure the advisor
           	//创建一个advisor的beanDefinition
			RootBeanDefinition advisorDefinition = new RootBeanDefinition(AspectJPointcutAdvisor.class);
			advisorDefinition.setSource(parserContext.extractSource(adviceElement));
             //利用封装好的advice的BeanDefinition 来封装advisor
            //放到advisor的构造参数里面
			advisorDefinition.getConstructorArgumentValues().addGenericArgumentValue(adviceDef);
            //如果切面aspect有order属性 那么就需要加入到当前advisor里面
            // <aop:aspect ref="logUtil" order="1">
			if (aspectElement.hasAttribute(ORDER_PROPERTY)) {
				advisorDefinition.getPropertyValues().add(
						ORDER_PROPERTY, aspectElement.getAttribute(ORDER_PROPERTY));
			}

			// register the final advisor
            //把这个advisor对象注册到beanFactory里面
			parserContext.getReaderContext().registerWithGeneratedName(advisorDefinition);

			return advisorDefinition;
		}
		finally {
			this.parseState.pop();
		}
	}

//------------------------------
//创建advice的BeanDefinition的方法

private AbstractBeanDefinition createAdviceDefinition(
			Element adviceElement, ParserContext parserContext, String aspectName, int order,
			RootBeanDefinition methodDef, RootBeanDefinition aspectFactoryDef,
			List<BeanDefinition> beanDefinitions, List<BeanReference> beanReferences) {
		//创建对应标签的advice的RootBeanDefinition
		RootBeanDefinition adviceDefinition = new RootBeanDefinition(getAdviceClass(adviceElement, parserContext));
		adviceDefinition.setSource(parserContext.extractSource(adviceElement));
		//设置属性 切面的名称
		adviceDefinition.getPropertyValues().add(ASPECT_NAME_PROPERTY, aspectName);
    	//设置申报顺序 也就是xml文件里的advice顺序 这个对最后的拦截器链顺序没有作用 
         // 因为后续实际创建advice的时候还会进行一次拓扑排序
		adviceDefinition.getPropertyValues().add(DECLARATION_ORDER_PROPERTY, order);
    	//?????
    	//该属性只对<after-returning>元素有效，用于指定一个形参名，后置通知方法可以通过该形参访问目标方法的返回值。
		//解析 <aop:after-returning returning="result"></aop:after-returning>的returning属性
		if (adviceElement.hasAttribute(RETURNING)) {
			adviceDefinition.getPropertyValues().add(
					RETURNING_PROPERTY, adviceElement.getAttribute(RETURNING));
		}
    	// throwing : 该属性只对<after-throwing>元素有效 ?????
    	//解析 <aop:after-throwing throwing="e"></aop:after-throwing>的throwing属性
		if (adviceElement.hasAttribute(THROWING)) {
			adviceDefinition.getPropertyValues().add(
					THROWING_PROPERTY, adviceElement.getAttribute(THROWING));
		}
   		//解析所有通知标签都含有的arg-names属性 ?????
		if (adviceElement.hasAttribute(ARG_NAMES)) {
			adviceDefinition.getPropertyValues().add(
					ARG_NAMES_PROPERTY, adviceElement.getAttribute(ARG_NAMES));
		}
		//获取advice的BeanDeifintion的构造参数map
		ConstructorArgumentValues cav = adviceDefinition.getConstructorArgumentValues();
    	//把刚才的MethodLocatingFactoryBean参数放在第0索引位置
		cav.addIndexedArgumentValue(METHOD_INDEX, methodDef);
		//构造advice的第二个参数
		Object pointcut = parsePointcutProperty(adviceElement, parserContext);
    	//如果返回的是刚才那个内部表达式构造的pointcut
		if (pointcut instanceof BeanDefinition) {
            //那么放到构造参数的第1索引位置
			cav.addIndexedArgumentValue(POINTCUT_INDEX, pointcut);
            //并把这个pointcut加入到beanDefinitions的集合中
			beanDefinitions.add((BeanDefinition) pointcut);
		}
    	//如果返回的是统一的pointcut的引用名称
		else if (pointcut instanceof String) {
             //那么是封装一个RuntimeBeanReference的对象 把ref的name传进去
			RuntimeBeanReference pointcutRef = new RuntimeBeanReference((String) pointcut);
             //放到构造参数的第1索引位置
			cav.addIndexedArgumentValue(POINTCUT_INDEX, pointcutRef);
            //并把这个pointcut加入到beanReferences的集合中
			beanReferences.add(pointcutRef);
		}
		//把刚才的第三个参数SimpleBeanFactoryAwareAspectInstanceFactory放在第2索引位置
		cav.addIndexedArgumentValue(ASPECT_INSTANCE_FACTORY_INDEX, aspectFactoryDef);

		return adviceDefinition;
	}

//-----------------------------
//基于标签名称来决定封装的advice的类型
	private Class<?> getAdviceClass(Element adviceElement, ParserContext parserContext) {
		String elementName = parserContext.getDelegate().getLocalName(adviceElement);
        //before
		if (BEFORE.equals(elementName)) {
			return AspectJMethodBeforeAdvice.class;
		}
         //after
		else if (AFTER.equals(elementName)) {
			return AspectJAfterAdvice.class;
		}
        //afterReturning
		else if (AFTER_RETURNING_ELEMENT.equals(elementName)) {
			return AspectJAfterReturningAdvice.class;
		}
        //afterThrowing
		else if (AFTER_THROWING_ELEMENT.equals(elementName)) {
			return AspectJAfterThrowingAdvice.class;
		}
        //around
		else if (AROUND.equals(elementName)) {
			return AspectJAroundAdvice.class;
		}
		else {
			throw new IllegalArgumentException("Unknown advice kind [" + elementName + "].");
		}
	}
//----------------------------------
//构造advice的第二个参数 也就是pointCut AspectJExpressionPointcut
@Nullable
	private Object parsePointcutProperty(Element element, ParserContext parserContext) {
        //POINTCUT指的是 <aop:around method="around pointcut="execution(xxxx)"></aop:around>
        //即指定在advice标签内部的切入点表达式
        //POINTCUT_REF则指的是 
        //<aop:pointcut id="myPoint" expression="execution( Integer com.example.demo.aop.xml.service.MyCalculator.*  (..))"/>
        // <aop:around method="around" pointcut-ref="myPoint"></aop:around>
        //单独独立定义的一个pointcut标签 
		if (element.hasAttribute(POINTCUT) && element.hasAttribute(POINTCUT_REF)) {
            //检查是否共存 
			parserContext.getReaderContext().error(
					"Cannot define both 'pointcut' and 'pointcut-ref' on <advisor> tag.",
					element, this.parseState.snapshot());
			return null;
		}
        //看看是拥有哪一种 如果是advice标签内部定义的切入点表达式
		else if (element.hasAttribute(POINTCUT)) {
            //那么就需要根据这个表达式来创造一个AspectJExpressionPointcut的BeanDefinition
			// Create a pointcut for the anonymous pc and register it.
			String expression = element.getAttribute(POINTCUT);
            //创建的过程也比较简单 就是直接new RootBeanDefinition(AspectJExpressionPointcut.class)
            //然后进行了一些属性的赋值 包括表达式的赋值等等操作
			AbstractBeanDefinition pointcutDefinition = createPointcutDefinition(expression);
			pointcutDefinition.setSource(parserContext.extractSource(element));
			return pointcutDefinition;
		}
        //如果是独立出来的<aop:pointcut>标签定义的表达式切入点 这里给的是引用
		else if (element.hasAttribute(POINTCUT_REF)) {
            //那么直接返回引用名称就可以了 因为后续会对这个<aop:pointcut>做统一解析
			String pointcutRef = element.getAttribute(POINTCUT_REF);
			if (!StringUtils.hasText(pointcutRef)) {
				parserContext.getReaderContext().error(
						"'pointcut-ref' attribute contains empty value.", element, this.parseState.snapshot());
				return null;
			}
			return pointcutRef;
		}
		else {
			parserContext.getReaderContext().error(
					"Must define one of 'pointcut' or 'pointcut-ref' on <advisor> tag.",
					element, this.parseState.snapshot());
			return null;
		}
	}
//-------------------------------
//解析切入点标签
private AbstractBeanDefinition parsePointcut(Element pointcutElement, ParserContext parserContext) {
    	//获得id属性
		String id = pointcutElement.getAttribute(ID);
    	//获得表达式属性
		String expression = pointcutElement.getAttribute(EXPRESSION);

		AbstractBeanDefinition pointcutDefinition = null;

		try {
			this.parseState.push(new PointcutEntry(id));
            //根据表达式创建AspectJExpressionPointcut的RootBeanDefinition
			pointcutDefinition = createPointcutDefinition(expression);
			pointcutDefinition.setSource(parserContext.extractSource(pointcutElement));

			String pointcutBeanName = id;
			if (StringUtils.hasText(pointcutBeanName)) {
                //注册到beanFactory
				parserContext.getRegistry().registerBeanDefinition(pointcutBeanName, pointcutDefinition);
			}
			else {
				pointcutBeanName = parserContext.getReaderContext().registerWithGeneratedName(pointcutDefinition);
			}

			parserContext.registerComponent(
					new PointcutComponentDefinition(pointcutBeanName, pointcutDefinition, expression));
		}
		finally {
			this.parseState.pop();
		}

		return pointcutDefinition;
	}
//-----------------------------------
	public String registerWithGeneratedName(BeanDefinition beanDefinition) {
        //生成一个bean的name
		String generatedName = generateBeanName(beanDefinition);
		getRegistry().registerBeanDefinition(generatedName, beanDefinition);
		return generatedName;
	}
//------------------------------------
//调用链DefaultBeanNameGenerator里的generateBeanName()----->BeanDefinitionReaderUtils.generateBeanName()
public static String generateBeanName(
			BeanDefinition definition, BeanDefinitionRegistry registry, boolean isInnerBean)
			throws BeanDefinitionStoreException {

		String generatedBeanName = definition.getBeanClassName();
    //	如果beanClassName为空
		if (generatedBeanName == null) {
			if (definition.getParentName() != null) {
				generatedBeanName = definition.getParentName() + "$child";
			}
			else if (definition.getFactoryBeanName() != null) {
				generatedBeanName = definition.getFactoryBeanName() + "$created";
			}
		}
		if (!StringUtils.hasText(generatedBeanName)) {
			throw new BeanDefinitionStoreException("Unnamed bean definition specifies neither " +
					"'class' nor 'parent' nor 'factory-bean' - can't generate bean name");
		}
		//如果是内部bean 这里后面创建advice的内部构造参数的时候会看到？还是生成代理会看到？
		if (isInnerBean) {
			// Inner bean: generate identity hashcode suffix.
			return generatedBeanName + GENERATED_BEAN_NAME_SEPARATOR + ObjectUtils.getIdentityHexString(definition);
		}
		//adivisor 采用的是这种命名方式
		// Top-level bean: use plain class name with unique suffix if necessary.
		return uniqueBeanName(generatedBeanName, registry);
	}
//---------------------------------
	public static String uniqueBeanName(String beanName, BeanDefinitionRegistry registry) {
		String id = beanName;
		int counter = -1;

		// Increase counter until the id is unique.
        //构造出了beanName + # + 自增数字
        //这也是后面看到org.springframework.aop.aspectj.AspectJPointcutAdvisor#0的由来
		String prefix = beanName + GENERATED_BEAN_NAME_SEPARATOR;
		while (counter == -1 || registry.containsBeanDefinition(id)) {
			counter++;
			id = prefix + counter;
		}
		return id;
	}
```

以上就完成了对advisor的注册工作，接下来是对如果包括aspectj-autoproxy标签的解析

```java
//如果xml文件中包含<aop:aspectj-autoproxy></aop:aspectj-autoproxy>标签 那么就会进行解析
class AspectJAutoProxyBeanDefinitionParser implements BeanDefinitionParser {

	@Override
	@Nullable
	public BeanDefinition parse(Element element, ParserContext parserContext) {
        //注册AspectJAnnotationAutoProxyCreator的BeanDefinition
		AopNamespaceUtils.registerAspectJAnnotationAutoProxyCreatorIfNecessary(parserContext, element);
		//如果<aop:aspectj-autoproxy></aop:aspectj-autoproxy>标签内包含子类 那么还会处理
        extendBeanDefinition(element, parserContext);
		return null;
	}
}
//-----------------------------
//注册AspectJAnnotationAutoProxyCreator的实际逻辑
	public static void registerAspectJAnnotationAutoProxyCreatorIfNecessary(
			ParserContext parserContext, Element sourceElement) {
		//用AspectJAnnotationAutoProxyCreator替换AspectJAwareAdvisorAutoProxyCreator
		BeanDefinition beanDefinition = AopConfigUtils.registerAspectJAnnotationAutoProxyCreatorIfNecessary(
				parserContext.getRegistry(), parserContext.extractSource(sourceElement));
        //与上面相同 处理cglib、jdk代理方式配置 处理ThreadLocal的配置
		useClassProxyingIfNecessary(parserContext.getRegistry(), sourceElement);
		registerComponentIfNecessary(beanDefinition, parserContext);
	}

//------------------------------------------
@Nullable
	private static BeanDefinition registerOrEscalateApcAsRequired(
			Class<?> cls, BeanDefinitionRegistry registry, @Nullable Object source) {

		Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
		//此时beanFactory里面已经包含一个org.springframework.aop.config.internalAutoProxyCreator
		if (registry.containsBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME)) {
			BeanDefinition apcDefinition = registry.getBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME);
			if (!cls.getName().equals(apcDefinition.getBeanClassName())) {
                //获取AspectJAwareAdvisorAutoProxyCreator的优先级
				int currentPriority = findPriorityForClass(apcDefinition.getBeanClassName());
                //获取AspectJAnnotationAutoProxyCreator的优先级
				int requiredPriority = findPriorityForClass(cls);
				if (currentPriority < requiredPriority) {
                    //一般来说 AspectJAnnotationAutoProxyCreator的优先级高 这里就做了替换了
					apcDefinition.setBeanClassName(cls.getName());
				}
			}
			return null;
		}

		RootBeanDefinition beanDefinition = new RootBeanDefinition(cls);
		beanDefinition.setSource(source);
		beanDefinition.getPropertyValues().add("order", Ordered.HIGHEST_PRECEDENCE);
		beanDefinition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
		registry.registerBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME, beanDefinition);
		return beanDefinition;
	}



```

至此，xml的解析注册工作全部完成



##### 注解方式

注解的解析依然离不开ioc流程，@EnableAspectJAutoProxy 使用了这个注解才能导入AnnotationAwareAspectJAutoProxyCreator

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
//核心在这里 通过@Import引入AspectJAutoProxyRegistrar来完成导入AnnotationAwareAspectJAutoProxyCreator
@Import(AspectJAutoProxyRegistrar.class)
public @interface EnableAspectJAutoProxy {

	/**
	 * Indicate whether subclass-based (CGLIB) proxies are to be created as opposed
	 * to standard Java interface-based proxies. The default is {@code false}.
	 */
    //把xml那套配置改成这样了 作用还是一样的
	boolean proxyTargetClass() default false;

	/**
	 * Indicate that the proxy should be exposed by the AOP framework as a {@code ThreadLocal}
	 * for retrieval via the {@link org.springframework.aop.framework.AopContext} class.
	 * Off by default, i.e. no guarantees that {@code AopContext} access will work.
	 * @since 4.3.1
	 */
	boolean exposeProxy() default false;

}
//-------------------
//开始前先看一下我配置的注解都在哪 
//这里是把@EnableAspectJAutoProxy注解放到和@Configuration一起
//
@Configuration
@ComponentScan(basePackages="com.example.demo.aop.annotation")
@EnableAspectJAutoProxy
public class SpringConfiguration { }

//这是我配置的切面
@Aspect
@Component
public class LogUtil implements ApplicationContextAware {}

//这是测试启动类
public class TestAnnotationAop {

    public static void main(String[] args) throws NoSuchMethodException {
        //因为使用了AnnotationConfigApplicationContext，所以会自动注册关于注解扫描和解析的BFPP，其中最重要的
        //ConfigurationClassPostProcessor自然也会被注册进去
        AnnotationConfigApplicationContext ac = new AnnotationConfigApplicationContext();
        ac.register(SpringConfiguration.class);
        ac.refresh();
        MyCalculator bean = ac.getBean(MyCalculator.class);
        System.out.println(bean.add(1, 1));
    }
}
```

这其中涉及到BFPP和BDRPP的处理，ConfigurationClassPostProcessor属于BDRPP和BFPP两个接口的子类，详情去看前面的ioc处理吧，这里就不做赘述了，直接看@Import的处理

前置：到这里的时候 已经完成 对@ComponentScan的处理，即@Component的扫描工作，也就是说现在logUtil已经加入到了beanFactory中，还没有进行@Aspect的处理

![image-20240119153747485](.\images\image-20240119153747485.png)

![image-20240119153333007](.\images\image-20240119153333007.png)

开始@Import的解析

```java
	private void processImports(ConfigurationClass configClass, SourceClass currentSourceClass,
			Collection<SourceClass> importCandidates, Predicate<String> exclusionFilter,
			boolean checkForCircularImports) {
		//这里的importCandidates是已经解析出来的@Import的value值的集合
        //所以这里会有AspectJAutoProxyRegistrar
		if (importCandidates.isEmpty()) {
			return;
		}

		if (checkForCircularImports && isChainedImportOnStack(configClass)) {
			this.problemReporter.error(new CircularImportProblem(configClass, this.importStack));
		}
		else {
			this.importStack.push(configClass);
			try {
                //对所有import进来的类进行处理 
				for (SourceClass candidate : importCandidates) {
					if (candidate.isAssignable(ImportSelector.class)) {
						// Candidate class is an ImportSelector -> delegate to it to determine imports
						Class<?> candidateClass = candidate.loadClass();
						ImportSelector selector = ParserStrategyUtils.instantiateClass(candidateClass, ImportSelector.class,
								this.environment, this.resourceLoader, this.registry);
						Predicate<String> selectorFilter = selector.getExclusionFilter();
						if (selectorFilter != null) {
							exclusionFilter = exclusionFilter.or(selectorFilter);
						}
						if (selector instanceof DeferredImportSelector) {
							this.deferredImportSelectorHandler.handle(configClass, (DeferredImportSelector) selector);
						}
						else {
							String[] importClassNames = selector.selectImports(currentSourceClass.getMetadata());
							Collection<SourceClass> importSourceClasses = asSourceClasses(importClassNames, exclusionFilter);
							processImports(configClass, currentSourceClass, importSourceClasses, exclusionFilter, false);
						}
					}
                    //AspectJAutoProxyRegistrar属于ImportBeanDefinitionRegistrar的子类
					else if (candidate.isAssignable(ImportBeanDefinitionRegistrar.class)) {
						// Candidate class is an ImportBeanDefinitionRegistrar ->
						// delegate to it to register additional bean definitions
                        //获取类的对象
						Class<?> candidateClass = candidate.loadClass();
                        //对import进来的类进行实例化
						ImportBeanDefinitionRegistrar registrar =
								ParserStrategyUtils.instantiateClass(candidateClass, ImportBeanDefinitionRegistrar.class,
										this.environment, this.resourceLoader, this.registry);
                        //加入到importBeanDefinitionRegistrars集合中
						configClass.addImportBeanDefinitionRegistrar(registrar, currentSourceClass.getMetadata());
					}
					else {
						// Candidate class not an ImportSelector or ImportBeanDefinitionRegistrar ->
						// process it as an @Configuration class
						this.importStack.registerImport(
								currentSourceClass.getMetadata(), candidate.getMetadata().getClassName());
						processConfigurationClass(candidate.asConfigClass(configClass), exclusionFilter);
					}
				}
			}
			catch (BeanDefinitionStoreException ex) {
				throw ex;
			}
			catch (Throwable ex) {
				throw new BeanDefinitionStoreException(
						"Failed to process import candidates for configuration class [" +
						configClass.getMetadata().getClassName() + "]", ex);
			}
			finally {
				this.importStack.pop();
			}
		}
	}
```



```java
//后续会将加入到importBeanDefinitionRegistrars集合的类进行处理
private void loadBeanDefinitionsFromRegistrars(Map<ImportBeanDefinitionRegistrar, AnnotationMetadata> registrars) {
		registrars.forEach((registrar, metadata) ->
                           //调用registerBeanDefinitions 那么就可以进入Import的类内部了
				registrar.registerBeanDefinitions(metadata, this.registry, this.importBeanNameGenerator));
	}

```

```java
//终于进入AspectJAutoProxyRegistrar了
class AspectJAutoProxyRegistrar implements ImportBeanDefinitionRegistrar {

	/**
	 * Register, escalate, and configure the AspectJ auto proxy creator based on the value
	 * of the @{@link EnableAspectJAutoProxy#proxyTargetClass()} attribute on the importing
	 * {@code @Configuration} class.
	 */
	@Override
	public void registerBeanDefinitions(
			AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
		//看到这个就比较亲切了 注册AnnotationAutoProxyCreator
		AopConfigUtils.registerAspectJAnnotationAutoProxyCreatorIfNecessary(registry);
		//下面就不说了 属性处理 
		AnnotationAttributes enableAspectJAutoProxy =
				AnnotationConfigUtils.attributesFor(importingClassMetadata, EnableAspectJAutoProxy.class);
		if (enableAspectJAutoProxy != null) {
			if (enableAspectJAutoProxy.getBoolean("proxyTargetClass")) {
				AopConfigUtils.forceAutoProxyCreatorToUseClassProxying(registry);
			}
			if (enableAspectJAutoProxy.getBoolean("exposeProxy")) {
				AopConfigUtils.forceAutoProxyCreatorToExposeProxy(registry);
			}
		}
	}

}

```

这部分完成了@EnableAspectJAutoProxy的引入 接下来是对@Aspect的处理，这部分我选择放到下一章节“创建切面对象”一起说 ，因为实际的@Aspect的处理逻辑也在那里













#### 创建切面对象 

创建advisor对象的工作，实际上是在AnnotationAwareAspectJAutoProxyCreator里面完成，而AnnotationAwareAspectJAutoProxyCreator继承了BeanPostProcessor

![image-20240119142311855](.\images\image-20240119142311855.png)

实际处理的地方在doGetBean()->createBean()->resolveBeforeInstantiation()

```java
//因为AnnotationAwareAspectJAutoProxyCreator实现了InstantiationAwareBeanPostProcessor接口
//所以实际处理实例化advisor的地方就在这里
	@Nullable
	protected Object resolveBeforeInstantiation(String beanName, RootBeanDefinition mbd) {
		Object bean = null;
		if (!Boolean.FALSE.equals(mbd.beforeInstantiationResolved)) {
			// Make sure bean class is actually resolved at this point.
            //看一下mbd是不是用户自定义的 不是内部的 并且有实现了InstantiationAwareBeanPostProcessor接口的子类
			if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
				Class<?> targetType = determineTargetType(beanName, mbd);
				if (targetType != null) {
                    //调用实例化前的处理方法
					bean = applyBeanPostProcessorsBeforeInstantiation(targetType, beanName);
					if (bean != null) {
						bean = applyBeanPostProcessorsAfterInitialization(bean, beanName);
					}
				}
			}
			mbd.beforeInstantiationResolved = (bean != null);
		}
		return bean;
	}
//-------------------------------------------------------
	@Nullable
	protected Object applyBeanPostProcessorsBeforeInstantiation(Class<?> beanClass, String beanName) {
        //获取所有的BeanPostProcessors
		for (BeanPostProcessor bp : getBeanPostProcessors()) {
            //找到属于InstantiationAwareBeanPostProcessor下的子类
			if (bp instanceof InstantiationAwareBeanPostProcessor) {
				InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
                 //调用实例化前的处理方法
				Object result = ibp.postProcessBeforeInstantiation(beanClass, beanName);
				if (result != null) {
					return result;
				}
			}
		}
		return null;
	}
//---------------------------------------------------------
//进入处理正题
	@Override
	public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) {
		Object cacheKey = getCacheKey(beanClass, beanName);

		if (!StringUtils.hasLength(beanName) || !this.targetSourcedBeans.contains(beanName)) {
			if (this.advisedBeans.containsKey(cacheKey)) {
				return null;
			}
            //如果当前类是Advice、Pointcut、Advisor、AopInfrastructureBean的子类或者@Aspect注解修饰
            //isInfrastructureClass返回true
            //进入核心重点shouldSkip()
			if (isInfrastructureClass(beanClass) || shouldSkip(beanClass, beanName)) {
				this.advisedBeans.put(cacheKey, Boolean.FALSE);
				return null;
			}
		}

		// Create proxy here if we have a custom TargetSource.
		// Suppresses unnecessary default instantiation of the target bean:
		// The TargetSource will handle target instances in a custom fashion.
		TargetSource targetSource = getCustomTargetSource(beanClass, beanName);
		if (targetSource != null) {
			if (StringUtils.hasLength(beanName)) {
				this.targetSourcedBeans.add(beanName);
			}
			Object[] specificInterceptors = getAdvicesAndAdvisorsForBean(beanClass, beanName, targetSource);
			Object proxy = createProxy(beanClass, beanName, specificInterceptors, targetSource);
			this.proxyTypes.put(cacheKey, proxy.getClass());
			return proxy;
		}

		return null;
	}
//---------------------
//进入到核心重点 shouldSkip()
	@Override
	protected boolean shouldSkip(Class<?> beanClass, String beanName) {
		// TODO: Consider optimization by caching the list of the aspect names
        //这里是入口 找到所有的候选advisor对象
		List<Advisor> candidateAdvisors = findCandidateAdvisors();
        //遍历所有advisor对象 判断当前bean是否是切面 
		for (Advisor advisor : candidateAdvisors) {
			if (advisor instanceof AspectJPointcutAdvisor &&
					((AspectJPointcutAdvisor) advisor).getAspectName().equals(beanName)) {
				return true;
			}
		}
        //调用父类判断 不太重要
		return super.shouldSkip(beanClass, beanName);
	}
//------------------------------
	@Override
	protected List<Advisor> findCandidateAdvisors() {
		// Add all the Spring advisors found according to superclass rules.
        //调用父类的查找方法 如果现在容器中有advisor的beanDefinition 这个方法就有返回值
        //一般是xml形式下才有
		List<Advisor> advisors = super.findCandidateAdvisors();
		// Build Advisors for all AspectJ aspects in the bean factory.
		if (this.aspectJAdvisorsBuilder != null) {
            //注解形式 解析注解@Aspect
			advisors.addAll(this.aspectJAdvisorsBuilder.buildAspectJAdvisors());
		}
		return advisors;
	}
//------------------------------
	protected List<Advisor> findCandidateAdvisors() {
		Assert.state(this.advisorRetrievalHelper != null, "No BeanFactoryAdvisorRetrievalHelper available");
        //顾名思义 找到advisor bean对象 那么就肯定有实例化环节
		return this.advisorRetrievalHelper.findAdvisorBeans();
	}
//------------------------------
public List<Advisor> findAdvisorBeans() {
		// Determine list of advisor bean names, if not cached already.
    	//从缓存中获取advisor的名称
		String[] advisorNames = this.cachedAdvisorBeanNames;
		if (advisorNames == null) {
			// Do not initialize FactoryBeans here: We need to leave all regular beans
			// uninitialized to let the auto-proxy creator apply to them!
            //从beanFactory中获取Advisor类型的beanNames
			advisorNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(
					this.beanFactory, Advisor.class, true, false);
            //缓存起来
			this.cachedAdvisorBeanNames = advisorNames;
		}
		if (advisorNames.length == 0) {
			return new ArrayList<>();
		}

		List<Advisor> advisors = new ArrayList<>();
		for (String name : advisorNames) {
			if (isEligibleBean(name)) {
				if (this.beanFactory.isCurrentlyInCreation(name)) {
					if (logger.isTraceEnabled()) {
						logger.trace("Skipping currently created advisor '" + name + "'");
					}
				}
				else {
					try {
                        //创建bean 
						advisors.add(this.beanFactory.getBean(name, Advisor.class));
					}
					catch (BeanCreationException ex) {
						Throwable rootCause = ex.getMostSpecificCause();
						if (rootCause instanceof BeanCurrentlyInCreationException) {
							BeanCreationException bce = (BeanCreationException) rootCause;
							String bceBeanName = bce.getBeanName();
							if (bceBeanName != null && this.beanFactory.isCurrentlyInCreation(bceBeanName)) {
								if (logger.isTraceEnabled()) {
									logger.trace("Skipping advisor '" + name +
											"' with dependency on currently created bean: " + ex.getMessage());
								}
								// Ignore: indicates a reference back to the bean we're trying to advise.
								// We want to find advisors other than the currently created bean itself.
								continue;
							}
						}
						throw ex;
					}
				}
			}
		}
		return advisors;
	}
```

实际的包含关系我在创建advisor的beanDefinition的时候有提到，涉及到很多构造参数的实例化，这里上个图来解释接下来会实例化哪些对象，以AspectJPointcutAdvisor#0来举例，那么我们在实例化AspectJPointcutAdvisor的时候，需要构造参数AspectJAroundAdvice，构建AspectJAroundAdvice的时候，又需要构造参数MethodLocatingFactoryBean、SimpleBeanFactoryAwareAspectInstanceFactory、AspectJExpressionPointcut这三个参数，调用层级会非常深，实例化的顺序也是如此，接下来我就挑一些重点，整个实例化过程就不再赘述了，详情请见ioc过程。

![image-20240119170300972](.\images\image-20240119170300972.png)

这里有几个对象需要进行说明，在创建advisor的beanDefinition的时候，分为BeanDefinition类型的参数和RuntimeBeanReference类型，其中MethodLocatingFactoryBean、SimpleBeanFactoryAwareAspectInstanceFactory为BeanDefinition，而AspectJExpressionPointcut为RuntimeBeanReference，RuntimeBeanReference指向的是一个在运行时的bean对象，一般有beanDefinition存放在beanFactory的beanDefinitionMap中，而这里指的BeanDefinition一般没有在Map中，生成的也就是innerBean打头的bean，不会存放在beanFactory的一级缓存中。

以上创建以xml生成的Advisor对象已经完成，接下来要解析@Aspect的类并进行实例化

```java
@Override
	protected List<Advisor> findCandidateAdvisors() {
		// Add all the Spring advisors found according to superclass rules.
		List<Advisor> advisors = super.findCandidateAdvisors();
		// Build Advisors for all AspectJ aspects in the bean factory.
        //这里对@Aspect的bean进行处理
        //只有AnnotationAwareAspectJAutoProxyCreator这个类才能进if
		if (this.aspectJAdvisorsBuilder != null) {
			advisors.addAll(this.aspectJAdvisorsBuilder.buildAspectJAdvisors());
		}
		return advisors;
	}
//------------------------------------------------------------------------
//进入处理@Aspect的方法
public List<Advisor> buildAspectJAdvisors() {
		List<String> aspectNames = this.aspectBeanNames;

		if (aspectNames == null) {
			synchronized (this) {
				aspectNames = this.aspectBeanNames;
				if (aspectNames == null) {
					List<Advisor> advisors = new ArrayList<>();
					aspectNames = new ArrayList<>();
                    	//获取所有的bean名字 包括父容器的所有bean名字   
                    //从这里就可以看出来 为什么@Aspect要和@Commponent一起使用了 因为这里不会扫描 只会对当前容器里面的beanNames做检测和处理
					String[] beanNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(
							this.beanFactory, Object.class, true, false);
					for (String beanName : beanNames) {
						if (!isEligibleBean(beanName)) {
							continue;
						}
						// We must be careful not to instantiate beans eagerly as in this case they
						// would be cached by the Spring container but would not have been weaved.
                        //获取bean的类型
						Class<?> beanType = this.beanFactory.getType(beanName);
						if (beanType == null) {
							continue;
						}
                        //判断当前bean是否是Aspect切面
						if (this.advisorFactory.isAspect(beanType)) {
							aspectNames.add(beanName);
                               //获取切面的元数据
							AspectMetadata amd = new AspectMetadata(beanType, beanName);
							if (amd.getAjType().getPerClause().getKind() == PerClauseKind.SINGLETON) {
								MetadataAwareAspectInstanceFactory factory =
										new BeanFactoryAspectInstanceFactory(this.beanFactory, beanName);
                                	//获取advisor
								List<Advisor> classAdvisors = this.advisorFactory.getAdvisors(factory);
                                //给切面和对应的advisor做缓存 如果切面是单例的话
								if (this.beanFactory.isSingleton(beanName)) {
									this.advisorsCache.put(beanName, classAdvisors);
								}
								else {
									this.aspectFactoryCache.put(beanName, factory);
								}
                                	//把@Aspect的类解析出的advisors和xml解析的advisor放在一起
								advisors.addAll(classAdvisors);
							}
							else {
                                	//处理prototype的切面？？？？？
								// Per target or per this.
                                //如果切面是prototype的 切面的实例化模式不是 那么就报异常
								if (this.beanFactory.isSingleton(beanName)) {
									throw new IllegalArgumentException("Bean with name '" + beanName +
											"' is a singleton, but aspect instantiation model is not singleton");
								}
                                //新建一个prototype的工厂对象
								MetadataAwareAspectInstanceFactory factory =
										new PrototypeAspectInstanceFactory(this.beanFactory, beanName);
								this.aspectFactoryCache.put(beanName, factory);
								advisors.addAll(this.advisorFactory.getAdvisors(factory));
							}
						}
					}
					this.aspectBeanNames = aspectNames;
					return advisors;
				}
			}
		}

		if (aspectNames.isEmpty()) {
			return Collections.emptyList();
		}
		List<Advisor> advisors = new ArrayList<>();
		for (String aspectName : aspectNames) {
			List<Advisor> cachedAdvisors = this.advisorsCache.get(aspectName);
			if (cachedAdvisors != null) {
				advisors.addAll(cachedAdvisors);
			}
			else {
				MetadataAwareAspectInstanceFactory factory = this.aspectFactoryCache.get(aspectName);
				advisors.addAll(this.advisorFactory.getAdvisors(factory));
			}
		}
		return advisors;
	}
//--------------------------------
//判断是否是切面
	@Override
	public boolean isAspect(Class<?> clazz) {
		return (hasAspectAnnotation(clazz) && !compiledByAjc(clazz));
	}
	private boolean hasAspectAnnotation(Class<?> clazz) {
        //找到当前类是否是被@Aspect注解修饰
        //1.搜索给定类的注释，如果找到，则返回它。
        //2.递归搜索给定类声明的所有注释。
        //3.递归搜索给定类声明的所有接口。
        //4.递归搜索给定类的超类层次结构。
		return (AnnotationUtils.findAnnotation(clazz, Aspect.class) != null);
	}

//------------------------------------
//List<Advisor> classAdvisors = this.advisorFactory.getAdvisors(factory);的实现

	@Override
	public List<Advisor> getAdvisors(MetadataAwareAspectInstanceFactory aspectInstanceFactory) {
        //获得切面类
		Class<?> aspectClass = aspectInstanceFactory.getAspectMetadata().getAspectClass();
        //获得切面名称
		String aspectName = aspectInstanceFactory.getAspectMetadata().getAspectName();
        //校验一下
		validate(aspectClass);

		// We need to wrap the MetadataAwareAspectInstanceFactory with a decorator
		// so that it will only instantiate once.
		MetadataAwareAspectInstanceFactory lazySingletonAspectInstanceFactory =
				new LazySingletonAspectInstanceFactoryDecorator(aspectInstanceFactory);

		List<Advisor> advisors = new ArrayList<>();
        //获得切面的所有方法--排除@Pointcut修饰的方法
		for (Method method : getAdvisorMethods(aspectClass)) {
			// Prior to Spring Framework 5.2.7, advisors.size() was supplied as the declarationOrderInAspect
			// to getAdvisor(...) to represent the "current position" in the declared methods list.
			// However, since Java 7 the "current position" is not valid since the JDK no longer
			// returns declared methods in the order in which they are declared in the source code.
			// Thus, we now hard code the declarationOrderInAspect to 0 for all advice methods
			// discovered via reflection in order to support reliable advice ordering across JVM launches.
			// Specifically, a value of 0 aligns with the default value used in
			// AspectJPrecedenceComparator.getAspectDeclarationOrder(Advisor).
            //通过切面方法 构造advisor对象
			Advisor advisor = getAdvisor(method, lazySingletonAspectInstanceFactory, 0, aspectName);
            //不为null 就加入集合
			if (advisor != null) {
				advisors.add(advisor);
			}
		}
		//如果是懒实例化 那么就创建一个advisor加入到集合中 
		// If it's a per target aspect, emit the dummy instantiating aspect.
		if (!advisors.isEmpty() && lazySingletonAspectInstanceFactory.getAspectMetadata().isLazilyInstantiated()) {
			Advisor instantiationAdvisor = new SyntheticInstantiationAdvisor(lazySingletonAspectInstanceFactory);
			advisors.add(0, instantiationAdvisor);
		}
		//处理@DeclareParents注解
		// Find introduction fields.
        //@DeclareParents注解用于在目标类加入新的方法 而不是增强原有的方法
		for (Field field : aspectClass.getDeclaredFields()) {
			Advisor advisor = getDeclareParentsAdvisor(field);
			if (advisor != null) {
				advisors.add(advisor);
			}
		}

		return advisors;
	}
//-----------------------------
//getAdvisorMethods(aspectClass)的具体实现
	private List<Method> getAdvisorMethods(Class<?> aspectClass) {
		final List<Method> methods = new ArrayList<>();
		ReflectionUtils.doWithMethods(aspectClass, method -> {
			// Exclude pointcuts
            //排除@Pointcut修饰的方法
			if (AnnotationUtils.getAnnotation(method, Pointcut.class) == null) {
				methods.add(method);
			}
		}, ReflectionUtils.USER_DECLARED_METHODS);
		if (methods.size() > 1) {
            //排序
			methods.sort(METHOD_COMPARATOR);
		}
		return methods;
	}
//------------------------------------------------------
//Advisor advisor = getAdvisor(method, lazySingletonAspectInstanceFactory, 0, aspectName);的实际实现
	@Override
	@Nullable
	public Advisor getAdvisor(Method candidateAdviceMethod, MetadataAwareAspectInstanceFactory aspectInstanceFactory,
			int declarationOrderInAspect, String aspectName) {

		validate(aspectInstanceFactory.getAspectMetadata().getAspectClass());
		//通过当前方法的通知注解创建pointcut的对象
		AspectJExpressionPointcut expressionPointcut = getPointcut(
				candidateAdviceMethod, aspectInstanceFactory.getAspectMetadata().getAspectClass());
		//如果为null 那么代表这个方法没有被通知注解修饰
        if (expressionPointcut == null) {
			return null;
		}
		//否则就实例化一个advisor
        //InstantiationModelAwarePointcutAdvisorImpl是Advisor的子类
		return new InstantiationModelAwarePointcutAdvisorImpl(expressionPointcut, candidateAdviceMethod,
				this, aspectInstanceFactory, declarationOrderInAspect, aspectName);
	}
//-----------------------------------------
	@Nullable
	private AspectJExpressionPointcut getPointcut(Method candidateAdviceMethod, Class<?> candidateAspectClass) {
		//在当前方法上去找是否有通知相关的注解 
        AspectJAnnotation<?> aspectJAnnotation =
				AbstractAspectJAdvisorFactory.findAspectJAnnotationOnMethod(candidateAdviceMethod);
		//如果没找到 返回null
        if (aspectJAnnotation == null) {
			return null;
		}
		//如果找到了 那么就封装一个AspectJExpressionPointcut对象
		AspectJExpressionPointcut ajexp =
				new AspectJExpressionPointcut(candidateAspectClass, new String[0], new Class<?>[0]);
        //把通知相关注解的切入点表达式传进去 这里给的可能是“myPointCut()”这样的表达式
		ajexp.setExpression(aspectJAnnotation.getPointcutExpression());
		if (this.beanFactory != null) {
			ajexp.setBeanFactory(this.beanFactory);
		}
        //返回pointCut的对象
		return ajexp;
	}
//--------------------------------------------------
//return new InstantiationModelAwarePointcutAdvisorImpl(expressionPointcut, candidateAdviceMethod,this, aspectInstanceFactory, declarationOrderInAspect, aspectName);的具体实现
//这里只截取关键代码
//根据注解来判断advice的类型 进行实例化
	switch (aspectJAnnotation.getAnnotationType()) {
			case AtPointcut:
				if (logger.isDebugEnabled()) {
					logger.debug("Processing pointcut '" + candidateAdviceMethod.getName() + "'");
				}
				return null;
			case AtAround:
            	 //advice通知的方法 表达式对象 工厂对象
				springAdvice = new AspectJAroundAdvice(
						candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
				break;
			case AtBefore:
				springAdvice = new AspectJMethodBeforeAdvice(
						candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
				break;
			case AtAfter:
				springAdvice = new AspectJAfterAdvice(
						candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
				break;
			case AtAfterReturning:
				springAdvice = new AspectJAfterReturningAdvice(
						candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
				AfterReturning afterReturningAnnotation = (AfterReturning) aspectJAnnotation.getAnnotation();
				if (StringUtils.hasText(afterReturningAnnotation.returning())) {
					springAdvice.setReturningName(afterReturningAnnotation.returning());
				}
				break;
			case AtAfterThrowing:
				springAdvice = new AspectJAfterThrowingAdvice(
						candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
				AfterThrowing afterThrowingAnnotation = (AfterThrowing) aspectJAnnotation.getAnnotation();
				if (StringUtils.hasText(afterThrowingAnnotation.throwing())) {
					springAdvice.setThrowingName(afterThrowingAnnotation.throwing());
				}
				break;
			default:
				throw new UnsupportedOperationException(
						"Unsupported advice type on method: " + candidateAdviceMethod);
		}
```

至此 xml和@Aspect注解修饰的切面advisor对象全部创建完成 





#### 生成代理对象

代理对象的生成，主要是在BPP的初始化后置处理方法中

```java
//代理对象的生成在doCreateBean()-->initializeBean()->applyBeanPostProcessorsAfterInitialization()-->postProcessAfterInitialization()

	@Override
	public Object postProcessAfterInitialization(@Nullable Object bean, String beanName) {
		if (bean != null) {
			Object cacheKey = getCacheKey(bean.getClass(), beanName);
			if (this.earlyProxyReferences.remove(cacheKey) != bean) {
                //代理目标对象
				return wrapIfNecessary(bean, beanName, cacheKey);
			}
		}
		return bean;
	}
//-------------------------------------------------------
	protected Object wrapIfNecessary(Object bean, String beanName, Object cacheKey) {
		if (StringUtils.hasLength(beanName) && this.targetSourcedBeans.contains(beanName)) {
			return bean;
		}
		if (Boolean.FALSE.equals(this.advisedBeans.get(cacheKey))) {
			return bean;
		}
        //对当前bean是否是切面的判断，还有些其他条件 总之被代理的bean不会跳过
		if (isInfrastructureClass(bean.getClass()) || shouldSkip(bean.getClass(), beanName)) {
			this.advisedBeans.put(cacheKey, Boolean.FALSE);
			return bean;
		}

		// Create proxy if we have advice.
        //找到目标对象的所有advisor 封装成数组
		Object[] specificInterceptors = getAdvicesAndAdvisorsForBean(bean.getClass(), beanName, null);
        //如果数组不为空
		if (specificInterceptors != DO_NOT_PROXY) {
            //标记这个目标类需要被代理
			this.advisedBeans.put(cacheKey, Boolean.TRUE);
            //创建代理对象 
			Object proxy = createProxy(
					bean.getClass(), beanName, specificInterceptors, new SingletonTargetSource(bean));
			this.proxyTypes.put(cacheKey, proxy.getClass());
			return proxy;
		}

		this.advisedBeans.put(cacheKey, Boolean.FALSE);
		return bean;
	}
//----------------------------------------------------------
	@Override
	@Nullable
	protected Object[] getAdvicesAndAdvisorsForBean(
			Class<?> beanClass, String beanName, @Nullable TargetSource targetSource) {
		//找到符合条件的advisor对象集合
		List<Advisor> advisors = findEligibleAdvisors(beanClass, beanName);
		if (advisors.isEmpty()) {
			return DO_NOT_PROXY;
		}
		return advisors.toArray();
	}

//-----------------------------------------------
	protected List<Advisor> findEligibleAdvisors(Class<?> beanClass, String beanName) {
        //这里就是之前实例化advisor对象时调用的方法 再次调用的时候有缓存 就不用重复创建了
        //返回所有的advisor对象
		List<Advisor> candidateAdvisors = findCandidateAdvisors();
        //找到代理该类的advisor对象
		List<Advisor> eligibleAdvisors = findAdvisorsThatCanApply(candidateAdvisors, beanClass, beanName);
        //扩展advisor
		extendAdvisors(eligibleAdvisors);
		if (!eligibleAdvisors.isEmpty()) {
            //对当前符合条件的advisor做排序
			eligibleAdvisors = sortAdvisors(eligibleAdvisors);
		}
		return eligibleAdvisors;
	}
//---------------------------------------------------
// 找到符合条件的advisor
	protected List<Advisor> findAdvisorsThatCanApply(
			List<Advisor> candidateAdvisors, Class<?> beanClass, String beanName) {

		ProxyCreationContext.setCurrentProxiedBeanName(beanName);
		try {
			return AopUtils.findAdvisorsThatCanApply(candidateAdvisors, beanClass);
		}
		finally {
			ProxyCreationContext.setCurrentProxiedBeanName(null);
		}
	}
//----------------------------------------------
//继续深入findAdvisorsThatCanApply方法 找到符合条件的advisor
	public static List<Advisor> findAdvisorsThatCanApply(List<Advisor> candidateAdvisors, Class<?> clazz) {
		//如果没有候选的advisor 那么直接返回
        if (candidateAdvisors.isEmpty()) {
			return candidateAdvisors;
		}
		List<Advisor> eligibleAdvisors = new ArrayList<>();
		for (Advisor candidate : candidateAdvisors) {
            //如果这个advisor属于引介切面 那么就判断是否符合条件 符合就加进去
            //IntroductionAdvisor是类级别的切面？？
			if (candidate instanceof IntroductionAdvisor && canApply(candidate, clazz)) {
				eligibleAdvisors.add(candidate);
			}
		}
        //是否有引介切面
		boolean hasIntroductions = !eligibleAdvisors.isEmpty();
		for (Advisor candidate : candidateAdvisors) {
			if (candidate instanceof IntroductionAdvisor) {
				// already processed
                //处理过引介切面 这里不再处理
				continue;
			}
            //判断这个advisor能否被加入到目标类的代理对象中 就是这个方法判断的
			if (canApply(candidate, clazz, hasIntroductions)) {
                 //类和方法都匹配 那么就加入到合格的advisor中
				eligibleAdvisors.add(candidate);
			}
		}
		return eligibleAdvisors;
	}
//-----------------------------------------------------
	public static boolean canApply(Advisor advisor, Class<?> targetClass, boolean hasIntroductions) {
        //因为外面有跳过IntroductionAdvisor 一般走不到这里
		if (advisor instanceof IntroductionAdvisor) {
			return ((IntroductionAdvisor) advisor).getClassFilter().matches(targetClass);
		}
        //常见的就是走到PointcutAdvisor这里
		else if (advisor instanceof PointcutAdvisor) {
			PointcutAdvisor pca = (PointcutAdvisor) advisor;
            //这里进行条件判断 pointcut是AspectJExpressionPointcut 表达式pointcut
			return canApply(pca.getPointcut(), targetClass, hasIntroductions);
		}
		else {
			// It doesn't have a pointcut so we assume it applies.
			return true;
		}
	}
//---------------------------------------------------------
public static boolean canApply(Pointcut pc, Class<?> targetClass, boolean hasIntroductions) {
		Assert.notNull(pc, "Pointcut must not be null");
    	//先根据表达式 判断这个类是不是要符合条件
		if (!pc.getClassFilter().matches(targetClass)) {
			return false;
		}
		//获得pointcut的方法匹配器
		MethodMatcher methodMatcher = pc.getMethodMatcher();
		if (methodMatcher == MethodMatcher.TRUE) {
			// No need to iterate the methods if we're matching any method anyway...
			return true;
		}
		//查看是否是引介方法匹配器 做一下类型强转
		IntroductionAwareMethodMatcher introductionAwareMethodMatcher = null;
		if (methodMatcher instanceof IntroductionAwareMethodMatcher) {
			introductionAwareMethodMatcher = (IntroductionAwareMethodMatcher) methodMatcher;
		}

		Set<Class<?>> classes = new LinkedHashSet<>();
    	//目标类如果不是一个代理类
		if (!Proxy.isProxyClass(targetClass)) {
            //那么把它的类加到classes中
			classes.add(ClassUtils.getUserClass(targetClass));
		}
    	//然后把它的全部接口和继承加到classes中
		classes.addAll(ClassUtils.getAllInterfacesForClassAsSet(targetClass));

    	//遍历所有接口的所有方法 
		for (Class<?> clazz : classes) {
			Method[] methods = ReflectionUtils.getAllDeclaredMethods(clazz);
			for (Method method : methods) {
                //用方法匹配器逐个判断 只要有一个方法匹配 那么这个advisor就应该被加入到代理中
				if (introductionAwareMethodMatcher != null ?
						introductionAwareMethodMatcher.matches(method, targetClass, hasIntroductions) :
						methodMatcher.matches(method, targetClass)) {
					return true;
				}
			}
		}

		return false;
	}
//-----------------------------------------------
//扩展advisor方法
	@Override
	protected void extendAdvisors(List<Advisor> candidateAdvisors) {
		AspectJProxyUtils.makeAdvisorChainAspectJCapableIfNecessary(candidateAdvisors);
	}
//----------------------------------------------
//其实就是如果是切面advisor 那么就加一个ExposeInvocationInterceptor到整个advisor集合的第一位去
	public static boolean makeAdvisorChainAspectJCapableIfNecessary(List<Advisor> advisors) {
		// Don't add advisors to an empty list; may indicate that proxying is just not required
		if (!advisors.isEmpty()) {
			boolean foundAspectJAdvice = false;
			for (Advisor advisor : advisors) {
				// Be careful not to get the Advice without a guard, as this might eagerly
				// instantiate a non-singleton AspectJ aspect...
				if (isAspectJAdvice(advisor)) {
					foundAspectJAdvice = true;
					break;
				}
			}
			if (foundAspectJAdvice && !advisors.contains(ExposeInvocationInterceptor.ADVISOR)) {
				advisors.add(0, ExposeInvocationInterceptor.ADVISOR);
				return true;
			}
		}
		return false;
	}
//-------------------------------------------------------
//advisor排序
	@Override
	protected List<Advisor> sortAdvisors(List<Advisor> advisors) {
        //包装一个可排序的advisor集合 
		List<PartiallyComparableAdvisorHolder> partiallyComparableAdvisors = new ArrayList<>(advisors.size());
		for (Advisor advisor : advisors) {
            //把每一个advisor和默认的排序器做包装
			partiallyComparableAdvisors.add(
					new PartiallyComparableAdvisorHolder(advisor, DEFAULT_PRECEDENCE_COMPARATOR));
		}
        //拓扑排序
		List<PartiallyComparableAdvisorHolder> sorted = PartialOrder.sort(partiallyComparableAdvisors);
		if (sorted != null) {
            //排序结束后把advisor重新放回去
			List<Advisor> result = new ArrayList<>(advisors.size());
			for (PartiallyComparableAdvisorHolder pcAdvisor : sorted) {
				result.add(pcAdvisor.getAdvisor());
			}
			return result;
		}
		else {
			return super.sortAdvisors(advisors);
		}
	}
//---------------------------------------
//创建代理对象
//targetSource是包装的原bean specificInterceptors是advisor集合
	protected Object createProxy(Class<?> beanClass, @Nullable String beanName,
			@Nullable Object[] specificInterceptors, TargetSource targetSource) {
		//如果beanFactory属于ConfigurableListableBeanFactory
		if (this.beanFactory instanceof ConfigurableListableBeanFactory) {
            //这里就是把beanClass 原目标类 记录到了原目标的beanDefinition的属性中去了
			AutoProxyUtils.exposeTargetClass((ConfigurableListableBeanFactory) this.beanFactory, beanName, beanClass);
		}

		ProxyFactory proxyFactory = new ProxyFactory();
        //AnnotationAwareAspectJAutoProxyCreator是ProxyConfig的子类 有许多关于proxy的配置项 
        //这里就是由AnnotationAwareAspectJAutoProxyCreator进行配置ProxyFactory
		proxyFactory.copyFrom(this);
		//根据proxy-target-class配置 来确定是jdk代理还是cglib代理
		if (!proxyFactory.isProxyTargetClass()) {
            //判断是否应该直接代理目标类（cglib）
			if (shouldProxyTargetClass(beanClass, beanName)) {
				proxyFactory.setProxyTargetClass(true);
			}
            //否则计算一下目标类的接口（jdk）
            //如果没有接口直接就cglib了
			else {
				evaluateProxyInterfaces(beanClass, proxyFactory);
			}
		}
		//构造advisor对象数组
		Advisor[] advisors = buildAdvisors(beanName, specificInterceptors);
        //把所有advisor加入到工厂里面
		proxyFactory.addAdvisors(advisors);
        //设置一下源目标对象
		proxyFactory.setTargetSource(targetSource);
        //空的配置
		customizeProxyFactory(proxyFactory);
		//设置一下frozen属性
		proxyFactory.setFrozen(this.freezeProxy);
		if (advisorsPreFiltered()) {
            //设置preFiltered属性
			proxyFactory.setPreFiltered(true);
		}
		//开始创建代理
		return proxyFactory.getProxy(getProxyClassLoader());
	}

//-------------------------------------------------------------------------

protected Advisor[] buildAdvisors(@Nullable String beanName, @Nullable Object[] specificInterceptors) {
		// Handle prototypes correctly...
		Advisor[] commonInterceptors = resolveInterceptorNames();

		List<Object> allInterceptors = new ArrayList<>();
		if (specificInterceptors != null) {
			allInterceptors.addAll(Arrays.asList(specificInterceptors));
			if (commonInterceptors.length > 0) {
				if (this.applyCommonInterceptorsFirst) {
					allInterceptors.addAll(0, Arrays.asList(commonInterceptors));
				}
				else {
					allInterceptors.addAll(Arrays.asList(commonInterceptors));
				}
			}
		}
		if (logger.isTraceEnabled()) {
			int nrOfCommonInterceptors = commonInterceptors.length;
			int nrOfSpecificInterceptors = (specificInterceptors != null ? specificInterceptors.length : 0);
			logger.trace("Creating implicit proxy for bean '" + beanName + "' with " + nrOfCommonInterceptors +
					" common interceptors and " + nrOfSpecificInterceptors + " specific interceptors");
		}

		Advisor[] advisors = new Advisor[allInterceptors.size()];
		for (int i = 0; i < allInterceptors.size(); i++) {
            //？？？这里面的包装？？
			advisors[i] = this.advisorAdapterRegistry.wrap(allInterceptors.get(i));
		}
		return advisors;
	}
//-------------------------------------------
	public Object getProxy(@Nullable ClassLoader classLoader) {
        //创建代理
		return createAopProxy().getProxy(classLoader);
	}
//--------------------------------------------
//创建代理
	protected final synchronized AopProxy createAopProxy() {
		if (!this.active) {
            //激活代理
			activate();
		}
        //AopProxy的代理工厂 根据proxyFactory创建代理
		return getAopProxyFactory().createAopProxy(this);
	}
//----------------------------------------------
//创建代理 config是之前配置的proxyFactory
	@Override
	public AopProxy createAopProxy(AdvisedSupport config) throws AopConfigException {
        //如果cofig配置为优化 或者 是直接代理目标类 或者 没有接口
		if (config.isOptimize() || config.isProxyTargetClass() || hasNoUserSuppliedProxyInterfaces(config)) {
			Class<?> targetClass = config.getTargetClass();
			if (targetClass == null) {
				throw new AopConfigException("TargetSource cannot determine target class: " +
						"Either an interface or a target is required for proxy creation.");
			}
            //如果目标是接口 或者 目标是代理类
            //那么就选择jdk代理
			if (targetClass.isInterface() || Proxy.isProxyClass(targetClass)) {
				return new JdkDynamicAopProxy(config);
			}
            //否则就选择cglib代理
			return new ObjenesisCglibAopProxy(config);
		}
		else {
			return new JdkDynamicAopProxy(config);
		}
	}
//-------------------------------------------------
//进入AopProxy代理
	@Override
	public Object getProxy(@Nullable ClassLoader classLoader) {
		if (logger.isTraceEnabled()) {
			logger.trace("Creating CGLIB proxy: " + this.advised.getTargetSource());
		}

		try {
            //获取源目标对象
			Class<?> rootClass = this.advised.getTargetClass();
			Assert.state(rootClass != null, "Target class must be available for creating a CGLIB proxy");

			Class<?> proxySuperClass = rootClass;
            //看看源目标对象是否是代理类
			if (rootClass.getName().contains(ClassUtils.CGLIB_CLASS_SEPARATOR)) {
				proxySuperClass = rootClass.getSuperclass();
				Class<?>[] additionalInterfaces = rootClass.getInterfaces();
				for (Class<?> additionalInterface : additionalInterfaces) {
					this.advised.addInterface(additionalInterface);
				}
			}

			// Validate the class, writing log messages as necessary.
			validateClassIfNecessary(proxySuperClass, classLoader);

			// Configure CGLIB Enhancer...
            //开始创建代理对象 new Enhancer();
			Enhancer enhancer = createEnhancer();
            //设置类加载器
			if (classLoader != null) {
				enhancer.setClassLoader(classLoader);
				if (classLoader instanceof SmartClassLoader &&
						((SmartClassLoader) classLoader).isClassReloadable(proxySuperClass)) {
					enhancer.setUseCache(false);
				}
			}
             //设置父类
			enhancer.setSuperclass(proxySuperClass);
             //设置代理的接口
			enhancer.setInterfaces(AopProxyUtils.completeProxiedInterfaces(this.advised));
             //设置命名策略
			enhancer.setNamingPolicy(SpringNamingPolicy.INSTANCE);
             //设置生成策略
			enhancer.setStrategy(new ClassLoaderAwareGeneratorStrategy(classLoader));
			//获得回调数组
			Callback[] callbacks = getCallbacks(rootClass);
			Class<?>[] types = new Class<?>[callbacks.length];
			for (int x = 0; x < types.length; x++) {
				types[x] = callbacks[x].getClass();
			}
			// fixedInterceptorMap only populated at this point, after getCallbacks call above
			enhancer.setCallbackFilter(new ProxyCallbackFilter(
					this.advised.getConfigurationOnlyCopy(), this.fixedInterceptorMap, this.fixedInterceptorOffset));
			enhancer.setCallbackTypes(types);

			// Generate the proxy class and create a proxy instance.
			return createProxyClassAndInstance(enhancer, callbacks);
		}
		catch (CodeGenerationException | IllegalArgumentException ex) {
			throw new AopConfigException("Could not generate CGLIB subclass of " + this.advised.getTargetClass() +
					": Common causes of this problem include using a final class or a non-visible class",
					ex);
		}
		catch (Throwable ex) {
			// TargetSource.getTarget() failed
			throw new AopConfigException("Unexpected AOP exception", ex);
		}
	}
//------------------------------------------------------
//计算代理接口
static Class<?>[] completeProxiedInterfaces(AdvisedSupport advised, boolean decoratingProxy) {
    	//获得proxyFactory配置的代理接口
		Class<?>[] specifiedInterfaces = advised.getProxiedInterfaces();
 		//如果没有的话
		if (specifiedInterfaces.length == 0) {
			// No user-specified interfaces: check whether target class is an interface.
			Class<?> targetClass = advised.getTargetClass();
			if (targetClass != null) {
                //这个类如果是接口的话 那么就把加入到代理接口里面
				if (targetClass.isInterface()) {
					advised.setInterfaces(targetClass);
				}
                //如果是代理类的话 那么就把它的接口加入到代理接口里面
				else if (Proxy.isProxyClass(targetClass)) {
					advised.setInterfaces(targetClass.getInterfaces());
				}
				specifiedInterfaces = advised.getProxiedInterfaces();
			}
		}
    	//是否加入SpringProxy接口
		boolean addSpringProxy = !advised.isInterfaceProxied(SpringProxy.class);
   		//是否加入Advised接口
		boolean addAdvised = !advised.isOpaque() && !advised.isInterfaceProxied(Advised.class);
         //是否加入DecoratingProxy接口
		boolean addDecoratingProxy = (decoratingProxy && !advised.isInterfaceProxied(DecoratingProxy.class));
		int nonUserIfcCount = 0;
		if (addSpringProxy) {
			nonUserIfcCount++;
		}
		if (addAdvised) {
			nonUserIfcCount++;
		}
		if (addDecoratingProxy) {
			nonUserIfcCount++;
		}
    	//整合上面的接口集合
		Class<?>[] proxiedInterfaces = new Class<?>[specifiedInterfaces.length + nonUserIfcCount];
		System.arraycopy(specifiedInterfaces, 0, proxiedInterfaces, 0, specifiedInterfaces.length);
		int index = specifiedInterfaces.length;
    	//把对应的接口加入到集合中
		if (addSpringProxy) {
            //SpringProxy标识是否是spring生成的代理
			proxiedInterfaces[index] = SpringProxy.class;
			index++;
		}
		if (addAdvised) {
            //Advised接口里面全是代理配置
			proxiedInterfaces[index] = Advised.class;
			index++;
		}
		if (addDecoratingProxy) {
            //这个玩意好像跟之前那个@DeclareParents 有关？？
			proxiedInterfaces[index] = DecoratingProxy.class;
		}
		return proxiedInterfaces;
	}
//-------------------------------------
//获得callback数组
	private Callback[] getCallbacks(Class<?> rootClass) throws Exception {
		// Parameters used for optimization choices...
        //proxyFactory配置的属性
		boolean exposeProxy = this.advised.isExposeProxy();
		boolean isFrozen = this.advised.isFrozen();
		boolean isStatic = this.advised.getTargetSource().isStatic();

		// Choose an "aop" interceptor (used for AOP calls).
        //新建一个DynamicAdvisedInterceptor对象作为aop拦截器
		Callback aopInterceptor = new DynamicAdvisedInterceptor(this.advised);

		// Choose a "straight to target" interceptor. (used for calls that are
		// unadvised but can return this). May be required to expose the proxy.
        //目标拦截器
		Callback targetInterceptor;
        //是否暴漏代理
		if (exposeProxy) {
            //根据isStatic决定创建的拦截器 isStatic默认为true
			targetInterceptor = (isStatic ?
					new StaticUnadvisedExposedInterceptor(this.advised.getTargetSource().getTarget()) :
					new DynamicUnadvisedExposedInterceptor(this.advised.getTargetSource()));
		}
		else {
			targetInterceptor = (isStatic ?
					new StaticUnadvisedInterceptor(this.advised.getTargetSource().getTarget()) :
					new DynamicUnadvisedInterceptor(this.advised.getTargetSource()));
		}

		// Choose a "direct to target" dispatcher (used for
		// unadvised calls to static targets that cannot return this).
        //创建目标的选择器
		Callback targetDispatcher = (isStatic ?
				new StaticDispatcher(this.advised.getTargetSource().getTarget()) : new SerializableNoOp());
		//创建callback数组 
		Callback[] mainCallbacks = new Callback[] {
				aopInterceptor,  // for normal advice
				targetInterceptor,  // invoke target without considering advice, if optimized
				new SerializableNoOp(),  // no override for methods mapped to this
				targetDispatcher, this.advisedDispatcher,
				new EqualsInterceptor(this.advised),
				new HashCodeInterceptor(this.advised)
		};

		Callback[] callbacks;

		// If the target is a static one and the advice chain is frozen,
		// then we can make some optimizations by sending the AOP calls
		// direct to the target using the fixed chain for that method.
		if (isStatic && isFrozen) {
			Method[] methods = rootClass.getMethods();
			Callback[] fixedCallbacks = new Callback[methods.length];
			this.fixedInterceptorMap = new HashMap<>(methods.length);

			// TODO: small memory optimization here (can skip creation for methods with no advice)
			for (int x = 0; x < methods.length; x++) {
				Method method = methods[x];
				List<Object> chain = this.advised.getInterceptorsAndDynamicInterceptionAdvice(method, rootClass);
				fixedCallbacks[x] = new FixedChainStaticTargetInterceptor(
						chain, this.advised.getTargetSource().getTarget(), this.advised.getTargetClass());
				this.fixedInterceptorMap.put(method, x);
			}

			// Now copy both the callbacks from mainCallbacks
			// and fixedCallbacks into the callbacks array.
			callbacks = new Callback[mainCallbacks.length + fixedCallbacks.length];
			System.arraycopy(mainCallbacks, 0, callbacks, 0, mainCallbacks.length);
			System.arraycopy(fixedCallbacks, 0, callbacks, mainCallbacks.length, fixedCallbacks.length);
			this.fixedInterceptorOffset = mainCallbacks.length;
		}
		else {
			callbacks = mainCallbacks;
		}
		return callbacks;
	}
```













#### 执行拦截器链





### AspectJAwareAdvisorAutoProxyCreator



### @Configuration标注的类被代理保证@Bean单例？





## 事务

### 事务源码



### 事务的传播特性

| 传播特性                  | 含义                                                         |
| ------------------------- | ------------------------------------------------------------ |
| PROPAGATION_REQUIRED      | 表示当前方法必须运行在事务中。如果当前事务存在，方法将会在该事务中运行。否则，会启动一个新的事务 |
| PROPAGATION_SUPPORTS      | 表示当前方法不需要事务上下文，但是如果存在当前事务的话，那么该方法会在这个事务中运行 |
| PROPAGATION_MANDATORY     | 表示该方法必须在事务中运行，如果当前事务不存在，则会抛出一个异常 |
| PROPAGATION_REQUIRED_NEW  | 表示当前方法必须运行在它自己的事务中。一个新的事务将被启动。如果存在当前事务，在该方法执行期间，当前事务会被挂起。如果使用JTATransactionManager的话，则需要访问TransactionManager |
| PROPAGATION_NOT_SUPPORTED | 表示该方法不应该运行在事务中。如果存在当前事务，在该方法运行期间，当前事务将被挂起。如果使用JTATransactionManager的话，则需要访问TransactionManager |
| PROPAGATION_NEVER         | 表示当前方法不应该运行在事务上下文中。如果当前正有一个事务在运行，则会抛出异常 |
| PROPAGATION_NESTED        | 表示如果当前已经存在一个事务，那么该方法将会在嵌套事务中运行。嵌套的事务可以独立于当前事务进行单独地提交或回滚。如果当前事务不存在，那么其行为与PROPAGATION_REQUIRED一样。注意各厂商对这种传播行为的支持是有所差异的。可以参考资源管理器的文档来确认它们是否支持嵌套事务 |



### 注意事项

当内外都是@Transactional(propagation = Propagation.REQUIRED)传播特性

当内外层采用相同事务的时候，如果内层出错被外层的try-catch捕获，整体事务依然回滚，因为内层被代理的对象，在执行方法出现异常时将共用的事务状态设置为回滚，所以在回到外层的时候即使捕获异常，也会整体回滚。例如

```java
    @Transactional(propagation = Propagation.REQUIRED)
    public void updateStock(int id){
        String sql = "update book_stock set stock=stock-1 where id=?";
        jdbcTemplate.update(sql,id);
        //抛出异常
        for (int i = 1 ;i>=0 ;i--)
            // ÷0
            System.out.println(10/i);
    }
    @Transactional(propagation = Propagation.REQUIRED)
 	public void checkout(String username,int id){
        try {
            //外层捕获 依然整体回滚
            bookDao.updateStock(id);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

```



当内部是@Transactional(propagation = Propagation.NESTED) 嵌套的传播特性时，从外部进入内部时，使用同一个事务，但是内部会开启一个保存点，当内部事务出现异常的时候，回滚到保存点结束事务，不影响外部事务的正常提交/回滚操作

```java
@Transactional(propagation = Propagation.REQUIRED)
    public void checkout(String username,int id){
        try {
            bookDao.updateStock(id);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

 @Transactional(propagation = Propagation.NESTED)
    public void updateStock(int id){
        String sql = "update book_stock set stock=stock-1 where id=?";
        jdbcTemplate.update(sql,id);
        //抛出异常
        for (int i = 1 ;i>=0 ;i--)
            System.out.println(10/i);
    }
```



MySQL在事务中支持保存点

```sql
set autocommit = 0;
update book_stock set book_stock.stock = stock - 1;
update book_stock set book_stock.stock = stock - 1;
update book_stock set book_stock.stock = stock - 1;
savepoint p1;
update book_stock set book_stock.stock = stock - 1;
update book_stock set book_stock.stock = stock - 1;
update book_stock set book_stock.stock = stock - 1;
rollback to savepoint p1;
commit ;
```













## spring中的其他知识

### ClassUtils

```java
//JDK自带的
package java.lang;
public final class Class<T> implements java.io.Serializable,
                              GenericDeclaration,
                              Type,
                              AnnotatedElement {
    //判断参数cls是不是和此对象类型相同/是此类型的子类
    //针对于基本数据类型 int.class double.class void.class ....等9个（8个基本+void）
    //只有完全相等的时候才会返回true 即 int.class.isAssignableFrom(int.class);
    @HotSpotIntrinsicCandidate
    public native boolean isAssignableFrom(Class<?> cls);
}
------------------------------------------------------------------------------------------
//spring封装的
package org.springframework.util;
public abstract class ClassUtils {
		//封装了JDK自带的isAssignableFrom方法 原生jdk中自带的不支持判断包装类的情况 封装后可以支持对包装类和基本数据类型的转换比较
    	public static boolean isAssignable(Class<?> lhsType, Class<?> rhsType) {
		Assert.notNull(lhsType, "Left-hand side type must not be null");
		Assert.notNull(rhsType, "Right-hand side type must not be null");
         //当判断完rhsType是不是lhsType的子类/本类型后 如果是 那直接返回
		if (lhsType.isAssignableFrom(rhsType)) {
			return true;
		}
         //如果不是 那么再进行一步判断 其实就是把lhsType和rhsType要么都转换成int.class这样的基本类型比较
         //要么就是都int.class都转换成Integer.class这样的包装类比较
		if (lhsType.isPrimitive()) {
            //primitiveWrapperTypeMap 注意看 这个是包装类转int基本类的map
			Class<?> resolvedPrimitive = primitiveWrapperTypeMap.get(rhsType);
            //int.class这样的基本类型完全相同才返回
			return (lhsType == resolvedPrimitive);
		}
		else {
            //primitiveTypeToWrapperMap 这个是int.class转包装类
			Class<?> resolvedWrapper = primitiveTypeToWrapperMap.get(rhsType);
            //包装类之间就需要通过isAssignableFrom判断子父类继承关系
			return (resolvedWrapper != null && lhsType.isAssignableFrom(resolvedWrapper));
		}
	}
}
------------------------------------------------------------------------------------------


//例子
      	Object object = new Object();
		//JDK原生的
        System.out.println(object.getClass().isAssignableFrom(int.class));
        System.out.println(object.getClass().isAssignableFrom(Integer.class));
        System.out.println(int.class.isAssignableFrom(int.class));
        System.out.println("-------------------");
        System.out.println(ClassUtils.isAssignable(object.getClass(), int.class));
        System.out.println(ClassUtils.isAssignable(int.class, Integer.class));
------------------------------------------------------------------------------------------
false
true
true
-------------------
true
true
-------------------------------------------------------------------------------------------
//MethodInvoker中的权重计算方法
public static int getTypeDifferenceWeight(Class<?>[] paramTypes, Object[] args) {
		int result = 0;
		for (int i = 0; i < paramTypes.length; i++) {
			if (!ClassUtils.isAssignableValue(paramTypes[i], args[i])) {
				return Integer.MAX_VALUE;
			}
			if (args[i] != null) {
				Class<?> paramType = paramTypes[i];
                  //getSuperclass在此对象的上层只有接口时 直接返回Object.class
				Class<?> superClass = args[i].getClass().getSuperclass();
				while (superClass != null) {
					if (paramType.equals(superClass)) {
						result = result + 2;
						superClass = null;
					}
					else if (ClassUtils.isAssignable(paramType, superClass)) {
						result = result + 2;
						superClass = superClass.getSuperclass();
					}
					else {
						superClass = null;
					}
				}
				if (paramType.isInterface()) {
					result = result + 1;
				}
			}
		}
		return result;
	}
//关于权重计算的例子 
//这个方法
public Constructor1(Object obj,Integer i,UserDao str){}
//继承关系
Integer extends Number extends Object
UserDaoImpl implements UserDao
//传递参数
{
new Integer(1),
new Integer(1),
new UserDaoImpl()
}

//权重计算过程为：
/**
先比较第一个参数：
	参数类型为Object，给定参数对象类型为Integer，继承自Object;
	给定参数对象类型为Integer，父类为Number，继承自Object，权重+2，当前权重为2
	父类Number的父类为Object，同参数类型相同，权重+2，当前权重为4，结束循环
	参数类型为Object不是接口，第一个参数权重计算完毕
比较第二个参数
	参数类型为Integer，给定参数类型为Integer，符合ClassUtils.isAssignableValue的判断条件
	给定参数对象类型为Integer，父类为Number，并非继承或实现自Integer，结束循环，当前权重为4
	参数类型Integer不是接口，第二个参数权重计算完毕
比较第三个参数
	参数类型为UserDao，给定参数对象类型为UserDaoImpl，实现自UserDao;
	给定参数对象类型为UserDaoImpl，父类为Object，并非继承或实现自UserDao，结束循环，当前权重为4
	参数类型UserDao是一个接口，权重+1，当前权重为5，第三个参数权重计算完毕
**/

	
```

### Objenesis 

位于org.springframework.objenesis包下，spring框架自带的。

> [Objenesis](http://objenesis.org/index.html)是一个轻量级的Java库，作用是**绕过构造器创建一个实例**。
>
> Java已经支持通过Class.newInstance()动态实例化Java类，但是这需要Java类有个适当的构造器。很多时候一个Java类无法通过这种途径创建，例如：
>
> - 构造器需要参数
>
> - 构造器有副作用
> - 构造器会抛出异常
>
> Objenesis可以绕过上述限制。它一般用于：
>
> - 序列化、远程处理和持久化：无需调用代码即可将Java类实例化并存储特定状态。
> - 代理、AOP库和Mock对象：可以创建特定Java类的子类而无需考虑super()构造器。
> - 容器框架：可以用非标准方式动态实例化Java类。例如Spring引入Objenesis后，Bean不再必须提供无参构造器了。

```java
package com.example.demo.objenesis;

import org.springframework.objenesis.Objenesis;
import org.springframework.objenesis.ObjenesisStd;
import org.springframework.objenesis.instantiator.ObjectInstantiator;

public class TestObjenesis {
    public static void main(String[] args) {
        Objenesis objenesis = new ObjenesisStd();
        ObjectInstantiator<TestObjenesis> instantiatorOf = objenesis.getInstantiatorOf(TestObjenesis.class);
        TestObjenesis testObjenesis = instantiatorOf.newInstance();
        System.out.println(testObjenesis);
    }
}
```

### 类加载器

在Java中存在三种原生的类加载器：

- AppClassloader：它负责在 JVM 启动时，**加载来自在命令java中的-classpath**或者java.class.path系统属性或者 CLASSPATH 操作系统属性所指定的 JAR 类包和类路径。
- ExtClassloader：主要负责加载 Java 的扩展类库
- BootstrapClassloader：主要加载JVM自身工作需要的类：将%JAVA_HOME%\lib路径下或-Xbootclasspath参数指定路径下的、能被虚拟机识别的类库（仅按照文件名识别，如：rt.jar，名字不符合的类库不会被加载）加载至虚拟机内存中。

以上三种基于jdk1.8

## jdk中的有趣知识

### Robot类

Robot类用于为测试自动化、自运行演示程序和其他需要控制鼠标和键盘的应用程序生成本机系统输入事件。Robot 的主要目的是便于 Java 平台实现自动测试。

可以用来写木马病毒~

```java
//例子：
//模拟按下键盘
 		Robot robot = new Robot();
        robot.keyPress(KeyEvent.VK_CONTROL); //按下ctrl健
        robot.keyPress(KeyEvent.VK_V);//按下v健
//截图
 		BufferedImage bi = robot.createScreenCapture(new Rectangle(0, 0,
                Toolkit.getDefaultToolkit().getScreenSize().width, Toolkit
                .getDefaultToolkit().getScreenSize().height));
//锁死鼠标
 		Point p = MouseInfo.getPointerInfo().getLocation();//获得当前鼠标位置
            while (flag) {
                try {
                    Thread.sleep(1);
                    robot.mouseMove(p.x, p.y);//死循环设置鼠标位置
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
```



### windows注册表知识

> **HKEY_CURRENT_USER\Software\Microsoft\Windows\CurrentVersion\Policies\Explorer\Run**
> 这个路径表示在当前用户的注册表中，有一个用于存储启动时运行程序信息的位置。Run键通常包含一些字符串值，这些值对应于要在用户登录时自动启动的应用程序或脚本的路径和参数。通过编辑这个注册表键下的值，可以管理开机启动项。

```java
String key = "HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Policies\\Explorer\\Run";
String name = "jx";
String value = "C:\\WINDOWS\\jx.jar";
String command = "reg add " + key + " /v " + name + " /d " + value;
/**
key变量定义了注册表的路径，指向“当前用户”（HKEY_CURRENT_USER）下的一个位置，这个位置通常用来设置开机启动项。
name变量是将要添加到指定键下的注册表值名称，这里是"jx"。
value变量是要关联给该名称的值数据，即开机启动时要执行的命令或程序路径，这里是一个Java JAR文件路径："C:\WINDOWS\jx.jar"。

最终生成的command字符串是一个完整的注册表命令行指令，当在命令提示符窗口执行后，会在注册表的指定位置创建（或修改）一个名为"jx"的键值对，使得每次系统启动时会自动运行"C:\WINDOWS\jx.jar"中的程序。
**/

```

### 获取当前类所在jar或目录的路径

```java
String path = Test.class.getProtectionDomain().getCodeSource()
                .getLocation().getFile();
```

### 获取本地IP 

```java
//这个方法并不能拿到在互联网中的ip 只能拿到局域网中的ip    
String ipString = "";
        Enumeration<NetworkInterface> netInterfaces = null;
        try {
            netInterfaces = NetworkInterface.getNetworkInterfaces();
            while (netInterfaces.hasMoreElements()) {
                NetworkInterface ni = netInterfaces.nextElement();
                ipString = ipString + ni.getDisplayName() + "\n";
                ipString = ipString + ni.getName() + "\n";
                Enumeration<InetAddress> ips = ni.getInetAddresses();
                while (ips.hasMoreElements()) {
                    ipString = ipString + ips.nextElement().getHostAddress()
                            + "\n";
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println(ipString);
/**
Intel(R) Ethernet Connection (17) I219-LM 是指计算机中使用的英特尔以太网控制器型号，它负责处理计算机与网络之间的数据传输。

eth0 是Linux系统中对这个网络接口的命名，它是默认的第一个以太网设备名称。在其他操作系统（如Windows）中，可能会显示为不同的名称，例如“本地连接”或带有数字编号的名称。

172.16.14.25 是该网络接口的IPv4地址，这是一个私有IP地址，表明这台计算机在网络内部进行通信，而非直接暴露于公共互联网。

fe80:0:0:0:a693:80fd:b25e:f9f5%eth0 是该网络接口的IPv6地址，前缀fe80:表示这是一个链路本地地址，仅用于同一链路上的通信。末尾的%eth0指示了此地址关联到的网络接口名称。
**/
```

### 闪屏

```java
class Flash {
    JFrame frame;
    JPanel pane;
    Color c[] = {  Color.pink,Color.white,Color.blue};
    int i;
    Image offScreenImage = null;
    String msg;
    public Flash(String s) {
        //实际上是造了一个frame 里面装一个pane
        msg=s;
        final int width=Toolkit.getDefaultToolkit().getScreenSize().width;
        final int height=Toolkit.getDefaultToolkit().getScreenSize().height;
        frame = new JFrame();
        frame.setAlwaysOnTop(true);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setUndecorated(true);
        frame.setBounds(0,0,width,height);
        pane = new JPanel() {
            //关键在于重写pane的重新绘制方法
            public void paint(Graphics g) {
                //创造一个和屏幕一样大的图像
                if(offScreenImage == null){
                    offScreenImage=this.createImage(width, height);
                }
                Graphics gg=offScreenImage.getGraphics();
                gg.setFont(new Font(null, Font.PLAIN, 50));
                //设置颜色
                gg.setColor(c[i]);
                gg.fillRect(0, 0, width, height);
                gg.setColor(Color.black);
                gg.drawString(msg, 200, 50);
                //画在pane上
                g.drawImage(offScreenImage, 0, 0, null);
            }
        };
        frame.setContentPane(pane);
        frame.setVisible(true);
        new Thread() {
            public void run() {
                int time=0;
                while (i < c.length) {
                    //循环重画pane
                    Flash.this.myUpdate();
                    try {
                        Thread.sleep(50);
                        time++;
                        if(time==100){
                            //销毁pane
                            frame.dispose();
                            break;
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }
    public void myUpdate() {
        if (i == c.length-1) {
            i = 0;
        } else {
            i++;
        }
        //循环重画
        pane.repaint();
    }
}
```

### 剪切板

```java
//获取剪切板内容

public class ClipboardDemo {
    public static void main(String[] args) throws Exception {
        // 获取系统默认的剪贴板
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

        // 获取剪贴板的内容
        Transferable contents = clipboard.getContents(null);

        // 检查剪贴板内容是否为文本类型
        if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            // 将内容转换为文本形式
            String text = (String) contents.getTransferData(DataFlavor.stringFlavor);
            
            System.out.println("Clipboard content: " + text);
        } else {
            System.out.println("No string data in the clipboard.");
        }
    }
}

//设置剪切板内容

import java.awt.*;
import java.awt.datatransfer.*;

public class ClipboardSetDemo {
    public static void main(String[] args) {
        String textToCopy = "Hello, this is the text to be copied to clipboard.";

        // 获取系统默认的剪贴板
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

        // 创建一个字符串选择器并设置要复制的内容
        StringSelection stringSelection = new StringSelection(textToCopy);

        // 将文本内容放入剪贴板
        clipboard.setContents(stringSelection, null);
        
        System.out.println("Text has been copied to the clipboard.");
    }
}
```







## 事件

### 为什么服务启动阶段，Spring Event 事件丢失了？

我们公司遇到的情况是， Kafka conumser 在 `init-method` 阶段开始消费，然而 Spring EventListener 被注册进 Spring 的时间点滞后于 `init-method` 时间点，所以 Kafka Consumer 中使用 Spring Event 发布事件时，没有找到监听者，出现消息处理丢失的情况。

从下图中可以看到 `init-method` 时间点 滞后于 EventListener 被注册的时间点。

![img](https://mmbiz.qpic.cn/mmbiz_png/eQPyBffYbuc2SLDhkR0TYXe6fyIGbjacKzL2ZjyPdlNNt1Hf8cKZAnjwsmDqt6IX1L7SibN3nia3KCkSSNqtSfWQ/640?wx_fmt=png&from=appmsg&wxfrom=13&tp=wxpic)

简单来说：SpringBoot 会在Spring完全启动完成后，才开启Http流量。这给了我们启示：应该在Spring启动完成后开启入口流量。Rpc和 MQ流量 也应该如此，所以建议大家 在 `SmartLifecype` 或者 `ContextRefreshedEvent` 等位置 注册服务，开启流量。

> **最佳实践是：改造系统开启入口流量（Http、MQ、RPC）的时机，确保在Spring 启动完成后开启入口流量。**



### 关于@PostMapping

```java
//此行代表 接收的content-type是application/x-www-form-urlencoded 而输出的content-type为application/json
@PostMapping(value = "xxx",consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,produces = MediaType.APPLICATION_JSON_VALUE)

@RequestParam 只处理application/x-www-form-urlencoded
    
@RequestBody 只能处理content-type为application/json的数据请求
    
当使用MultipartFile时 consumes自动设置为multipart/form-data 接收文件+其他参数
    
当单纯接收二进制数据时使用 application/octet-stream     
```

当使用 application/x-www-form-urlencoded时 并不是 http://www.baidu.com?key=value 的形式 而是

```http
POST /submit_form HTTP/1.1
Host: example.com
Content-Type: application/x-www-form-urlencoded

key1=value1&key2=value2
```

当使用 application/json时

```http
POST /submit_form HTTP/1.1
Host: example.com
Content-Type: application/x-www-form-urlencoded

{
    "schoolId":432630,
    "productId":81,
    "addPurchaseMoney":3000,
    "addPurchaseMoneyRemark":"xxx",
    "serviceList":[
        {
            "productServiceId": 29,
            "addPurchaseYear": 1
        }
    ]
}
```

