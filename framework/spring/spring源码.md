# Spring源码



## 整体概述

![image-20250213102918979](./images/image-20250213102918979.png)

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

![image-20250213103145924](./images/image-20250213103145924.png)

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

<img src="./images/image-20250213105127872.png" alt="image-20250213105127872" style="zoom: 33%;" />

- 继承了ResourceLoader和ResourcePatternResolver，拥有了资源加载和资源路径解析的能力
- 继承了ApplicationEventPublisher，具有应用事件推送的能力
- 继承了MessageSource，具有国际化参数的能力
- 继承了BeanFactory，在ApplicationContext中具有了控制和管理BeanFactory的能力



以核心类**DefaultListableBeanFactory**为例，其主要作用只有两个，管理Bean和BeanDefinition

<img src="./images/image-20250213105246688.png" alt="image-20250213105246688" style="zoom: 50%;" />

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
> <img src="./images/image-20250213111616906.png" alt="image-20250213111616906" style="zoom: 50%;" />

### AbstractEnvironment

```java
public abstract class AbstractEnvironment implements ConfigurableEnvironment {
    
    //是否禁止使用getenv
	public static final String IGNORE_GETENV_PROPERTY_NAME = "spring.getenv.ignore";
    //当前使用的配置文件 spring.profiles.active=prod
    public static final String ACTIVE_PROFILES_PROPERTY_NAME = "spring.profiles.active";
    public static final String DEFAULT_PROFILES_PROPERTY_NAME = "spring.profiles.default";
	protected static final String RESERVED_DEFAULT_PROFILE_NAME = "default";


	protected final Log logger = LogFactory.getLog(getClass());

	private final Set<String> activeProfiles = new LinkedHashSet<>();

	private final Set<String> defaultProfiles = new LinkedHashSet<>(getReservedDefaultProfiles());

	private final MutablePropertySources propertySources = new MutablePropertySources();

	private final ConfigurablePropertyResolver propertyResolver =
			new PropertySourcesPropertyResolver(this.propertySources);

}
```

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

<img src="./images/image-20250213113417680.png" alt="image-20250213113417680" style="zoom:50%;" />

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

<img src="./images/image-20250225111422617.png" alt="image-20250225111422617" style="zoom:50%;" />

<img src="./images/image-20250225111650181.png" alt="image-20250225111650181" style="zoom: 33%;" />

<img src="./images/image-20250225112032475.png" alt="image-20250225112032475" style="zoom:33%;" />

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

<img src="./images/image-20250225112055062.png" alt="image-20250225112055062" style="zoom:33%;" />

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

```java
public abstract class AbstractApplicationContext extends DefaultResourceLoader
		implements ConfigurableApplicationContext {
    
    //当前ApplicationContext下包含的BFPP
    private final List<BeanFactoryPostProcessor> beanFactoryPostProcessors = new ArrayList<>();

    protected void invokeBeanFactoryPostProcessors(ConfigurableListableBeanFactory beanFactory) {
        //调用执行BeanFactoryPostProcessors 
        //getBeanFactoryPostProcessors()获取当前ApplicationContext下包含的BFPP
        PostProcessorRegistrationDelegate.invokeBeanFactoryPostProcessors(beanFactory, getBeanFactoryPostProcessors());

        // Detect a LoadTimeWeaver and prepare for weaving, if found in the meantime
        // (e.g. through an @Bean method registered by ConfigurationClassPostProcessor)
        if (beanFactory.getTempClassLoader() == null && beanFactory.containsBean(LOAD_TIME_WEAVER_BEAN_NAME)) {
           beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
           beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
        }
    }
}
```



### invokeBeanFactoryPostProcessors

BeanFactoryPostProcessor调用执行流程

- 先执行外部集合的postProcessBeanDefinitionRegistry
- 获取容器中的BeanDefinitionRegistryPostProcessor中实现了PriorityOrdered接口的bean，排序并调用postProcessBeanDefinitionRegistry
- 再获取容器中的BeanDefinitionRegistryPostProcessor中实现了Ordered接口的bean，排序并调用postProcessBeanDefinitionRegistry
- 再获取容器中没调用的BeanDefinitionRegistryPostProcessor，排序并调用postProcessBeanDefinitionRegistry
- 将上述的BeanDefinitionRegistryPostProcessor调用postProcessBeanFactory，调用外部集合的postProcessBeanFactory
- 接下来处理容器中的BeanFactoryPostProcessor
- 获取容器中所有的BeanFactoryPostProcessor的bean名称，分别按照PriorityOrdered、Ordered和无排序区分三个集合
- 针对实现了PriorityOrdered接口的BeanFactoryPostProcessor实例化并调用postProcessBeanFactory
- 针对实现了Ordered接口的BeanFactoryPostProcessor实例化并调用postProcessBeanFactory
- 针对实现了无排序的BeanFactoryPostProcessor实例化并调用postProcessBeanFactory

```java
public static void invokeBeanFactoryPostProcessors(
       ConfigurableListableBeanFactory beanFactory, List<BeanFactoryPostProcessor> beanFactoryPostProcessors) {

    // Invoke BeanDefinitionRegistryPostProcessors first, if any.
    Set<String> processedBeans = new HashSet<>();

    if (beanFactory instanceof BeanDefinitionRegistry) {
       BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
       List<BeanFactoryPostProcessor> regularPostProcessors = new ArrayList<>();
       List<BeanDefinitionRegistryPostProcessor> registryProcessors = new ArrayList<>();

       for (BeanFactoryPostProcessor postProcessor : beanFactoryPostProcessors) {
          if (postProcessor instanceof BeanDefinitionRegistryPostProcessor) {
             BeanDefinitionRegistryPostProcessor registryProcessor =
                   (BeanDefinitionRegistryPostProcessor) postProcessor;
             registryProcessor.postProcessBeanDefinitionRegistry(registry);
             registryProcessors.add(registryProcessor);
          }
          else {
             regularPostProcessors.add(postProcessor);
          }
       }

       // Do not initialize FactoryBeans here: We need to leave all regular beans
       // uninitialized to let the bean factory post-processors apply to them!
       // Separate between BeanDefinitionRegistryPostProcessors that implement
       // PriorityOrdered, Ordered, and the rest.
       List<BeanDefinitionRegistryPostProcessor> currentRegistryProcessors = new ArrayList<>();

       // First, invoke the BeanDefinitionRegistryPostProcessors that implement PriorityOrdered.
       String[] postProcessorNames =
             beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
       for (String ppName : postProcessorNames) {
          if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
             currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
             processedBeans.add(ppName);
          }
       }
       sortPostProcessors(currentRegistryProcessors, beanFactory);
       registryProcessors.addAll(currentRegistryProcessors);
       invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
       currentRegistryProcessors.clear();

       // Next, invoke the BeanDefinitionRegistryPostProcessors that implement Ordered.
       postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
       for (String ppName : postProcessorNames) {
          if (!processedBeans.contains(ppName) && beanFactory.isTypeMatch(ppName, Ordered.class)) {
             currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
             processedBeans.add(ppName);
          }
       }
       sortPostProcessors(currentRegistryProcessors, beanFactory);
       registryProcessors.addAll(currentRegistryProcessors);
       invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
       currentRegistryProcessors.clear();

       // Finally, invoke all other BeanDefinitionRegistryPostProcessors until no further ones appear.
       boolean reiterate = true;
       while (reiterate) {
          reiterate = false;
          postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
          for (String ppName : postProcessorNames) {
             if (!processedBeans.contains(ppName)) {
                currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
                processedBeans.add(ppName);
                reiterate = true;
             }
          }
          sortPostProcessors(currentRegistryProcessors, beanFactory);
          registryProcessors.addAll(currentRegistryProcessors);
          invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
          currentRegistryProcessors.clear();
       }

       // Now, invoke the postProcessBeanFactory callback of all processors handled so far.
       invokeBeanFactoryPostProcessors(registryProcessors, beanFactory);
       invokeBeanFactoryPostProcessors(regularPostProcessors, beanFactory);
    }

    else {
       // Invoke factory processors registered with the context instance.
       invokeBeanFactoryPostProcessors(beanFactoryPostProcessors, beanFactory);
    }

    // Do not initialize FactoryBeans here: We need to leave all regular beans
    // uninitialized to let the bean factory post-processors apply to them!
    String[] postProcessorNames =
          beanFactory.getBeanNamesForType(BeanFactoryPostProcessor.class, true, false);

    // Separate between BeanFactoryPostProcessors that implement PriorityOrdered,
    // Ordered, and the rest.
    List<BeanFactoryPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
    List<String> orderedPostProcessorNames = new ArrayList<>();
    List<String> nonOrderedPostProcessorNames = new ArrayList<>();
    for (String ppName : postProcessorNames) {
       if (processedBeans.contains(ppName)) {
          // skip - already processed in first phase above
       }
       else if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
          priorityOrderedPostProcessors.add(beanFactory.getBean(ppName, BeanFactoryPostProcessor.class));
       }
       else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
          orderedPostProcessorNames.add(ppName);
       }
       else {
          nonOrderedPostProcessorNames.add(ppName);
       }
    }

    // First, invoke the BeanFactoryPostProcessors that implement PriorityOrdered.
    sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
    invokeBeanFactoryPostProcessors(priorityOrderedPostProcessors, beanFactory);

    // Next, invoke the BeanFactoryPostProcessors that implement Ordered.
    List<BeanFactoryPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());
    for (String postProcessorName : orderedPostProcessorNames) {
       orderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
    }
    sortPostProcessors(orderedPostProcessors, beanFactory);
    invokeBeanFactoryPostProcessors(orderedPostProcessors, beanFactory);

    // Finally, invoke all other BeanFactoryPostProcessors.
    List<BeanFactoryPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());
    for (String postProcessorName : nonOrderedPostProcessorNames) {
       nonOrderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
    }
    invokeBeanFactoryPostProcessors(nonOrderedPostProcessors, beanFactory);

    // Clear cached merged bean definitions since the post-processors might have
    // modified the original metadata, e.g. replacing placeholders in values...
    beanFactory.clearMetadataCache();
}
```





## 注解扫描解析注册原理

### 注解bean的扫描注册和核心注解处理器的注册

#### xml+注解方式

```xml
<beans xmlns:context="http://www.springframework.org/schema/context"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns="http://www.springframework.org/schema/beans"
       xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd
        http://www.springframework.org/schema/context  http://www.springframework.org/schema/context/spring-context.xsd
        ">

	<!--在配置文件中指定好component-scan-->
	<context:component-scan base-package="com.zcq.demo.annotation"/>
 
</beans>
```

##### ContextNamespaceHandler

在处理context标签时就会使用对应的处理器处理标签

```handlers
http\://www.springframework.org/schema/context=org.springframework.context.config.ContextNamespaceHandler
```

```java
public class ContextNamespaceHandler extends NamespaceHandlerSupport {

    @Override
    public void init() {
       registerBeanDefinitionParser("property-placeholder", new PropertyPlaceholderBeanDefinitionParser());
       registerBeanDefinitionParser("property-override", new PropertyOverrideBeanDefinitionParser());
       registerBeanDefinitionParser("annotation-config", new AnnotationConfigBeanDefinitionParser());
        //处理component-scan的标签
       registerBeanDefinitionParser("component-scan", new ComponentScanBeanDefinitionParser());
       registerBeanDefinitionParser("load-time-weaver", new LoadTimeWeaverBeanDefinitionParser());
       registerBeanDefinitionParser("spring-configured", new SpringConfiguredBeanDefinitionParser());
       registerBeanDefinitionParser("mbean-export", new MBeanExportBeanDefinitionParser());
       registerBeanDefinitionParser("mbean-server", new MBeanServerBeanDefinitionParser());
    }

}
```

##### ComponentScanBeanDefinitionParser

```java
public class ComponentScanBeanDefinitionParser implements BeanDefinitionParser {

    private static final String BASE_PACKAGE_ATTRIBUTE = "base-package";

    @Override
	@Nullable
    //解析component-scan标签并扫描bean
	public BeanDefinition parse(Element element, ParserContext parserContext) {
        //获取component-scan标签的base-package属性 并且进行占位符替换
		String basePackage = element.getAttribute(BASE_PACKAGE_ATTRIBUTE);
		basePackage = parserContext.getReaderContext().getEnvironment().resolvePlaceholders(basePackage);
        //如果配置多个base-package进行拆分
		String[] basePackages = StringUtils.tokenizeToStringArray(basePackage,
				ConfigurableApplicationContext.CONFIG_LOCATION_DELIMITERS);

       
		// Actually scan for bean definitions and register them.
		ClassPathBeanDefinitionScanner scanner = configureScanner(parserContext, element);
		//扫描并且注册bean
        Set<BeanDefinitionHolder> beanDefinitions = scanner.doScan(basePackages);
        //注册 注解的内置处理器
		registerComponents(parserContext.getReaderContext(), beanDefinitions, element);

		return null;
	}
}


//注册 注解的内置处理器
	protected void registerComponents(
			XmlReaderContext readerContext, Set<BeanDefinitionHolder> beanDefinitions, Element element) {

		Object source = readerContext.extractSource(element);
		CompositeComponentDefinition compositeDef = new CompositeComponentDefinition(element.getTagName(), source);

		for (BeanDefinitionHolder beanDefHolder : beanDefinitions) {
			compositeDef.addNestedComponent(new BeanComponentDefinition(beanDefHolder));
		}

		// Register annotation config processors, if necessary.
		boolean annotationConfig = true;
		if (element.hasAttribute(ANNOTATION_CONFIG_ATTRIBUTE)) {
			annotationConfig = Boolean.parseBoolean(element.getAttribute(ANNOTATION_CONFIG_ATTRIBUTE));
		}
		if (annotationConfig) {
            //注册 注解的内置处理器
			Set<BeanDefinitionHolder> processorDefinitions =
					AnnotationConfigUtils.registerAnnotationConfigProcessors(readerContext.getRegistry(), source);
			for (BeanDefinitionHolder processorDefinition : processorDefinitions) {
				compositeDef.addNestedComponent(new BeanComponentDefinition(processorDefinition));
			}
		}

		readerContext.fireComponentRegistered(compositeDef);
	}


```



#### 纯注解方式

```java
  AnnotationConfigApplicationContext ac1 = new AnnotationConfigApplicationContext("com.zcq.demo");
```

```java
public class AnnotationConfigApplicationContext extends GenericApplicationContext implements AnnotationConfigRegistry {

    //在刷新前进行扫描注册bean 
	public AnnotationConfigApplicationContext(String... basePackages) {
		this();
		scan(basePackages);
		refresh();
	}

    //使用ClassPathBeanDefinitionScanner扫描basePackages注册
    //添加内部几个解析注解的BFPP和BPP
    @Override
	public void scan(String... basePackages) {
		Assert.notEmpty(basePackages, "At least one base package must be specified");
		this.scanner.scan(basePackages);
	}
}
```





#### Bean的扫描和注册

- 将basePackage替换成`classpath*:xxx/**/*.class`这样的Ant风格路径，利用资源加载器加载所有的class文件
- 循环读取所有的class文件及其元数据
- 基于元数据判断是否是@Component、@ManagedBean、@Named并且类的@Conditional是否满足条件
- 创建ScannedGenericBeanDefinition，使用名称生成器AnnotationBeanNameGenerator生成beanName
- 处理@Lazy、@Primary、@DependsOn、@Role、@Description 将对应属性设置到beanDefinition
- 检查容器中是否包含相同名称的bean
- 注册beanDefinition进入容器

##### ClassPathBeanDefinitionScanner

```java
public class ClassPathBeanDefinitionScanner extends ClassPathScanningCandidateComponentProvider {
    
    //默认名称生成器是AnnotationBeanNameGenerator
    private BeanNameGenerator beanNameGenerator = AnnotationBeanNameGenerator.INSTANCE;
    
    
	public int scan(String... basePackages) {
		int beanCountAtScanStart = this.registry.getBeanDefinitionCount();
		//扫描注册
		doScan(basePackages);

         //注册内部BFPP和BPP
		// Register annotation config processors, if necessary.
		if (this.includeAnnotationConfig) {
			AnnotationConfigUtils.registerAnnotationConfigProcessors(this.registry);
		}

		return (this.registry.getBeanDefinitionCount() - beanCountAtScanStart);
	}
    
	protected Set<BeanDefinitionHolder> doScan(String... basePackages) {
		Assert.notEmpty(basePackages, "At least one base package must be specified");
		Set<BeanDefinitionHolder> beanDefinitions = new LinkedHashSet<>();
		for (String basePackage : basePackages) {
             //查找指定basePackage下的所有候选bean
			Set<BeanDefinition> candidates = findCandidateComponents(basePackage);
			for (BeanDefinition candidate : candidates) {
                  //处理@Scope注解
				ScopeMetadata scopeMetadata = this.scopeMetadataResolver.resolveScopeMetadata(candidate);
				candidate.setScope(scopeMetadata.getScopeName());
                 //使用名称生成器生成beanName
				String beanName = this.beanNameGenerator.generateBeanName(candidate, this.registry);
				if (candidate instanceof AbstractBeanDefinition) {
					postProcessBeanDefinition((AbstractBeanDefinition) candidate, beanName);
				}
				if (candidate instanceof AnnotatedBeanDefinition) {
                     //处理@Lazy、@Primary、@DependsOn、@Role、@Description 将对应属性设置到BeanDefinition
					AnnotationConfigUtils.processCommonDefinitionAnnotations((AnnotatedBeanDefinition) candidate);
				}
                 //检查容器中是否包含相同名称的bean
				if (checkCandidate(beanName, candidate)) {
					BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(candidate, beanName);
					definitionHolder =
							AnnotationConfigUtils.applyScopedProxyMode(scopeMetadata, definitionHolder, this.registry);
					beanDefinitions.add(definitionHolder);
                      //注册进入容器
					registerBeanDefinition(definitionHolder, this.registry);
				}
			}
		}
		return beanDefinitions;
	}



}
```

##### ClassPathScanningCandidateComponentProvider

```java
public class ClassPathScanningCandidateComponentProvider implements EnvironmentCapable, ResourceLoaderAware {
    
    //bean的扫描和加载
	private Set<BeanDefinition> scanCandidateComponents(String basePackage) {
		Set<BeanDefinition> candidates = new LinkedHashSet<>();
		try {
            //将basePackage替换成`classpath*:xxx/**/*.class`这样的Ant风格
			String packageSearchPath = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX +
					resolveBasePackage(basePackage) + '/' + this.resourcePattern;
            //加载所有的class文件
			Resource[] resources = getResourcePatternResolver().getResources(packageSearchPath);
			boolean traceEnabled = logger.isTraceEnabled();
			boolean debugEnabled = logger.isDebugEnabled();
            //循环读取所有的class文件
			for (Resource resource : resources) {
				if (traceEnabled) {
					logger.trace("Scanning " + resource);
				}
				if (resource.isReadable()) {
					try {
                          //加载类文件和及其元数据
						MetadataReader metadataReader = getMetadataReaderFactory().getMetadataReader(resource);
                          //基于元数据判断是否是要被装入容器的类
						if (isCandidateComponent(metadataReader)) {
                               //创建ScannedGenericBeanDefinition装入集合中
							ScannedGenericBeanDefinition sbd = new ScannedGenericBeanDefinition(metadataReader);
							sbd.setSource(resource);
							if (isCandidateComponent(sbd)) {
								if (debugEnabled) {
									logger.debug("Identified candidate component class: " + resource);
								}
								candidates.add(sbd);
							}
							else {
								if (debugEnabled) {
									logger.debug("Ignored because not a concrete top-level class: " + resource);
								}
							}
						}
						else {
							if (traceEnabled) {
								logger.trace("Ignored because not matching any filter: " + resource);
							}
						}
					}
					catch (Throwable ex) {
						throw new BeanDefinitionStoreException(
								"Failed to read candidate component class: " + resource, ex);
					}
				}
				else {
					if (traceEnabled) {
						logger.trace("Ignored because not readable: " + resource);
					}
				}
			}
		}
		catch (IOException ex) {
			throw new BeanDefinitionStoreException("I/O failure during classpath scanning", ex);
		}
		return candidates;
	}

    
    //基于元数据判断是否是要被装入容器的类
    protected boolean isCandidateComponent(MetadataReader metadataReader) throws IOException {
        //是否是排除项中的
		for (TypeFilter tf : this.excludeFilters) {
			if (tf.match(metadataReader, getMetadataReaderFactory())) {
				return false;
			}
		}
        //是否是包含项中的@Component、@ManagedBean、@Named
		for (TypeFilter tf : this.includeFilters) {
			if (tf.match(metadataReader, getMetadataReaderFactory())) {
                 //判断类的@Conditional是否满足条件 满足条件的才能被装入容器
				return isConditionMatch(metadataReader);
			}
		}
		return false;
	}
    
    //@todo ？
	protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
		AnnotationMetadata metadata = beanDefinition.getMetadata();
		return (metadata.isIndependent() && (metadata.isConcrete() ||
				(metadata.isAbstract() && metadata.hasAnnotatedMethods(Lookup.class.getName()))));
	}
}
```



#### ConfigurationClassPostProcessor的注册

##### AnnotationConfigUtils

注册后续解析处理注解的处理器

```java
public abstract class AnnotationConfigUtils {
    
	public static final String CONFIGURATION_ANNOTATION_PROCESSOR_BEAN_NAME =
			"org.springframework.context.annotation.internalConfigurationAnnotationProcessor";
    
	public static final String AUTOWIRED_ANNOTATION_PROCESSOR_BEAN_NAME =
			"org.springframework.context.annotation.internalAutowiredAnnotationProcessor";

	public static final String COMMON_ANNOTATION_PROCESSOR_BEAN_NAME =
			"org.springframework.context.annotation.internalCommonAnnotationProcessor";
    
	public static final String PERSISTENCE_ANNOTATION_PROCESSOR_BEAN_NAME =
			"org.springframework.context.annotation.internalPersistenceAnnotationProcessor";
	private static final String PERSISTENCE_ANNOTATION_PROCESSOR_CLASS_NAME =
			"org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor";
    
	public static final String EVENT_LISTENER_PROCESSOR_BEAN_NAME =
			"org.springframework.context.event.internalEventListenerProcessor";

	public static final String EVENT_LISTENER_FACTORY_BEAN_NAME =
			"org.springframework.context.event.internalEventListenerFactory";


    //注册内置的注解处理器
	public static Set<BeanDefinitionHolder> registerAnnotationConfigProcessors(
			BeanDefinitionRegistry registry, @Nullable Object source) {

		DefaultListableBeanFactory beanFactory = unwrapDefaultListableBeanFactory(registry);
		if (beanFactory != null) {
			if (!(beanFactory.getDependencyComparator() instanceof AnnotationAwareOrderComparator)) {
				beanFactory.setDependencyComparator(AnnotationAwareOrderComparator.INSTANCE);
			}
			if (!(beanFactory.getAutowireCandidateResolver() instanceof ContextAnnotationAutowireCandidateResolver)) {
				beanFactory.setAutowireCandidateResolver(new ContextAnnotationAutowireCandidateResolver());
			}
		}

		Set<BeanDefinitionHolder> beanDefs = new LinkedHashSet<>(8);

        //处理解析@Component、@Configuration、@Bean、@ComponentScan、@PropertySource、@Import、@ImportResource等核心注解的解析器
		if (!registry.containsBeanDefinition(CONFIGURATION_ANNOTATION_PROCESSOR_BEAN_NAME)) {
			RootBeanDefinition def = new RootBeanDefinition(ConfigurationClassPostProcessor.class);
			def.setSource(source);
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

##### @Lazy、@Primary、@DependsOn、@Role、@Description处理

```java
public abstract class AnnotationConfigUtils {

	//处理@Lazy、@Primary、@DependsOn、@Role、@Description 将对应属性设置到BeanDefinition
	static void processCommonDefinitionAnnotations(AnnotatedBeanDefinition abd, AnnotatedTypeMetadata metadata) {
		AnnotationAttributes lazy = attributesFor(metadata, Lazy.class);
		if (lazy != null) {
			abd.setLazyInit(lazy.getBoolean("value"));
		}
		else if (abd.getMetadata() != metadata) {
			lazy = attributesFor(abd.getMetadata(), Lazy.class);
			if (lazy != null) {
				abd.setLazyInit(lazy.getBoolean("value"));
			}
		}

		if (metadata.isAnnotated(Primary.class.getName())) {
			abd.setPrimary(true);
		}
		AnnotationAttributes dependsOn = attributesFor(metadata, DependsOn.class);
		if (dependsOn != null) {
			abd.setDependsOn(dependsOn.getStringArray("value"));
		}

		AnnotationAttributes role = attributesFor(metadata, Role.class);
		if (role != null) {
			abd.setRole(role.getNumber("value").intValue());
		}
		AnnotationAttributes description = attributesFor(metadata, Description.class);
		if (description != null) {
			abd.setDescription(description.getString("value"));
		}
	}

}
```







### 核心注解解析工作流程

#### ConfigurationClassPostProcessor

核心注解`@Component、@Configuration、@Bean、@ComponentScan、@PropertySource、@Import、@ImportResource`的解析处理工作是由`ConfigurationClassPostProcessor`负责，`ConfigurationClassPostProcessor`继承了`BeanDefinitionRegistryPostProcessor`，在执行`invokeBeanFactoryPostProcessors`方法时由spring进行调用`postProcessBeanDefinitionRegistry`。

- 由spring调用`postProcessBeanDefinitionRegistry`
- 遍历所有的beanDefinitions，将@Component、@Configuration、@Bean、@ComponentScan、@PropertySource、@Import、@ImportResource注解的beanDefinitions加入待处理集合中
- 排序并设置BeanNameGenerator，创建ConfigurationClassParser解析器
- 使用ConfigurationClassParser解析所有待处理的beanDefinitions
- 处理@Bean、@ImportSource和ImportBeanDefinitionRegistrar的后续，新增beanDefinitions
- 如果新增的beanDefinitions有没解析过注解的，并且包含@Component、@Configuration、@Bean、@ComponentScan、@PropertySource、@Import、@ImportResource注解的，加入待处理集合中，循环解析

```java
public class ConfigurationClassPostProcessor implements BeanDefinitionRegistryPostProcessor,
       PriorityOrdered, ResourceLoaderAware, BeanClassLoaderAware, EnvironmentAware {
           
	@Override
	public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
		int registryId = System.identityHashCode(registry);
		if (this.registriesPostProcessed.contains(registryId)) {
			throw new IllegalStateException(
					"postProcessBeanDefinitionRegistry already called on this post-processor against " + registry);
		}
		if (this.factoriesPostProcessed.contains(registryId)) {
			throw new IllegalStateException(
					"postProcessBeanFactory already called on this post-processor against " + registry);
		}
		this.registriesPostProcessed.add(registryId);

        //处理带核心注解的beanDefinitions
		processConfigBeanDefinitions(registry);
	}
           
     
    //处理带核心注解的beanDefinitions
	public void processConfigBeanDefinitions(BeanDefinitionRegistry registry) {
		List<BeanDefinitionHolder> configCandidates = new ArrayList<>();
        
        //遍历所有的候选beanDefinition
		String[] candidateNames = registry.getBeanDefinitionNames();
		for (String beanName : candidateNames) {
			BeanDefinition beanDef = registry.getBeanDefinition(beanName);
			if (beanDef.getAttribute(ConfigurationClassUtils.CONFIGURATION_CLASS_ATTRIBUTE) != null) {
				if (logger.isDebugEnabled()) {
					logger.debug("Bean definition has already been processed as a configuration class: " + beanDef);
				}
			}
            //检查是不是包含@Configuration、@Component、@ComponentScan、@Import、@ImportResource、@Bean注解中的一个
			else if (ConfigurationClassUtils.checkConfigurationClassCandidate(beanDef, this.metadataReaderFactory)) {
				configCandidates.add(new BeanDefinitionHolder(beanDef, beanName));
			}
		}
		
        //没有需要处理的beanDefinition 返回
		// Return immediately if no @Configuration classes were found
		if (configCandidates.isEmpty()) {
			return;
		}
		
        //根据order进行排序
		// Sort by previously determined @Order value, if applicable
		configCandidates.sort((bd1, bd2) -> {
			int i1 = ConfigurationClassUtils.getOrder(bd1.getBeanDefinition());
			int i2 = ConfigurationClassUtils.getOrder(bd2.getBeanDefinition());
			return Integer.compare(i1, i2);
		});

        
        //检查有没有自定义的BeanNameGenerator 使用自定义的BeanNameGenerator
		// Detect any custom bean name generation strategy supplied through the enclosing application context
		SingletonBeanRegistry sbr = null;
		if (registry instanceof SingletonBeanRegistry) {
			sbr = (SingletonBeanRegistry) registry;
			if (!this.localBeanNameGeneratorSet) {
				BeanNameGenerator generator = (BeanNameGenerator) sbr.getSingleton(
						AnnotationConfigUtils.CONFIGURATION_BEAN_NAME_GENERATOR);
				if (generator != null) {
					this.componentScanBeanNameGenerator = generator;
					this.importBeanNameGenerator = generator;
				}
			}
		}

		if (this.environment == null) {
			this.environment = new StandardEnvironment();
		}

        //创建解析注解的解析类
		// Parse each @Configuration class
		ConfigurationClassParser parser = new ConfigurationClassParser(
				this.metadataReaderFactory, this.problemReporter, this.environment,
				this.resourceLoader, this.componentScanBeanNameGenerator, registry);

		Set<BeanDefinitionHolder> candidates = new LinkedHashSet<>(configCandidates);
		Set<ConfigurationClass> alreadyParsed = new HashSet<>(configCandidates.size());
		do {
             //核心解析处理
			parser.parse(candidates);
			parser.validate();

			Set<ConfigurationClass> configClasses = new LinkedHashSet<>(parser.getConfigurationClasses());
			configClasses.removeAll(alreadyParsed);

			// Read the model and create bean definitions based on its content
			if (this.reader == null) {
				this.reader = new ConfigurationClassBeanDefinitionReader(
						registry, this.sourceExtractor, this.resourceLoader, this.environment,
						this.importBeanNameGenerator, parser.getImportRegistry());
			}
             //处理@Bean、@ImportSource和ImportBeanDefinitionRegistrar的后续，加载新增BeanDefinitions
			this.reader.loadBeanDefinitions(configClasses);
			alreadyParsed.addAll(configClasses);

			candidates.clear();
             //筛选出没解析过的beanDefinitions 解析新加载进来的beanDefinitions的注解
			if (registry.getBeanDefinitionCount() > candidateNames.length) {
				String[] newCandidateNames = registry.getBeanDefinitionNames();
				Set<String> oldCandidateNames = new HashSet<>(Arrays.asList(candidateNames));
				Set<String> alreadyParsedClasses = new HashSet<>();
				for (ConfigurationClass configurationClass : alreadyParsed) {
					alreadyParsedClasses.add(configurationClass.getMetadata().getClassName());
				}
				for (String candidateName : newCandidateNames) {
					if (!oldCandidateNames.contains(candidateName)) {
						BeanDefinition bd = registry.getBeanDefinition(candidateName);
						if (ConfigurationClassUtils.checkConfigurationClassCandidate(bd, this.metadataReaderFactory) &&
								!alreadyParsedClasses.contains(bd.getBeanClassName())) {
							candidates.add(new BeanDefinitionHolder(bd, candidateName));
						}
					}
				}
				candidateNames = newCandidateNames;
			}
		}
		while (!candidates.isEmpty());

		// Register the ImportRegistry as a bean in order to support ImportAware @Configuration classes
		if (sbr != null && !sbr.containsSingleton(IMPORT_REGISTRY_BEAN_NAME)) {
			sbr.registerSingleton(IMPORT_REGISTRY_BEAN_NAME, parser.getImportRegistry());
		}

		if (this.metadataReaderFactory instanceof CachingMetadataReaderFactory) {
			// Clear cache in externally provided MetadataReaderFactory; this is a no-op
			// for a shared cache since it'll be cleared by the ApplicationContext.
			((CachingMetadataReaderFactory) this.metadataReaderFactory).clearCache();
		}
	}

}
```

##### ConfigurationClassUtils

```java
abstract class ConfigurationClassUtils {
    
    
    public static final String CONFIGURATION_CLASS_FULL = "full";

	public static final String CONFIGURATION_CLASS_LITE = "lite";

	public static final String CONFIGURATION_CLASS_ATTRIBUTE =
			Conventions.getQualifiedAttributeName(ConfigurationClassPostProcessor.class, "configurationClass");

    private static final Set<String> candidateIndicators = new HashSet<>(8);

	static {
		candidateIndicators.add(Component.class.getName());
		candidateIndicators.add(ComponentScan.class.getName());
		candidateIndicators.add(Import.class.getName());
		candidateIndicators.add(ImportResource.class.getName());
	}

    //检查是不是需要处理注解的beanDefinition
	public static boolean checkConfigurationClassCandidate(
			BeanDefinition beanDef, MetadataReaderFactory metadataReaderFactory) {

		String className = beanDef.getBeanClassName();
		if (className == null || beanDef.getFactoryMethodName() != null) {
			return false;
		}
		
        //获取beanDefinition的元数据信息
		AnnotationMetadata metadata;
		if (beanDef instanceof AnnotatedBeanDefinition &&
				className.equals(((AnnotatedBeanDefinition) beanDef).getMetadata().getClassName())) {
			// Can reuse the pre-parsed metadata from the given BeanDefinition...
			metadata = ((AnnotatedBeanDefinition) beanDef).getMetadata();
		}
		else if (beanDef instanceof AbstractBeanDefinition && ((AbstractBeanDefinition) beanDef).hasBeanClass()) {
			// Check already loaded Class if present...
			// since we possibly can't even load the class file for this Class.
			Class<?> beanClass = ((AbstractBeanDefinition) beanDef).getBeanClass();
			if (BeanFactoryPostProcessor.class.isAssignableFrom(beanClass) ||
					BeanPostProcessor.class.isAssignableFrom(beanClass) ||
					AopInfrastructureBean.class.isAssignableFrom(beanClass) ||
					EventListenerFactory.class.isAssignableFrom(beanClass)) {
				return false;
			}
			metadata = AnnotationMetadata.introspect(beanClass);
		}
		else {
			try {
				MetadataReader metadataReader = metadataReaderFactory.getMetadataReader(className);
				metadata = metadataReader.getAnnotationMetadata();
			}
			catch (IOException ex) {
				if (logger.isDebugEnabled()) {
					logger.debug("Could not find class file for introspecting configuration annotations: " +
							className, ex);
				}
				return false;
			}
		}

        //获取当前beanDefinition上的@Configuration注解信息
		Map<String, Object> config = metadata.getAnnotationAttributes(Configuration.class.getName());
        //如果有@Configuration注解 那么设置属性为full
		if (config != null && !Boolean.FALSE.equals(config.get("proxyBeanMethods"))) {
			beanDef.setAttribute(CONFIGURATION_CLASS_ATTRIBUTE, CONFIGURATION_CLASS_FULL);
		}//否则需要确认是否包含@Component、@ComponentScan、@Import、@ImportResource、@Bean注解中的一个 设置属性为lite
		else if (config != null || isConfigurationCandidate(metadata)) {
			beanDef.setAttribute(CONFIGURATION_CLASS_ATTRIBUTE, CONFIGURATION_CLASS_LITE);
		}
		else {
			return false;
		}

        //设置一下顺序
		// It's a full or lite configuration candidate... Let's determine the order value, if any.
		Integer order = getOrder(metadata);
		if (order != null) {
			beanDef.setAttribute(ORDER_ATTRIBUTE, order);
		}

		return true;
	}


    //确认是否包含@Component、@ComponentScan、@Import、@ImportResource、@Bean注解中的一个
	public static boolean isConfigurationCandidate(AnnotationMetadata metadata) {
		// Do not consider an interface or an annotation...
		if (metadata.isInterface()) {
			return false;
		}

		// Any of the typical annotations found?
		for (String indicator : candidateIndicators) {
			if (metadata.isAnnotated(indicator)) {
				return true;
			}
		}

		// Finally, let's look for @Bean methods...
		try {
			return metadata.hasAnnotatedMethods(Bean.class.getName());
		}
		catch (Throwable ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("Failed to introspect @Bean methods on class [" + metadata.getClassName() + "]: " + ex);
			}
			return false;
		}
	}


}    
```

#### ConfigurationClassParser

- 包装BeanDefinition的AnnotationMetadata，包装成ConfigurationClass，获得BeanDefinition的元数据信息
- 如果类上包含@Conditional 需要满足条件才能被处理
- 如果已经解析过当前ConfigurationClass，那么要么合并，要么去掉旧的使用新的
- 解析处理@Component、@Configuration、@Bean、@ComponentScan、@PropertySource、@Import、@ImportResource注解
- 循环解析当前ConfigurationClass及其父类，直到父类解析完成

```java
class ConfigurationClassParser {

    //解析处理包含@Configuration、@Component、@ComponentScan、@Import、@ImportResource、@Bean、@PropertySource注解的beanDefinition
    public void parse(Set<BeanDefinitionHolder> configCandidates) {
		for (BeanDefinitionHolder holder : configCandidates) {
			BeanDefinition bd = holder.getBeanDefinition();
			try {
				if (bd instanceof AnnotatedBeanDefinition) {
                    //解析
					parse(((AnnotatedBeanDefinition) bd).getMetadata(), holder.getBeanName());
				}
				else if (bd instanceof AbstractBeanDefinition && ((AbstractBeanDefinition) bd).hasBeanClass()) {
					parse(((AbstractBeanDefinition) bd).getBeanClass(), holder.getBeanName());
				}
				else {
					parse(bd.getBeanClassName(), holder.getBeanName());
				}
			}
			catch (BeanDefinitionStoreException ex) {
				throw ex;
			}
			catch (Throwable ex) {
				throw new BeanDefinitionStoreException(
						"Failed to parse configuration class [" + bd.getBeanClassName() + "]", ex);
			}
		}
		
         //处理 延迟处理的Selector
		this.deferredImportSelectorHandler.process();
	}

	//解析
	protected final void parse(AnnotationMetadata metadata, String beanName) throws IOException {
		processConfigurationClass(new ConfigurationClass(metadata, beanName), DEFAULT_EXCLUSION_FILTER);
	}
    
    
    //解析
	protected void processConfigurationClass(ConfigurationClass configClass, Predicate<String> filter) throws IOException {
        //如果类上包含@Conditional 需要满足条件才能被处理
		if (this.conditionEvaluator.shouldSkip(configClass.getMetadata(), ConfigurationPhase.PARSE_CONFIGURATION)) {
			return;
		}
		
        //判断当前类是不是已经被处理过了
		ConfigurationClass existingClass = this.configurationClasses.get(configClass);
		if (existingClass != null) {
			if (configClass.isImported()) {
				if (existingClass.isImported()) {
					existingClass.mergeImportedBy(configClass);
				}
				// Otherwise ignore new imported config class; existing non-imported class overrides it.
				return;
			}
			else {
				// Explicit bean definition found, probably replacing an import.
				// Let's remove the old one and go with the new one.
				this.configurationClasses.remove(configClass);
				this.knownSuperclasses.values().removeIf(configClass::equals);
			}
		}
		
        //处理类
		// Recursively process the configuration class and its superclass hierarchy.
		SourceClass sourceClass = asSourceClass(configClass, filter);
        //循环处理 因为在处理当前类的注解后 还需要处理父类的相应注解
		do {
             //返回值是父类 没有父类返回值就是null
			sourceClass = doProcessConfigurationClass(configClass, sourceClass, filter);
		}
		while (sourceClass != null);

		this.configurationClasses.put(configClass, configClass);
	}

    
    
    
    //处理@Configuration、@Component、@ComponentScan、@Import、@ImportResource、@Bean注解
	@Nullable
	protected final SourceClass doProcessConfigurationClass(
			ConfigurationClass configClass, SourceClass sourceClass, Predicate<String> filter)
			throws IOException {

        //如果类上包含@Component注解
		if (configClass.getMetadata().isAnnotated(Component.class.getName())) {
             //先递归处理它的内部类
			// Recursively process any member (nested) classes first
			processMemberClasses(configClass, sourceClass, filter);
		}

        //处理类上的@PropertySource注解
		// Process any @PropertySource annotations
		for (AnnotationAttributes propertySource : AnnotationConfigUtils.attributesForRepeatable(
				sourceClass.getMetadata(), PropertySources.class,
				org.springframework.context.annotation.PropertySource.class)) {
			if (this.environment instanceof ConfigurableEnvironment) {
				processPropertySource(propertySource);
			}
			else {
				logger.info("Ignoring @PropertySource annotation on [" + sourceClass.getMetadata().getClassName() +
						"]. Reason: Environment must implement ConfigurableEnvironment");
			}
		}

        //处理@ComponentScan注解
		// Process any @ComponentScan annotations
		Set<AnnotationAttributes> componentScans = AnnotationConfigUtils.attributesForRepeatable(
				sourceClass.getMetadata(), ComponentScans.class, ComponentScan.class);
        //如果满足@Conditional 那么才进行处理
		if (!componentScans.isEmpty() &&
				!this.conditionEvaluator.shouldSkip(sourceClass.getMetadata(), ConfigurationPhase.REGISTER_BEAN)) {
			for (AnnotationAttributes componentScan : componentScans) {
				// The config class is annotated with @ComponentScan -> perform the scan immediately
				Set<BeanDefinitionHolder> scannedBeanDefinitions =
						this.componentScanParser.parse(componentScan, sourceClass.getMetadata().getClassName());
                
                 //如果扫描进来的类也带有需要处理的注解 那么需要进行解析处理
				// Check the set of scanned definitions for any further config classes and parse recursively if needed
				for (BeanDefinitionHolder holder : scannedBeanDefinitions) {
					BeanDefinition bdCand = holder.getBeanDefinition().getOriginatingBeanDefinition();
					if (bdCand == null) {
						bdCand = holder.getBeanDefinition();
					}
                      //检查扫描进来的类是否带有@Configuration、@Component、@ComponentScan、@Import、@ImportResource、@Bean注解
					if (ConfigurationClassUtils.checkConfigurationClassCandidate(bdCand, this.metadataReaderFactory)) {
                          //解析处理
						parse(bdCand.getBeanClassName(), holder.getBeanName());
					}
				}
			}
		}

        //处理@Import注解
		// Process any @Import annotations
		processImports(configClass, sourceClass, getImports(sourceClass), filter, true);

        //处理@ImportResource注解
		// Process any @ImportResource annotations
		AnnotationAttributes importResource =
				AnnotationConfigUtils.attributesFor(sourceClass.getMetadata(), ImportResource.class);
		if (importResource != null) {
			String[] resources = importResource.getStringArray("locations");
			Class<? extends BeanDefinitionReader> readerClass = importResource.getClass("reader");
			for (String resource : resources) {
				String resolvedResource = this.environment.resolveRequiredPlaceholders(resource);
				configClass.addImportedResource(resolvedResource, readerClass);
			}
		}
		
        //处理@Bean的注解
		// Process individual @Bean methods
        //获取所有的@Bean注解标注的方法
		Set<MethodMetadata> beanMethods = retrieveBeanMethodMetadata(sourceClass);
		for (MethodMetadata methodMetadata : beanMethods) {
             //把@Bean注解标注的方法加入configClass
			configClass.addBeanMethod(new BeanMethod(methodMetadata, configClass));
		}
		
        //处理接口默认方法上的@Bean注解标注的方法
		// Process default methods on interfaces
		processInterfaces(configClass, sourceClass);

        //如果有父类 返回父类
		// Process superclass, if any
		if (sourceClass.getMetadata().hasSuperClass()) {
			String superclass = sourceClass.getMetadata().getSuperClassName();
			if (superclass != null && !superclass.startsWith("java") &&
					!this.knownSuperclasses.containsKey(superclass)) {
				this.knownSuperclasses.put(superclass, configClass);
				// Superclass found, return its annotation metadata and recurse
				return sourceClass.getSuperClass();
			}
		}
        
        //没有父类 都处理完了 返回null
		// No superclass -> processing is complete
		return null;
	}

}    
```

##### @Component解析

- 如果包含@Component注解，那么递归解析它的内部类
- 内部类中的注解解析完成后，此内部类会加入到`this.configurationClasses`中，等待后续的`this.reader.loadBeanDefinitions(configClasses)`时会作为beanDefinition加入容器中

```java
class ConfigurationClassParser {

	//处理内部类
    private void processMemberClasses(ConfigurationClass configClass, SourceClass sourceClass,
			Predicate<String> filter) throws IOException {

        //如果这个类有内部类的话 
		Collection<SourceClass> memberClasses = sourceClass.getMemberClasses();
		if (!memberClasses.isEmpty()) {
			List<SourceClass> candidates = new ArrayList<>(memberClasses.size());
			for (SourceClass memberClass : memberClasses) {
                //并且这个内部类被@Component、@ComponentScan、@Import、@ImportResource、@Bean注解修饰
				if (ConfigurationClassUtils.isConfigurationCandidate(memberClass.getMetadata()) &&
						!memberClass.getMetadata().getClassName().equals(configClass.getMetadata().getClassName())) {
					candidates.add(memberClass);
				}
			}
            //排序
			OrderComparator.sort(candidates);
			for (SourceClass candidate : candidates) {
				if (this.importStack.contains(configClass)) {
					this.problemReporter.error(new CircularImportProblem(configClass, this.importStack));
				}
				else {
					this.importStack.push(configClass);
					try {
                          //先递归处理内部类的注解
						processConfigurationClass(candidate.asConfigClass(configClass), filter);
					}
					finally {
						this.importStack.pop();
					}
				}
			}
		}
	}
}
```

##### @PropertySource解析

- 如果当前类包含@PropertySource注解
- 读取所有@PropertySource注解上的属性，读取要导入的配置类文件名称数组
- 循环读取配置类文件，将配置文件对应的名称和属性设置到Environment中

```java
class ConfigurationClassParser {

    //处理@PropertySource注解
	private void processPropertySource(AnnotationAttributes propertySource) throws IOException {
        //获取name
		String name = propertySource.getString("name");
		if (!StringUtils.hasLength(name)) {
			name = null;
		}
        //获取encoding
		String encoding = propertySource.getString("encoding");
		if (!StringUtils.hasLength(encoding)) {
			encoding = null;
		}
        //获取value 要导入的配置类文件名称
		String[] locations = propertySource.getStringArray("value");
		Assert.isTrue(locations.length > 0, "At least one @PropertySource(value) location is required");
		boolean ignoreResourceNotFound = propertySource.getBoolean("ignoreResourceNotFound");

		Class<? extends PropertySourceFactory> factoryClass = propertySource.getClass("factory");
		PropertySourceFactory factory = (factoryClass == PropertySourceFactory.class ?
				DEFAULT_PROPERTY_SOURCE_FACTORY : BeanUtils.instantiateClass(factoryClass));
		
        //循环所有导入的配置类文件名称
		for (String location : locations) {
			try {
                 //解析替换文件名称的占位符
				String resolvedLocation = this.environment.resolveRequiredPlaceholders(location);
                 //加载配置文件
				Resource resource = this.resourceLoader.getResource(resolvedLocation);
                 //利用jdk的Properties类读取配置文件中的所有属性 支持读取yml和properties文件
                 //将配置文件中的属性全部加入到Environment中 
				addPropertySource(factory.createPropertySource(name, new EncodedResource(resource, encoding)));
			}
			catch (IllegalArgumentException | FileNotFoundException | UnknownHostException ex) {
				// Placeholders not resolvable or resource not found when trying to open it
				if (ignoreResourceNotFound) {
					if (logger.isInfoEnabled()) {
						logger.info("Properties location [" + location + "] not resolvable: " + ex.getMessage());
					}
				}
				else {
					throw ex;
				}
			}
		}
	}
}
```

###### Properties

在JDK中，Properties支持读取yml和properties文件，能直接将属性转换成key-value形式

```java
public class PropertiesTest {
    public static void main(String[] args) throws Exception {

        URL resource = PropertiesTest.class.getClassLoader().getResource("myconfig2.properties");
        URL resource1 = PropertiesTest.class.getClassLoader().getResource("myconfig3.yml");

        Properties properties = new Properties();
        properties.load(resource.openStream());
        System.out.println(properties);

        String property = properties.getProperty("myconfig2.name");

        Properties properties2 = new Properties();
        properties2.load(resource1.openStream());
        System.out.println(properties2);
    }
}
```



##### @ComponentScan解析

- 如果当前类包含@ComponentScan、@ComponentScans注解
- 判断其@Conditional是否满足条件，循环遍历所有的@ComponentScan注解
- 读取@ComponentScan的所有属性，利用`ClassPathBeanDefinitionScanner`将`basePackages`下的所有带有注解@Component、@ManagedBean、@Named并且类的@Conditional是否满足条件的类加载到容器中
- 循环新增的beanDefinitions，逐个解析带有@Component、@Configuration、@Bean、@ComponentScan、@PropertySource、@Import、@ImportResource注解的beanDefinition

###### ComponentScanAnnotationParser

```java
class ComponentScanAnnotationParser {

    //解析处理@ComponentScan注解
	public Set<BeanDefinitionHolder> parse(AnnotationAttributes componentScan, final String declaringClass) {
        //核心扫描类 和上面Bean的扫描和注册工作用的是同一个类
		ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(this.registry,
				componentScan.getBoolean("useDefaultFilters"), this.environment, this.resourceLoader);

        //设置扫描类的属性
		Class<? extends BeanNameGenerator> generatorClass = componentScan.getClass("nameGenerator");
		boolean useInheritedGenerator = (BeanNameGenerator.class == generatorClass);
		scanner.setBeanNameGenerator(useInheritedGenerator ? this.beanNameGenerator :
				BeanUtils.instantiateClass(generatorClass));

		ScopedProxyMode scopedProxyMode = componentScan.getEnum("scopedProxy");
		if (scopedProxyMode != ScopedProxyMode.DEFAULT) {
			scanner.setScopedProxyMode(scopedProxyMode);
		}
		else {
			Class<? extends ScopeMetadataResolver> resolverClass = componentScan.getClass("scopeResolver");
			scanner.setScopeMetadataResolver(BeanUtils.instantiateClass(resolverClass));
		}

		scanner.setResourcePattern(componentScan.getString("resourcePattern"));

		for (AnnotationAttributes filter : componentScan.getAnnotationArray("includeFilters")) {
			for (TypeFilter typeFilter : typeFiltersFor(filter)) {
				scanner.addIncludeFilter(typeFilter);
			}
		}
		for (AnnotationAttributes filter : componentScan.getAnnotationArray("excludeFilters")) {
			for (TypeFilter typeFilter : typeFiltersFor(filter)) {
				scanner.addExcludeFilter(typeFilter);
			}
		}

		boolean lazyInit = componentScan.getBoolean("lazyInit");
		if (lazyInit) {
			scanner.getBeanDefinitionDefaults().setLazyInit(true);
		}

		Set<String> basePackages = new LinkedHashSet<>();
		String[] basePackagesArray = componentScan.getStringArray("basePackages");
		for (String pkg : basePackagesArray) {
			String[] tokenized = StringUtils.tokenizeToStringArray(this.environment.resolvePlaceholders(pkg),
					ConfigurableApplicationContext.CONFIG_LOCATION_DELIMITERS);
			Collections.addAll(basePackages, tokenized);
		}
		for (Class<?> clazz : componentScan.getClassArray("basePackageClasses")) {
			basePackages.add(ClassUtils.getPackageName(clazz));
		}
		if (basePackages.isEmpty()) {
			basePackages.add(ClassUtils.getPackageName(declaringClass));
		}

		scanner.addExcludeFilter(new AbstractTypeHierarchyTraversingFilter(false, false) {
			@Override
			protected boolean matchClassName(String className) {
				return declaringClass.equals(className);
			}
		});
        //使用ClassPathBeanDefinitionScanner进行扫描 详见上面的ClassPathBeanDefinitionScanner
        //将包含@Component、@ManagedBean、@Named的类都扫描进入容器
		return scanner.doScan(StringUtils.toStringArray(basePackages));
	}




}
```



##### @Import解析

> `@Import` 注解用于在一个配置类中导入其他配置类、组件类或实现了特定接口的类。通过该注解，可以将多个配置类组合在一起，实现配置的模块化和复用。

- 递归当前类及其父类的所有注解和父注解，获取所有的@Import注解信息

- 遍历Import注解导入的类

- 如果导入的类属于`ImportSelector`，进行实例化，如果属于`DeferredImportSelector`延迟处理的类型，那么加入`DeferredImportSelectorHandler`稍后处理

  否则立即调用`selector.selectImports`方法，导入其他类，继续递归调用`processImports`处理导入的其他类

- 如果导入的类属于`ImportBeanDefinitionRegistrar`，加入到当前类的`importBeanDefinitionRegistrars`集合中稍后通过`this.reader.loadBeanDefinitions(configClasses)`处理

- 如果导入的类不属于上述两种类型，那么就递归处理下此类的@Component、@Configuration、@Bean、@ComponentScan、@PropertySource、@Import、@ImportResource注解，此类会加入到`this.configurationClasses`中，稍后会被`this.reader.loadBeanDefinitions(configClasses)`处理加入容器中

```java
class ConfigurationClassParser {

    
	//递归获取此类及其父类的所有@Import注解信息
	private Set<SourceClass> getImports(SourceClass sourceClass) throws IOException {
        //@Import注解信息
		Set<SourceClass> imports = new LinkedHashSet<>();
        //已经访问过的类
		Set<SourceClass> visited = new LinkedHashSet<>();
		collectImports(sourceClass, imports, visited);
		return imports;
	}
    
    //递归获取此类及其父类的所有@Import注解信息
    private void collectImports(SourceClass sourceClass, Set<SourceClass> imports, Set<SourceClass> visited)
			throws IOException {
		//已经访问过的类
		if (visited.add(sourceClass)) {
			for (SourceClass annotation : sourceClass.getAnnotations()) {
				String annName = annotation.getMetadata().getClassName();
				if (!annName.equals(Import.class.getName())) {
                      //递归父类
					collectImports(annotation, imports, visited);
				}
			}
            //加入@Import导入的类
			imports.addAll(sourceClass.getAnnotationAttributes(Import.class.getName(), "value"));
		}
	}
    
    //处理@Import导入的类
	private void processImports(ConfigurationClass configClass, SourceClass currentSourceClass,
			Collection<SourceClass> importCandidates, Predicate<String> exclusionFilter,
			boolean checkForCircularImports) {

		if (importCandidates.isEmpty()) {
			return;
		}

		if (checkForCircularImports && isChainedImportOnStack(configClass)) {
			this.problemReporter.error(new CircularImportProblem(configClass, this.importStack));
		}
		else {
			this.importStack.push(configClass);
			try {
                 //循环处理导入的类
				for (SourceClass candidate : importCandidates) {
                      //如果当前类属于ImportSelector
					if (candidate.isAssignable(ImportSelector.class)) {
                          //实例化类
						// Candidate class is an ImportSelector -> delegate to it to determine imports
						Class<?> candidateClass = candidate.loadClass();
						ImportSelector selector = ParserStrategyUtils.instantiateClass(candidateClass, ImportSelector.class,
								this.environment, this.resourceLoader, this.registry);
						Predicate<String> selectorFilter = selector.getExclusionFilter();
						if (selectorFilter != null) {
							exclusionFilter = exclusionFilter.or(selectorFilter);
						}
                          //如果当前selector属于延迟处理
						if (selector instanceof DeferredImportSelector) {
                               //加入到集合中稍后处理selector
							this.deferredImportSelectorHandler.handle(configClass, (DeferredImportSelector) selector);
						}
                          //否则立即处理selector
						else {
                               //调用selector的selectImports 导入类
							String[] importClassNames = selector.selectImports(currentSourceClass.getMetadata());
							Collection<SourceClass> importSourceClasses = asSourceClasses(importClassNames, exclusionFilter);
                               //递归处理导入的类
							processImports(configClass, currentSourceClass, importSourceClasses, exclusionFilter, false);
						}
					}
                      //如果当前类属于ImportBeanDefinitionRegistrar
					else if (candidate.isAssignable(ImportBeanDefinitionRegistrar.class)) {
						// Candidate class is an ImportBeanDefinitionRegistrar ->
						// delegate to it to register additional bean definitions
                          //实例化类 加入configClass
						Class<?> candidateClass = candidate.loadClass();
						ImportBeanDefinitionRegistrar registrar =
								ParserStrategyUtils.instantiateClass(candidateClass, ImportBeanDefinitionRegistrar.class,
										this.environment, this.resourceLoader, this.registry);
						configClass.addImportBeanDefinitionRegistrar(registrar, currentSourceClass.getMetadata());
					}
                      //如果导入的类不是一个Selector
					else {
						// Candidate class not an ImportSelector or ImportBeanDefinitionRegistrar ->
						// process it as an @Configuration class
						this.importStack.registerImport(
								currentSourceClass.getMetadata(), candidate.getMetadata().getClassName());
                          //那么去递归处理它身上的@Configuration、@Component、@ComponentScan、@Import、@ImportResource、@Bean注解
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


}
```

##### @ImportResource解析

> `@ImportResource` 注解用于在 Java 配置类中导入 XML 配置文件，通过读取XML配置文件来导入beanDefinitions。

- 如果当前类包含@ImportResource注解，那么就直接加入到当前类的configClass的importedResources，稍后通过`this.reader.loadBeanDefinitions(configClasses)`处理

```java
class ConfigurationClassParser {

    //处理@Configuration、@Component、@ComponentScan、@Import、@ImportResource、@Bean注解
	@Nullable
	protected final SourceClass doProcessConfigurationClass(
			ConfigurationClass configClass, SourceClass sourceClass, Predicate<String> filter)
			throws IOException {

        //处理@ImportResource注解
		// Process any @ImportResource annotations
		AnnotationAttributes importResource =
				AnnotationConfigUtils.attributesFor(sourceClass.getMetadata(), ImportResource.class);
		if (importResource != null) {
			String[] resources = importResource.getStringArray("locations");
			Class<? extends BeanDefinitionReader> readerClass = importResource.getClass("reader");
			for (String resource : resources) {
				String resolvedResource = this.environment.resolveRequiredPlaceholders(resource);
				configClass.addImportedResource(resolvedResource, readerClass);
			}
		}
		
	}

}
```



##### @Bean解析

- 解析所有带有@Bean注解的方法，加入到当前类的configClass的beanMethods集合中，稍后通过`this.reader.loadBeanDefinitions(configClasses)`处理

```java
class ConfigurationClassParser {

    //处理@Configuration、@Component、@ComponentScan、@Import、@ImportResource、@Bean注解
	@Nullable
	protected final SourceClass doProcessConfigurationClass(
			ConfigurationClass configClass, SourceClass sourceClass, Predicate<String> filter)
			throws IOException {

         //处理@Bean的注解
		// Process individual @Bean methods
        //获取所有的@Bean注解标注的方法
		Set<MethodMetadata> beanMethods = retrieveBeanMethodMetadata(sourceClass);
		for (MethodMetadata methodMetadata : beanMethods) {
             //把@Bean注解标注的方法加入configClass
			configClass.addBeanMethod(new BeanMethod(methodMetadata, configClass));
		}
		
	}

}
```



#### loadBeanDefinitions

在解析完注解后，通过`this.reader.loadBeanDefinitions(configClasses)`将上述`@Bean、@ImportResource和ImportBeanDefinitionRegistrar`新增beanDefinition的工作完成。

```java
class ConfigurationClassBeanDefinitionReader {

    //遍历所有类的configClass，将@Bean、@ImportResource和ImportBeanDefinitionRegistrar新增beanDefinition的工作完成
	public void loadBeanDefinitions(Set<ConfigurationClass> configurationModel) {
		TrackedConditionEvaluator trackedConditionEvaluator = new TrackedConditionEvaluator();
		for (ConfigurationClass configClass : configurationModel) {
            //新增beanDefinition
			loadBeanDefinitionsForConfigurationClass(configClass, trackedConditionEvaluator);
		}
	}
    
	private void loadBeanDefinitionsForConfigurationClass(
			ConfigurationClass configClass, TrackedConditionEvaluator trackedConditionEvaluator) {
		//计算@Conditional满足条件
		if (trackedConditionEvaluator.shouldSkip(configClass)) {
			String beanName = configClass.getBeanName();
			if (StringUtils.hasLength(beanName) && this.registry.containsBeanDefinition(beanName)) {
				this.registry.removeBeanDefinition(beanName);
			}
			this.importRegistry.removeImportingClass(configClass.getMetadata().getClassName());
			return;
		}
		//如果当前类是被其他类导入进来的
		if (configClass.isImported()) {
             //将导入的类注册到容器中去
			registerBeanDefinitionForImportedConfigurationClass(configClass);
		}
         //遍历当前类的BeanMethods集合，处理@Bean的注册工作
		for (BeanMethod beanMethod : configClass.getBeanMethods()) {
			loadBeanDefinitionsForBeanMethod(beanMethod);
		}
		//解析加载导入的xml文件，将xml文件中的所有beanDefinition注册进入容器
		loadBeanDefinitionsFromImportedResources(configClass.getImportedResources());
         //
		loadBeanDefinitionsFromRegistrars(configClass.getImportBeanDefinitionRegistrars());
	}

}
```

##### 将导入的类注册到容器中

导入的类包括@Component的内部类、@Import注解导入的普通类

```java
class ConfigurationClassBeanDefinitionReader {

    //将导入的类注册到容器中
	private void registerBeanDefinitionForImportedConfigurationClass(ConfigurationClass configClass) {
		AnnotationMetadata metadata = configClass.getMetadata();
		AnnotatedGenericBeanDefinition configBeanDef = new AnnotatedGenericBeanDefinition(metadata);

		ScopeMetadata scopeMetadata = scopeMetadataResolver.resolveScopeMetadata(configBeanDef);
		configBeanDef.setScope(scopeMetadata.getScopeName());
        //生成beanName
		String configBeanName = this.importBeanNameGenerator.generateBeanName(configBeanDef, this.registry);
        //处理@Lazy、@Primary、@DependsOn、@Role、@Description 将对应属性设置到BeanDefinition
		AnnotationConfigUtils.processCommonDefinitionAnnotations(configBeanDef, metadata);

        //注册进入容器
		BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(configBeanDef, configBeanName);
		definitionHolder = AnnotationConfigUtils.applyScopedProxyMode(scopeMetadata, definitionHolder, this.registry);
		this.registry.registerBeanDefinition(definitionHolder.getBeanName(), definitionHolder.getBeanDefinition());
		configClass.setBeanName(configBeanName);

		if (logger.isTraceEnabled()) {
			logger.trace("Registered bean definition for imported class '" + configBeanName + "'");
		}
	}


}
```

##### 处理@Bean的注册BeanDefinition工作

```java
class ConfigurationClassBeanDefinitionReader {
    
    //遍历当前类的beanMethods集合，将所有的bean注册进入容器
	private void loadBeanDefinitionsForBeanMethod(BeanMethod beanMethod) {
		ConfigurationClass configClass = beanMethod.getConfigurationClass();
		MethodMetadata metadata = beanMethod.getMetadata();
		String methodName = metadata.getMethodName();
		
        //满足@Conditional条件
		// Do we need to mark the bean as skipped by its condition?
		if (this.conditionEvaluator.shouldSkip(metadata, ConfigurationPhase.REGISTER_BEAN)) {
			configClass.skippedBeanMethods.add(methodName);
			return;
		}
		if (configClass.skippedBeanMethods.contains(methodName)) {
			return;
		}
		
        //读取@Bean的属性
		AnnotationAttributes bean = AnnotationConfigUtils.attributesFor(metadata, Bean.class);
		Assert.state(bean != null, "No @Bean annotation attributes");

		// Consider name and any aliases
		List<String> names = new ArrayList<>(Arrays.asList(bean.getStringArray("name")));
		String beanName = (!names.isEmpty() ? names.remove(0) : methodName);

		// Register aliases even when overridden
		for (String alias : names) {
			this.registry.registerAlias(beanName, alias);
		}

		// Has this effectively been overridden before (e.g. via XML)?
		if (isOverriddenByExistingDefinition(beanMethod, beanName)) {
			if (beanName.equals(beanMethod.getConfigurationClass().getBeanName())) {
				throw new BeanDefinitionStoreException(beanMethod.getConfigurationClass().getResource().getDescription(),
						beanName, "Bean name derived from @Bean method '" + beanMethod.getMetadata().getMethodName() +
						"' clashes with bean name for containing configuration class; please make those names unique!");
			}
			return;
		}

		ConfigurationClassBeanDefinition beanDef = new ConfigurationClassBeanDefinition(configClass, metadata);
		beanDef.setSource(this.sourceExtractor.extractSource(metadata, configClass.getResource()));

        //查看@Bean的方法是静态的
		if (metadata.isStatic()) {
             //设置静态工厂类和静态工厂方法
			// static @Bean method
			if (configClass.getMetadata() instanceof StandardAnnotationMetadata) {
				beanDef.setBeanClass(((StandardAnnotationMetadata) configClass.getMetadata()).getIntrospectedClass());
			}
			else {
				beanDef.setBeanClassName(configClass.getMetadata().getClassName());
			}
			beanDef.setUniqueFactoryMethodName(methodName);
		}
		else {
             //设置实例工厂类和实例工厂方法
			// instance @Bean method
			beanDef.setFactoryBeanName(configClass.getBeanName());
			beanDef.setUniqueFactoryMethodName(methodName);
		}

		if (metadata instanceof StandardMethodMetadata) {
			beanDef.setResolvedFactoryMethod(((StandardMethodMetadata) metadata).getIntrospectedMethod());
		}

		beanDef.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_CONSTRUCTOR);
		beanDef.setAttribute(org.springframework.beans.factory.annotation.RequiredAnnotationBeanPostProcessor.
				SKIP_REQUIRED_CHECK_ATTRIBUTE, Boolean.TRUE);
		//处理@Lazy、@Primary、@DependsOn、@Role、@Description 将对应属性设置到BeanDefinition
		AnnotationConfigUtils.processCommonDefinitionAnnotations(beanDef, metadata);

		Autowire autowire = bean.getEnum("autowire");
		if (autowire.isAutowire()) {
			beanDef.setAutowireMode(autowire.value());
		}

		boolean autowireCandidate = bean.getBoolean("autowireCandidate");
		if (!autowireCandidate) {
			beanDef.setAutowireCandidate(false);
		}

		String initMethodName = bean.getString("initMethod");
		if (StringUtils.hasText(initMethodName)) {
			beanDef.setInitMethodName(initMethodName);
		}

		String destroyMethodName = bean.getString("destroyMethod");
		beanDef.setDestroyMethodName(destroyMethodName);

		// Consider scoping
		ScopedProxyMode proxyMode = ScopedProxyMode.NO;
		AnnotationAttributes attributes = AnnotationConfigUtils.attributesFor(metadata, Scope.class);
		if (attributes != null) {
			beanDef.setScope(attributes.getString("value"));
			proxyMode = attributes.getEnum("proxyMode");
			if (proxyMode == ScopedProxyMode.DEFAULT) {
				proxyMode = ScopedProxyMode.NO;
			}
		}

		// Replace the original bean definition with the target one, if necessary
		BeanDefinition beanDefToRegister = beanDef;
		if (proxyMode != ScopedProxyMode.NO) {
			BeanDefinitionHolder proxyDef = ScopedProxyCreator.createScopedProxy(
					new BeanDefinitionHolder(beanDef, beanName), this.registry,
					proxyMode == ScopedProxyMode.TARGET_CLASS);
			beanDefToRegister = new ConfigurationClassBeanDefinition(
					(RootBeanDefinition) proxyDef.getBeanDefinition(), configClass, metadata);
		}

		if (logger.isTraceEnabled()) {
			logger.trace(String.format("Registering bean definition for @Bean method %s.%s()",
					configClass.getMetadata().getClassName(), beanName));
		}
        //注册进入容器
		this.registry.registerBeanDefinition(beanName, beanDefToRegister);
	}


}
```



##### 从导入的xml文件中注册BeanDefinition

```java
class ConfigurationClassBeanDefinitionReader {

    
	private void loadBeanDefinitionsFromImportedResources(
			Map<String, Class<? extends BeanDefinitionReader>> importedResources) {

		Map<Class<?>, BeanDefinitionReader> readerInstanceCache = new HashMap<>();
		//循环遍历所有的importedResources
		importedResources.forEach((resource, readerClass) -> {
			// Default reader selection necessary?
			if (BeanDefinitionReader.class == readerClass) {
                  //使用.groovy解析器 GroovyBeanDefinitionReader
				if (StringUtils.endsWithIgnoreCase(resource, ".groovy")) {
					// When clearly asking for Groovy, that's what they'll get...
					readerClass = GroovyBeanDefinitionReader.class;
				}
				else {
                      //默认使用xml文件解析器 XmlBeanDefinitionReader
					// Primarily ".xml" files but for any other extension as well
					readerClass = XmlBeanDefinitionReader.class;
				}
			}

            //初始化BeanDefinitionReader
			BeanDefinitionReader reader = readerInstanceCache.get(readerClass);
			if (reader == null) {
				try {
					// Instantiate the specified BeanDefinitionReader
					reader = readerClass.getConstructor(BeanDefinitionRegistry.class).newInstance(this.registry);
					// Delegate the current ResourceLoader to it if possible
					if (reader instanceof AbstractBeanDefinitionReader) {
						AbstractBeanDefinitionReader abdr = ((AbstractBeanDefinitionReader) reader);
						abdr.setResourceLoader(this.resourceLoader);
						abdr.setEnvironment(this.environment);
					}
					readerInstanceCache.put(readerClass, reader);
				}
				catch (Throwable ex) {
					throw new IllegalStateException(
							"Could not instantiate BeanDefinitionReader class [" + readerClass.getName() + "]");
				}
			}
			//读取文件加载beanDefinition
			// TODO SPR-6310: qualify relative path locations as done in AbstractContextLoader.modifyLocations
			reader.loadBeanDefinitions(resource);
		});
	}

}    
```

##### 通过ImportBeanDefinitionRegistrar注册BeanDefinition

```java
class ConfigurationClassBeanDefinitionReader {

	private void loadBeanDefinitionsFromRegistrars(Map<ImportBeanDefinitionRegistrar, AnnotationMetadata> registrars) {
		registrars.forEach((registrar, metadata) ->
				registrar.registerBeanDefinitions(metadata, this.registry, this.importBeanNameGenerator));
	}


}
```

###### ImportBeanDefinitionRegistrar

```java
public interface ImportBeanDefinitionRegistrar {
    
    //用于注册beanDefinitions
	default void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry,
			BeanNameGenerator importBeanNameGenerator) {

		registerBeanDefinitions(importingClassMetadata, registry);
	}
    
    default void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
	
}
```





## springboot中的@Import

@todo













## BeanDefinition

<img src="./images/image-20250320145721617.png" alt="image-20250320145721617" style="zoom: 33%;" />

- 由xml方式进行导入的bean都是`GenericBeanDefinition`类型
- 被`@ComponentScan`扫描到容器的类都是`ScannedGenericBeanDefinition`类型，`ScannedGenericBeanDefinition`也隶属于`GenericBeanDefinition`
- 被`@Import`导入的类都是`AnnotatedGenericBeanDefinition`类型，`AnnotatedGenericBeanDefinition`也隶属于`GenericBeanDefinition`
- 最终在进行创建bean的过程中，所有的`GenericBeanDefinition`类型都会进行父子beanDefinition合并，从而变成`RootBeanDefinition`

## BeanNameGenerator

```java
public interface BeanNameGenerator {

    /**
     * Generate a bean name for the given bean definition.
     * @param definition the bean definition to generate a name for
     * @param registry the bean definition registry that the given definition
     * is supposed to be registered with
     * @return the generated bean name
     */
    String generateBeanName(BeanDefinition definition, BeanDefinitionRegistry registry);

}
```

### DefaultBeanNameGenerator

```java
//默认的命名生成器，一般用在xml导入bean时
public class DefaultBeanNameGenerator implements BeanNameGenerator {

    public static final DefaultBeanNameGenerator INSTANCE = new DefaultBeanNameGenerator();

    @Override
    public String generateBeanName(BeanDefinition definition, BeanDefinitionRegistry registry) {
       return BeanDefinitionReaderUtils.generateBeanName(definition, registry);
    }
    
    public static String generateBeanName(
			BeanDefinition definition, BeanDefinitionRegistry registry, boolean isInnerBean)
			throws BeanDefinitionStoreException {

		String generatedBeanName = definition.getBeanClassName();
        //没有指定的名字
		if (generatedBeanName == null) {
             //若 BeanDefinition 有父 Bean 名称，则将父 Bean 名称加上 "$child" 作为初始名称。
			if (definition.getParentName() != null) {
				generatedBeanName = definition.getParentName() + "$child";
			}
            //若 BeanDefinition 有工厂 Bean 名称，则将工厂 Bean 名称加上 "$created" 作为初始名称
			else if (definition.getFactoryBeanName() != null) {
				generatedBeanName = definition.getFactoryBeanName() + "$created";
			}
		}
		if (!StringUtils.hasText(generatedBeanName)) {
			throw new BeanDefinitionStoreException("Unnamed bean definition specifies neither " +
					"'class' nor 'parent' nor 'factory-bean' - can't generate bean name");
		}

		if (isInnerBean) {
             //如果是容器内部的bean 加上#和一个hashcode
			// Inner bean: generate identity hashcode suffix.
			return generatedBeanName + GENERATED_BEAN_NAME_SEPARATOR + ObjectUtils.getIdentityHexString(definition);
		}
		
        //唯一化处理beanName 在名字后面再加一个编号 parentName$child#0
		// Top-level bean: use plain class name with unique suffix if necessary.
		return uniqueBeanName(generatedBeanName, registry);
	}
    
    
    public static String uniqueBeanName(String beanName, BeanDefinitionRegistry registry) {
		String id = beanName;
		int counter = -1;
        
		//在名字后面再加一个编号
		// Increase counter until the id is unique.
		String prefix = beanName + GENERATED_BEAN_NAME_SEPARATOR;
		while (counter == -1 || registry.containsBeanDefinition(id)) {
			counter++;
			id = prefix + counter;
		}
		return id;
	}


}
```

### AnnotationBeanNameGenerator

```java
//注解常用的命名生成器，用于导入的bean的beanName生成
public class AnnotationBeanNameGenerator implements BeanNameGenerator {
    
    public static final AnnotationBeanNameGenerator INSTANCE = new AnnotationBeanNameGenerator();

	private static final String COMPONENT_ANNOTATION_CLASSNAME = "org.springframework.stereotype.Component";

	private final Map<String, Set<String>> metaAnnotationTypesCache = new ConcurrentHashMap<>();
    
	@Override
	public String generateBeanName(BeanDefinition definition, BeanDefinitionRegistry registry) {
        //如果是扫描进来的bean或者@Import导入的类
		if (definition instanceof AnnotatedBeanDefinition) {
            //看看@Component @ManagedBean @Named上面有没有写beanName的值
			String beanName = determineBeanNameFromAnnotation((AnnotatedBeanDefinition) definition);
             //如果有 那么使用注解上的beanName
			if (StringUtils.hasText(beanName)) {
				// Explicit bean name found.
				return beanName;
			}
		}
        //没有的话就使用当前类名小写首字母的形式 myComponet
		// Fallback: generate a unique default bean name.
		return buildDefaultBeanName(definition, registry);
	}
    
    @Nullable
	protected String determineBeanNameFromAnnotation(AnnotatedBeanDefinition annotatedDef) {
		AnnotationMetadata amd = annotatedDef.getMetadata();
		Set<String> types = amd.getAnnotationTypes();
		String beanName = null;
         //遍历类上所有注解
		for (String type : types) {
			AnnotationAttributes attributes = AnnotationConfigUtils.attributesFor(amd, type);
			if (attributes != null) {
				Set<String> metaTypes = this.metaAnnotationTypesCache.computeIfAbsent(type, key -> {
					Set<String> result = amd.getMetaAnnotationTypes(key);
					return (result.isEmpty() ? Collections.emptySet() : result);
				});
                 //如果是@Componet @ManagedBean @Named注解 并且有指定名字
				if (isStereotypeWithNameValue(type, metaTypes, attributes)) {
					Object value = attributes.get("value");
					if (value instanceof String) {
						String strVal = (String) value;
						if (StringUtils.hasLength(strVal)) {
							if (beanName != null && !strVal.equals(beanName)) {
								throw new IllegalStateException("Stereotype annotations suggest inconsistent " +
										"component names: '" + beanName + "' versus '" + strVal + "'");
							}
                              //设置名字
							beanName = strVal;
						}
					}
				}
			}
		}
		return beanName;
	}
    
    protected boolean isStereotypeWithNameValue(String annotationType,
			Set<String> metaAnnotationTypes, @Nullable Map<String, Object> attributes) {
		
        //是否@Componet @ManagedBean @Named
		boolean isStereotype = annotationType.equals(COMPONENT_ANNOTATION_CLASSNAME) ||
				metaAnnotationTypes.contains(COMPONENT_ANNOTATION_CLASSNAME) ||
				annotationType.equals("javax.annotation.ManagedBean") ||
				annotationType.equals("javax.inject.Named");

		return (isStereotype && attributes != null && attributes.containsKey("value"));
	}
    
    //生成默认的首字母小写的beanName
    protected String buildDefaultBeanName(BeanDefinition definition) {
		String beanClassName = definition.getBeanClassName();
		Assert.state(beanClassName != null, "No bean class name set");
		String shortClassName = ClassUtils.getShortName(beanClassName);
		return Introspector.decapitalize(shortClassName);
	}
}
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
.\springstudycode\target\classes\com
//假设当前依赖两个jar包，那么每个jar包的路径都可以看作一个独立的查找路径，在每个jar包下查找对应的文件
//依赖Jar包的类和资源路径的com文件
D:\doc\my\studymd\LearningNotes\file:\D:\softwares\LocalRepository\com\fasterxml\jackson\core\jackson-databind\2.11.0\jackson-databind-2.11.0.jar!\com
//依赖Jar包的类和资源路径的com文件
D:\doc\my\studymd\LearningNotes\file:\D:\softwares\LocalRepository\com\fasterxml\jackson\core\jackson-annotations\2.11.0\jackson-annotations-2.11.0.jar!\com
```

当前项目的类和资源路径 （`target/classes`）的目录结构 ： **顶级类包** + **开发时resources目录下的所有文件**

<img src="./images/image-20250227140553290.png" alt="image-20250227140553290" style="zoom:50%;" />

依赖Jar包的类和资源路径 （`D:\softwares\LocalRepository\kkk.jar/`）的目录结构 ：和上面基本一致  **顶级类包** + **所有文件资源文件**

<img src="./images/image-20250227140835971.png" alt="image-20250227140835971" style="zoom:50%;" />

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



### 常见的使用资源加载和占位符替换的方法

```java
@Component
public class ResourceTest {

    @Resource
    Environment environment;

    private void test() {
        new PathMatchingResourcePatternResolver().getResource("xxx.yml");
        environment.resolvePlaceholders("${user.name}");
    }

}
```



## registerBeanPostProcessors()

- 获取beanFactory中所有的BeanPostProcessor的，根据PriorityOrdered、MergedBeanDefinitionPostProcessor、Ordered和无序的进行分类
- 首先注册PriorityOrdered的BeanPostProcessor到beanFactory中
- 注册Ordered的BeanPostProcessor到beanFactory中
- 注册无序的BeanPostProcessor到beanFactory中
- 注册MergedBeanDefinitionPostProcessor的BeanPostProcessor到beanFactory中

```java
public static void registerBeanPostProcessors(
       ConfigurableListableBeanFactory beanFactory, AbstractApplicationContext applicationContext) {

    String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanPostProcessor.class, true, false);

    // Register BeanPostProcessorChecker that logs an info message when
    // a bean is created during BeanPostProcessor instantiation, i.e. when
    // a bean is not eligible for getting processed by all BeanPostProcessors.
    int beanProcessorTargetCount = beanFactory.getBeanPostProcessorCount() + 1 + postProcessorNames.length;
    beanFactory.addBeanPostProcessor(new BeanPostProcessorChecker(beanFactory, beanProcessorTargetCount));

    //获取beanFactory中所有的BeanPostProcessor的，根据PriorityOrdered、MergedBeanDefinitionPostProcessor、Ordered和无序的进行分类
    // Separate between BeanPostProcessors that implement PriorityOrdered,
    // Ordered, and the rest.
    List<BeanPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
    List<BeanPostProcessor> internalPostProcessors = new ArrayList<>();
    List<String> orderedPostProcessorNames = new ArrayList<>();
    List<String> nonOrderedPostProcessorNames = new ArrayList<>();
    for (String ppName : postProcessorNames) {
       if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
          BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
          priorityOrderedPostProcessors.add(pp);
          if (pp instanceof MergedBeanDefinitionPostProcessor) {
             internalPostProcessors.add(pp);
          }
       }
       else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
          orderedPostProcessorNames.add(ppName);
       }
       else {
          nonOrderedPostProcessorNames.add(ppName);
       }
    }

    //首先注册PriorityOrdered的BeanPostProcessor到beanFactory中
    // First, register the BeanPostProcessors that implement PriorityOrdered.
    sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
    registerBeanPostProcessors(beanFactory, priorityOrderedPostProcessors);

    //注册Ordered的BeanPostProcessor到beanFactory中
    // Next, register the BeanPostProcessors that implement Ordered.
    List<BeanPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());
    for (String ppName : orderedPostProcessorNames) {
       BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
       orderedPostProcessors.add(pp);
       if (pp instanceof MergedBeanDefinitionPostProcessor) {
          internalPostProcessors.add(pp);
       }
    }
    sortPostProcessors(orderedPostProcessors, beanFactory);
    registerBeanPostProcessors(beanFactory, orderedPostProcessors);

    //注册无序的BeanPostProcessor到beanFactory中
    // Now, register all regular BeanPostProcessors.
    List<BeanPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());
    for (String ppName : nonOrderedPostProcessorNames) {
       BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
       nonOrderedPostProcessors.add(pp);
       if (pp instanceof MergedBeanDefinitionPostProcessor) {
          internalPostProcessors.add(pp);
       }
    }
    registerBeanPostProcessors(beanFactory, nonOrderedPostProcessors);

    //注册MergedBeanDefinitionPostProcessor的BeanPostProcessor到beanFactory中
    // Finally, re-register all internal BeanPostProcessors.
    sortPostProcessors(internalPostProcessors, beanFactory);
    registerBeanPostProcessors(beanFactory, internalPostProcessors);

    // Re-register post-processor for detecting inner beans as ApplicationListeners,
    // moving it to the end of the processor chain (for picking up proxies etc).
    beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(applicationContext));
}
```





### BeanPostProcessor继承关系

![image-20250304201532795](./images/image-20250304201532795.png)

@todo





## initMessageSource()

```java

public static final String MESSAGE_SOURCE_BEAN_NAME = "messageSource";

//定义国际化 创建messageSource的对象
protected void initMessageSource() {
    ConfigurableListableBeanFactory beanFactory = getBeanFactory();
    if (beanFactory.containsLocalBean(MESSAGE_SOURCE_BEAN_NAME)) {
       this.messageSource = beanFactory.getBean(MESSAGE_SOURCE_BEAN_NAME, MessageSource.class);
       // Make MessageSource aware of parent MessageSource.
       if (this.parent != null && this.messageSource instanceof HierarchicalMessageSource) {
          HierarchicalMessageSource hms = (HierarchicalMessageSource) this.messageSource;
          if (hms.getParentMessageSource() == null) {
             // Only set parent context as parent MessageSource if no parent MessageSource
             // registered already.
             hms.setParentMessageSource(getInternalParentMessageSource());
          }
       }
       if (logger.isTraceEnabled()) {
          logger.trace("Using MessageSource [" + this.messageSource + "]");
       }
    }
    else {
       // Use empty MessageSource to be able to accept getMessage calls.
       DelegatingMessageSource dms = new DelegatingMessageSource();
       dms.setParentMessageSource(getInternalParentMessageSource());
       this.messageSource = dms;
       beanFactory.registerSingleton(MESSAGE_SOURCE_BEAN_NAME, this.messageSource);
       if (logger.isTraceEnabled()) {
          logger.trace("No '" + MESSAGE_SOURCE_BEAN_NAME + "' bean, using [" + this.messageSource + "]");
       }
    }
}
```



## initApplicationEventMulticaster()

<img src="./images/image-20250304204121825.png" alt="image-20250304204121825" style="zoom:50%;" />

```java
public static final String APPLICATION_EVENT_MULTICASTER_BEAN_NAME = "applicationEventMulticaster";

//初始化事件多播器
protected void initApplicationEventMulticaster() {
    ConfigurableListableBeanFactory beanFactory = getBeanFactory();
    if (beanFactory.containsLocalBean(APPLICATION_EVENT_MULTICASTER_BEAN_NAME)) {
       this.applicationEventMulticaster =
             beanFactory.getBean(APPLICATION_EVENT_MULTICASTER_BEAN_NAME, ApplicationEventMulticaster.class);
       if (logger.isTraceEnabled()) {
          logger.trace("Using ApplicationEventMulticaster [" + this.applicationEventMulticaster + "]");
       }
    }
    else {
        //创建默认的事件多播器SimpleApplicationEventMulticaster
       this.applicationEventMulticaster = new SimpleApplicationEventMulticaster(beanFactory);
       beanFactory.registerSingleton(APPLICATION_EVENT_MULTICASTER_BEAN_NAME, this.applicationEventMulticaster);
       if (logger.isTraceEnabled()) {
          logger.trace("No '" + APPLICATION_EVENT_MULTICASTER_BEAN_NAME + "' bean, using " +
                "[" + this.applicationEventMulticaster.getClass().getSimpleName() + "]");
       }
    }
}
```



## registerListeners()

```java
//注册监听器
protected void registerListeners() {
    //将ApplicationContext下的所有监听器注册到事件多播器中
    // Register statically specified listeners first.
    for (ApplicationListener<?> listener : getApplicationListeners()) {
       getApplicationEventMulticaster().addApplicationListener(listener);
    }

    //将beanFactory中的所有监听器注册到事件多播器中
    // Do not initialize FactoryBeans here: We need to leave all regular beans
    // uninitialized to let post-processors apply to them!
    String[] listenerBeanNames = getBeanNamesForType(ApplicationListener.class, true, false);
    for (String listenerBeanName : listenerBeanNames) {
       getApplicationEventMulticaster().addApplicationListenerBean(listenerBeanName);
    }

    //发布需要提前触发的事件
    // Publish early application events now that we finally have a multicaster...
    Set<ApplicationEvent> earlyEventsToProcess = this.earlyApplicationEvents;
    this.earlyApplicationEvents = null;
    if (!CollectionUtils.isEmpty(earlyEventsToProcess)) {
       for (ApplicationEvent earlyEvent : earlyEventsToProcess) {
          getApplicationEventMulticaster().multicastEvent(earlyEvent);
       }
    }
}
```



## finishBeanFactoryInitialization()

```java

String CONVERSION_SERVICE_BEAN_NAME = "conversionService";


protected void finishBeanFactoryInitialization(ConfigurableListableBeanFactory beanFactory) {
    //设置conversionService
    // Initialize conversion service for this context.
    if (beanFactory.containsBean(CONVERSION_SERVICE_BEAN_NAME) &&
          beanFactory.isTypeMatch(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class)) {
       beanFactory.setConversionService(
             beanFactory.getBean(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class));
    }

    //如果没有设置占位符处理器，那么使用默认的Environment中的PropertySourcesPropertyResolver
    // Register a default embedded value resolver if no bean post-processor
    // (such as a PropertyPlaceholderConfigurer bean) registered any before:
    // at this point, primarily for resolution in annotation attribute values.
    if (!beanFactory.hasEmbeddedValueResolver()) {
       beanFactory.addEmbeddedValueResolver(strVal -> getEnvironment().resolvePlaceholders(strVal));
    }

    //初始化LoadTimeWeaverAware
    // Initialize LoadTimeWeaverAware beans early to allow for registering their transformers early.
    String[] weaverAwareNames = beanFactory.getBeanNamesForType(LoadTimeWeaverAware.class, false, false);
    for (String weaverAwareName : weaverAwareNames) {
       getBean(weaverAwareName);
    }

    // Stop using the temporary ClassLoader for type matching.
    beanFactory.setTempClassLoader(null);

    //冻结配置
    // Allow for caching all bean definition metadata, not expecting further changes.
    beanFactory.freezeConfiguration();

    //开始创建bean
    // Instantiate all remaining (non-lazy-init) singletons.
    beanFactory.preInstantiateSingletons();
}
```





### ConversionService

在spring中使用的默认ConversionService是`DefaultConversionService`

#### DefaultConversionService

```java
public class DefaultConversionService extends GenericConversionService {
 
    //在创建DefaultConversionService时，会自动添加默认的一些类型转换器
    public DefaultConversionService() {
		addDefaultConverters(this);
	}

    //默认的类型转换器可以支持绝大部分类型转换工作
	public static void addDefaultConverters(ConverterRegistry converterRegistry) {
		addScalarConverters(converterRegistry);
		addCollectionConverters(converterRegistry);

		converterRegistry.addConverter(new ByteBufferConverter((ConversionService) converterRegistry));
		converterRegistry.addConverter(new StringToTimeZoneConverter());
		converterRegistry.addConverter(new ZoneIdToTimeZoneConverter());
		converterRegistry.addConverter(new ZonedDateTimeToCalendarConverter());

		converterRegistry.addConverter(new ObjectToObjectConverter());
		converterRegistry.addConverter(new IdToEntityConverter((ConversionService) converterRegistry));
		converterRegistry.addConverter(new FallbackObjectToStringConverter());
		converterRegistry.addConverter(new ObjectToOptionalConverter((ConversionService) converterRegistry));
	}
    //添加基本数据类型引用的转换器
	private static void addScalarConverters(ConverterRegistry converterRegistry) {
		converterRegistry.addConverterFactory(new NumberToNumberConverterFactory());

		converterRegistry.addConverterFactory(new StringToNumberConverterFactory());
		converterRegistry.addConverter(Number.class, String.class, new ObjectToStringConverter());

		converterRegistry.addConverter(new StringToCharacterConverter());
		converterRegistry.addConverter(Character.class, String.class, new ObjectToStringConverter());

		converterRegistry.addConverter(new NumberToCharacterConverter());
		converterRegistry.addConverterFactory(new CharacterToNumberFactory());

		converterRegistry.addConverter(new StringToBooleanConverter());
		converterRegistry.addConverter(Boolean.class, String.class, new ObjectToStringConverter());

		converterRegistry.addConverterFactory(new StringToEnumConverterFactory());
		converterRegistry.addConverter(new EnumToStringConverter((ConversionService) converterRegistry));

		converterRegistry.addConverterFactory(new IntegerToEnumConverterFactory());
		converterRegistry.addConverter(new EnumToIntegerConverter((ConversionService) converterRegistry));

		converterRegistry.addConverter(new StringToLocaleConverter());
		converterRegistry.addConverter(Locale.class, String.class, new ObjectToStringConverter());

		converterRegistry.addConverter(new StringToCharsetConverter());
		converterRegistry.addConverter(Charset.class, String.class, new ObjectToStringConverter());

		converterRegistry.addConverter(new StringToCurrencyConverter());
		converterRegistry.addConverter(Currency.class, String.class, new ObjectToStringConverter());

		converterRegistry.addConverter(new StringToPropertiesConverter());
		converterRegistry.addConverter(new PropertiesToStringConverter());

		converterRegistry.addConverter(new StringToUUIDConverter());
		converterRegistry.addConverter(UUID.class, String.class, new ObjectToStringConverter());
	}
    
    //添加集合间的转换器
    public static void addCollectionConverters(ConverterRegistry converterRegistry) {
		ConversionService conversionService = (ConversionService) converterRegistry;

		converterRegistry.addConverter(new ArrayToCollectionConverter(conversionService));
		converterRegistry.addConverter(new CollectionToArrayConverter(conversionService));

		converterRegistry.addConverter(new ArrayToArrayConverter(conversionService));
		converterRegistry.addConverter(new CollectionToCollectionConverter(conversionService));
		converterRegistry.addConverter(new MapToMapConverter(conversionService));

		converterRegistry.addConverter(new ArrayToStringConverter(conversionService));
		converterRegistry.addConverter(new StringToArrayConverter(conversionService));

		converterRegistry.addConverter(new ArrayToObjectConverter(conversionService));
		converterRegistry.addConverter(new ObjectToArrayConverter(conversionService));

		converterRegistry.addConverter(new CollectionToStringConverter(conversionService));
		converterRegistry.addConverter(new StringToCollectionConverter(conversionService));

		converterRegistry.addConverter(new CollectionToObjectConverter(conversionService));
		converterRegistry.addConverter(new ObjectToCollectionConverter(conversionService));

		converterRegistry.addConverter(new StreamConverter(conversionService));
	}
    
}    
    
```







### PropertySourcesPlaceholderConfigurer

继承BeanFactoryPostProcessor 是

```xml
 <context:property-placeholder location="classpath:dbconfig.properties"></context:property-placeholder>
```

这个标签的解析结果 用于在BFPP环节处理beanDefinition中的${}占位符替换工作

@todo 那PropertySourcesPropertyResolver有同样的作用





### preInstantiateSingletons()

```java
public class DefaultListableBeanFactory extends AbstractAutowireCapableBeanFactory
		implements ConfigurableListableBeanFactory, BeanDefinitionRegistry, Serializable {
    
    String FACTORY_BEAN_PREFIX = "&";
    
    
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
             //合并BeanDefinition，将GenericBeanDefinition、ScannedGenericBeanDefinition变成RootBeanDefinition
			RootBeanDefinition bd = getMergedLocalBeanDefinition(beanName);
             //校验是否是非抽象的、单例的、非懒加载的bean
			if (!bd.isAbstract() && bd.isSingleton() && !bd.isLazyInit()) {
                  //判断一下是否是FactoryBean
				if (isFactoryBean(beanName)) {
                      //通过 &beanName 的形式获取bean 这里是去创建FactoryBean的实例，内部的对象还没有被创建
					Object bean = getBean(FACTORY_BEAN_PREFIX + beanName);
					if (bean instanceof FactoryBean) {
						final FactoryBean<?> factory = (FactoryBean<?>) bean;
                          //如果bean继承了SmartFactoryBean，并且标注要早早创建内部的对象
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
                          //通过 beanName创建内部
						if (isEagerInit) {
							getBean(beanName);
						}
					}
				}
                 //创建bean
				else {
					getBean(beanName);
				}
			}
		}

        //循环所有创建好的bean 如果属于SmartInitializingSingleton 调用afterSingletonsInstantiated方法
		// Trigger post-initialization callback for all applicable beans...
		for (String beanName : beanNames) {
			Object singletonInstance = getSingleton(beanName);
			if (singletonInstance instanceof SmartInitializingSingleton) {
				final SmartInitializingSingleton smartSingleton = (SmartInitializingSingleton) singletonInstance;
				if (System.getSecurityManager() != null) {
					AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
						smartSingleton.afterSingletonsInstantiated();
						return null;
					}, getAccessControlContext());
				}
				else {
					smartSingleton.afterSingletonsInstantiated();
				}
			}
		}
	}
    
    
    //判断是否是FactoryBean
	@Override
	public boolean isFactoryBean(String name) throws NoSuchBeanDefinitionException {
         //获取beanName
		String beanName = transformedBeanName(name);
		Object beanInstance = getSingleton(beanName, false);
        //如果有实例 那么判断是否属于FactoryBean
		if (beanInstance != null) {
			return (beanInstance instanceof FactoryBean);
		}
         //没有实例 交给父类容器去判断
		// No singleton instance found -> check bean definition.
		if (!containsBeanDefinition(beanName) && getParentBeanFactory() instanceof ConfigurableBeanFactory) {
			// No bean definition found in this factory -> delegate to parent.
			return ((ConfigurableBeanFactory) getParentBeanFactory()).isFactoryBean(name);
		}
         //根据RootBeanDefinition判断
		return isFactoryBean(beanName, getMergedLocalBeanDefinition(beanName));
	}
    
    //判断是否是FactoryBean
	protected boolean isFactoryBean(String beanName, RootBeanDefinition mbd) {
		Boolean result = mbd.isFactoryBean;
         //如果没判断过 那么去判断一下当前bean是否属于FactoryBean
		if (result == null) {
			Class<?> beanType = predictBeanType(beanName, mbd, FactoryBean.class);
			result = (beanType != null && FactoryBean.class.isAssignableFrom(beanType));
			mbd.isFactoryBean = result;
		}
		return result;
	}

}
```

#### 合并BeanDefinition

合并BeanDefinition，将GenericBeanDefinition、ScannedGenericBeanDefinition变成RootBeanDefinition

- 从合并好的RootBeanDefinition缓存中取，没有的话进行合并
- 查找beanDefinition的parentName，如果没有，直接对当前beanDefinition包装成RootBeanDefinition
- 如果有parentName，递归先合并父beanDefinition
- 包装父beanDefinition为RootBeanDefinition，将子beanDefinition的属性覆盖父beanDefinition
- 返回合并好的RootBeanDefinition

```java
public abstract class AbstractBeanFactory extends FactoryBeanRegistrySupport implements ConfigurableBeanFactory {

    //合并BeanDefinition
	protected RootBeanDefinition getMergedLocalBeanDefinition(String beanName) throws BeansException {
		// Quick check on the concurrent map first, with minimal locking.
		RootBeanDefinition mbd = this.mergedBeanDefinitions.get(beanName);
		if (mbd != null && !mbd.stale) {
			return mbd;
		}
		return getMergedBeanDefinition(beanName, getBeanDefinition(beanName));
	}
    
    protected RootBeanDefinition getMergedBeanDefinition(String beanName, BeanDefinition bd)
			throws BeanDefinitionStoreException {

		return getMergedBeanDefinition(beanName, bd, null);
	}
    
    
	protected RootBeanDefinition getMergedBeanDefinition(
			String beanName, BeanDefinition bd, @Nullable BeanDefinition containingBd)
			throws BeanDefinitionStoreException {

		synchronized (this.mergedBeanDefinitions) {
			RootBeanDefinition mbd = null;
			RootBeanDefinition previous = null;

			// Check with full lock now in order to enforce the same merged instance.
			if (containingBd == null) {
				mbd = this.mergedBeanDefinitions.get(beanName);
			}

            //合并
			if (mbd == null || mbd.stale) {
				previous = mbd;
                //如果beanDefinition没有父beanDefinition的名称
				if (bd.getParentName() == null) {
                     //如果这个beanDefinition属于RootBeanDefinition 那么克隆一个新的就算合并完了
					// Use copy of given root bean definition.
					if (bd instanceof RootBeanDefinition) {
						mbd = ((RootBeanDefinition) bd).cloneBeanDefinition();
					}
                      //不属于RootBeanDefinition 包装一下
					else {
						mbd = new RootBeanDefinition(bd);
					}
				}
                //如果beanDefinition有父beanDefinition的名称
				else {
					// Child bean definition: needs to be merged with parent.
					BeanDefinition pbd;
					try {
                          //转换父beanDefinition的名称 别名变本名、去掉&等
						String parentBeanName = transformedBeanName(bd.getParentName());
                          //如果父beanDefinition的名称不等于自己的名称
						if (!beanName.equals(parentBeanName)) {
                               //那么递归先合并父beanDefinition
							pbd = getMergedBeanDefinition(parentBeanName);
						}
                          //如果父beanDefinition的名称等于自己的名称
						else {
                               //尝试去父BeanFactory合并
							BeanFactory parent = getParentBeanFactory();
							if (parent instanceof ConfigurableBeanFactory) {
								pbd = ((ConfigurableBeanFactory) parent).getMergedBeanDefinition(parentBeanName);
							}
                              // 否则报错
							else {
								throw new NoSuchBeanDefinitionException(parentBeanName,
										"Parent name '" + parentBeanName + "' is equal to bean name '" + beanName +
										"': cannot be resolved without a ConfigurableBeanFactory parent");
							}
						}
					}
					catch (NoSuchBeanDefinitionException ex) {
						throw new BeanDefinitionStoreException(bd.getResourceDescription(), beanName,
								"Could not resolve parent bean definition '" + bd.getParentName() + "'", ex);
					}
					// Deep copy with overridden values.
                      //包装父beanDefinition
					mbd = new RootBeanDefinition(pbd);
                      //合并父beanDefinition和当前beanDefinition
					mbd.overrideFrom(bd);
				}

				// Set default singleton scope, if not configured before.
				if (!StringUtils.hasLength(mbd.getScope())) {
					mbd.setScope(SCOPE_SINGLETON);
				}

				// A bean contained in a non-singleton bean cannot be a singleton itself.
				// Let's correct this on the fly here, since this might be the result of
				// parent-child merging for the outer bean, in which case the original inner bean
				// definition will not have inherited the merged outer bean's singleton status.
				if (containingBd != null && !containingBd.isSingleton() && mbd.isSingleton()) {
					mbd.setScope(containingBd.getScope());
				}
				
                 //缓存合并好的RootBeanDefinition
				// Cache the merged bean definition for the time being
				// (it might still get re-merged later on in order to pick up metadata changes)
				if (containingBd == null && isCacheBeanMetadata()) {
					this.mergedBeanDefinitions.put(beanName, mbd);
				}
			}
			if (previous != null) {
				copyRelevantMergedBeanDefinitionCaches(previous, mbd);
			}
             //返回RootBeanDefinition
			return mbd;
		}
	}


}
```

##### AbstractBeanDefinition

```java
public abstract class AbstractBeanDefinition extends BeanMetadataAttributeAccessor
       implements BeanDefinition, Cloneable {
    
   //用子beanDefinition的属性覆盖父beanDefinition的属性
   public void overrideFrom(BeanDefinition other) {
		if (StringUtils.hasLength(other.getBeanClassName())) {
			setBeanClassName(other.getBeanClassName());
		}
		if (StringUtils.hasLength(other.getScope())) {
			setScope(other.getScope());
		}
		setAbstract(other.isAbstract());
		if (StringUtils.hasLength(other.getFactoryBeanName())) {
			setFactoryBeanName(other.getFactoryBeanName());
		}
		if (StringUtils.hasLength(other.getFactoryMethodName())) {
			setFactoryMethodName(other.getFactoryMethodName());
		}
		setRole(other.getRole());
		setSource(other.getSource());
		copyAttributesFrom(other);

		if (other instanceof AbstractBeanDefinition) {
			AbstractBeanDefinition otherAbd = (AbstractBeanDefinition) other;
			if (otherAbd.hasBeanClass()) {
				setBeanClass(otherAbd.getBeanClass());
			}
			if (otherAbd.hasConstructorArgumentValues()) {
				getConstructorArgumentValues().addArgumentValues(other.getConstructorArgumentValues());
			}
			if (otherAbd.hasPropertyValues()) {
				getPropertyValues().addPropertyValues(other.getPropertyValues());
			}
			if (otherAbd.hasMethodOverrides()) {
				getMethodOverrides().addOverrides(otherAbd.getMethodOverrides());
			}
			Boolean lazyInit = otherAbd.getLazyInit();
			if (lazyInit != null) {
				setLazyInit(lazyInit);
			}
			setAutowireMode(otherAbd.getAutowireMode());
			setDependencyCheck(otherAbd.getDependencyCheck());
			setDependsOn(otherAbd.getDependsOn());
			setAutowireCandidate(otherAbd.isAutowireCandidate());
			setPrimary(otherAbd.isPrimary());
			copyQualifiersFrom(otherAbd);
			setInstanceSupplier(otherAbd.getInstanceSupplier());
			setNonPublicAccessAllowed(otherAbd.isNonPublicAccessAllowed());
			setLenientConstructorResolution(otherAbd.isLenientConstructorResolution());
			if (otherAbd.getInitMethodName() != null) {
				setInitMethodName(otherAbd.getInitMethodName());
				setEnforceInitMethod(otherAbd.isEnforceInitMethod());
			}
			if (otherAbd.getDestroyMethodName() != null) {
				setDestroyMethodName(otherAbd.getDestroyMethodName());
				setEnforceDestroyMethod(otherAbd.isEnforceDestroyMethod());
			}
			setSynthetic(otherAbd.isSynthetic());
			setResource(otherAbd.getResource());
		}
		else {
			getConstructorArgumentValues().addArgumentValues(other.getConstructorArgumentValues());
			getPropertyValues().addPropertyValues(other.getPropertyValues());
			setLazyInit(other.isLazyInit());
			setResourceDescription(other.getResourceDescription());
		}
	}

}
```



#### FactoryBean

`FactoryBean` 是 Spring 框架提供的一个接口，用于绕过 Spring 复杂的 Bean 创建流程。它为 Bean 的创建过程提供了一种更灵活的方式，允许开发者自定义 Bean 的创建逻辑。

- 自定义的FactoryBean在注册到容器后，会在生成RootBeanDefinition后根据是否实现了FactoryBean接口被标注`isFactoryBean=true`

- 容器在创建bean时，会根据标注来区分创建bean，被判断为FactoryBean的会在创建时加上`&`，即`getBean(&beanName)`

- 和常规的bean创建流程一致，自定义的FactoryBean会在创建后去掉`&`，在一级缓存中存放 `beanName -> factoryBean`，如果实现了SmartFactoryBean接口并且标注提前创建内部对象，那么自定义的FactoryBean会在创建后立刻调用`getBean(beanName)`

- 获取内部对象的方法就是在FactoryBean创建后，调用`getBean(beanName)`，容器会根据一级缓存中的 `beanName -> factoryBean`找到自定义的FactoryBean

- 如果传入的名称是`&beanName`，那么直接返回当前的自定义的FactoryBean

- 如果传入的名称是`beanName `，那么从缓存`factoryBeanObjectCache`中查找`beanName -> bean`，如果没有就调用`factoryBean.getObject()`获得内部对象并放入缓存中

- 以上步骤完成后，会存在两个beanName在不同的集合下存放不同的bean

  <img src="./images/image-20250307152352847.png" alt="image-20250307152352847" style="zoom:50%;" />

```java
public abstract class AbstractBeanFactory extends FactoryBeanRegistrySupport implements ConfigurableBeanFactory {
    
	protected <T> T doGetBean(final String name, @Nullable final Class<T> requiredType,
			@Nullable final Object[] args, boolean typeCheckOnly) throws BeansException {
		//无论是&beanName和beanName都转换成beanName
		final String beanName = transformedBeanName(name);
		Object bean;

		// Eagerly check singleton cache for manually registered singletons.
        //根据beanName获取factorybean实例对象
		Object sharedInstance = getSingleton(beanName);
        //如果能获取到factorybean实例对象
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
             //获取到factorybean实例对象后，再根据&beanName和beanName 带不带&来决定返回factorybean实例对象还是内部对象
			bean = getObjectForBeanInstance(sharedInstance, name, beanName, null);
		}
         //否则进行常规的创建流程 创建factorybean实例对象
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

			if (!typeCheckOnly) {
				markBeanAsCreated(beanName);
			}

			try {
				final RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
				checkMergedBeanDefinition(mbd, beanName, args);

				// Guarantee initialization of beans that the current bean depends on.
				String[] dependsOn = mbd.getDependsOn();
				if (dependsOn != null) {
					for (String dep : dependsOn) {
						if (isDependent(beanName, dep)) {
							throw new BeanCreationException(mbd.getResourceDescription(), beanName,
									"Circular depends-on relationship between '" + beanName + "' and '" + dep + "'");
						}
						registerDependentBean(dep, beanName);
						try {
							getBean(dep);
						}
						catch (NoSuchBeanDefinitionException ex) {
							throw new BeanCreationException(mbd.getResourceDescription(), beanName,
									"'" + beanName + "' depends on missing bean '" + dep + "'", ex);
						}
					}
				}

				// Create bean instance.
				if (mbd.isSingleton()) {
					sharedInstance = getSingleton(beanName, () -> {
						try {
							return createBean(beanName, mbd, args);
						}
						catch (BeansException ex) {
							// Explicitly remove instance from singleton cache: It might have been put there
							// eagerly by the creation process, to allow for circular reference resolution.
							// Also remove any beans that received a temporary reference to the bean.
							destroySingleton(beanName);
							throw ex;
						}
					});
					bean = getObjectForBeanInstance(sharedInstance, name, beanName, mbd);
				}

				else if (mbd.isPrototype()) {
					// It's a prototype -> create a new instance.
					Object prototypeInstance = null;
					try {
						beforePrototypeCreation(beanName);
						prototypeInstance = createBean(beanName, mbd, args);
					}
					finally {
						afterPrototypeCreation(beanName);
					}
					bean = getObjectForBeanInstance(prototypeInstance, name, beanName, mbd);
				}

				else {
					String scopeName = mbd.getScope();
					final Scope scope = this.scopes.get(scopeName);
					if (scope == null) {
						throw new IllegalStateException("No Scope registered for scope name '" + scopeName + "'");
					}
					try {
						Object scopedInstance = scope.get(beanName, () -> {
							beforePrototypeCreation(beanName);
							try {
								return createBean(beanName, mbd, args);
							}
							finally {
								afterPrototypeCreation(beanName);
							}
						});
						bean = getObjectForBeanInstance(scopedInstance, name, beanName, mbd);
					}
					catch (IllegalStateException ex) {
						throw new BeanCreationException(beanName,
								"Scope '" + scopeName + "' is not active for the current thread; consider " +
								"defining a scoped proxy for this bean if you intend to refer to it from a singleton",
								ex);
					}
				}
			}
			catch (BeansException ex) {
				cleanupAfterBeanCreationFailure(beanName);
				throw ex;
			}
		}

		// Check if required type matches the type of the actual bean instance.
		if (requiredType != null && !requiredType.isInstance(bean)) {
			try {
				T convertedBean = getTypeConverter().convertIfNecessary(bean, requiredType);
				if (convertedBean == null) {
					throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
				}
				return convertedBean;
			}
			catch (TypeMismatchException ex) {
				if (logger.isTraceEnabled()) {
					logger.trace("Failed to convert bean '" + name + "' to required type '" +
							ClassUtils.getQualifiedName(requiredType) + "'", ex);
				}
				throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
			}
		}
		return (T) bean;
	}
    
    
    
    
    //根据&beanName和beanName 带不带&来决定返回factorybean实例对象还是内部对象
    protected Object getObjectForBeanInstance(
			Object beanInstance, String name, String beanName, @Nullable RootBeanDefinition mbd) {

        //如果name带有&前缀 那么直接返回factorybean实例对象
		// Don't let calling code try to dereference the factory if the bean isn't a factory.
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
			return beanInstance;
		}

		// Now we have the bean instance, which may be a normal bean or a FactoryBean.
		// If it's a FactoryBean, we use it to create a bean instance, unless the
		// caller actually wants a reference to the factory.
		if (!(beanInstance instanceof FactoryBean)) {
			return beanInstance;
		}
		
        //否则进行获取内部对象
		Object object = null;
		if (mbd != null) {
			mbd.isFactoryBean = true;
		}
		else {
             //从factoryBeanObjectCache缓冲中获取一下内部对象
			object = getCachedObjectForFactoryBean(beanName);
		}
         //没有的话只能调用factorybean.getObject()来获得
		if (object == null) {
			// Return bean instance from factory.
			FactoryBean<?> factory = (FactoryBean<?>) beanInstance;
			// Caches object obtained from FactoryBean if it is a singleton.
			if (mbd == null && containsBeanDefinition(beanName)) {
				mbd = getMergedLocalBeanDefinition(beanName);
			}
			boolean synthetic = (mbd != null && mbd.isSynthetic());
			object = getObjectFromFactoryBean(factory, beanName, !synthetic);
		}
		return object;
	}

	@Nullable
	protected Object getCachedObjectForFactoryBean(String beanName) {
		return this.factoryBeanObjectCache.get(beanName);
	}
}
```

##### 自定义FactoryBean

```java
@Component
public class MyFactoryBean implements FactoryBean<User> {
    @Override
    public User getObject() throws Exception {
        User user = new User();
        user.setAge(18);
        user.setName("zcq");
        return user;
    }

    @Override
    public Class<?> getObjectType() {
        return User.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}

class User {
    private String name;
    private Integer age;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

}
```

##### 获取FactoryBean

容器在通过beanName取值时

- 如果带有&，则取得是对应的FactoryBean实例
- 如果不带有，那么取得是内部的实例对象

```java
public class GetBeanTest {
    public static void main(String[] args) {
        AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext("com.zcq.demo.getbean");
        User myFactoryBean = (User) applicationContext.getBean("myFactoryBean");
        MyFactoryBean myFactoryBean2 = (MyFactoryBean) applicationContext.getBean("&myFactoryBean");
    }
}
```



#### 创建bean

##### getBean()

```java
public abstract class AbstractBeanFactory extends FactoryBeanRegistrySupport implements ConfigurableBeanFactory {
    
    //核心方法 用于创建bean
    @Override
    public Object getBean(String name) throws BeansException {
        return doGetBean(name, null, null, false);
    }
}
```

##### doGetBean()

```java
public abstract class AbstractBeanFactory extends FactoryBeanRegistrySupport implements ConfigurableBeanFactory {
    
    //一级缓存
    /** Cache of singleton objects: bean name to bean instance. */
	private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);
    
    //二级缓存
	/** Cache of early singleton objects: bean name to bean instance. */
	private final Map<String, Object> earlySingletonObjects = new HashMap<>(16);
	
    //三级缓存
	/** Cache of singleton factories: bean name to ObjectFactory. */
	private final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<>(16);

    
    protected <T> T doGetBean(final String name, @Nullable final Class<T> requiredType,
           @Nullable final Object[] args, boolean typeCheckOnly) throws BeansException {
	    //处理beanName 去掉&前缀、转换别名变成本名
        final String beanName = transformedBeanName(name);
        Object bean;

        // Eagerly check singleton cache for manually registered singletons.
        //在一二三缓存中获取bean对象
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
           //获取到factorybean实例对象后，再根据&beanName和beanName 带不带&来决定返回factorybean实例对象还是内部对象
           bean = getObjectForBeanInstance(sharedInstance, name, beanName, null);
        }

        else {
           // Fail if we're already creating this bean instance:
           // We're assumably within a circular reference.
           //正在创建此bean 抛出异常
           if (isPrototypeCurrentlyInCreation(beanName)) {
              throw new BeanCurrentlyInCreationException(beanName);
           }
		
           //获得父容器
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
		
           //标记bean已创建
           if (!typeCheckOnly) {
              markBeanAsCreated(beanName);
           }

           try {
              //合并BeanDefinition
              final RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
              checkMergedBeanDefinition(mbd, beanName, args);
			
              //处理@DependsOn依赖的bean 优先创建DependsOn依赖的bean
              // Guarantee initialization of beans that the current bean depends on.
              String[] dependsOn = mbd.getDependsOn();
              if (dependsOn != null) {
                 for (String dep : dependsOn) {
                    if (isDependent(beanName, dep)) {
                       throw new BeanCreationException(mbd.getResourceDescription(), beanName,
                             "Circular depends-on relationship between '" + beanName + "' and '" + dep + "'");
                    }
                    registerDependentBean(dep, beanName);
                    try {
                       //优先创建DependsOn依赖的bean
                       getBean(dep);
                    }
                    catch (NoSuchBeanDefinitionException ex) {
                       throw new BeanCreationException(mbd.getResourceDescription(), beanName,
                             "'" + beanName + "' depends on missing bean '" + dep + "'", ex);
                    }
                 }
              }
			
              //创建bean
              // Create bean instance.
              //如果bean是单例的
              if (mbd.isSingleton()) {
                 //创建单例 使用lambda表达式传入createBean
                 sharedInstance = getSingleton(beanName, () -> {
                    try {
                       return createBean(beanName, mbd, args);
                    }
                    catch (BeansException ex) {
                       // Explicitly remove instance from singleton cache: It might have been put there
                       // eagerly by the creation process, to allow for circular reference resolution.
                       // Also remove any beans that received a temporary reference to the bean.
                       destroySingleton(beanName);
                       throw ex;
                    }
                 });
                 //处理FactoryBean
                 bean = getObjectForBeanInstance(sharedInstance, name, beanName, mbd);
              }
 			//如果bean是原型的
              else if (mbd.isPrototype()) {
                 // It's a prototype -> create a new instance.
                 Object prototypeInstance = null;
                 try {
                    beforePrototypeCreation(beanName);
                    prototypeInstance = createBean(beanName, mbd, args);
                 }
                 finally {
                    afterPrototypeCreation(beanName);
                 }
                 bean = getObjectForBeanInstance(prototypeInstance, name, beanName, mbd);
              }

              else {
                 String scopeName = mbd.getScope();
                 final Scope scope = this.scopes.get(scopeName);
                 if (scope == null) {
                    throw new IllegalStateException("No Scope registered for scope name '" + scopeName + "'");
                 }
                 try {
                    Object scopedInstance = scope.get(beanName, () -> {
                       beforePrototypeCreation(beanName);
                       try {
                          return createBean(beanName, mbd, args);
                       }
                       finally {
                          afterPrototypeCreation(beanName);
                       }
                    });
                    bean = getObjectForBeanInstance(scopedInstance, name, beanName, mbd);
                 }
                 catch (IllegalStateException ex) {
                    throw new BeanCreationException(beanName,
                          "Scope '" + scopeName + "' is not active for the current thread; consider " +
                          "defining a scoped proxy for this bean if you intend to refer to it from a singleton",
                          ex);
                 }
              }
           }
           catch (BeansException ex) {
              cleanupAfterBeanCreationFailure(beanName);
              throw ex;
           }
        }

        // Check if required type matches the type of the actual bean instance.
        if (requiredType != null && !requiredType.isInstance(bean)) {
           try {
              T convertedBean = getTypeConverter().convertIfNecessary(bean, requiredType);
              if (convertedBean == null) {
                 throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
              }
              return convertedBean;
           }
           catch (TypeMismatchException ex) {
              if (logger.isTraceEnabled()) {
                 logger.trace("Failed to convert bean '" + name + "' to required type '" +
                       ClassUtils.getQualifiedName(requiredType) + "'", ex);
              }
              throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
           }
        }
        return (T) bean;
    }
    
    //在一二三缓存中获取bean对象
    @Nullable
	protected Object getSingleton(String beanName, boolean allowEarlyReference) {
		Object singletonObject = this.singletonObjects.get(beanName);
		if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
			synchronized (this.singletonObjects) {
				singletonObject = this.earlySingletonObjects.get(beanName);
				if (singletonObject == null && allowEarlyReference) {
					ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
					if (singletonFactory != null) {
						singletonObject = singletonFactory.getObject();
						this.earlySingletonObjects.put(beanName, singletonObject);
						this.singletonFactories.remove(beanName);
					}
				}
			}
		}
		return singletonObject;
	}
    
    //将指定的 bean 标记为已创建（或即将创建）
    protected void markBeanAsCreated(String beanName) {
		if (!this.alreadyCreated.contains(beanName)) {
			synchronized (this.mergedBeanDefinitions) {
				if (!this.alreadyCreated.contains(beanName)) {
					// Let the bean definition get re-merged now that we're actually creating
					// the bean... just in case some of its metadata changed in the meantime.
                      //标记合并的RootBeanDefinition失效
					clearMergedBeanDefinition(beanName);
                      //加入已创建（或即将创建）集合
					this.alreadyCreated.add(beanName);
				}
			}
		}
	}
} 
```



##### getSingleton()

```java
public class DefaultSingletonBeanRegistry extends SimpleAliasRegistry implements SingletonBeanRegistry {
    
    
	public Object getSingleton(String beanName, ObjectFactory<?> singletonFactory) {
		Assert.notNull(beanName, "Bean name must not be null");
        
		synchronized (this.singletonObjects) {
             //从一级缓存中取实例
			Object singletonObject = this.singletonObjects.get(beanName);
             //没取到实例 创建
			if (singletonObject == null) {
				if (this.singletonsCurrentlyInDestruction) {
					throw new BeanCreationNotAllowedException(beanName,
							"Singleton bean creation not allowed while singletons of this factory are in destruction " +
							"(Do not request a bean from a BeanFactory in a destroy method implementation!)");
				}
				if (logger.isDebugEnabled()) {
					logger.debug("Creating shared instance of singleton bean '" + beanName + "'");
				}
                  //标记单例正在创建中
				beforeSingletonCreation(beanName);
				boolean newSingleton = false;
				boolean recordSuppressedExceptions = (this.suppressedExceptions == null);
				if (recordSuppressedExceptions) {
					this.suppressedExceptions = new LinkedHashSet<>();
				}
				try {
                      //调用lambda表达式中的createBean()
					singletonObject = singletonFactory.getObject();
					newSingleton = true;
				}
				catch (IllegalStateException ex) {
					// Has the singleton object implicitly appeared in the meantime ->
					// if yes, proceed with it since the exception indicates that state.
					singletonObject = this.singletonObjects.get(beanName);
					if (singletonObject == null) {
						throw ex;
					}
				}
				catch (BeanCreationException ex) {
					if (recordSuppressedExceptions) {
						for (Exception suppressedException : this.suppressedExceptions) {
							ex.addRelatedCause(suppressedException);
						}
					}
					throw ex;
				}
				finally {
					if (recordSuppressedExceptions) {
						this.suppressedExceptions = null;
					}
                     //移除正在创建中的实例标记
					afterSingletonCreation(beanName);
				}
                  //加入一级缓存，移出二三级缓存
				if (newSingleton) {
					addSingleton(beanName, singletonObject);
				}
			}
			return singletonObject;
		}
	}
    
    //标记单例正在创建中
	protected void beforeSingletonCreation(String beanName) {
		if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.add(beanName)) {
			throw new BeanCurrentlyInCreationException(beanName);
		}
	}
    //移除正在创建中的实例标记
    protected void afterSingletonCreation(String beanName) {
		if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.remove(beanName)) {
			throw new IllegalStateException("Singleton '" + beanName + "' isn't currently in creation");
		}
	}
    
    //加入一级缓存，移出二三级缓存
    protected void addSingleton(String beanName, Object singletonObject) {
		synchronized (this.singletonObjects) {
			this.singletonObjects.put(beanName, singletonObject);
			this.singletonFactories.remove(beanName);
			this.earlySingletonObjects.remove(beanName);
			this.registeredSingletons.add(beanName);
		}
	}

}
```



##### createBean()

```java
public abstract class AbstractAutowireCapableBeanFactory extends AbstractBeanFactory
       implements AutowireCapableBeanFactory {

    
    
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
        //获取bean的Class对象 通过class.forName()获取
		Class<?> resolvedClass = resolveBeanClass(mbd, beanName);
		if (resolvedClass != null && !mbd.hasBeanClass() && mbd.getBeanClassName() != null) {
			mbdToUse = new RootBeanDefinition(mbd);
             //设置Class对象
			mbdToUse.setBeanClass(resolvedClass);
		}

		// Prepare method overrides.
		try {
            //对MethodOverride的做检查 仅仅只是检查而已
			mbdToUse.prepareMethodOverrides();
		}
		catch (BeanDefinitionValidationException ex) {
			throw new BeanDefinitionStoreException(mbdToUse.getResourceDescription(),
					beanName, "Validation of method overrides failed", ex);
		}

		try {
			// Give BeanPostProcessors a chance to return a proxy instead of the target bean instance.
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
             //创建bean
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


}
```

###### prepareMethodOverrides

```java
public void prepareMethodOverrides() throws BeanDefinitionValidationException {
    // Check that lookup methods exist and determine their overloaded status.
    //如果有MethodOverride 那么进行检查
    if (hasMethodOverrides()) {
       getMethodOverrides().getOverrides().forEach(this::prepareMethodOverride);
    }
}
```

###### resolveBeforeInstantiation

```java
//如果有InstantiationAwareBeanPostProcessor 
//使用InstantiationAwareBeanPostProcessor可以快速创建对象，跳过后续创建对象的环节
@Nullable
protected Object resolveBeforeInstantiation(String beanName, RootBeanDefinition mbd) {
    Object bean = null;
    if (!Boolean.FALSE.equals(mbd.beforeInstantiationResolved)) {
       // Make sure bean class is actually resolved at this point.
       //如果有实现了InstantiationAwareBeanPostProcessor
       if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
          Class<?> targetType = determineTargetType(beanName, mbd);
          if (targetType != null) {
             //调用InstantiationAwareBeanPostProcessor
             bean = applyBeanPostProcessorsBeforeInstantiation(targetType, beanName);
             //如果创建了对象
             if (bean != null) {
                //调用BeanPostProcessor的后置处理
                bean = applyBeanPostProcessorsAfterInitialization(bean, beanName);
             }
          }
       }
       mbd.beforeInstantiationResolved = (bean != null);
    }
    return bean;
}
```





#### doCreateBean()



```java
protected Object doCreateBean(final String beanName, final RootBeanDefinition mbd, final @Nullable Object[] args)
       throws BeanCreationException {

    // Instantiate the bean.
    BeanWrapper instanceWrapper = null;
    if (mbd.isSingleton()) {
       instanceWrapper = this.factoryBeanInstanceCache.remove(beanName);
    }
    //创建bean实例
    if (instanceWrapper == null) {
       instanceWrapper = createBeanInstance(beanName, mbd, args);
    }
    final Object bean = instanceWrapper.getWrappedInstance();
    Class<?> beanType = instanceWrapper.getWrappedClass();
    if (beanType != NullBean.class) {
       mbd.resolvedTargetType = beanType;
    }

    //调用实现了MergedBeanDefinitionPostProcessor的postProcessMergedBeanDefinition方法更改beanDefinition
    // Allow post-processors to modify the merged bean definition.
    synchronized (mbd.postProcessingLock) {
       if (!mbd.postProcessed) {
          try {
             applyMergedBeanDefinitionPostProcessors(mbd, beanType, beanName);
          }
          catch (Throwable ex) {
             throw new BeanCreationException(mbd.getResourceDescription(), beanName,
                   "Post-processing of merged bean definition failed", ex);
          }
          mbd.postProcessed = true;
       }
    }

    //循环依赖
    // Eagerly cache singletons to be able to resolve circular references
    // even when triggered by lifecycle interfaces like BeanFactoryAware.
    boolean earlySingletonExposure = (mbd.isSingleton() && this.allowCircularReferences &&
          isSingletonCurrentlyInCreation(beanName));
    if (earlySingletonExposure) {
       if (logger.isTraceEnabled()) {
          logger.trace("Eagerly caching bean '" + beanName +
                "' to allow for resolving potential circular references");
       }
       //将当前未完成的bean的代理表达式放入三级缓存
       addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
    }

    // Initialize the bean instance.
    Object exposedObject = bean;
    try {
       //填充属性
       populateBean(beanName, mbd, instanceWrapper);
       exposedObject = initializeBean(beanName, exposedObject, mbd);
    }
    catch (Throwable ex) {
       if (ex instanceof BeanCreationException && beanName.equals(((BeanCreationException) ex).getBeanName())) {
          throw (BeanCreationException) ex;
       }
       else {
          throw new BeanCreationException(
                mbd.getResourceDescription(), beanName, "Initialization of bean failed", ex);
       }
    }

    if (earlySingletonExposure) {
       Object earlySingletonReference = getSingleton(beanName, false);
       if (earlySingletonReference != null) {
          if (exposedObject == bean) {
             exposedObject = earlySingletonReference;
          }
          else if (!this.allowRawInjectionDespiteWrapping && hasDependentBean(beanName)) {
             String[] dependentBeans = getDependentBeans(beanName);
             Set<String> actualDependentBeans = new LinkedHashSet<>(dependentBeans.length);
             for (String dependentBean : dependentBeans) {
                if (!removeSingletonIfCreatedForTypeCheckOnly(dependentBean)) {
                   actualDependentBeans.add(dependentBean);
                }
             }
             if (!actualDependentBeans.isEmpty()) {
                throw new BeanCurrentlyInCreationException(beanName,
                      "Bean with name '" + beanName + "' has been injected into other beans [" +
                      StringUtils.collectionToCommaDelimitedString(actualDependentBeans) +
                      "] in its raw version as part of a circular reference, but has eventually been " +
                      "wrapped. This means that said other beans do not use the final version of the " +
                      "bean. This is often the result of over-eager type matching - consider using " +
                      "'getBeanNamesForType' with the 'allowEagerInit' flag turned off, for example.");
             }
          }
       }
    }

    // Register bean as disposable.
    try {
       //对于@PreDestroy或者有destroyMethodName的bean进行注册记录
       registerDisposableBeanIfNecessary(beanName, bean, mbd);
    }
    catch (BeanDefinitionValidationException ex) {
       throw new BeanCreationException(
             mbd.getResourceDescription(), beanName, "Invalid destruction signature", ex);
    }

    return exposedObject;
}
```









##### createBeanInstance()



```java
public abstract class AbstractAutowireCapableBeanFactory extends AbstractBeanFactory
		implements AutowireCapableBeanFactory {

	protected BeanWrapper createBeanInstance(String beanName, RootBeanDefinition mbd, @Nullable Object[] args) {
         //获取bean的class对象
		// Make sure bean class is actually resolved at this point.
		Class<?> beanClass = resolveBeanClass(mbd, beanName);

        //检查bean是public修饰的
		if (beanClass != null && !Modifier.isPublic(beanClass.getModifiers()) && !mbd.isNonPublicAccessAllowed()) {
			throw new BeanCreationException(mbd.getResourceDescription(), beanName,
					"Bean class isn't public, and non-public access not allowed: " + beanClass.getName());
		}
		
         //supplier方法获取bean
		Supplier<?> instanceSupplier = mbd.getInstanceSupplier();
		if (instanceSupplier != null) {
			return obtainFromSupplier(instanceSupplier, beanName);
		}
		//工厂方法获取bean @Bean也是使用工厂方法实例化在这里处理的
		if (mbd.getFactoryMethodName() != null) {
			return instantiateUsingFactoryMethod(beanName, mbd, args);
		}
		
         // 创建相同的bean时，可以使用缓存的构造方法和参数来实例化 避免重复解析
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
         //直接使用缓存的构造方法进行实例化
		if (resolved) {
			if (autowireNecessary) {
				return autowireConstructor(beanName, mbd, null, null);
			}
			else {
                 //使用默认的构造方法进行实例化
				return instantiateBean(beanName, mbd);
			}
		}
		//使用BeanPostProcessor来确定使用的构造方法
		// Candidate constructors for autowiring?
		Constructor<?>[] ctors = determineConstructorsFromBeanPostProcessors(beanClass, beanName);
        
        //如果有选中的构造方法 或者 有构造方法的参数
		if (ctors != null || mbd.getResolvedAutowireMode() == AUTOWIRE_CONSTRUCTOR ||
				mbd.hasConstructorArgumentValues() || !ObjectUtils.isEmpty(args)) {
             //使用构造方法进行创建bean
			return autowireConstructor(beanName, mbd, ctors, args);
		}
		
         //获取一下有没有优先的构造方法
		// Preferred constructors for default construction?
		ctors = mbd.getPreferredConstructors();
		if (ctors != null) {
            //使用优先的构造方法创建对象
			return autowireConstructor(beanName, mbd, ctors, null);
		}
		
        //使用默认无参的构造方法创建
		// No special handling: simply use no-arg constructor.
		return instantiateBean(beanName, mbd);
	}



}
```

###### obtainFromSupplier

```java
//使用beanDefintion中设置的继承了Supplier的类
protected BeanWrapper obtainFromSupplier(Supplier<?> instanceSupplier, String beanName) {
    Object instance;

    String outerBean = this.currentlyCreatedBean.get();
    this.currentlyCreatedBean.set(beanName);
    try {
        //直接调用Supplier的get方法获取bean实例
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
    //包装返回bean
    BeanWrapper bw = new BeanWrapperImpl(instance);
    initBeanWrapper(bw);
    return bw;
}
```

###### instantiateUsingFactoryMethod

```java
public BeanWrapper instantiateUsingFactoryMethod(
       String beanName, RootBeanDefinition mbd, @Nullable Object[] explicitArgs) {

    BeanWrapperImpl bw = new BeanWrapperImpl();
    this.beanFactory.initBeanWrapper(bw);

    Object factoryBean;
    Class<?> factoryClass;
    boolean isStatic;
	
    //如果factorybeanName不为空 那么是实例工厂
    String factoryBeanName = mbd.getFactoryBeanName();
    if (factoryBeanName != null) {
       if (factoryBeanName.equals(beanName)) {
          throw new BeanDefinitionStoreException(mbd.getResourceDescription(), beanName,
                "factory-bean reference points back to the same bean definition");
       }
       //从容器中获取实例工厂
       factoryBean = this.beanFactory.getBean(factoryBeanName);
       if (mbd.isSingleton() && this.beanFactory.containsSingleton(beanName)) {
          throw new ImplicitlyAppearedSingletonException();
       }
       factoryClass = factoryBean.getClass();
       isStatic = false;
    }
    //否则就是静态工厂
    else {
       // It's a static factory method on the bean class.
       if (!mbd.hasBeanClass()) {
          throw new BeanDefinitionStoreException(mbd.getResourceDescription(), beanName,
                "bean definition declares neither a bean class nor a factory-bean reference");
       }
       factoryBean = null;
       //通过beanClass获得静态工厂的信息
       factoryClass = mbd.getBeanClass();
       isStatic = true;
    }

    //由于工厂方法可能存在重载 所以需要根据bean的构造方法参数 选择最匹配的工厂方法
    Method factoryMethodToUse = null;
    ArgumentsHolder argsHolderToUse = null;
    Object[] argsToUse = null;

    if (explicitArgs != null) {
       argsToUse = explicitArgs;
    }
    else {
       Object[] argsToResolve = null;
       synchronized (mbd.constructorArgumentLock) {
          factoryMethodToUse = (Method) mbd.resolvedConstructorOrFactoryMethod;
          if (factoryMethodToUse != null && mbd.constructorArgumentsResolved) {
             // Found a cached factory method...
             argsToUse = mbd.resolvedConstructorArguments;
             if (argsToUse == null) {
                argsToResolve = mbd.preparedConstructorArguments;
             }
          }
       }
       if (argsToResolve != null) {
          argsToUse = resolvePreparedArguments(beanName, mbd, bw, factoryMethodToUse, argsToResolve, true);
       }
    }

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
       //筛选出所有工厂方法
       if (candidates == null) {
          candidates = new ArrayList<>();
          Method[] rawCandidates = getCandidateMethods(factoryClass, mbd);
          for (Method candidate : rawCandidates) {
             if (Modifier.isStatic(candidate.getModifiers()) == isStatic && mbd.isFactoryMethod(candidate)) {
                candidates.add(candidate);
             }
          }
       }
	  //如果只有一个方法且没有参数 那就直接实例化
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
	   //参数个数做排序
       if (candidates.size() > 1) {  // explicitly skip immutable singletonList
          candidates.sort(AutowireUtils.EXECUTABLE_COMPARATOR);
       }

       ConstructorArgumentValues resolvedValues = null;
       boolean autowiring = (mbd.getResolvedAutowireMode() == AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR);
       int minTypeDiffWeight = Integer.MAX_VALUE;
       Set<Method> ambiguousFactoryMethods = null;

       //解析构造参数个数
       int minNrOfArgs;
       if (explicitArgs != null) {
          minNrOfArgs = explicitArgs.length;
       }
       else {
          // We don't have arguments passed in programmatically, so we need to resolve the
          // arguments specified in the constructor arguments held in the bean definition.
          if (mbd.hasConstructorArgumentValues()) {
             ConstructorArgumentValues cargs = mbd.getConstructorArgumentValues();
             resolvedValues = new ConstructorArgumentValues();
             minNrOfArgs = resolveConstructorArguments(beanName, mbd, bw, cargs, resolvedValues);
          }
          else {
             minNrOfArgs = 0;
          }
       }

       LinkedList<UnsatisfiedDependencyException> causes = null;

        //遍历工厂方法 选择最匹配的方法
       for (Method candidate : candidates) {
          int parameterCount = candidate.getParameterCount();

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
                      paramNames = pnd.getParameterNames(candidate);
                   }
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

       if (factoryMethodToUse == null || argsToUse == null) {
          if (causes != null) {
             UnsatisfiedDependencyException ex = causes.removeLast();
             for (Exception cause : causes) {
                this.beanFactory.onSuppressedException(cause);
             }
             throw ex;
          }
          List<String> argTypes = new ArrayList<>(minNrOfArgs);
          if (explicitArgs != null) {
             for (Object arg : explicitArgs) {
                argTypes.add(arg != null ? arg.getClass().getSimpleName() : "null");
             }
          }
          else if (resolvedValues != null) {
             Set<ValueHolder> valueHolders = new LinkedHashSet<>(resolvedValues.getArgumentCount());
             valueHolders.addAll(resolvedValues.getIndexedArgumentValues().values());
             valueHolders.addAll(resolvedValues.getGenericArgumentValues());
             for (ValueHolder value : valueHolders) {
                String argType = (value.getType() != null ? ClassUtils.getShortName(value.getType()) :
                      (value.getValue() != null ? value.getValue().getClass().getSimpleName() : "null"));
                argTypes.add(argType);
             }
          }
          String argDesc = StringUtils.collectionToCommaDelimitedString(argTypes);
          throw new BeanCreationException(mbd.getResourceDescription(), beanName,
                "No matching factory method found: " +
                (mbd.getFactoryBeanName() != null ?
                   "factory bean '" + mbd.getFactoryBeanName() + "'; " : "") +
                "factory method '" + mbd.getFactoryMethodName() + "(" + argDesc + ")'. " +
                "Check that a method with the specified name " +
                (minNrOfArgs > 0 ? "and arguments " : "") +
                "exists and that it is " +
                (isStatic ? "static" : "non-static") + ".");
       }
       else if (void.class == factoryMethodToUse.getReturnType()) {
          throw new BeanCreationException(mbd.getResourceDescription(), beanName,
                "Invalid factory method '" + mbd.getFactoryMethodName() +
                "': needs to have a non-void return type!");
       }
       else if (ambiguousFactoryMethods != null) {
          throw new BeanCreationException(mbd.getResourceDescription(), beanName,
                "Ambiguous factory method matches found in bean '" + beanName + "' " +
                "(hint: specify index/type/name arguments for simple parameters to avoid type ambiguities): " +
                ambiguousFactoryMethods);
       }

       if (explicitArgs == null && argsHolderToUse != null) {
          mbd.factoryMethodToIntrospect = factoryMethodToUse;
          argsHolderToUse.storeCache(mbd, factoryMethodToUse);
       }
    }
	//调用选中的工厂方法和构造参数实例化
    bw.setBeanInstance(instantiate(beanName, mbd, factoryBean, factoryMethodToUse, argsToUse));
    return bw;
}
```

###### BeanWrapperImpl

```java
//bean实例的包装类
public class BeanWrapperImpl extends AbstractNestablePropertyAccessor implements BeanWrapper {



}


public abstract class AbstractBeanFactory extends FactoryBeanRegistrySupport implements ConfigurableBeanFactory {
	
    //初始化beanWrapper 设置类型转换服务和属性编辑器
	protected void initBeanWrapper(BeanWrapper bw) {
		bw.setConversionService(getConversionService());
        //将自定义的属性编辑器注册到beanWrapper上
		registerCustomEditors(bw);
	}

}
```



###### determineConstructorsFromBeanPostProcessors

```java
public abstract class AbstractAutowireCapableBeanFactory extends AbstractBeanFactory
       implements AutowireCapableBeanFactory {
    
    
	@Nullable
	protected Constructor<?>[] determineConstructorsFromBeanPostProcessors(@Nullable Class<?> beanClass, String beanName)
			throws BeansException {
		//遍历所有的beanPostProcessor 找到SmartInstantiationAwareBeanPostProcessor调用
		if (beanClass != null && hasInstantiationAwareBeanPostProcessors()) {
			for (BeanPostProcessor bp : getBeanPostProcessors()) {
				if (bp instanceof SmartInstantiationAwareBeanPostProcessor) {
					SmartInstantiationAwareBeanPostProcessor ibp = (SmartInstantiationAwareBeanPostProcessor) bp;
					Constructor<?>[] ctors = ibp.determineCandidateConstructors(beanClass, beanName);
					if (ctors != null) {
						return ctors;
					}
				}
			}
		}
		return null;
	}
    
    
}
```

###### AutowiredAnnotationBeanPostProcessor

<img src="./images/image-20250317145651315.png" alt="image-20250317145651315" style="zoom: 33%;" />

- `@Lookup`注解的处理
- 查找缓存中此类已经解析的`@Lookup`信息，如果没有则解析
- 遍历所有方法，获取方法上的`@Lookup`注解，封装成`LookupOverride`加入`beanDefinition`的`MethodOverrides`中
- 将解析过的`@Lookup`信息加入缓存
- 
- `@Autowired`标注的构造方法筛选
- 通过`candidateConstructorsCache`缓存获取已经解析好的`@Autowired、@Inject`注解标注的构造方法，如果能获取到，直接返回，获取不到走下面的流程
- 获取当前类的全部构造方法，循环遍历所有的构造方法
- 获取当前构造方法上的`@Autowired、@Inject`注解，如果没有，检查这个类是否是CGlib代理类，获取原生类的构造方法和对应的注解
- 根据获取到的`@Autowired`注解，如果有一个`@Autowired(required=true)`的构造方法，其他`@Autowried(required=false)`也会报错，多个`@Autowired(required=false)`的构造方法可以共存，`@Inject`没有`required`属性默认为`false`
- 如果当前构造方法包含`@Autowired、@Inject`无论`required`是`true`或者`false`，都会加入到候选集合`candidates`中，如果`required`属性为`true`，将其设置为`requiredConstructor`，并且设置`requiredConstructor`为此构造方法
- 如果当前构造方法没有参数，设置`defaultConstructor`为此构造方法
- 最后处理`candidates`，把`@Autowired、@Inject`标注的构造方法全都返回去，如果只有默认无参构造方法，返回null

```java
public class AutowiredAnnotationBeanPostProcessor extends InstantiationAwareBeanPostProcessorAdapter
       implements MergedBeanDefinitionPostProcessor, PriorityOrdered, BeanFactoryAware {
    
    //初始化@Autowired、@Value、@Inject注解
    public AutowiredAnnotationBeanPostProcessor() {
		this.autowiredAnnotationTypes.add(Autowired.class);
		this.autowiredAnnotationTypes.add(Value.class);
		try {
			this.autowiredAnnotationTypes.add((Class<? extends Annotation>)
					ClassUtils.forName("javax.inject.Inject", AutowiredAnnotationBeanPostProcessor.class.getClassLoader()));
			logger.trace("JSR-330 'javax.inject.Inject' annotation found and supported for autowiring");
		}
		catch (ClassNotFoundException ex) {
			// JSR-330 API not available - simply skip.
		}
	}
    
    //获取要使用的构造方法
	@Override
	@Nullable
	public Constructor<?>[] determineCandidateConstructors(Class<?> beanClass, final String beanName)
			throws BeanCreationException {
		
        //处理@Lookup注解
		// Let's check for lookup methods here...
        //查看缓存中当前beanName是否已经解析过
		if (!this.lookupMethodsChecked.contains(beanName)) {
             //检查当前类中是否包含@Lookup注解
			if (AnnotationUtils.isCandidateClass(beanClass, Lookup.class)) {
				try {
					Class<?> targetClass = beanClass;
					do {
                          //遍历所有方法 获取方法上的@Lookup注解信息
						ReflectionUtils.doWithLocalMethods(targetClass, method -> {
							Lookup lookup = method.getAnnotation(Lookup.class);
                               //如果有@Lookup注解
							if (lookup != null) {
								Assert.state(this.beanFactory != null, "No BeanFactory available");
                                   //将@Lookup封装成LookupOverride
								LookupOverride override = new LookupOverride(method, lookup.value());
								try {
                                       //将LookupOverride放到beanDefinition的MethodOverrides中
									RootBeanDefinition mbd = (RootBeanDefinition)
											this.beanFactory.getMergedBeanDefinition(beanName);
									mbd.getMethodOverrides().addOverride(override);
								}
								catch (NoSuchBeanDefinitionException ex) {
									throw new BeanCreationException(beanName,
											"Cannot apply @Lookup to beans without corresponding bean definition");
								}
							}
						});
						targetClass = targetClass.getSuperclass();
					}
					while (targetClass != null && targetClass != Object.class);

				}
				catch (IllegalStateException ex) {
					throw new BeanCreationException(beanName, "Lookup method resolution failed", ex);
				}
			}
             //设置缓存
			this.lookupMethodsChecked.add(beanName);
		}
		
        //从构造方法缓存中获取
		// Quick check on the concurrent map first, with minimal locking.
		Constructor<?>[] candidateConstructors = this.candidateConstructorsCache.get(beanClass);
		if (candidateConstructors == null) {
			// Fully synchronized resolution now...
			synchronized (this.candidateConstructorsCache) {
				candidateConstructors = this.candidateConstructorsCache.get(beanClass);
				if (candidateConstructors == null) {
					Constructor<?>[] rawCandidates;
					try {
                          //获得所有的构造方法
						rawCandidates = beanClass.getDeclaredConstructors();
					}
					catch (Throwable ex) {
						throw new BeanCreationException(beanName,
								"Resolution of declared constructors on bean Class [" + beanClass.getName() +
								"] from ClassLoader [" + beanClass.getClassLoader() + "] failed", ex);
					}
                     //候选的构造方法
					List<Constructor<?>> candidates = new ArrayList<>(rawCandidates.length);
                      //@Autowired(required=true)标注的构造方法
					Constructor<?> requiredConstructor = null;
                      //默认的构造方法
					Constructor<?> defaultConstructor = null;
					Constructor<?> primaryConstructor = BeanUtils.findPrimaryConstructor(beanClass);
					int nonSyntheticConstructors = 0;
                      //遍历所有的构造方法
					for (Constructor<?> candidate : rawCandidates) {
						if (!candidate.isSynthetic()) {
							nonSyntheticConstructors++;
						}
						else if (primaryConstructor != null) {
							continue;
						}
                          //获取构造方法上的@Autowired注解
						MergedAnnotation<?> ann = findAutowiredAnnotation(candidate);
                          //如果没获取到@Autowired注解
						if (ann == null) {
                               //如果此类是CGLIB代理类 获取原生的类
							Class<?> userClass = ClassUtils.getUserClass(beanClass);
							if (userClass != beanClass) {
								try {
                                        //获取原生类的构造方法和@Autowired注解
									Constructor<?> superCtor =
											userClass.getDeclaredConstructor(candidate.getParameterTypes());
									ann = findAutowiredAnnotation(superCtor);
								}
								catch (NoSuchMethodException ex) {
									// Simply proceed, no equivalent superclass constructor found...
								}
							}
						}
                          //如果获取到了@Autowired注解
						if (ann != null) {
                              //如果有已经有一个@Autowired(required=true)注解标注的构造方法 报错
							if (requiredConstructor != null) {
								throw new BeanCreationException(beanName,
										"Invalid autowire-marked constructor: " + candidate +
										". Found constructor with 'required' Autowired annotation already: " +
										requiredConstructor);
							}
                              //获取@Autowired注解的required字段
							boolean required = determineRequiredStatus(ann);
                               //如果是requird=true
							if (required) {
                                   //检查已经有一个@Autowired(required=true)注解标注的构造方法
								if (!candidates.isEmpty()) {
									throw new BeanCreationException(beanName,
											"Invalid autowire-marked constructors: " + candidates +
											". Found constructor with 'required' Autowired annotation: " +
											candidate);
								}
                                   //标记当前这个构造方法被@Autowired(required=true)注解修饰
								requiredConstructor = candidate;
							}
                               //加入候选构造方法
							candidates.add(candidate);
						}
                          //如果没有参数
						else if (candidate.getParameterCount() == 0) {
                               //标记当前构造方法为默认构造方法
							defaultConstructor = candidate;
						}
					}
                    
                      //处理候选的构造方法
					if (!candidates.isEmpty()) {
                          //如果没有@Autowired注解的构造方法 
						// Add default constructor to list of optional constructors, as fallback.
						if (requiredConstructor == null) {
                               //那么使用默认构造方法
							if (defaultConstructor != null) {
								candidates.add(defaultConstructor);
							}
							else if (candidates.size() == 1 && logger.isInfoEnabled()) {
                                   //没有默认构造方法 打印日志
								logger.info("Inconsistent constructor declaration on bean with name '" + beanName +
										"': single autowire-marked constructor flagged as optional - " +
										"this constructor is effectively required since there is no " +
										"default constructor to fall back to: " + candidates.get(0));
							}
						}
						candidateConstructors = candidates.toArray(new Constructor<?>[0]);
					}
					else if (rawCandidates.length == 1 && rawCandidates[0].getParameterCount() > 0) {
						candidateConstructors = new Constructor<?>[] {rawCandidates[0]};
					}
					else if (nonSyntheticConstructors == 2 && primaryConstructor != null &&
							defaultConstructor != null && !primaryConstructor.equals(defaultConstructor)) {
						candidateConstructors = new Constructor<?>[] {primaryConstructor, defaultConstructor};
					}
					else if (nonSyntheticConstructors == 1 && primaryConstructor != null) {
						candidateConstructors = new Constructor<?>[] {primaryConstructor};
					}
					else {
						candidateConstructors = new Constructor<?>[0];
					}
                      //将解析的构造方法加入缓存
					this.candidateConstructorsCache.put(beanClass, candidateConstructors);
				}
			}
		}
        //返回候选的构造方法
		return (candidateConstructors.length > 0 ? candidateConstructors : null);
	}


}
```





###### autowireConstructor

```java
public abstract class AbstractAutowireCapableBeanFactory extends AbstractBeanFactory
		implements AutowireCapableBeanFactory {
    
    //使用构造器解析器找出符合参数条件的构造方法 并使用构造方法完成实例化
	protected BeanWrapper autowireConstructor(
			String beanName, RootBeanDefinition mbd, @Nullable Constructor<?>[] ctors, @Nullable Object[] explicitArgs) {

		return new ConstructorResolver(this).autowireConstructor(beanName, mbd, ctors, explicitArgs);
	}


}
```



```java
class ConstructorResolver {

    //利用选择的构造方法和参数创建对象
	public BeanWrapper autowireConstructor(String beanName, RootBeanDefinition mbd,
			@Nullable Constructor<?>[] chosenCtors, @Nullable Object[] explicitArgs) {
		
         //创建bean的包装类 设置类型转换服务和属性编辑器到BeanWrapperImpl上
		BeanWrapperImpl bw = new BeanWrapperImpl();
		this.beanFactory.initBeanWrapper(bw);

		Constructor<?> constructorToUse = null;
		ArgumentsHolder argsHolderToUse = null;
		Object[] argsToUse = null;

		if (explicitArgs != null) {
			argsToUse = explicitArgs;
		}
		else {
             //获取mbd中缓存的解析的构造方法和参数列表
			Object[] argsToResolve = null;
			synchronized (mbd.constructorArgumentLock) {
				constructorToUse = (Constructor<?>) mbd.resolvedConstructorOrFactoryMethod;
				if (constructorToUse != null && mbd.constructorArgumentsResolved) {
					// Found a cached constructor...
					argsToUse = mbd.resolvedConstructorArguments;
					if (argsToUse == null) {
						argsToResolve = mbd.preparedConstructorArguments;
					}
				}
			}
			if (argsToResolve != null) {
				argsToUse = resolvePreparedArguments(beanName, mbd, bw, constructorToUse, argsToResolve, true);
			}
		}
		
         //没有获取到对应的构造方法和参数列表 开始解析
		if (constructorToUse == null || argsToUse == null) {
			// Take specified constructors, if any.
             //如果有选中的构造方法，就从选中的构造方法中筛选
			Constructor<?>[] candidates = chosenCtors;
             //如果没有，那么就获取全部的构造方法
			if (candidates == null) {
				Class<?> beanClass = mbd.getBeanClass();
				try {
                      //获取全部的构造方法
					candidates = (mbd.isNonPublicAccessAllowed() ?
							beanClass.getDeclaredConstructors() : beanClass.getConstructors());
				}
				catch (Throwable ex) {
					throw new BeanCreationException(mbd.getResourceDescription(), beanName,
							"Resolution of declared constructors on bean Class [" + beanClass.getName() +
							"] from ClassLoader [" + beanClass.getClassLoader() + "] failed", ex);
				}
			}
			
             //如果只有一个候选的构造方法 并且没有构造参数
			if (candidates.length == 1 && explicitArgs == null && !mbd.hasConstructorArgumentValues()) {
				Constructor<?> uniqueCandidate = candidates[0];
                 //并且构造方法是默认构造方法 
				if (uniqueCandidate.getParameterCount() == 0) {
                      //将解析好的构造方法和构造参数都设置缓存里
					synchronized (mbd.constructorArgumentLock) {
						mbd.resolvedConstructorOrFactoryMethod = uniqueCandidate;
						mbd.constructorArgumentsResolved = true;
						mbd.resolvedConstructorArguments = EMPTY_ARGS;
					}
                      //那么就直接使用默认的构造方法创建bean
					bw.setBeanInstance(instantiate(beanName, mbd, uniqueCandidate, EMPTY_ARGS));
					return bw;
				}
			}
			
             //如果有@Autowired标注的构造方法
			// Need to resolve the constructor.
			boolean autowiring = (chosenCtors != null ||
					mbd.getResolvedAutowireMode() == AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR);
			ConstructorArgumentValues resolvedValues = null;
			
             //参数个数
			int minNrOfArgs;
             //如果有外部参数 参数个数就是外部参数的数量
			if (explicitArgs != null) {
				minNrOfArgs = explicitArgs.length;
			}
             //如果没有
			else {
                 //获取构造参数
				ConstructorArgumentValues cargs = mbd.getConstructorArgumentValues();
                  //解析好的构造参数
				resolvedValues = new ConstructorArgumentValues();
                 //获取构造参数constructorArgumentValues的数量
				minNrOfArgs = resolveConstructorArguments(beanName, mbd, bw, cargs, resolvedValues);
			}
			
             //对构造方法进行排序 参数个数从多到少
			AutowireUtils.sortConstructors(candidates);
			int minTypeDiffWeight = Integer.MAX_VALUE;
			Set<Constructor<?>> ambiguousConstructors = null;
			LinkedList<UnsatisfiedDependencyException> causes = null;
			
             //遍历构造方法 计算参数和构造方法的参数类型的差异性 选择出类型最匹配的那个构造方法
			for (Constructor<?> candidate : candidates) {
                 //获取当前构造方法的参数个数
				int parameterCount = candidate.getParameterCount();

				if (constructorToUse != null && argsToUse != null && argsToUse.length > parameterCount) {
					// Already found greedy constructor that can be satisfied ->
					// do not look any further, there are only less greedy constructors left.
					break;
				}
                 //如果当前构造方法的参数个数 小于 constructorArgumentValues的数量 直接退出循环 
				if (parameterCount < minNrOfArgs) {
					continue;
				}

				ArgumentsHolder argsHolder;
                 //获取当前构造方法的参数类型
				Class<?>[] paramTypes = candidate.getParameterTypes();
				if (resolvedValues != null) {
					try {
                          //获得当前构造方法的参数名称
						String[] paramNames = ConstructorPropertiesChecker.evaluate(candidate, parameterCount);
						if (paramNames == null) {
							ParameterNameDiscoverer pnd = this.beanFactory.getParameterNameDiscoverer();
							if (pnd != null) {
								paramNames = pnd.getParameterNames(candidate);
							}
						}
                          //根据当前构造方法的参数类型 
                          //对constructorArgumentValues内的参数尝试进行类型转换
                          //如果constructorArgumentValues不包含的参数，并且带有@Autowired注解，那么进行自动注入
						argsHolder = createArgumentArray(beanName, mbd, resolvedValues, bw, paramTypes, paramNames,
								getUserDeclaredConstructor(candidate), autowiring, candidates.length == 1);
					}
                      //捕获类型不匹配的异常 
					catch (UnsatisfiedDependencyException ex) {
						if (logger.isTraceEnabled()) {
							logger.trace("Ignoring constructor [" + candidate + "] of bean '" + beanName + "': " + ex);
						}
						// Swallow and try next constructor.
						if (causes == null) {
							causes = new LinkedList<>();
						}
                          //加入异常集合中
						causes.add(ex);
                          //跳过此构造方法
						continue;
					}
				}
				else {
					// Explicit arguments given -> arguments length must match exactly.
					if (parameterCount != explicitArgs.length) {
						continue;
					}
					argsHolder = new ArgumentsHolder(explicitArgs);
				}
				
                 //获取当前构造方法的参数类型匹配权重值 根据要参与构造函数的参数列表和本构造函数的参数列表进行计算
                //权重值越小 代表当前参数列表和此构造方法的参数列表类型更匹配
				int typeDiffWeight = (mbd.isLenientConstructorResolution() ?
						argsHolder.getTypeDifferenceWeight(paramTypes) : argsHolder.getAssignabilityWeight(paramTypes));
				// Choose this constructor if it represents the closest match.
                 //找到权重值最小的构造方法
				if (typeDiffWeight < minTypeDiffWeight) {
                      //设置使用当前构造方法和参数
					constructorToUse = candidate;
					argsHolderToUse = argsHolder;
					argsToUse = argsHolder.arguments;
					minTypeDiffWeight = typeDiffWeight;
					ambiguousConstructors = null;
				}
                 //如果权重值一样 加入怀疑列表
				else if (constructorToUse != null && typeDiffWeight == minTypeDiffWeight) {
					if (ambiguousConstructors == null) {
						ambiguousConstructors = new LinkedHashSet<>();
						ambiguousConstructors.add(constructorToUse);
					}
					ambiguousConstructors.add(candidate);
				}
			}
            
            
			//如果没有匹配的构造方法 抛出异常
			if (constructorToUse == null) {
				if (causes != null) {
					UnsatisfiedDependencyException ex = causes.removeLast();
					for (Exception cause : causes) {
						this.beanFactory.onSuppressedException(cause);
					}
					throw ex;
				}
				throw new BeanCreationException(mbd.getResourceDescription(), beanName,
						"Could not resolve matching constructor " +
						"(hint: specify index/type/name arguments for simple parameters to avoid type ambiguities)");
			}
             //如果有权重值相同的构造方法并且严格模式下 抛出异常
			else if (ambiguousConstructors != null && !mbd.isLenientConstructorResolution()) {
				throw new BeanCreationException(mbd.getResourceDescription(), beanName,
						"Ambiguous constructor matches found in bean '" + beanName + "' " +
						"(hint: specify index/type/name arguments for simple parameters to avoid type ambiguities): " +
						ambiguousConstructors);
			}

			if (explicitArgs == null && argsHolderToUse != null) {
				argsHolderToUse.storeCache(mbd, constructorToUse);
			}
		}
		//使用选中的最优构造方法实例化对象
		Assert.state(argsToUse != null, "Unresolved constructor arguments");
		bw.setBeanInstance(instantiate(beanName, mbd, constructorToUse, argsToUse));
		return bw;
	}


}
```



###### resolveConstructorArguments

```java
class ConstructorResolver {
    
    //解析constructorArgumentValues的个数
	private int resolveConstructorArguments(String beanName, RootBeanDefinition mbd, BeanWrapper bw,
			ConstructorArgumentValues cargs, ConstructorArgumentValues resolvedValues) {
		
		TypeConverter customConverter = this.beanFactory.getCustomTypeConverter();
		TypeConverter converter = (customConverter != null ? customConverter : bw);
        
        //创建BeanDefinitionValueResolver 值解析器
		BeanDefinitionValueResolver valueResolver =
				new BeanDefinitionValueResolver(this.beanFactory, beanName, mbd, converter);
		
        //获取constructorArgumentValues的最小个数
		int minNrOfArgs = cargs.getArgumentCount();
		
        //遍历下标索引参数列表
		for (Map.Entry<Integer, ConstructorArgumentValues.ValueHolder> entry : cargs.getIndexedArgumentValues().entrySet()) {
			int index = entry.getKey();
			if (index < 0) {
				throw new BeanCreationException(mbd.getResourceDescription(), beanName,
						"Invalid constructor argument index: " + index);
			}
             //更新参数个数
			if (index + 1 > minNrOfArgs) {
				minNrOfArgs = index + 1;
			}
             //获取值
			ConstructorArgumentValues.ValueHolder valueHolder = entry.getValue();
             //如果参数值被处理过了 那么直接加入到处理好的参数集合中
			if (valueHolder.isConverted()) {
				resolvedValues.addIndexedArgumentValue(index, valueHolder);
			}
			else {
                 //否则进行参数解析
				Object resolvedValue =
						valueResolver.resolveValueIfNecessary("constructor argument", valueHolder.getValue());
				ConstructorArgumentValues.ValueHolder resolvedValueHolder =
						new ConstructorArgumentValues.ValueHolder(resolvedValue, valueHolder.getType(), valueHolder.getName());
				resolvedValueHolder.setSource(valueHolder);
                 //加入到处理好的参数集合中
				resolvedValues.addIndexedArgumentValue(index, resolvedValueHolder);
			}
		}

        //遍历常规参数列表
		for (ConstructorArgumentValues.ValueHolder valueHolder : cargs.getGenericArgumentValues()) {
             //如果参数值被处理过了 那么直接加入到处理好的参数集合中
			if (valueHolder.isConverted()) {
				resolvedValues.addGenericArgumentValue(valueHolder);
			}
			else {
                 //否则进行参数解析
				Object resolvedValue =
						valueResolver.resolveValueIfNecessary("constructor argument", valueHolder.getValue());
				ConstructorArgumentValues.ValueHolder resolvedValueHolder = new ConstructorArgumentValues.ValueHolder(
						resolvedValue, valueHolder.getType(), valueHolder.getName());
				resolvedValueHolder.setSource(valueHolder);
                 //加入到处理好的参数集合中
				resolvedValues.addGenericArgumentValue(resolvedValueHolder);
			}
		}

		return minNrOfArgs;
	}

}
```



###### resolveValueIfNecessary

```java
class BeanDefinitionValueResolver {

    //解析参数类型
    @Nullable
	public Object resolveValueIfNecessary(Object argName, @Nullable Object value) {
		// We must check each value to see whether it requires a runtime reference
		// to another bean to be resolved.
		if (value instanceof RuntimeBeanReference) {
			RuntimeBeanReference ref = (RuntimeBeanReference) value;
			return resolveReference(argName, ref);
		}
		else if (value instanceof RuntimeBeanNameReference) {
			String refName = ((RuntimeBeanNameReference) value).getBeanName();
			refName = String.valueOf(doEvaluate(refName));
			if (!this.beanFactory.containsBean(refName)) {
				throw new BeanDefinitionStoreException(
						"Invalid bean name '" + refName + "' in bean reference for " + argName);
			}
			return refName;
		}
		else if (value instanceof BeanDefinitionHolder) {
			// Resolve BeanDefinitionHolder: contains BeanDefinition with name and aliases.
			BeanDefinitionHolder bdHolder = (BeanDefinitionHolder) value;
			return resolveInnerBean(argName, bdHolder.getBeanName(), bdHolder.getBeanDefinition());
		}
		else if (value instanceof BeanDefinition) {
			// Resolve plain BeanDefinition, without contained name: use dummy name.
			BeanDefinition bd = (BeanDefinition) value;
			String innerBeanName = "(inner bean)" + BeanFactoryUtils.GENERATED_BEAN_NAME_SEPARATOR +
					ObjectUtils.getIdentityHexString(bd);
			return resolveInnerBean(argName, innerBeanName, bd);
		}
		else if (value instanceof DependencyDescriptor) {
			Set<String> autowiredBeanNames = new LinkedHashSet<>(4);
			Object result = this.beanFactory.resolveDependency(
					(DependencyDescriptor) value, this.beanName, autowiredBeanNames, this.typeConverter);
			for (String autowiredBeanName : autowiredBeanNames) {
				if (this.beanFactory.containsBean(autowiredBeanName)) {
					this.beanFactory.registerDependentBean(autowiredBeanName, this.beanName);
				}
			}
			return result;
		}
		else if (value instanceof ManagedArray) {
			// May need to resolve contained runtime references.
			ManagedArray array = (ManagedArray) value;
			Class<?> elementType = array.resolvedElementType;
			if (elementType == null) {
				String elementTypeName = array.getElementTypeName();
				if (StringUtils.hasText(elementTypeName)) {
					try {
						elementType = ClassUtils.forName(elementTypeName, this.beanFactory.getBeanClassLoader());
						array.resolvedElementType = elementType;
					}
					catch (Throwable ex) {
						// Improve the message by showing the context.
						throw new BeanCreationException(
								this.beanDefinition.getResourceDescription(), this.beanName,
								"Error resolving array type for " + argName, ex);
					}
				}
				else {
					elementType = Object.class;
				}
			}
			return resolveManagedArray(argName, (List<?>) value, elementType);
		}
		else if (value instanceof ManagedList) {
			// May need to resolve contained runtime references.
			return resolveManagedList(argName, (List<?>) value);
		}
		else if (value instanceof ManagedSet) {
			// May need to resolve contained runtime references.
			return resolveManagedSet(argName, (Set<?>) value);
		}
		else if (value instanceof ManagedMap) {
			// May need to resolve contained runtime references.
			return resolveManagedMap(argName, (Map<?, ?>) value);
		}
		else if (value instanceof ManagedProperties) {
			Properties original = (Properties) value;
			Properties copy = new Properties();
			original.forEach((propKey, propValue) -> {
				if (propKey instanceof TypedStringValue) {
					propKey = evaluate((TypedStringValue) propKey);
				}
				if (propValue instanceof TypedStringValue) {
					propValue = evaluate((TypedStringValue) propValue);
				}
				if (propKey == null || propValue == null) {
					throw new BeanCreationException(
							this.beanDefinition.getResourceDescription(), this.beanName,
							"Error converting Properties key/value pair for " + argName + ": resolved to null");
				}
				copy.put(propKey, propValue);
			});
			return copy;
		}
		else if (value instanceof TypedStringValue) {
			// Convert value to target type here.
			TypedStringValue typedStringValue = (TypedStringValue) value;
			Object valueObject = evaluate(typedStringValue);
			try {
				Class<?> resolvedTargetType = resolveTargetType(typedStringValue);
				if (resolvedTargetType != null) {
					return this.typeConverter.convertIfNecessary(valueObject, resolvedTargetType);
				}
				else {
					return valueObject;
				}
			}
			catch (Throwable ex) {
				// Improve the message by showing the context.
				throw new BeanCreationException(
						this.beanDefinition.getResourceDescription(), this.beanName,
						"Error converting typed String value for " + argName, ex);
			}
		}
		else if (value instanceof NullBean) {
			return null;
		}
		else {
             //对于value是String/String[]类型会尝试评估为表达式并解析出表达式的值，其他类型直接返回value.
			return evaluate(value);
		}
	}

}
```

###### createArgumentArray

- 获取类型转换器，初始化结果参数集合
- 根据下标遍历当前构造方法的参数列表，获取当前参数类型和对应的下标
- 首先根据`beanDefinition`中设置的`constructorArgumentValues`进行获取，先根据下标在`indexedArgumentValues`中进行获取，获取不到再从`genericArgumentValues`中找一个没使用过的参数值
- 对上述从`constructorArgumentValues`中获取到的参数值依照当前参数类型进行一个类型转换，有编辑器优先用编辑器，否则用转换器转换，类型转换成功继续下一个参数类型，类型转换失败，抛出异常证明当前构造方法并不是最匹配的
- 如果上述`constructorArgumentValues`中没有获取到任何参数值，那么就检查是不是带有`@Autowired`注解，没有就报错，说明少参数
- 如果有`@Autowired`注解，尝试在容器中查找一个与参数类型匹配的bean，找不到对应参数类型的bean也会报错



```java
class ConstructorResolver {
    
    //对constructorArgumentValues尝试进行类型转换，constructorArgumentValues中不包含的进行自动注入
    private ArgumentsHolder createArgumentArray(
			String beanName, RootBeanDefinition mbd, @Nullable ConstructorArgumentValues resolvedValues,
			BeanWrapper bw, Class<?>[] paramTypes, @Nullable String[] paramNames, Executable executable,
			boolean autowiring, boolean fallback) throws UnsatisfiedDependencyException {
		
        //获取类型转换器
		TypeConverter customConverter = this.beanFactory.getCustomTypeConverter();
		TypeConverter converter = (customConverter != null ? customConverter : bw);
		
        //存储处理后的参数的对象
		ArgumentsHolder args = new ArgumentsHolder(paramTypes.length);
        
		Set<ConstructorArgumentValues.ValueHolder> usedValueHolders = new HashSet<>(paramTypes.length);
		Set<String> autowiredBeanNames = new LinkedHashSet<>(4);
		
        //根据参数下标遍历参数
		for (int paramIndex = 0; paramIndex < paramTypes.length; paramIndex++) {
             //获取构造方法中的指定下标的参数类型和参数名称
			Class<?> paramType = paramTypes[paramIndex];
			String paramName = (paramNames != null ? paramNames[paramIndex] : "");
            
			// Try to find matching constructor argument value, either indexed or generic.
			ConstructorArgumentValues.ValueHolder valueHolder = null;
			if (resolvedValues != null) {
                 //根据对应的参数下标和参数类型获取参数值
				valueHolder = resolvedValues.getArgumentValue(paramIndex, paramType, paramName, usedValueHolders);
				// If we couldn't find a direct match and are not supposed to autowire,
				// let's try the next generic, untyped argument value as fallback:
				// it could match after type conversion (for example, String -> int).
				if (valueHolder == null && (!autowiring || paramTypes.length == resolvedValues.getArgumentCount())) {
					valueHolder = resolvedValues.getGenericArgumentValue(null, null, usedValueHolders);
				}
			}
             //获取到参数值
			if (valueHolder != null) {
				// We found a potential match - let's give it a try.
				// Do not consider the same value definition multiple times!
                 //加入已经处理的参数集合中
				usedValueHolders.add(valueHolder);
				Object originalValue = valueHolder.getValue();
				Object convertedValue;
                  //如果已经转换过类型 直接使用
				if (valueHolder.isConverted()) {
					convertedValue = valueHolder.getConvertedValue();
					args.preparedArguments[paramIndex] = convertedValue;
				}
				else {
                      //否则进行类型转换 尝试将当前值转换成指定的参数类型
					MethodParameter methodParam = MethodParameter.forExecutable(executable, paramIndex);
					try {
						convertedValue = converter.convertIfNecessary(originalValue, paramType, methodParam);
					}
                      //类型不匹配转换失败的异常捕获
					catch (TypeMismatchException ex) {
                          //继续抛异常给上层
						throw new UnsatisfiedDependencyException(
								mbd.getResourceDescription(), beanName, new InjectionPoint(methodParam),
								"Could not convert argument value of type [" +
										ObjectUtils.nullSafeClassName(valueHolder.getValue()) +
										"] to required type [" + paramType.getName() + "]: " + ex.getMessage());
					}
					Object sourceHolder = valueHolder.getSource();
					if (sourceHolder instanceof ConstructorArgumentValues.ValueHolder) {
						Object sourceValue = ((ConstructorArgumentValues.ValueHolder) sourceHolder).getValue();
						args.resolveNecessary = true;
						args.preparedArguments[paramIndex] = sourceValue;
					}
				}
                  //设置类型转换完成的值
				args.arguments[paramIndex] = convertedValue;
				args.rawArguments[paramIndex] = originalValue;
			}
			else {
                 //如果没有构造方法参数 那么就是@Autowired要注入的参数
                 //指定下标参数
				MethodParameter methodParam = MethodParameter.forExecutable(executable, paramIndex);
				// No explicit match found: we're either supposed to autowire or
				// have to fail creating an argument array for the given constructor.
                 //如果不是@Autowired的 报错
				if (!autowiring) {
					throw new UnsatisfiedDependencyException(
							mbd.getResourceDescription(), beanName, new InjectionPoint(methodParam),
							"Ambiguous argument values for parameter of type [" + paramType.getName() +
							"] - did you specify the correct bean references as arguments?");
				}
				try {
                     // 解析指定下标@Autowired要注入的参数
					Object autowiredArgument = resolveAutowiredArgument(
							methodParam, beanName, autowiredBeanNames, converter, fallback);
                      //将@Autowired的参数设置到列表中
					args.rawArguments[paramIndex] = autowiredArgument;
					args.arguments[paramIndex] = autowiredArgument;
					args.preparedArguments[paramIndex] = autowiredArgumentMarker;
					args.resolveNecessary = true;
				}
				catch (BeansException ex) {
					throw new UnsatisfiedDependencyException(
							mbd.getResourceDescription(), beanName, new InjectionPoint(methodParam), ex);
				}
			}
		}
		//缓存
		for (String autowiredBeanName : autowiredBeanNames) {
			this.beanFactory.registerDependentBean(autowiredBeanName, beanName);
			if (logger.isDebugEnabled()) {
				logger.debug("Autowiring by type from bean name '" + beanName +
						"' via " + (executable instanceof Constructor ? "constructor" : "factory method") +
						" to bean named '" + autowiredBeanName + "'");
			}
		}
		//返回参数列表
		return args;
	}
    
    
    //通过指定的类型查找bean
    @Nullable
	protected Object resolveAutowiredArgument(MethodParameter param, String beanName,
			@Nullable Set<String> autowiredBeanNames, TypeConverter typeConverter, boolean fallback) {
		//获取参数类型
		Class<?> paramType = param.getParameterType();
        
		if (InjectionPoint.class.isAssignableFrom(paramType)) {
			InjectionPoint injectionPoint = currentInjectionPoint.get();
			if (injectionPoint == null) {
				throw new IllegalStateException("No current InjectionPoint available for " + param);
			}
			return injectionPoint;
		}
		try {
             //通过指定的类型查找bean
			return this.beanFactory.resolveDependency(
					new DependencyDescriptor(param, true), beanName, autowiredBeanNames, typeConverter);
		}
		catch (NoUniqueBeanDefinitionException ex) {
			throw ex;
		}
		catch (NoSuchBeanDefinitionException ex) {
			if (fallback) {
				// Single constructor or factory method -> let's return an empty array/collection
				// for e.g. a vararg or a non-null List/Set/Map parameter.
				if (paramType.isArray()) {
					return Array.newInstance(paramType.getComponentType(), 0);
				}
				else if (CollectionFactory.isApproximableCollectionType(paramType)) {
					return CollectionFactory.createCollection(paramType, 0);
				}
				else if (CollectionFactory.isApproximableMapType(paramType)) {
					return CollectionFactory.createMap(paramType, 0);
				}
			}
			throw ex;
		}
	}


}
```

###### resolveDependency

```java
public class DefaultListableBeanFactory extends AbstractAutowireCapableBeanFactory
       implements ConfigurableListableBeanFactory, BeanDefinitionRegistry, Serializable {

    //根据指定的类型查找bean
	@Override
	@Nullable
	public Object resolveDependency(DependencyDescriptor descriptor, @Nullable String requestingBeanName,
			@Nullable Set<String> autowiredBeanNames, @Nullable TypeConverter typeConverter) throws BeansException {
		//设置参数名称发现器
		descriptor.initParameterNameDiscovery(getParameterNameDiscoverer());
        //如果指定的类型是Optional
		if (Optional.class == descriptor.getDependencyType()) {
			return createOptionalDependency(descriptor, requestingBeanName);
		}
         //如果指定的类型是ObjectFactory或ObjectProvider
		else if (ObjectFactory.class == descriptor.getDependencyType() ||
				ObjectProvider.class == descriptor.getDependencyType()) {
			return new DependencyObjectProvider(descriptor, requestingBeanName);
		}
         //如果指定的类型是Provider
		else if (javaxInjectProviderClass == descriptor.getDependencyType()) {
			return new Jsr330Factory().createDependencyProvider(descriptor, requestingBeanName);
		}
		else {
             //处理@Lazy @todo??
			Object result = getAutowireCandidateResolver().getLazyResolutionProxyIfNecessary(
					descriptor, requestingBeanName);
			if (result == null) {
                  //对指定类型查找bean
				result = doResolveDependency(descriptor, requestingBeanName, autowiredBeanNames, typeConverter);
			}
			return result;
		}
	}
    
    
    @Nullable
	public Object doResolveDependency(DependencyDescriptor descriptor, @Nullable String beanName,
			@Nullable Set<String> autowiredBeanNames, @Nullable TypeConverter typeConverter) throws BeansException {

		InjectionPoint previousInjectionPoint = ConstructorResolver.setCurrentInjectionPoint(descriptor);
		try {
             //快捷方式
			Object shortcut = descriptor.resolveShortcut(this);
			if (shortcut != null) {
				return shortcut;
			}
			//获取指定的类型
			Class<?> type = descriptor.getDependencyType();
             //如果当前要注入的属性或者参数前有@Value注解
			Object value = getAutowireCandidateResolver().getSuggestedValue(descriptor);
             //获取@Value注解的值
			if (value != null) {
                  //如果是@Value注解的值是字符串
				if (value instanceof String) {
                      //那么进行一下占位符的解析替换 ${}
					String strVal = resolveEmbeddedValue((String) value);
					BeanDefinition bd = (beanName != null && containsBean(beanName) ?
							getMergedBeanDefinition(beanName) : null);
                      //再进行一下spel的解析 #{}
					value = evaluateBeanDefinitionString(strVal, bd);
				}
                 //最后 看看类型是否匹配 如果不匹配尝试进行一下转换 有编辑器先用编辑器 没有用转换服务
				TypeConverter converter = (typeConverter != null ? typeConverter : getTypeConverter());
				try {
					return converter.convertIfNecessary(value, type, descriptor.getTypeDescriptor());
				}
				catch (UnsupportedOperationException ex) {
					// A custom TypeConverter which does not support TypeDescriptor resolution...
					return (descriptor.getField() != null ?
							converter.convertIfNecessary(value, type, descriptor.getField()) :
							converter.convertIfNecessary(value, type, descriptor.getMethodParameter()));
				}
			}
			//如果指定的类型是一个Stream、Collection、数组、Map类型 在这里解析
			Object multipleBeans = resolveMultipleBeans(descriptor, beanName, autowiredBeanNames, typeConverter);
			if (multipleBeans != null) {
				return multipleBeans;
			}
			//如果指定的类型是一个普通引用类型
			Map<String, Object> matchingBeans = findAutowireCandidates(beanName, type, descriptor);
             //如果没找到对应的bean 并且这个bean是required=true必须的
			if (matchingBeans.isEmpty()) {
				if (isRequired(descriptor)) {
                      //抛出找不到bean异常
					raiseNoMatchingBeanFound(type, descriptor.getResolvableType(), descriptor);
				}
				return null;
			}

			String autowiredBeanName;
			Object instanceCandidate;
			
             //如果查找到了多余1个匹配的bean
			if (matchingBeans.size() > 1) {
                 //通过@Primary注解筛选 和 通过@Priority比较筛选
				autowiredBeanName = determineAutowireCandidate(matchingBeans, descriptor);
                 //如果没筛选出来
				if (autowiredBeanName == null) {
					//是必须的 抛出不唯一异常 
                      if (isRequired(descriptor) || !indicatesMultipleBeans(type)) {
						return descriptor.resolveNotUnique(descriptor.getResolvableType(), matchingBeans);
					}
					else {
						// In case of an optional Collection/Map, silently ignore a non-unique case:
						// possibly it was meant to be an empty collection of multiple regular beans
						// (before 4.3 in particular when we didn't even look for collection beans).
						return null;
					}
				}
                  //获取筛选的bean
				instanceCandidate = matchingBeans.get(autowiredBeanName);
			}
             //如果只有一个匹配的bean
			else {
				// We have exactly one match.
				Map.Entry<String, Object> entry = matchingBeans.entrySet().iterator().next();
                  //设置这个beanName和bean
				autowiredBeanName = entry.getKey();
				instanceCandidate = entry.getValue();
			}
			//添加到集合
			if (autowiredBeanNames != null) {
				autowiredBeanNames.add(autowiredBeanName);
			}
             //instanceCandidate的此时是个class对象 获取bean实例
			if (instanceCandidate instanceof Class) {
                  //调用beanFactory.getBean
				instanceCandidate = descriptor.resolveCandidate(autowiredBeanName, type, this);
			}
			Object result = instanceCandidate;
			if (result instanceof NullBean) {
				if (isRequired(descriptor)) {
					raiseNoMatchingBeanFound(type, descriptor.getResolvableType(), descriptor);
				}
				result = null;
			}
             //最后检查一下获取的类型和所需的类型是否相匹配 抛出类型不匹配异常
			if (!ClassUtils.isAssignableValue(type, result)) {
				throw new BeanNotOfRequiredTypeException(autowiredBeanName, type, instanceCandidate.getClass());
			}
             //返回结果
			return result;
		}
		finally {
			ConstructorResolver.setCurrentInjectionPoint(previousInjectionPoint);
		}
	}

    //如果指定的类型是一个Stream、Collection、数组、Map类型 解析
	@Nullable
	private Object resolveMultipleBeans(DependencyDescriptor descriptor, @Nullable String beanName,
			@Nullable Set<String> autowiredBeanNames, @Nullable TypeConverter typeConverter) {
		
         //获取指定的类型
		final Class<?> type = descriptor.getDependencyType();

        //如果指定的类型是一个Stream
		if (descriptor instanceof StreamDependencyDescriptor) {
             //查找Stream类型的bean
			Map<String, Object> matchingBeans = findAutowireCandidates(beanName, type, descriptor);
			if (autowiredBeanNames != null) {
				autowiredBeanNames.addAll(matchingBeans.keySet());
			}
             //再查找Stream中内置类型的bean
			Stream<Object> stream = matchingBeans.keySet().stream()
					.map(name -> descriptor.resolveCandidate(name, type, this))
					.filter(bean -> !(bean instanceof NullBean));
			if (((StreamDependencyDescriptor) descriptor).isOrdered()) {
				stream = stream.sorted(adaptOrderComparator(matchingBeans));
			}
			return stream;
		}
         //如果指定的类型是一个数组
		else if (type.isArray()) {
             //获取数组内置的类型
			Class<?> componentType = type.getComponentType();
			ResolvableType resolvableType = descriptor.getResolvableType();
			Class<?> resolvedArrayType = resolvableType.resolve(type);
			if (resolvedArrayType != type) {
				componentType = resolvableType.getComponentType().resolve();
			}
			if (componentType == null) {
				return null;
			}
             //查找内置类型的bean
			Map<String, Object> matchingBeans = findAutowireCandidates(beanName, componentType,
					new MultiElementDescriptor(descriptor));
			if (matchingBeans.isEmpty()) {
				return null;
			}
			if (autowiredBeanNames != null) {
				autowiredBeanNames.addAll(matchingBeans.keySet());
			}
             //将所有的bean转换成数组类型
			TypeConverter converter = (typeConverter != null ? typeConverter : getTypeConverter());
			Object result = converter.convertIfNecessary(matchingBeans.values(), resolvedArrayType);
			if (result instanceof Object[]) {
				Comparator<Object> comparator = adaptDependencyComparator(matchingBeans);
				if (comparator != null) {
					Arrays.sort((Object[]) result, comparator);
				}
			}
			return result;
		}
         //如果指定的类型是Collection
		else if (Collection.class.isAssignableFrom(type) && type.isInterface()) {
             //获取内置的类型
			Class<?> elementType = descriptor.getResolvableType().asCollection().resolveGeneric();
			if (elementType == null) {
				return null;
			}
             //查找内置类型的bean
			Map<String, Object> matchingBeans = findAutowireCandidates(beanName, elementType,
					new MultiElementDescriptor(descriptor));
			if (matchingBeans.isEmpty()) {
				return null;
			}
			if (autowiredBeanNames != null) {
				autowiredBeanNames.addAll(matchingBeans.keySet());
			}
             //转换成集合类型
			TypeConverter converter = (typeConverter != null ? typeConverter : getTypeConverter());
			Object result = converter.convertIfNecessary(matchingBeans.values(), type);
			if (result instanceof List) {
				if (((List<?>) result).size() > 1) {
					Comparator<Object> comparator = adaptDependencyComparator(matchingBeans);
					if (comparator != null) {
						((List<?>) result).sort(comparator);
					}
				}
			}
			return result;
		}
         //如果指定的类型是Map
		else if (Map.class == type) {
			ResolvableType mapType = descriptor.getResolvableType().asMap();
             //如果key不是String类型 直接返回
			Class<?> keyType = mapType.resolveGeneric(0);
			if (String.class != keyType) {
				return null;
			}
             //获取value的类型
			Class<?> valueType = mapType.resolveGeneric(1);
			if (valueType == null) {
				return null;
			}
             //查找指定value类型的bean
			Map<String, Object> matchingBeans = findAutowireCandidates(beanName, valueType,
					new MultiElementDescriptor(descriptor));
			if (matchingBeans.isEmpty()) {
				return null;
			}
			if (autowiredBeanNames != null) {
				autowiredBeanNames.addAll(matchingBeans.keySet());
			}
			return matchingBeans;
		}
         //不是上述的集合类型 返回null
		else {
			return null;
		}
	}

    //查找指定类型的所有bean
    protected Map<String, Object> findAutowireCandidates(
			@Nullable String beanName, Class<?> requiredType, DependencyDescriptor descriptor) {
		//获取指定类型的所有的beanName
		String[] candidateNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(
				this, requiredType, true, descriptor.isEager());
		Map<String, Object> result = new LinkedHashMap<>(candidateNames.length);
        
         //如果要获取的类型是ApplicationContext、ResourceLoader、ApplicationEventPublisher、BeanFactory类型
         //直接返回预先设置好的bean
		for (Map.Entry<Class<?>, Object> classObjectEntry : this.resolvableDependencies.entrySet()) {
			Class<?> autowiringType = classObjectEntry.getKey();
			if (autowiringType.isAssignableFrom(requiredType)) {
				Object autowiringValue = classObjectEntry.getValue();
				autowiringValue = AutowireUtils.resolveAutowiringValue(autowiringValue, requiredType);
				if (requiredType.isInstance(autowiringValue)) {
					result.put(ObjectUtils.identityToString(autowiringValue), autowiringValue);
					break;
				}
			}
		}
        //遍历指定类型的所有的beanName
		for (String candidate : candidateNames) {
             //如果不是一个自我引用 并且 如果有@Qualifier注解标注 筛选和@Qualifier的value一致的beanName
			if (!isSelfReference(beanName, candidate) && isAutowireCandidate(candidate, descriptor)) {
                 //将当前参数名称和获取的类型加入result
				addCandidateEntry(result, candidate, descriptor, requiredType);
			}
		}
		if (result.isEmpty()) {
			boolean multiple = indicatesMultipleBeans(requiredType);
			// Consider fallback matches if the first pass failed to find anything...
			DependencyDescriptor fallbackDescriptor = descriptor.forFallbackMatch();
			for (String candidate : candidateNames) {
				if (!isSelfReference(beanName, candidate) && isAutowireCandidate(candidate, fallbackDescriptor) &&
						(!multiple || getAutowireCandidateResolver().hasQualifier(descriptor))) {
					addCandidateEntry(result, candidate, descriptor, requiredType);
				}
			}
			if (result.isEmpty() && !multiple) {
				// Consider self references as a final pass...
				// but in the case of a dependency collection, not the very same bean itself.
				for (String candidate : candidateNames) {
					if (isSelfReference(beanName, candidate) &&
							(!(descriptor instanceof MultiElementDescriptor) || !beanName.equals(candidate)) &&
							isAutowireCandidate(candidate, fallbackDescriptor)) {
						addCandidateEntry(result, candidate, descriptor, requiredType);
					}
				}
			}
		}
		return result;
	}
    
    //获取bean
    public Object resolveCandidate(String beanName, Class<?> requiredType, BeanFactory beanFactory)
			throws BeansException {

		return beanFactory.getBean(beanName);
	}


}
```



###### getTypeDifferenceWeight

```java
public class MethodInvoker {
    
    // MethodInvoker.getTypeDifferenceWeight-确定表示类型和参数之间的类层次结构差异的权重：
	// 1. arguments的类型不paramTypes类型的子类，直接返回 Integer.MAX_VALUE,最大重量，也就是直接不匹配
	// 2. paramTypes类型是arguments类型的父类则+2
	// 3. paramTypes类型是arguments类型的接口，则+1
	// 4. arguments的类型直接就是paramTypes类型,则+0
	// 获取表示paramTypes和arguments之间的类层次结构差异的权重
	public static int getTypeDifferenceWeight(Class<?>[] paramTypes, Object[] args) {
		int result = 0;
		for (int i = 0; i < paramTypes.length; i++) {
			if (!ClassUtils.isAssignableValue(paramTypes[i], args[i])) {
				return Integer.MAX_VALUE;
			}
			if (args[i] != null) {
				Class<?> paramType = paramTypes[i];
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
}
```



###### instantiate

```java
class ConstructorResolver {
    
    //使用构造方法进行实例化
	private Object instantiate(
			String beanName, RootBeanDefinition mbd, Constructor<?> constructorToUse, Object[] argsToUse) {

		try {
             //获得实例化策略
			InstantiationStrategy strategy = this.beanFactory.getInstantiationStrategy();
			if (System.getSecurityManager() != null) {
				return AccessController.doPrivileged((PrivilegedAction<Object>) () ->
						strategy.instantiate(mbd, beanName, this.beanFactory, constructorToUse, argsToUse),
						this.beanFactory.getAccessControlContext());
			}
			else {
                 //调用实例化策略来实例化对象
				return strategy.instantiate(mbd, beanName, this.beanFactory, constructorToUse, argsToUse);
			}
		}
		catch (Throwable ex) {
			throw new BeanCreationException(mbd.getResourceDescription(), beanName,
					"Bean instantiation via constructor failed", ex);
		}
	}
}
```

```java
public class SimpleInstantiationStrategy implements InstantiationStrategy {
    
    //利用构造方法进行实例化
	@Override
	public Object instantiate(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner,
			final Constructor<?> ctor, Object... args) {

		if (!bd.hasMethodOverrides()) {
			if (System.getSecurityManager() != null) {
				// use own privileged to change accessibility (when security is on)
				AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
					ReflectionUtils.makeAccessible(ctor);
					return null;
				});
			}
             //利用构造方法进行实例化
			return BeanUtils.instantiateClass(ctor, args);
		}
		else {
			return instantiateWithMethodInjection(bd, beanName, owner, ctor, args);
		}
	}
}
```



###### instantiateBean

```java

//默认构造方法的实例化
protected BeanWrapper instantiateBean(final String beanName, final RootBeanDefinition mbd) {
    try {
       Object beanInstance;
       final BeanFactory parent = this;
       if (System.getSecurityManager() != null) {
          beanInstance = AccessController.doPrivileged((PrivilegedAction<Object>) () ->
                getInstantiationStrategy().instantiate(mbd, beanName, parent),
                getAccessControlContext());
       }
       else {
          beanInstance = getInstantiationStrategy().instantiate(mbd, beanName, parent);
       }
       BeanWrapper bw = new BeanWrapperImpl(beanInstance);
       initBeanWrapper(bw);
       return bw;
    }
    catch (Throwable ex) {
       throw new BeanCreationException(
             mbd.getResourceDescription(), beanName, "Instantiation of bean failed", ex);
    }
}
```

```java
public class SimpleInstantiationStrategy implements InstantiationStrategy {
    
    
    @Override
	public Object instantiate(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner) {
         //如果没有MethodOverrides 那么使用默认构造方法进行实例化
		// Don't override the class with CGLIB if no overrides.
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
                               //获取默认构造方法
							constructorToUse = clazz.getDeclaredConstructor();
						}
						bd.resolvedConstructorOrFactoryMethod = constructorToUse;
					}
					catch (Throwable ex) {
						throw new BeanInstantiationException(clazz, "No default constructor found", ex);
					}
				}
			}
             //使用默认构造方法进行实例化
			return BeanUtils.instantiateClass(constructorToUse);
		}
		else {
             //如果有MethodOverride 那么必须进行CGLIB代理生成实例
			// Must generate CGLIB subclass.
			return instantiateWithMethodInjection(bd, beanName, owner);
		}
	}

}
```



##### applyMergedBeanDefinitionPostProcessors()

```java
protected void applyMergedBeanDefinitionPostProcessors(RootBeanDefinition mbd, Class<?> beanType, String beanName) {
    for (BeanPostProcessor bp : getBeanPostProcessors()) {
        //处理MergedBeanDefinitionPostProcessor
       if (bp instanceof MergedBeanDefinitionPostProcessor) {
          MergedBeanDefinitionPostProcessor bdp = (MergedBeanDefinitionPostProcessor) bp;
          bdp.postProcessMergedBeanDefinition(mbd, beanType, beanName);
       }
    }
}
```

###### InitDestroyAnnotationBeanPostProcessor

- 负责扫描`@PostConstruct`和`@PreDestroy`的所有方法，将其注册到beanDefinition的`externallyManagedInitMethods`和`externallyManagedDestroyMethods`

```java
public class InitDestroyAnnotationBeanPostProcessor
       implements DestructionAwareBeanPostProcessor, MergedBeanDefinitionPostProcessor, PriorityOrdered, Serializable {

    //@PostConstruct注解
    @Nullable
	private Class<? extends Annotation> initAnnotationType;

    //@PreDestroy注解
	@Nullable
	private Class<? extends Annotation> destroyAnnotationType;

    
    public void setInitAnnotationType(Class<? extends Annotation> initAnnotationType) {
		this.initAnnotationType = initAnnotationType;
	}
    
    public void setDestroyAnnotationType(Class<? extends Annotation> destroyAnnotationType) {
		this.destroyAnnotationType = destroyAnnotationType;
	}
    
    
    //扫描所有的@PostConstruct和@PreDestroy标注的方法 将其注册到beanDefinition中
    @Override
	public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
         //找到所有的@PostConstruct和@PreDestroy的方法
		LifecycleMetadata metadata = findLifecycleMetadata(beanType);
         //将找到的方法注册到beanDefinition中
		metadata.checkConfigMembers(beanDefinition);
	}
    
    //扫描所有的@PostConstruct和@PreDestroy标注的方法
    private LifecycleMetadata findLifecycleMetadata(Class<?> clazz) {
		if (this.lifecycleMetadataCache == null) {
			// Happens after deserialization, during destruction...
			return buildLifecycleMetadata(clazz);
		}
        //从缓存中取
		// Quick check on the concurrent map first, with minimal locking.
		LifecycleMetadata metadata = this.lifecycleMetadataCache.get(clazz);
		if (metadata == null) {
			synchronized (this.lifecycleMetadataCache) {
				metadata = this.lifecycleMetadataCache.get(clazz);
				if (metadata == null) {
                      //缓存中没有 构建
					metadata = buildLifecycleMetadata(clazz);
					this.lifecycleMetadataCache.put(clazz, metadata);
				}
				return metadata;
			}
		}
		return metadata;
	}
	
    //扫描所有的@PostConstruct和@PreDestroy标注的方法
	private LifecycleMetadata buildLifecycleMetadata(final Class<?> clazz) {
		if (!AnnotationUtils.isCandidateClass(clazz, Arrays.asList(this.initAnnotationType, this.destroyAnnotationType))) {
			return this.emptyLifecycleMetadata;
		}

        //初始化初始化方法和销毁方法集合
		List<LifecycleElement> initMethods = new ArrayList<>();
		List<LifecycleElement> destroyMethods = new ArrayList<>();
		Class<?> targetClass = clazz;
		//遍历当前类和父类
		do {
			final List<LifecycleElement> currInitMethods = new ArrayList<>();
			final List<LifecycleElement> currDestroyMethods = new ArrayList<>();
			//扫描所有的@PostConstruct和@PreDestroy标注的方法
			ReflectionUtils.doWithLocalMethods(targetClass, method -> {
				if (this.initAnnotationType != null && method.isAnnotationPresent(this.initAnnotationType)) {
					LifecycleElement element = new LifecycleElement(method);
					currInitMethods.add(element);
					if (logger.isTraceEnabled()) {
						logger.trace("Found init method on class [" + clazz.getName() + "]: " + method);
					}
				}
				if (this.destroyAnnotationType != null && method.isAnnotationPresent(this.destroyAnnotationType)) {
					currDestroyMethods.add(new LifecycleElement(method));
					if (logger.isTraceEnabled()) {
						logger.trace("Found destroy method on class [" + clazz.getName() + "]: " + method);
					}
				}
			});

			initMethods.addAll(0, currInitMethods);
			destroyMethods.addAll(currDestroyMethods);
			targetClass = targetClass.getSuperclass();
		}
		while (targetClass != null && targetClass != Object.class);
		
         //将扫描到的方法封装成LifecycleMetadata
		return (initMethods.isEmpty() && destroyMethods.isEmpty() ? this.emptyLifecycleMetadata :
				new LifecycleMetadata(clazz, initMethods, destroyMethods));
	}
    
    //生命周期元数据内部类
	private class LifecycleMetadata {
        
        //将LifecycleMetadata中的初始化方法和销毁方法注册到beanDefinition中
    	public void checkConfigMembers(RootBeanDefinition beanDefinition) {
			Set<LifecycleElement> checkedInitMethods = new LinkedHashSet<>(this.initMethods.size());
             //遍历所有初始化方法
			for (LifecycleElement element : this.initMethods) {
				String methodIdentifier = element.getIdentifier();
				if (!beanDefinition.isExternallyManagedInitMethod(methodIdentifier)) {
                      //注册到beanDefinition的externallyManagedInitMethods中
					beanDefinition.registerExternallyManagedInitMethod(methodIdentifier);
					checkedInitMethods.add(element);
					if (logger.isTraceEnabled()) {
						logger.trace("Registered init method on class [" + this.targetClass.getName() + "]: " + element);
					}
				}
			}
			Set<LifecycleElement> checkedDestroyMethods = new LinkedHashSet<>(this.destroyMethods.size());
			for (LifecycleElement element : this.destroyMethods) {
				String methodIdentifier = element.getIdentifier();
				if (!beanDefinition.isExternallyManagedDestroyMethod(methodIdentifier)) {
                     //注册到beanDefinition的externallyManagedDestroyMethods中
					beanDefinition.registerExternallyManagedDestroyMethod(methodIdentifier);
					checkedDestroyMethods.add(element);
					if (logger.isTraceEnabled()) {
						logger.trace("Registered destroy method on class [" + this.targetClass.getName() + "]: " + element);
					}
				}
			}
			this.checkedInitMethods = checkedInitMethods;
			this.checkedDestroyMethods = checkedDestroyMethods;
		}

    
    }

}
```





###### CommonAnnotationBeanPostProcessor

- 负责扫描`@Resource、@WebServiceRef、@EJB`的所有字段和方法，将其注册到beanDefinition的`externallyManagedConfigMembers`中

```java

//CommonAnnotationBeanPostProcessor继承了InitDestroyAnnotationBeanPostProcessor

public class CommonAnnotationBeanPostProcessor extends InitDestroyAnnotationBeanPostProcessor
       implements InstantiationAwareBeanPostProcessor, BeanFactoryAware, Serializable {

    //包含@Resource、@WebServiceRef、@EJB
    private static final Set<Class<? extends Annotation>> resourceAnnotationTypes = new LinkedHashSet<>(4);

    
    //初始化要处理的注解@Resource、@WebServiceRef、@EJB
	static {
		webServiceRefClass = loadAnnotationType("javax.xml.ws.WebServiceRef");
		ejbClass = loadAnnotationType("javax.ejb.EJB");

		resourceAnnotationTypes.add(Resource.class);
		if (webServiceRefClass != null) {
			resourceAnnotationTypes.add(webServiceRefClass);
		}
		if (ejbClass != null) {
			resourceAnnotationTypes.add(ejbClass);
		}
	}
    
 
    public CommonAnnotationBeanPostProcessor() {
		setOrder(Ordered.LOWEST_PRECEDENCE - 3);
         //设置父类InitDestroyAnnotationBeanPostProcessor处理的注解
		setInitAnnotationType(PostConstruct.class);
		setDestroyAnnotationType(PreDestroy.class);
		ignoreResourceType("javax.xml.ws.WebServiceContext");
	}
    
    @Override
	public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
         //先处理父类InitDestroyAnnotationBeanPostProcessor的@PostConstruct和@PreDestroy
		super.postProcessMergedBeanDefinition(beanDefinition, beanType, beanName);
         //处理@Resource、@WebServiceRef、@EJB
		InjectionMetadata metadata = findResourceMetadata(beanName, beanType, null);
		metadata.checkConfigMembers(beanDefinition);
	}
    
    //创建InjectionMetadata 扫描找出带有@Resource、@WebServiceRef、@EJB的所有字段和方法
    private InjectionMetadata buildResourceMetadata(final Class<?> clazz) {
		if (!AnnotationUtils.isCandidateClass(clazz, resourceAnnotationTypes)) {
			return InjectionMetadata.EMPTY;
		}

		List<InjectionMetadata.InjectedElement> elements = new ArrayList<>();
		Class<?> targetClass = clazz;

         //遍历当前类和父类
		do {
			final List<InjectionMetadata.InjectedElement> currElements = new ArrayList<>();
			
             //扫描所有字段 找出带有@Resource、@WebServiceRef、@EJB的所有字段
			ReflectionUtils.doWithLocalFields(targetClass, field -> {
				if (webServiceRefClass != null && field.isAnnotationPresent(webServiceRefClass)) {
					if (Modifier.isStatic(field.getModifiers())) {
						throw new IllegalStateException("@WebServiceRef annotation is not supported on static fields");
					}
					currElements.add(new WebServiceRefElement(field, field, null));
				}
				else if (ejbClass != null && field.isAnnotationPresent(ejbClass)) {
					if (Modifier.isStatic(field.getModifiers())) {
						throw new IllegalStateException("@EJB annotation is not supported on static fields");
					}
					currElements.add(new EjbRefElement(field, field, null));
				}
				else if (field.isAnnotationPresent(Resource.class)) {
					if (Modifier.isStatic(field.getModifiers())) {
						throw new IllegalStateException("@Resource annotation is not supported on static fields");
					}
					if (!this.ignoredResourceTypes.contains(field.getType().getName())) {
						currElements.add(new ResourceElement(field, field, null));
					}
				}
			});

             //扫描所有方法 找出带有@Resource、@WebServiceRef、@EJB的所有方法
			ReflectionUtils.doWithLocalMethods(targetClass, method -> {
				Method bridgedMethod = BridgeMethodResolver.findBridgedMethod(method);
				if (!BridgeMethodResolver.isVisibilityBridgeMethodPair(method, bridgedMethod)) {
					return;
				}
				if (method.equals(ClassUtils.getMostSpecificMethod(method, clazz))) {
					if (webServiceRefClass != null && bridgedMethod.isAnnotationPresent(webServiceRefClass)) {
						if (Modifier.isStatic(method.getModifiers())) {
							throw new IllegalStateException("@WebServiceRef annotation is not supported on static methods");
						}
                          //方法的参数只能有1个
						if (method.getParameterCount() != 1) {
							throw new IllegalStateException("@WebServiceRef annotation requires a single-arg method: " + method);
						}
						PropertyDescriptor pd = BeanUtils.findPropertyForMethod(bridgedMethod, clazz);
						currElements.add(new WebServiceRefElement(method, bridgedMethod, pd));
					}
					else if (ejbClass != null && bridgedMethod.isAnnotationPresent(ejbClass)) {
						if (Modifier.isStatic(method.getModifiers())) {
							throw new IllegalStateException("@EJB annotation is not supported on static methods");
						}
                          //方法的参数只能有1个
						if (method.getParameterCount() != 1) {
							throw new IllegalStateException("@EJB annotation requires a single-arg method: " + method);
						}
						PropertyDescriptor pd = BeanUtils.findPropertyForMethod(bridgedMethod, clazz);
						currElements.add(new EjbRefElement(method, bridgedMethod, pd));
					}
					else if (bridgedMethod.isAnnotationPresent(Resource.class)) {
						if (Modifier.isStatic(method.getModifiers())) {
							throw new IllegalStateException("@Resource annotation is not supported on static methods");
						}
						Class<?>[] paramTypes = method.getParameterTypes();
                          //方法的参数只能有1个
						if (paramTypes.length != 1) {
							throw new IllegalStateException("@Resource annotation requires a single-arg method: " + method);
						}
						if (!this.ignoredResourceTypes.contains(paramTypes[0].getName())) {
							PropertyDescriptor pd = BeanUtils.findPropertyForMethod(bridgedMethod, clazz);
                               //创建ResourceElement
							currElements.add(new ResourceElement(method, bridgedMethod, pd));
						}
					}
				}
			});

			elements.addAll(0, currElements);
			targetClass = targetClass.getSuperclass();
		}
		while (targetClass != null && targetClass != Object.class);
		
        //封装InjectionMetadata数据
		return InjectionMetadata.forElements(elements, clazz);
	}

	public class InjectionMetadata {
        
        //遍历所有InjectionMetadata数据 注册到beanDefinition的externallyManagedConfigMembers中
    	public void checkConfigMembers(RootBeanDefinition beanDefinition) {
            Set<InjectedElement> checkedElements = new LinkedHashSet<>(this.injectedElements.size());
            //遍历InjectionMetadata数据 
            for (InjectedElement element : this.injectedElements) {
                Member member = element.getMember();
                if (!beanDefinition.isExternallyManagedConfigMember(member)) {
                    //注册到beanDefinition的externallyManagedConfigMembers中
                    beanDefinition.registerExternallyManagedConfigMember(member);
                    checkedElements.add(element);
                    if (logger.isTraceEnabled()) {
                        logger.trace("Registered injected element on class [" + this.targetClass.getName() + "]: " + element);
                    }
                }
            }
            this.checkedElements = checkedElements;
		}
        
     //@Resource对应的类
	private class ResourceElement extends LookupElement {
        
        
		public ResourceElement(Member member, AnnotatedElement ae, @Nullable PropertyDescriptor pd) {
			super(member, pd);
             //获取@Resource的name和type值
			Resource resource = ae.getAnnotation(Resource.class);
			String resourceName = resource.name();
			Class<?> resourceType = resource.type();
             //如果name没有值 代表使用字段名称
			this.isDefaultName = !StringUtils.hasLength(resourceName);
			if (this.isDefaultName) {
                  //使用字段名作为name
				resourceName = this.member.getName();
                  //如果是set方法 就使用set方法后面的名字当name
				if (this.member instanceof Method && resourceName.startsWith("set") && resourceName.length() > 3) {
					resourceName = Introspector.decapitalize(resourceName.substring(3));
				}
			}
             //如果指定了名字 需要进行占位符替换解析
			else if (embeddedValueResolver != null) {
				resourceName = embeddedValueResolver.resolveStringValue(resourceName);
			}
             //如果设置了type 就检查一下type
			if (Object.class != resourceType) {
				checkResourceType(resourceType);
			}
			else {
				// No resource type specified... check field/method.
				resourceType = getResourceType();
			}
             //设置name
			this.name = (resourceName != null ? resourceName : "");
             //设置查找类型
			this.lookupType = resourceType;
             //如果有lookup值 设置给mappedName 方便进行jndi
			String lookupValue = resource.lookup();
			this.mappedName = (StringUtils.hasLength(lookupValue) ? lookupValue : resource.mappedName());
             //如果包含@Lazy注解 获取值
			Lazy lazy = ae.getAnnotation(Lazy.class);
			this.lazyLookup = (lazy != null && lazy.value());
		}

    
    }

}
```

###### AutowiredAnnotationBeanPostProcessor

- 负责扫描`@Autowired、@Value、@Inject`的所有字段和方法，将其注册到beanDefinition的`externallyManagedConfigMembers`中

```java
public class AutowiredAnnotationBeanPostProcessor extends InstantiationAwareBeanPostProcessorAdapter
       implements MergedBeanDefinitionPostProcessor, PriorityOrdered, BeanFactoryAware {
    
    //初始化@Autowired、@Value、@Inject注解
	public AutowiredAnnotationBeanPostProcessor() {
		this.autowiredAnnotationTypes.add(Autowired.class);
		this.autowiredAnnotationTypes.add(Value.class);
		try {
			this.autowiredAnnotationTypes.add((Class<? extends Annotation>)
					ClassUtils.forName("javax.inject.Inject", AutowiredAnnotationBeanPostProcessor.class.getClassLoader()));
			logger.trace("JSR-330 'javax.inject.Inject' annotation found and supported for autowiring");
		}
		catch (ClassNotFoundException ex) {
			// JSR-330 API not available - simply skip.
		}
	}
    
    //扫描所有@Autowired、@Value、@Inject注解的字段和方法 加入到beanDefinition中去
    @Override
	public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
         //获取@Autowired、@Value、@Inject注解的字段和方法
		InjectionMetadata metadata = findAutowiringMetadata(beanName, beanType, null);
        //加入到beanDefinition中去
		metadata.checkConfigMembers(beanDefinition);
	}

    private InjectionMetadata buildAutowiringMetadata(final Class<?> clazz) {
		if (!AnnotationUtils.isCandidateClass(clazz, this.autowiredAnnotationTypes)) {
			return InjectionMetadata.EMPTY;
		}

		List<InjectionMetadata.InjectedElement> elements = new ArrayList<>();
		Class<?> targetClass = clazz;
		
        //遍历当前类和父类
		do {
			final List<InjectionMetadata.InjectedElement> currElements = new ArrayList<>();
			//遍历所有字段 找到@Autowired、@Value、@Inject注解的字段
			ReflectionUtils.doWithLocalFields(targetClass, field -> {
				MergedAnnotation<?> ann = findAutowiredAnnotation(field);
				if (ann != null) {
					if (Modifier.isStatic(field.getModifiers())) {
						if (logger.isInfoEnabled()) {
							logger.info("Autowired annotation is not supported on static fields: " + field);
						}
						return;
					}
					boolean required = determineRequiredStatus(ann);
					currElements.add(new AutowiredFieldElement(field, required));
				}
			});

             //遍历所有方法 找到@Autowired、@Value、@Inject注解的方法 不包括构造方法
			ReflectionUtils.doWithLocalMethods(targetClass, method -> {
				Method bridgedMethod = BridgeMethodResolver.findBridgedMethod(method);
				if (!BridgeMethodResolver.isVisibilityBridgeMethodPair(method, bridgedMethod)) {
					return;
				}
				MergedAnnotation<?> ann = findAutowiredAnnotation(bridgedMethod);
				if (ann != null && method.equals(ClassUtils.getMostSpecificMethod(method, clazz))) {
					if (Modifier.isStatic(method.getModifiers())) {
						if (logger.isInfoEnabled()) {
							logger.info("Autowired annotation is not supported on static methods: " + method);
						}
						return;
					}
					if (method.getParameterCount() == 0) {
						if (logger.isInfoEnabled()) {
							logger.info("Autowired annotation should only be used on methods with parameters: " +
									method);
						}
					}
					boolean required = determineRequiredStatus(ann);
					PropertyDescriptor pd = BeanUtils.findPropertyForMethod(bridgedMethod, clazz);
					currElements.add(new AutowiredMethodElement(method, required, pd));
				}
			});

			elements.addAll(0, currElements);
			targetClass = targetClass.getSuperclass();
		}
		while (targetClass != null && targetClass != Object.class);

        //返回封装好的InjectionMetadata
		return InjectionMetadata.forElements(elements, clazz);
	}
    
    
    public class InjectionMetadata {
        
        //遍历所有InjectionMetadata数据 注册到beanDefinition的externallyManagedConfigMembers中
    	public void checkConfigMembers(RootBeanDefinition beanDefinition) {
            Set<InjectedElement> checkedElements = new LinkedHashSet<>(this.injectedElements.size());
            //遍历InjectionMetadata数据 
            for (InjectedElement element : this.injectedElements) {
                Member member = element.getMember();
                if (!beanDefinition.isExternallyManagedConfigMember(member)) {
                    //注册到beanDefinition的externallyManagedConfigMembers中
                    beanDefinition.registerExternallyManagedConfigMember(member);
                    checkedElements.add(element);
                    if (logger.isTraceEnabled()) {
                        logger.trace("Registered injected element on class [" + this.targetClass.getName() + "]: " + element);
                    }
                }
            }
            this.checkedElements = checkedElements;
		}
   
    
    }


}
```





##### populateBean()

```java
public abstract class AbstractAutowireCapableBeanFactory extends AbstractBeanFactory
		implements AutowireCapableBeanFactory {

    //填充属性
	@SuppressWarnings("deprecation")  // for postProcessPropertyValues
	protected void populateBean(String beanName, RootBeanDefinition mbd, @Nullable BeanWrapper bw) {
		if (bw == null) {
			if (mbd.hasPropertyValues()) {
				throw new BeanCreationException(
						mbd.getResourceDescription(), beanName, "Cannot apply property values to null instance");
			}
			else {
				// Skip property population phase for null instance.
				return;
			}
		}

		// Give any InstantiationAwareBeanPostProcessors the opportunity to modify the
		// state of the bean before properties are set. This can be used, for example,
		// to support styles of field injection.
         //给InstantiationAwareBeanPostProcessor一个机会再去更改beanDefinition 在属性值注入之前
		if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
			for (BeanPostProcessor bp : getBeanPostProcessors()) {
				if (bp instanceof InstantiationAwareBeanPostProcessor) {
					InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
					if (!ibp.postProcessAfterInstantiation(bw.getWrappedInstance(), beanName)) {
						return;
					}
				}
			}
		}
		
        //获取beanDefinition的PropertyValues
		PropertyValues pvs = (mbd.hasPropertyValues() ? mbd.getPropertyValues() : null);
		
        //获取属性PropertyValues的注入模式 一般只有xml中才会有 这个设置跟注解的自动注入无关 只针对于PropertyValues
		int resolvedAutowireMode = mbd.getResolvedAutowireMode();
		if (resolvedAutowireMode == AUTOWIRE_BY_NAME || resolvedAutowireMode == AUTOWIRE_BY_TYPE) {
             //处理PropertyValues
			MutablePropertyValues newPvs = new MutablePropertyValues(pvs);
             //根据propertyName进行查找PropertyValues中的属性
			// Add property values based on autowire by name if applicable.
			if (resolvedAutowireMode == AUTOWIRE_BY_NAME) {
				autowireByName(beanName, mbd, bw, newPvs);
			}
            
             //根据类型进行查找PropertyValues中的属性
			// Add property values based on autowire by type if applicable.
			if (resolvedAutowireMode == AUTOWIRE_BY_TYPE) {
				autowireByType(beanName, mbd, bw, newPvs);
			}
             //将找到的PropertyValues设置回pvs
			pvs = newPvs;
		}
		
        //检查是否有InstantiationAwareBeanPostProcessor
		boolean hasInstAwareBpps = hasInstantiationAwareBeanPostProcessors();
		boolean needsDepCheck = (mbd.getDependencyCheck() != AbstractBeanDefinition.DEPENDENCY_CHECK_NONE);

         //开始进行注解属性注入
		PropertyDescriptor[] filteredPds = null;
		if (hasInstAwareBpps) {
			if (pvs == null) {
				pvs = mbd.getPropertyValues();
			}
             //遍历InstantiationAwareBeanPostProcessor
			for (BeanPostProcessor bp : getBeanPostProcessors()) {
				if (bp instanceof InstantiationAwareBeanPostProcessor) {
					InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
                      //处理属性填充
					PropertyValues pvsToUse = ibp.postProcessProperties(pvs, bw.getWrappedInstance(), beanName);
					if (pvsToUse == null) {
						if (filteredPds == null) {
							filteredPds = filterPropertyDescriptorsForDependencyCheck(bw, mbd.allowCaching);
						}
						pvsToUse = ibp.postProcessPropertyValues(pvs, filteredPds, bw.getWrappedInstance(), beanName);
						if (pvsToUse == null) {
							return;
						}
					}
					pvs = pvsToUse;
				}
			}
		}
		if (needsDepCheck) {
			if (filteredPds == null) {
				filteredPds = filterPropertyDescriptorsForDependencyCheck(bw, mbd.allowCaching);
			}
			checkDependencies(beanName, mbd, filteredPds, pvs);
		}

		if (pvs != null) {
			applyPropertyValues(beanName, mbd, bw, pvs);
		}
	}



}
```



###### CommonAnnotationBeanPostProcessor



```java
public class CommonAnnotationBeanPostProcessor extends InitDestroyAnnotationBeanPostProcessor
       implements InstantiationAwareBeanPostProcessor, BeanFactoryAware, Serializable {
    
    //包含@Resource、@WebServiceRef、@EJB
    private static final Set<Class<? extends Annotation>> resourceAnnotationTypes = new LinkedHashSet<>(4);

    
    //初始化要处理的注解@Resource、@WebServiceRef、@EJB
	static {
		webServiceRefClass = loadAnnotationType("javax.xml.ws.WebServiceRef");
		ejbClass = loadAnnotationType("javax.ejb.EJB");

		resourceAnnotationTypes.add(Resource.class);
		if (webServiceRefClass != null) {
			resourceAnnotationTypes.add(webServiceRefClass);
		}
		if (ejbClass != null) {
			resourceAnnotationTypes.add(ejbClass);
		}
	}
    
 
    public CommonAnnotationBeanPostProcessor() {
		setOrder(Ordered.LOWEST_PRECEDENCE - 3);
         //设置父类InitDestroyAnnotationBeanPostProcessor处理的注解
		setInitAnnotationType(PostConstruct.class);
		setDestroyAnnotationType(PreDestroy.class);
		ignoreResourceType("javax.xml.ws.WebServiceContext");
	}

    //处理@Resource、@WebServiceRef、@EJB注解的字段和方法
    @Override
	public PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName) {
         //获取之前缓存到injectionMetadataCache的beanName对应的InjectionMetadata
		InjectionMetadata metadata = findResourceMetadata(beanName, bean.getClass(), pvs);
		try {
             //处理@Resource、@WebServiceRef、@EJB注解
			metadata.inject(bean, beanName, pvs);
		}
		catch (Throwable ex) {
			throw new BeanCreationException(beanName, "Injection of resource dependencies failed", ex);
		}
		return pvs;
	}
    
    //获取InjectionMetadata
    private InjectionMetadata findResourceMetadata(String beanName, final Class<?> clazz, @Nullable PropertyValues pvs) {
		// Fall back to class name as cache key, for backwards compatibility with custom callers.
		String cacheKey = (StringUtils.hasLength(beanName) ? beanName : clazz.getName());
		// Quick check on the concurrent map first, with minimal locking.
        //从缓存中获取beanName对应的InjectionMetadata
		InjectionMetadata metadata = this.injectionMetadataCache.get(cacheKey);
		if (InjectionMetadata.needsRefresh(metadata, clazz)) {
			synchronized (this.injectionMetadataCache) {
				metadata = this.injectionMetadataCache.get(cacheKey);
				if (InjectionMetadata.needsRefresh(metadata, clazz)) {
					if (metadata != null) {
						metadata.clear(pvs);
					}
                      //重新扫描@Resource、@WebServiceRef、@EJB的字段和方法创建InjectionMetadata
					metadata = buildResourceMetadata(clazz);
					this.injectionMetadataCache.put(cacheKey, metadata);
				}
			}
		}
		return metadata;
	}
    
    public class InjectionMetadata {
          //遍历所有的带@Resource、@WebServiceRef、@EJB注解的字段和方法 属性注入
          public void inject(Object target, @Nullable String beanName, @Nullable PropertyValues pvs) throws Throwable {
            Collection<InjectedElement> checkedElements = this.checkedElements;
            Collection<InjectedElement> elementsToIterate =
                    (checkedElements != null ? checkedElements : this.injectedElements);
            if (!elementsToIterate.isEmpty()) {
                //遍历@Resource、@WebServiceRef、@EJB注解的字段和方法
                for (InjectedElement element : elementsToIterate) {
                    if (logger.isTraceEnabled()) {
                        logger.trace("Processing injected element of bean '" + beanName + "': " + element);
                    }
                    //属性注入
                    element.inject(target, beanName, pvs);
                }
            }
        }
        
        //内部类
        public abstract static class InjectedElement {
            //属性注入
            protected void inject(Object target, @Nullable String requestingBeanName, @Nullable PropertyValues pvs)
                    throws Throwable {
			   //如果要注入字段
                if (this.isField) {
                    Field field = (Field) this.member;
                    ReflectionUtils.makeAccessible(field);
                    //获取属性bean 将属性bean设置给实例
                    field.set(target, getResourceToInject(target, requestingBeanName));
                }
                //如果是要注入方法
                else {
                    if (checkPropertySkipping(pvs)) {
                        return;
                    }
                    try {
                        Method method = (Method) this.member;
                        ReflectionUtils.makeAccessible(method);
                        method.invoke(target, getResourceToInject(target, requestingBeanName));
                    }
                    catch (InvocationTargetException ex) {
                        throw ex.getTargetException();
                    }
                }
            }

        }
        
        private class ResourceElement extends LookupElement {
        	
            //获取@Resource注解标注属性/参数的bean
            @Override
            protected Object getResourceToInject(Object target, @Nullable String requestingBeanName) {
                //如果是懒加载 返回一个代理对象 否则直接获取bean
                return (this.lazyLookup ? buildLazyResourceProxy(this, requestingBeanName) :
                        getResource(this, requestingBeanName));
            }
        
        }

        
        
    }
    
    //获取@Resource的bean
	protected Object getResource(LookupElement element, @Nullable String requestingBeanName)
			throws NoSuchBeanDefinitionException {
		
        //如果有@Resource(lookup="")属性 去jndi中查找对象
		if (StringUtils.hasLength(element.mappedName)) {
			return this.jndiFactory.getBean(element.mappedName, element.lookupType);
		}
		if (this.alwaysUseJndiLookup) {
			return this.jndiFactory.getBean(element.name, element.lookupType);
		}
		if (this.resourceFactory == null) {
			throw new NoSuchBeanDefinitionException(element.lookupType,
					"No resource factory configured - specify the 'resourceFactory' property");
		}
         //通过容器查找bean
		return autowireResource(this.resourceFactory, element, requestingBeanName);
	}
    
	protected Object autowireResource(BeanFactory factory, LookupElement element, @Nullable String requestingBeanName)
			throws NoSuchBeanDefinitionException {

		Object resource;
		Set<String> autowiredBeanNames;
         //获取@Resource中的name属性
		String name = element.name;
		
         //当前容器是AutowireCapableBeanFactory类型
		if (factory instanceof AutowireCapableBeanFactory) {
			AutowireCapableBeanFactory beanFactory = (AutowireCapableBeanFactory) factory;
			DependencyDescriptor descriptor = element.getDependencyDescriptor();
             //是否回退到根据类型查找？
			if (this.fallbackToDefaultTypeMatch && element.isDefaultName && !factory.containsBean(name)) {
				autowiredBeanNames = new LinkedHashSet<>();
                 //根据类型查找bean
				resource = beanFactory.resolveDependency(descriptor, requestingBeanName, autowiredBeanNames, null);
				if (resource == null) {
					throw new NoSuchBeanDefinitionException(element.getLookupType(), "No resolvable resource object");
				}
			}
             //使用名称进行查找
			else {
				resource = beanFactory.resolveBeanByName(name, descriptor);
				autowiredBeanNames = Collections.singleton(name);
			}
		}
		else {
             //根据名称查找bean
			resource = factory.getBean(name, element.lookupType);
			autowiredBeanNames = Collections.singleton(name);
		}
		
        //注册属性bean和bean的依赖关系
		if (factory instanceof ConfigurableBeanFactory) {
			ConfigurableBeanFactory beanFactory = (ConfigurableBeanFactory) factory;
			for (String autowiredBeanName : autowiredBeanNames) {
				if (requestingBeanName != null && beanFactory.containsBean(autowiredBeanName)) {
					beanFactory.registerDependentBean(autowiredBeanName, requestingBeanName);
				}
			}
		}
		//返回属性bean
		return resource;
	}

    
    
}
```

###### AutowiredAnnotationBeanPostProcessor



```java
public class AutowiredAnnotationBeanPostProcessor extends InstantiationAwareBeanPostProcessorAdapter
       implements MergedBeanDefinitionPostProcessor, PriorityOrdered, BeanFactoryAware {
    
    //@Autowired、@Value、@Inject
    public AutowiredAnnotationBeanPostProcessor() {
		this.autowiredAnnotationTypes.add(Autowired.class);
		this.autowiredAnnotationTypes.add(Value.class);
		try {
			this.autowiredAnnotationTypes.add((Class<? extends Annotation>)
					ClassUtils.forName("javax.inject.Inject", AutowiredAnnotationBeanPostProcessor.class.getClassLoader()));
			logger.trace("JSR-330 'javax.inject.Inject' annotation found and supported for autowiring");
		}
		catch (ClassNotFoundException ex) {
			// JSR-330 API not available - simply skip.
		}
	}
	
    //处理@Autowired、@Value、@Inject注解的属性注入
	@Override
	public PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName) {
		InjectionMetadata metadata = findAutowiringMetadata(beanName, bean.getClass(), pvs);
		try {
			metadata.inject(bean, beanName, pvs);
		}
		catch (BeanCreationException ex) {
			throw ex;
		}
		catch (Throwable ex) {
			throw new BeanCreationException(beanName, "Injection of autowired dependencies failed", ex);
		}
		return pvs;
	}
    
    private class AutowiredFieldElement extends InjectionMetadata.InjectedElement {

		private final boolean required;

		private volatile boolean cached = false;

		@Nullable
		private volatile Object cachedFieldValue;

		public AutowiredFieldElement(Field field, boolean required) {
			super(field, null);
			this.required = required;
		}

        //属性注入
		@Override
		protected void inject(Object bean, @Nullable String beanName, @Nullable PropertyValues pvs) throws Throwable {
			Field field = (Field) this.member;
			Object value;
             //有缓存从缓存中获得
			if (this.cached) {
				value = resolvedCachedArgument(beanName, this.cachedFieldValue);
			}
             //否则就获取
			else {
                 //将字段做包装DependencyDescriptor
				DependencyDescriptor desc = new DependencyDescriptor(field, this.required);
				desc.setContainingClass(bean.getClass());
				Set<String> autowiredBeanNames = new LinkedHashSet<>(1);
				Assert.state(beanFactory != null, "No BeanFactory available");
				TypeConverter typeConverter = beanFactory.getTypeConverter();
				try {
                      //根据类型进行获取bean
					value = beanFactory.resolveDependency(desc, beanName, autowiredBeanNames, typeConverter);
				}
				catch (BeansException ex) {
					throw new UnsatisfiedDependencyException(null, beanName, new InjectionPoint(field), ex);
				}
				synchronized (this) {
					if (!this.cached) {
						if (value != null || this.required) {
							this.cachedFieldValue = desc;
							registerDependentBeans(beanName, autowiredBeanNames);
							if (autowiredBeanNames.size() == 1) {
								String autowiredBeanName = autowiredBeanNames.iterator().next();
								if (beanFactory.containsBean(autowiredBeanName) &&
										beanFactory.isTypeMatch(autowiredBeanName, field.getType())) {
									this.cachedFieldValue = new ShortcutDependencyDescriptor(
											desc, autowiredBeanName, field.getType());
								}
							}
						}
						else {
							this.cachedFieldValue = null;
						}
						this.cached = true;
					}
				}
			}
			if (value != null) {
				ReflectionUtils.makeAccessible(field);
                 //属性注入
				field.set(bean, value);
			}
		}
	}
    
    private class AutowiredMethodElement extends InjectionMetadata.InjectedElement {

		private final boolean required;

		private volatile boolean cached = false;

		@Nullable
		private volatile Object[] cachedMethodArguments;

		public AutowiredMethodElement(Method method, boolean required, @Nullable PropertyDescriptor pd) {
			super(method, pd);
			this.required = required;
		}
		//方法的参数注入
		@Override
		protected void inject(Object bean, @Nullable String beanName, @Nullable PropertyValues pvs) throws Throwable {
			if (checkPropertySkipping(pvs)) {
				return;
			}
			Method method = (Method) this.member;
			Object[] arguments;
             //有缓存走缓存
			if (this.cached) {
				// Shortcut for avoiding synchronization...
				arguments = resolveCachedArguments(beanName);
			}
			else {
				int argumentCount = method.getParameterCount();
				arguments = new Object[argumentCount];
				DependencyDescriptor[] descriptors = new DependencyDescriptor[argumentCount];
				Set<String> autowiredBeans = new LinkedHashSet<>(argumentCount);
				Assert.state(beanFactory != null, "No BeanFactory available");
				TypeConverter typeConverter = beanFactory.getTypeConverter();
                 //遍历方法上的所有参数
				for (int i = 0; i < arguments.length; i++) {
					MethodParameter methodParam = new MethodParameter(method, i);
					DependencyDescriptor currDesc = new DependencyDescriptor(methodParam, this.required);
					currDesc.setContainingClass(bean.getClass());
					descriptors[i] = currDesc;
					try {
                          //通过类型查找每个bean
						Object arg = beanFactory.resolveDependency(currDesc, beanName, autowiredBeans, typeConverter);
						if (arg == null && !this.required) {
							arguments = null;
							break;
						}
						arguments[i] = arg;
					}
					catch (BeansException ex) {
                          //如果方法参数有任意一个没有找到对应类型的bean 报错
						throw new UnsatisfiedDependencyException(null, beanName, new InjectionPoint(methodParam), ex);
					}
				}
				synchronized (this) {
					if (!this.cached) {
						if (arguments != null) {
							DependencyDescriptor[] cachedMethodArguments = Arrays.copyOf(descriptors, arguments.length);
							registerDependentBeans(beanName, autowiredBeans);
							if (autowiredBeans.size() == argumentCount) {
								Iterator<String> it = autowiredBeans.iterator();
								Class<?>[] paramTypes = method.getParameterTypes();
								for (int i = 0; i < paramTypes.length; i++) {
									String autowiredBeanName = it.next();
									if (beanFactory.containsBean(autowiredBeanName) &&
											beanFactory.isTypeMatch(autowiredBeanName, paramTypes[i])) {
										cachedMethodArguments[i] = new ShortcutDependencyDescriptor(
												descriptors[i], autowiredBeanName, paramTypes[i]);
									}
								}
							}
							this.cachedMethodArguments = cachedMethodArguments;
						}
						else {
							this.cachedMethodArguments = null;
						}
						this.cached = true;
					}
				}
			}
			if (arguments != null) {
				try {
                      //参数注入 调用方法
					ReflectionUtils.makeAccessible(method);
					method.invoke(bean, arguments);
				}
				catch (InvocationTargetException ex) {
					throw ex.getTargetException();
				}
			}
		}
	}



}
```



##### 依赖注入

###### 通过beanName获取依赖

```java
public abstract class AbstractAutowireCapableBeanFactory extends AbstractBeanFactory
       implements AutowireCapableBeanFactory {
    
    //通过beanName获取依赖的核心方法
    @Override
	public Object resolveBeanByName(String name, DependencyDescriptor descriptor) {
		InjectionPoint previousInjectionPoint = ConstructorResolver.setCurrentInjectionPoint(descriptor);
		try {
             //getBean通过beanName获取
			return getBean(name, descriptor.getDependencyType());
		}
		finally {
			ConstructorResolver.setCurrentInjectionPoint(previousInjectionPoint);
		}
	}
}
```



###### 通过类型获取依赖

```java
public class DefaultListableBeanFactory extends AbstractAutowireCapableBeanFactory
       implements ConfigurableListableBeanFactory, BeanDefinitionRegistry, Serializable {

    //根据指定的类型查找bean
	@Override
	@Nullable
	public Object resolveDependency(DependencyDescriptor descriptor, @Nullable String requestingBeanName,
			@Nullable Set<String> autowiredBeanNames, @Nullable TypeConverter typeConverter) throws BeansException {
		//设置参数名称发现器
		descriptor.initParameterNameDiscovery(getParameterNameDiscoverer());
        //如果指定的类型是Optional
		if (Optional.class == descriptor.getDependencyType()) {
			return createOptionalDependency(descriptor, requestingBeanName);
		}
         //如果指定的类型是ObjectFactory或ObjectProvider
		else if (ObjectFactory.class == descriptor.getDependencyType() ||
				ObjectProvider.class == descriptor.getDependencyType()) {
			return new DependencyObjectProvider(descriptor, requestingBeanName);
		}
         //如果指定的类型是Provider
		else if (javaxInjectProviderClass == descriptor.getDependencyType()) {
			return new Jsr330Factory().createDependencyProvider(descriptor, requestingBeanName);
		}
		else {
             //处理@Lazy @todo??
			Object result = getAutowireCandidateResolver().getLazyResolutionProxyIfNecessary(
					descriptor, requestingBeanName);
			if (result == null) {
                  //对指定类型查找bean
				result = doResolveDependency(descriptor, requestingBeanName, autowiredBeanNames, typeConverter);
			}
			return result;
		}
	}
    
    
    @Nullable
	public Object doResolveDependency(DependencyDescriptor descriptor, @Nullable String beanName,
			@Nullable Set<String> autowiredBeanNames, @Nullable TypeConverter typeConverter) throws BeansException {

		InjectionPoint previousInjectionPoint = ConstructorResolver.setCurrentInjectionPoint(descriptor);
		try {
             //快捷方式
			Object shortcut = descriptor.resolveShortcut(this);
			if (shortcut != null) {
				return shortcut;
			}
			//获取指定的类型
			Class<?> type = descriptor.getDependencyType();
             //如果当前要注入的属性或者参数前有@Value注解
			Object value = getAutowireCandidateResolver().getSuggestedValue(descriptor);
             //获取@Value注解的值
			if (value != null) {
                  //如果是@Value注解的值是字符串
				if (value instanceof String) {
                      //那么进行一下占位符的解析替换 ${}
					String strVal = resolveEmbeddedValue((String) value);
					BeanDefinition bd = (beanName != null && containsBean(beanName) ?
							getMergedBeanDefinition(beanName) : null);
                      //再进行一下spel的解析 #{}
					value = evaluateBeanDefinitionString(strVal, bd);
				}
                 //最后 看看类型是否匹配 如果不匹配尝试进行一下转换 有编辑器先用编辑器 没有用转换服务
				TypeConverter converter = (typeConverter != null ? typeConverter : getTypeConverter());
				try {
					return converter.convertIfNecessary(value, type, descriptor.getTypeDescriptor());
				}
				catch (UnsupportedOperationException ex) {
					// A custom TypeConverter which does not support TypeDescriptor resolution...
					return (descriptor.getField() != null ?
							converter.convertIfNecessary(value, type, descriptor.getField()) :
							converter.convertIfNecessary(value, type, descriptor.getMethodParameter()));
				}
			}
			//如果指定的类型是一个Stream、Collection、数组、Map类型 在这里解析
			Object multipleBeans = resolveMultipleBeans(descriptor, beanName, autowiredBeanNames, typeConverter);
			if (multipleBeans != null) {
				return multipleBeans;
			}
			//如果指定的类型是一个普通引用类型
			Map<String, Object> matchingBeans = findAutowireCandidates(beanName, type, descriptor);
             //如果没找到对应的bean 并且这个bean是required=true必须的
			if (matchingBeans.isEmpty()) {
				if (isRequired(descriptor)) {
                      //抛出找不到bean异常
					raiseNoMatchingBeanFound(type, descriptor.getResolvableType(), descriptor);
				}
				return null;
			}

			String autowiredBeanName;
			Object instanceCandidate;
			
             //如果查找到了多余1个匹配的bean
			if (matchingBeans.size() > 1) {
                 //通过@Primary注解筛选 和 通过@Priority比较筛选 如果这两个都没有 那么使用字段名称匹配
				autowiredBeanName = determineAutowireCandidate(matchingBeans, descriptor);
                 //如果没筛选出来
				if (autowiredBeanName == null) {
					//是必须的 抛出不唯一异常 
                      if (isRequired(descriptor) || !indicatesMultipleBeans(type)) {
						return descriptor.resolveNotUnique(descriptor.getResolvableType(), matchingBeans);
					}
					else {
						// In case of an optional Collection/Map, silently ignore a non-unique case:
						// possibly it was meant to be an empty collection of multiple regular beans
						// (before 4.3 in particular when we didn't even look for collection beans).
						return null;
					}
				}
                  //获取筛选的bean
				instanceCandidate = matchingBeans.get(autowiredBeanName);
			}
             //如果只有一个匹配的bean
			else {
				// We have exactly one match.
				Map.Entry<String, Object> entry = matchingBeans.entrySet().iterator().next();
                  //设置这个beanName和bean
				autowiredBeanName = entry.getKey();
				instanceCandidate = entry.getValue();
			}
			//添加到集合
			if (autowiredBeanNames != null) {
				autowiredBeanNames.add(autowiredBeanName);
			}
             //instanceCandidate的此时是个class对象 获取bean实例
			if (instanceCandidate instanceof Class) {
                  //调用beanFactory.getBean
				instanceCandidate = descriptor.resolveCandidate(autowiredBeanName, type, this);
			}
			Object result = instanceCandidate;
			if (result instanceof NullBean) {
				if (isRequired(descriptor)) {
					raiseNoMatchingBeanFound(type, descriptor.getResolvableType(), descriptor);
				}
				result = null;
			}
             //最后检查一下获取的类型和所需的类型是否相匹配 抛出类型不匹配异常
			if (!ClassUtils.isAssignableValue(type, result)) {
				throw new BeanNotOfRequiredTypeException(autowiredBeanName, type, instanceCandidate.getClass());
			}
             //返回结果
			return result;
		}
		finally {
			ConstructorResolver.setCurrentInjectionPoint(previousInjectionPoint);
		}
	}

    //如果指定的类型是一个Stream、Collection、数组、Map类型 解析
	@Nullable
	private Object resolveMultipleBeans(DependencyDescriptor descriptor, @Nullable String beanName,
			@Nullable Set<String> autowiredBeanNames, @Nullable TypeConverter typeConverter) {
		
         //获取指定的类型
		final Class<?> type = descriptor.getDependencyType();

        //如果指定的类型是一个Stream
		if (descriptor instanceof StreamDependencyDescriptor) {
             //查找Stream类型的bean
			Map<String, Object> matchingBeans = findAutowireCandidates(beanName, type, descriptor);
			if (autowiredBeanNames != null) {
				autowiredBeanNames.addAll(matchingBeans.keySet());
			}
             //再查找Stream中内置类型的bean
			Stream<Object> stream = matchingBeans.keySet().stream()
					.map(name -> descriptor.resolveCandidate(name, type, this))
					.filter(bean -> !(bean instanceof NullBean));
			if (((StreamDependencyDescriptor) descriptor).isOrdered()) {
				stream = stream.sorted(adaptOrderComparator(matchingBeans));
			}
			return stream;
		}
         //如果指定的类型是一个数组
		else if (type.isArray()) {
             //获取数组内置的类型
			Class<?> componentType = type.getComponentType();
			ResolvableType resolvableType = descriptor.getResolvableType();
			Class<?> resolvedArrayType = resolvableType.resolve(type);
			if (resolvedArrayType != type) {
				componentType = resolvableType.getComponentType().resolve();
			}
			if (componentType == null) {
				return null;
			}
             //查找内置类型的bean
			Map<String, Object> matchingBeans = findAutowireCandidates(beanName, componentType,
					new MultiElementDescriptor(descriptor));
			if (matchingBeans.isEmpty()) {
				return null;
			}
			if (autowiredBeanNames != null) {
				autowiredBeanNames.addAll(matchingBeans.keySet());
			}
             //将所有的bean转换成数组类型
			TypeConverter converter = (typeConverter != null ? typeConverter : getTypeConverter());
			Object result = converter.convertIfNecessary(matchingBeans.values(), resolvedArrayType);
			if (result instanceof Object[]) {
				Comparator<Object> comparator = adaptDependencyComparator(matchingBeans);
				if (comparator != null) {
					Arrays.sort((Object[]) result, comparator);
				}
			}
			return result;
		}
         //如果指定的类型是Collection
		else if (Collection.class.isAssignableFrom(type) && type.isInterface()) {
             //获取内置的类型
			Class<?> elementType = descriptor.getResolvableType().asCollection().resolveGeneric();
			if (elementType == null) {
				return null;
			}
             //查找内置类型的bean
			Map<String, Object> matchingBeans = findAutowireCandidates(beanName, elementType,
					new MultiElementDescriptor(descriptor));
			if (matchingBeans.isEmpty()) {
				return null;
			}
			if (autowiredBeanNames != null) {
				autowiredBeanNames.addAll(matchingBeans.keySet());
			}
             //转换成集合类型
			TypeConverter converter = (typeConverter != null ? typeConverter : getTypeConverter());
			Object result = converter.convertIfNecessary(matchingBeans.values(), type);
			if (result instanceof List) {
				if (((List<?>) result).size() > 1) {
					Comparator<Object> comparator = adaptDependencyComparator(matchingBeans);
					if (comparator != null) {
						((List<?>) result).sort(comparator);
					}
				}
			}
			return result;
		}
         //如果指定的类型是Map
		else if (Map.class == type) {
			ResolvableType mapType = descriptor.getResolvableType().asMap();
             //如果key不是String类型 直接返回
			Class<?> keyType = mapType.resolveGeneric(0);
			if (String.class != keyType) {
				return null;
			}
             //获取value的类型
			Class<?> valueType = mapType.resolveGeneric(1);
			if (valueType == null) {
				return null;
			}
             //查找指定value类型的bean
			Map<String, Object> matchingBeans = findAutowireCandidates(beanName, valueType,
					new MultiElementDescriptor(descriptor));
			if (matchingBeans.isEmpty()) {
				return null;
			}
			if (autowiredBeanNames != null) {
				autowiredBeanNames.addAll(matchingBeans.keySet());
			}
			return matchingBeans;
		}
         //不是上述的集合类型 返回null
		else {
			return null;
		}
	}

    //查找指定类型的所有bean
    protected Map<String, Object> findAutowireCandidates(
			@Nullable String beanName, Class<?> requiredType, DependencyDescriptor descriptor) {
		//获取指定类型的所有的beanName
		String[] candidateNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(
				this, requiredType, true, descriptor.isEager());
		Map<String, Object> result = new LinkedHashMap<>(candidateNames.length);
        
         //如果要获取的类型是ApplicationContext、ResourceLoader、ApplicationEventPublisher、BeanFactory类型
         //直接返回预先设置好的bean
		for (Map.Entry<Class<?>, Object> classObjectEntry : this.resolvableDependencies.entrySet()) {
			Class<?> autowiringType = classObjectEntry.getKey();
			if (autowiringType.isAssignableFrom(requiredType)) {
				Object autowiringValue = classObjectEntry.getValue();
				autowiringValue = AutowireUtils.resolveAutowiringValue(autowiringValue, requiredType);
				if (requiredType.isInstance(autowiringValue)) {
					result.put(ObjectUtils.identityToString(autowiringValue), autowiringValue);
					break;
				}
			}
		}
        //遍历指定类型的所有的beanName
		for (String candidate : candidateNames) {
             //如果不是一个自我引用 并且 如果有@Qualifier注解标注 筛选和@Qualifier的value一致的beanName
			if (!isSelfReference(beanName, candidate) && isAutowireCandidate(candidate, descriptor)) {
                 //将当前参数名称和获取的类型加入result
				addCandidateEntry(result, candidate, descriptor, requiredType);
			}
		}
		if (result.isEmpty()) {
			boolean multiple = indicatesMultipleBeans(requiredType);
			// Consider fallback matches if the first pass failed to find anything...
			DependencyDescriptor fallbackDescriptor = descriptor.forFallbackMatch();
			for (String candidate : candidateNames) {
				if (!isSelfReference(beanName, candidate) && isAutowireCandidate(candidate, fallbackDescriptor) &&
						(!multiple || getAutowireCandidateResolver().hasQualifier(descriptor))) {
					addCandidateEntry(result, candidate, descriptor, requiredType);
				}
			}
			if (result.isEmpty() && !multiple) {
				// Consider self references as a final pass...
				// but in the case of a dependency collection, not the very same bean itself.
				for (String candidate : candidateNames) {
					if (isSelfReference(beanName, candidate) &&
							(!(descriptor instanceof MultiElementDescriptor) || !beanName.equals(candidate)) &&
							isAutowireCandidate(candidate, fallbackDescriptor)) {
						addCandidateEntry(result, candidate, descriptor, requiredType);
					}
				}
			}
		}
		return result;
	}
    
    //获取bean
    public Object resolveCandidate(String beanName, Class<?> requiredType, BeanFactory beanFactory)
			throws BeansException {

		return beanFactory.getBean(beanName);
	}

    //从众多类型相匹配的bean中选择@Primary或者@Priority优先级高的 
    //都没有的话 默认使用字段名称匹配
	@Nullable
	protected String determineAutowireCandidate(Map<String, Object> candidates, DependencyDescriptor descriptor) {
		Class<?> requiredType = descriptor.getDependencyType();
		String primaryCandidate = determinePrimaryCandidate(candidates, requiredType);
		if (primaryCandidate != null) {
			return primaryCandidate;
		}
		String priorityCandidate = determineHighestPriorityCandidate(candidates, requiredType);
		if (priorityCandidate != null) {
			return priorityCandidate;
		}
		// Fallback: pick directly registered dependency or qualified bean name match
		for (Map.Entry<String, Object> entry : candidates.entrySet()) {
			String candidateName = entry.getKey();
			Object beanInstance = entry.getValue();
			if ((beanInstance != null && this.resolvableDependencies.containsValue(beanInstance)) ||
					matchesBeanName(candidateName, descriptor.getDependencyName())) {
				return candidateName;
			}
		}
		return null;
	}

}
```





##### initializeBean()



```java
protected Object initializeBean(final String beanName, final Object bean, @Nullable RootBeanDefinition mbd) {
    if (System.getSecurityManager() != null) {
       AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
          invokeAwareMethods(beanName, bean);
          return null;
       }, getAccessControlContext());
    }
    else {
       //对实现了BeanNameAware、BeanClassLoaderAware、BeanFactoryAware接口的bean进行处理
       invokeAwareMethods(beanName, bean);
    }

    Object wrappedBean = bean;
    if (mbd == null || !mbd.isSynthetic()) {
       //遍历所有的BeanPostProcessor 调用postProcessBeforeInitialization
       wrappedBean = applyBeanPostProcessorsBeforeInitialization(wrappedBean, beanName);
    }

    try {
       //如果bean实现了InitializingBean接口或者有initMethodName
       invokeInitMethods(beanName, wrappedBean, mbd);
    }
    catch (Throwable ex) {
       throw new BeanCreationException(
             (mbd != null ? mbd.getResourceDescription() : null),
             beanName, "Invocation of init method failed", ex);
    }
    if (mbd == null || !mbd.isSynthetic()) {
        //遍历所有的BeanPostProcessor 调用applyBeanPostProcessorsAfterInitialization
       wrappedBean = applyBeanPostProcessorsAfterInitialization(wrappedBean, beanName);
    }

    return wrappedBean;
}
```

###### invokeAwareMethods

```java
//对实现了BeanNameAware、BeanClassLoaderAware、BeanFactoryAware接口的bean进行处理
private void invokeAwareMethods(final String beanName, final Object bean) {
    if (bean instanceof Aware) {
       if (bean instanceof BeanNameAware) {
          ((BeanNameAware) bean).setBeanName(beanName);
       }
       if (bean instanceof BeanClassLoaderAware) {
          ClassLoader bcl = getBeanClassLoader();
          if (bcl != null) {
             ((BeanClassLoaderAware) bean).setBeanClassLoader(bcl);
          }
       }
       if (bean instanceof BeanFactoryAware) {
          ((BeanFactoryAware) bean).setBeanFactory(AbstractAutowireCapableBeanFactory.this);
       }
    }
}
```

###### applyBeanPostProcessorsBeforeInitialization

```java
//调用beanPostProcessor的postProcessBeforeInitialization
@Override
public Object applyBeanPostProcessorsBeforeInitialization(Object existingBean, String beanName)
       throws BeansException {

    //遍历所有的BeanPostProcessor 调用postProcessBeforeInitialization
    Object result = existingBean;
    for (BeanPostProcessor processor : getBeanPostProcessors()) {
       Object current = processor.postProcessBeforeInitialization(result, beanName);
       if (current == null) {
          return result;
       }
       result = current;
    }
    return result;
}
```

###### ApplicationContextAwareProcessor

```java
class ApplicationContextAwareProcessor implements BeanPostProcessor {
    
    //对实现了EnvironmentAware、EmbeddedValueResolverAware、ResourceLoaderAware、ApplicationEventPublisherAware、MessageSourceAware、ApplicationContextAware接口的bean进行处理
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



###### InitDestroyAnnotationBeanPostProcessor

```java
public class InitDestroyAnnotationBeanPostProcessor
       implements DestructionAwareBeanPostProcessor, MergedBeanDefinitionPostProcessor, PriorityOrdered, Serializable {
    
    //调用@PostConstruct标注的方法
	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		LifecycleMetadata metadata = findLifecycleMetadata(bean.getClass());
		try {
			metadata.invokeInitMethods(bean, beanName);
		}
		catch (InvocationTargetException ex) {
			throw new BeanCreationException(beanName, "Invocation of init method failed", ex.getTargetException());
		}
		catch (Throwable ex) {
			throw new BeanCreationException(beanName, "Failed to invoke init method", ex);
		}
		return bean;
	}

}
```

###### invokeInitMethods

```java
protected void invokeInitMethods(String beanName, final Object bean, @Nullable RootBeanDefinition mbd)
       throws Throwable {

    //如果bean实现了InitializingBean接口
    boolean isInitializingBean = (bean instanceof InitializingBean);
    //并且没有执行过afterPropertiesSet方法
    if (isInitializingBean && (mbd == null || !mbd.isExternallyManagedInitMethod("afterPropertiesSet方法"))) {
       if (logger.isTraceEnabled()) {
          logger.trace("Invoking afterPropertiesSet() on bean with name '" + beanName + "'");
       }
       if (System.getSecurityManager() != null) {
          try {
             AccessController.doPrivileged((PrivilegedExceptionAction<Object>) () -> {
                ((InitializingBean) bean).afterPropertiesSet();
                return null;
             }, getAccessControlContext());
          }
          catch (PrivilegedActionException pae) {
             throw pae.getException();
          }
       }
       else {
           //调用afterPropertiesSet方法
          ((InitializingBean) bean).afterPropertiesSet();
       }
    }

    //如果bean设置了initMethodName
    if (mbd != null && bean.getClass() != NullBean.class) {
       String initMethodName = mbd.getInitMethodName();
       //并且不是afterPropertiesSet和执行过的方法
       if (StringUtils.hasLength(initMethodName) &&
             !(isInitializingBean && "afterPropertiesSet".equals(initMethodName)) &&
             !mbd.isExternallyManagedInitMethod(initMethodName)) {
           //调用initMethod执行
          invokeCustomInitMethod(beanName, bean, mbd);
       }
    }
}
```



###### applyBeanPostProcessorsAfterInitialization

```java
//处理BeanPostProcessor的postProcessAfterInitialization
@Override
public Object applyBeanPostProcessorsAfterInitialization(Object existingBean, String beanName)
       throws BeansException {

    Object result = existingBean;
    for (BeanPostProcessor processor : getBeanPostProcessors()) {
       Object current = processor.postProcessAfterInitialization(result, beanName);
       if (current == null) {
          return result;
       }
       result = current;
    }
    return result;
}
```

###### ApplicationListenerDetector

```java

//如果bean属于ApplicationListener 加入事件多播器中
@Override
public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (bean instanceof ApplicationListener) {
       // potentially not detected as a listener by getBeanNamesForType retrieval
       Boolean flag = this.singletonNames.get(beanName);
       if (Boolean.TRUE.equals(flag)) {
          // singleton bean (top-level or inner): register on the fly
          this.applicationContext.addApplicationListener((ApplicationListener<?>) bean);
       }
       else if (Boolean.FALSE.equals(flag)) {
          if (logger.isWarnEnabled() && !this.applicationContext.containsBean(beanName)) {
             // inner bean with other scope - can't reliably process events
             logger.warn("Inner bean '" + beanName + "' implements ApplicationListener interface " +
                   "but is not reachable for event multicasting by its containing ApplicationContext " +
                   "because it does not have singleton scope. Only top-level listener beans are allowed " +
                   "to be of non-singleton scope.");
          }
          this.singletonNames.remove(beanName);
       }
    }
    return bean;
}
```



## @Lookup和MethodReplacer

<img src="./images/image-20250319152055989.png" alt="image-20250319152055989" style="zoom:50%;" />

**原理流程**

- 在进行实例化时，即`createBeanInstance`会调用`determineConstructorsFromBeanPostProcessors`方法
- 其中实现了`SmartInstantiationAwareBeanPostProcessor`的类`AutowiredAnnotationBeanPostProcessor`对`@Lookup`标注的方法进行了处理，将`@Lookup`信息`LookupOverride`放入`beanDefinition`的`MethodOverrides`属性中
- 当进行实例化时，`CglibSubclassingInstantiationStrategy`实例化策略会根据是否含有`MethodOverrides`来决定是否生成代理对象
- 对于包含`LookupOverride`或者`ReplaceOverride`的`beanDefinition`，会生成一个CGlib的代理实例，此实例包含了`LookupOverrideMethodInterceptor`和`ReplaceOverrideMethodInterceptor`的拦截器
- 当调用一个代理实例的任意方法时，会先通过`MethodOverrideCallbackFilter`来判断当前方法是否需要被拦截，对应`LookupOverride`的方法会调用`LookupOverrideMethodInterceptor`进行拦截，对应`ReplaceOverride`的方法会调用`ReplaceOverrideMethodInterceptor`进行拦截
- `LookupOverrideMethodInterceptor`拦截器根据`@Lookup`的注解信息用beanName获取bean或者根据返回类型获取bean，并返回结果
- `ReplaceOverrideMethodInterceptor`拦截器找到对应实现了`MethodReplacer`的bean，调用`reimplement`返回结果

### @Lookup

`@Lookup`注解用于替换指定方法的返回值为容器中的指定bean

更多用于单例对象引用多例对象时，每次调用方法都可以生成一个新的spring管理的对象

```java
//常规情况下 抽象类并不能被加入容器中，但是由于@Lookup注解，容器中就可以存在当前beanName -> 代理对象
@Component
public abstract class MyLookup {

    //返回beanName为myLookupObject的bean实例对象
    @Lookup("myLookupObject")
    public abstract MyLookupObject getMyLookupObject();
}

@Component
public class MyLookupObject {
}

```

#### LookupOverride

```JAVA
public class LookupOverride extends MethodOverride {
    //要替换返回的bean的name
    @Nullable
	private final String beanName;
	//@Lookup标注的方法
	@Nullable
	private Method method;
    
    public LookupOverride(String methodName, @Nullable String beanName) {
		super(methodName);
		this.beanName = beanName;
	}
}
```





### MethodReplacer

MethodReplacer用于指定替换某个方法 

```java
@Component
public class MyMethodReplacer implements MethodReplacer {
    @Override
    public Object reimplement(Object obj, Method method, Object[] args) throws Throwable {
        System.out.println("替换增强");
        return new Object();
    }
}

@Component
class MyBeanFactoryPostProcessor implements BeanFactoryPostProcessor {
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        //找到要被替换的bean
        GenericBeanDefinition beanDefinition = (GenericBeanDefinition) beanFactory.getBeanDefinition("myMethod");
        //设置要替换的方法和对应的MethodReplacer
        MethodOverride methodOverride = new ReplaceOverride("myMethod", "myMethodReplacer");
        //加入MethodOverrides
        beanDefinition.getMethodOverrides().addOverride(methodOverride);
    }
}
```

#### ReplaceOverride

```java
public class ReplaceOverride extends MethodOverride {

    //对应的MethodReplacer的beanName
    private final String methodReplacerBeanName;

    private List<String> typeIdentifiers = new LinkedList<>();
    
    public ReplaceOverride(String methodName, String methodReplacerBeanName) {
		super(methodName);
		Assert.notNull(methodName, "Method replacer bean name must not be null");
		this.methodReplacerBeanName = methodReplacerBeanName;
	}
}
```



## 创建Bean的几种方式

![image-20231211090141402](./images/image-20231211090141402.png)

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

![image-20231211105324341](./images/image-20231211105324341.png)

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
        if (beanClass.equals(MyCommponet.class)){
            return new MyComponet("zcq");
        }
        return null;
    }
}

@Component
public class MyComponet {

    private String value;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public MyComponet() {
    }

    public MyComponet(String value) {
        this.value = value;
    }
}
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
@FunctionalInterface
public interface Supplier<T> {

    /**
     * Gets a result.
     *
     * @return a result
     */
    T get();
}
```

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



## 实例化策略

<img src="./images/image-20250319150517457.png" alt="image-20250319150517457" style="zoom: 33%;" />

### SimpleInstantiationStrategy

简单实例化策略负责三部分

1. 使用默认构造方法进行实例化，如果有`MethodOverrides`那么调用子类`CglibSubclassingInstantiationStrategy`进行代理实例化
2. 使用指定构造方法进行实例化，如果有`MethodOverrides`那么调用子类`CglibSubclassingInstantiationStrategy`进行代理实例化
3. 使用`FactoryMethod`进行实例化

```java
public class SimpleInstantiationStrategy implements InstantiationStrategy {

    private static final ThreadLocal<Method> currentlyInvokedFactoryMethod = new ThreadLocal<>();
    
    
    //使用默认构造方法进行实例化，如果有MethodOverrides那么调用子类CglibSubclassingInstantiationStrategy进行代理实例化
	@Override
	public Object instantiate(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner) {
         //如果没有MethodOverrides 
		// Don't override the class with CGLIB if no overrides.
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
                               //获取默认的构造方法
							constructorToUse = clazz.getDeclaredConstructor();
						}
						bd.resolvedConstructorOrFactoryMethod = constructorToUse;
					}
					catch (Throwable ex) {
						throw new BeanInstantiationException(clazz, "No default constructor found", ex);
					}
				}
			}
             //使用默认构造方法进行实例化
			return BeanUtils.instantiateClass(constructorToUse);
		}
		else {
			// Must generate CGLIB subclass.
			return instantiateWithMethodInjection(bd, beanName, owner);
		}
	}
    
    //使用指定构造方法进行实例化，如果有MethodOverrides那么调用子类CglibSubclassingInstantiationStrategy进行代理实例化
    @Override
	public Object instantiate(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner,
			final Constructor<?> ctor, Object... args) {
		//如果没有MethodOverrides 
		if (!bd.hasMethodOverrides()) {
			if (System.getSecurityManager() != null) {
				// use own privileged to change accessibility (when security is on)
				AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
					ReflectionUtils.makeAccessible(ctor);
					return null;
				});
			}
             //那么使用指定的构造方法进行实例化
			return BeanUtils.instantiateClass(ctor, args);
		}
		else {
             //调用子类CglibSubclassingInstantiationStrategy进行代理实例化
			return instantiateWithMethodInjection(bd, beanName, owner, ctor, args);
		}
	}
    
    //使用FactoryMethod进行实例化
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

    //提供给子类CglibSubclassingInstantiationStrategy实现的代理实例化接口
    protected Object instantiateWithMethodInjection(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner) {
		throw new UnsupportedOperationException("Method Injection not supported in SimpleInstantiationStrategy");
	}
    
    //提供给子类CglibSubclassingInstantiationStrategy实现的代理实例化接口
    protected Object instantiateWithMethodInjection(RootBeanDefinition bd, @Nullable String beanName,
			BeanFactory owner, @Nullable Constructor<?> ctor, Object... args) {

		throw new UnsupportedOperationException("Method Injection not supported in SimpleInstantiationStrategy");
	}


}
```

### CglibSubclassingInstantiationStrategy

`CglibSubclassingInstantiationStrategy`是`SimpleInstantiationStrategy`的子类，负责代理对象的实例化工作

```java
public class CglibSubclassingInstantiationStrategy extends SimpleInstantiationStrategy {
    
	private static final int PASSTHROUGH = 0;
	private static final int LOOKUP_OVERRIDE = 1;
    private static final int METHOD_REPLACER = 2;
    
    //继承CGLIB代理实例化的方法
    @Override
	protected Object instantiateWithMethodInjection(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner) {
		return instantiateWithMethodInjection(bd, beanName, owner, null);
	}
    
	//继承CGLIB代理实例化的方法
	@Override
	protected Object instantiateWithMethodInjection(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner,
			@Nullable Constructor<?> ctor, Object... args) {

		// Must generate CGLIB subclass...
		return new CglibSubclassCreator(bd, owner).instantiate(ctor, args);
	}

}
```

#### CglibSubclassCreator

`CglibSubclassCreator`作为`CglibSubclassingInstantiationStrategy`的内部类，负责CGlib代理实例化工作

```java
private static class CglibSubclassCreator {
    
     	//拦截器类型数组 
		private static final Class<?>[] CALLBACK_TYPES = new Class<?>[]
				{NoOp.class, LookupOverrideMethodInterceptor.class, ReplaceOverrideMethodInterceptor.class};
    
		//当前要代理的beanDefinition
		private final RootBeanDefinition beanDefinition;
		//当前容器
		private final BeanFactory owner;

		CglibSubclassCreator(RootBeanDefinition beanDefinition, BeanFactory owner) {
			this.beanDefinition = beanDefinition;
			this.owner = owner;
		}
    
    	//创建代理并实例化
    	public Object instantiate(@Nullable Constructor<?> ctor, Object... args) {
             //利用CGlib创建当前beanDefinition的代理类
			Class<?> subclass = createEnhancedSubclass(this.beanDefinition);
			Object instance;
             //如果没有指定的构造器
			if (ctor == null) {
                 //使用默认构造器创建代理类对象
				instance = BeanUtils.instantiateClass(subclass);
			}
			else {
				try {
                      //获取代理类中指定的构造器 使用指定的构造器创建代理类
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
			Factory factory = (Factory) instance;
             //设置拦截器实例对象到代理实例中
			factory.setCallbacks(new Callback[] {NoOp.INSTANCE,
					new LookupOverrideMethodInterceptor(this.beanDefinition, this.owner),
					new ReplaceOverrideMethodInterceptor(this.beanDefinition, this.owner)});
             //返回创建好的代理类实例
			return instance;
		}
	
		//使用CGLIB生成指定beanDefinition的代理类
		private Class<?> createEnhancedSubclass(RootBeanDefinition beanDefinition) {
			Enhancer enhancer = new Enhancer();
             //设置继承的类
			enhancer.setSuperclass(beanDefinition.getBeanClass());
             //设置命名策略BySpringCGLIB
			enhancer.setNamingPolicy(SpringNamingPolicy.INSTANCE);
			if (this.owner instanceof ConfigurableBeanFactory) {
				ClassLoader cl = ((ConfigurableBeanFactory) this.owner).getBeanClassLoader();
				enhancer.setStrategy(new ClassLoaderAwareGeneratorStrategy(cl));
			}
             //设置主拦截器 决定使用哪个拦截器 自定义逻辑来指定调用的callback下标
			enhancer.setCallbackFilter(new MethodOverrideCallbackFilter(beanDefinition));
             //设置拦截器类型数组
			enhancer.setCallbackTypes(CALLBACK_TYPES);
			return enhancer.createClass();
		}
    
}
```

##### 代理的命名生成策略

<img src="./images/image-20250319153900779.png" alt="image-20250319153900779" style="zoom:33%;" />

###### DefaultNamingPolicy

```java
public class DefaultNamingPolicy implements NamingPolicy {
    public static final DefaultNamingPolicy INSTANCE = new DefaultNamingPolicy();
    private static final boolean STRESS_HASH_CODE = Boolean.getBoolean("org.springframework.cglib.test.stressHashCodes");

    public DefaultNamingPolicy() {
    }

    public String getClassName(String prefix, String source, Object key, Predicate names) {
        if (prefix == null) {
            prefix = "org.springframework.cglib.empty.Object";
        } else if (prefix.startsWith("java")) {
            //如果是java开头的类 前面加个$
            prefix = "$" + prefix;
        }
		//生成com.zcq.demo.getbean.lookup.MyMethod$$EnhancerBySpringCGLIB$$616fc504的全类名
        //实际类名是MyMethod$$EnhancerBySpringCGLIB$$616fc504
        String base = prefix + "$$" + source.substring(source.lastIndexOf(46) + 1) + this.getTag() + "$$" + Integer.toHexString(STRESS_HASH_CODE ? 0 : key.hashCode());
        String attempt = base;

        for(int index = 2; names.evaluate(attempt); attempt = base + "_" + index++) {
        }

        return attempt;
    }

    protected String getTag() {
        return "ByCGLIB";
    }

    public int hashCode() {
        return this.getTag().hashCode();
    }

    public boolean equals(Object o) {
        return o instanceof DefaultNamingPolicy && ((DefaultNamingPolicy)o).getTag().equals(this.getTag());
    }
}
```

###### SpringNamingPolicy

```java
public class SpringNamingPolicy extends DefaultNamingPolicy {

    public static final SpringNamingPolicy INSTANCE = new SpringNamingPolicy();

    //生成com.zcq.demo.getbean.lookup.MyMethod$$EnhancerBySpringCGLIB$$616fc504的全类名
    //相当于在com.zcq.demo.getbean.lookup包下创建了一个名为MyMethod$$EnhancerBySpringCGLIB$$616fc504的类 继承了MyMethod
    @Override
    protected String getTag() {
       return "BySpringCGLIB";
    }

}
```

#### MethodOverrideCallbackFilter

```java
private static class MethodOverrideCallbackFilter extends CglibIdentitySupport implements CallbackFilter {

    private static final Log logger = LogFactory.getLog(MethodOverrideCallbackFilter.class);

    public MethodOverrideCallbackFilter(RootBeanDefinition beanDefinition) {
       super(beanDefinition);
    }

    //主拦截器 通过判断当前方法是否需要被覆盖来选择不同的拦截器
    @Override
    public int accept(Method method) {
       //获取当前方法的MethodOverride信息
       MethodOverride methodOverride = getBeanDefinition().getMethodOverrides().getOverride(method);
       if (logger.isTraceEnabled()) {
          logger.trace("MethodOverride for " + method + ": " + methodOverride);
       }
       //当前方法不需要被覆盖 跳过拦截
       if (methodOverride == null) {
          return PASSTHROUGH;
       }
       //当前方法需要被LookupOverrideMethodInterceptor拦截 调用LookupOverrideMethodInterceptor
       else if (methodOverride instanceof LookupOverride) {
          return LOOKUP_OVERRIDE;
       }
       //当前方法需要被ReplaceOverrideMethodInterceptor拦截 调用ReplaceOverrideMethodInterceptor
       else if (methodOverride instanceof ReplaceOverride) {
          return METHOD_REPLACER;
       }
       throw new UnsupportedOperationException("Unexpected MethodOverride subclass: " +
             methodOverride.getClass().getName());
    }
}
```

#### LookupOverrideMethodInterceptor

```java
private static class LookupOverrideMethodInterceptor extends CglibIdentitySupport implements MethodInterceptor {

    private final BeanFactory owner;

    public LookupOverrideMethodInterceptor(RootBeanDefinition beanDefinition, BeanFactory owner) {
       super(beanDefinition);
       this.owner = owner;
    }
	//拦截@Lookup标注的方法
    @Override
    public Object intercept(Object obj, Method method, Object[] args, MethodProxy mp) throws Throwable {
       // Cast is safe, as CallbackFilter filters are used selectively.
       //获取LookupOverride的信息
       LookupOverride lo = (LookupOverride) getBeanDefinition().getMethodOverrides().getOverride(method);
       Assert.state(lo != null, "LookupOverride not found");
       Object[] argsToUse = (args.length > 0 ? args : null);  // if no-arg, don't insist on args at all
       //判断@Lookup注解上是否设置了beanName
       if (StringUtils.hasText(lo.getBeanName())) {
          //设置了beanName 获取指定beanName的bean 返回
          return (argsToUse != null ? this.owner.getBean(lo.getBeanName(), argsToUse) :
                this.owner.getBean(lo.getBeanName()));
       }
       else {
          //没有beanName 根据返回值获取bean 返回
          return (argsToUse != null ? this.owner.getBean(method.getReturnType(), argsToUse) :
                this.owner.getBean(method.getReturnType()));
       }
    }
}
```

#### ReplaceOverrideMethodInterceptor

```java
private static class ReplaceOverrideMethodInterceptor extends CglibIdentitySupport implements MethodInterceptor {

    private final BeanFactory owner;

    public ReplaceOverrideMethodInterceptor(RootBeanDefinition beanDefinition, BeanFactory owner) {
       super(beanDefinition);
       this.owner = owner;
    }

    //拦截被MethodReplacer覆盖的方法
    @Override
    public Object intercept(Object obj, Method method, Object[] args, MethodProxy mp) throws Throwable {
       //获取ReplaceOverride信息
       ReplaceOverride ro = (ReplaceOverride) getBeanDefinition().getMethodOverrides().getOverride(method);
       Assert.state(ro != null, "ReplaceOverride not found");
       // TODO could cache if a singleton for minor performance optimization
       //获取对应的MethodReplacer对象
       MethodReplacer mr = this.owner.getBean(ro.getMethodReplacerBeanName(), MethodReplacer.class);
       //调用reimplement方法替换当前方法
       return mr.reimplement(obj, method, args);
    }
}
```







## 循环依赖

- 创建对象实例后，在未进行初始化前，将包含对象实例的代理表达式方法`ObjectFactory`放入三级缓存中
- 当出现获取正在创建的bean时，从一、二级缓存中取，如果没有则调用三级缓存中的`ObjectFactory`，如果bean需要被代理，则返回代理后的bean，如果不需要，则直接返回正在创建的bean
- 将正在创建的bean的引用或者代理对象返回，完成循环依赖的解决

```java
	protected Object doCreateBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)
			throws BeanCreationException {

		// Instantiate the bean.
		BeanWrapper instanceWrapper = null;
		if (mbd.isSingleton()) {
			instanceWrapper = this.factoryBeanInstanceCache.remove(beanName);
		}
		if (instanceWrapper == null) {
			instanceWrapper = createBeanInstance(beanName, mbd, args);
		}
		Object bean = instanceWrapper.getWrappedInstance();
		Class<?> beanType = instanceWrapper.getWrappedClass();
		if (beanType != NullBean.class) {
			mbd.resolvedTargetType = beanType;
		}

		// Allow post-processors to modify the merged bean definition.
		synchronized (mbd.postProcessingLock) {
			if (!mbd.postProcessed) {
				try {
					applyMergedBeanDefinitionPostProcessors(mbd, beanType, beanName);
				}
				catch (Throwable ex) {
					throw new BeanCreationException(mbd.getResourceDescription(), beanName,
							"Post-processing of merged bean definition failed", ex);
				}
				mbd.markAsPostProcessed();
			}
		}

        
		// Eagerly cache singletons to be able to resolve circular references
		// even when triggered by lifecycle interfaces like BeanFactoryAware.
		boolean earlySingletonExposure = (mbd.isSingleton() && this.allowCircularReferences &&
				isSingletonCurrentlyInCreation(beanName));
		if (earlySingletonExposure) {
			if (logger.isTraceEnabled()) {
				logger.trace("Eagerly caching bean '" + beanName +
						"' to allow for resolving potential circular references");
			}
            //在此将未填充属性的bean的表达式加入三级缓存中
			addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
		}

		// Initialize the bean instance.
		Object exposedObject = bean;
		try {
			populateBean(beanName, mbd, instanceWrapper);
			exposedObject = initializeBean(beanName, exposedObject, mbd);
		}
		catch (Throwable ex) {
			if (ex instanceof BeanCreationException bce && beanName.equals(bce.getBeanName())) {
				throw bce;
			}
			else {
				throw new BeanCreationException(mbd.getResourceDescription(), beanName, ex.getMessage(), ex);
			}
		}

		if (earlySingletonExposure) {
			Object earlySingletonReference = getSingleton(beanName, false);
			if (earlySingletonReference != null) {
				if (exposedObject == bean) {
					exposedObject = earlySingletonReference;
				}
				else if (!this.allowRawInjectionDespiteWrapping && hasDependentBean(beanName)) {
					String[] dependentBeans = getDependentBeans(beanName);
					Set<String> actualDependentBeans = new LinkedHashSet<>(dependentBeans.length);
					for (String dependentBean : dependentBeans) {
						if (!removeSingletonIfCreatedForTypeCheckOnly(dependentBean)) {
							actualDependentBeans.add(dependentBean);
						}
					}
					if (!actualDependentBeans.isEmpty()) {
						throw new BeanCurrentlyInCreationException(beanName,
								"Bean with name '" + beanName + "' has been injected into other beans [" +
								StringUtils.collectionToCommaDelimitedString(actualDependentBeans) +
								"] in its raw version as part of a circular reference, but has eventually been " +
								"wrapped. This means that said other beans do not use the final version of the " +
								"bean. This is often the result of over-eager type matching - consider using " +
								"'getBeanNamesForType' with the 'allowEagerInit' flag turned off, for example.");
					}
				}
			}
		}

		// Register bean as disposable.
		try {
			registerDisposableBeanIfNecessary(beanName, bean, mbd);
		}
		catch (BeanDefinitionValidationException ex) {
			throw new BeanCreationException(
					mbd.getResourceDescription(), beanName, "Invalid destruction signature", ex);
		}

		return exposedObject;
	}

```





### DefaultSingletonBeanRegistry

```JAVA
public class DefaultSingletonBeanRegistry extends SimpleAliasRegistry implements SingletonBeanRegistry {


    //一级缓存
	/** Cache of singleton objects: bean name to bean instance. */
	private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);

    //三级缓存
	/** Cache of singleton factories: bean name to ObjectFactory. */
	private final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<>(16);

    //二级缓存
	/** Cache of early singleton objects: bean name to bean instance. */
	private final Map<String, Object> earlySingletonObjects = new HashMap<>(16);


    //将当前未填充属性的bean的代理表达式放入三级缓存中
    protected void addSingletonFactory(String beanName, ObjectFactory<?> singletonFactory) {
		Assert.notNull(singletonFactory, "Singleton factory must not be null");
		synchronized (this.singletonObjects) {
			if (!this.singletonObjects.containsKey(beanName)) {
                  //放入三级缓存
				this.singletonFactories.put(beanName, singletonFactory);
				this.earlySingletonObjects.remove(beanName);
				this.registeredSingletons.add(beanName);
			}
		}
	}
    
    
    
    //核心 在获取bean时 首先从缓存中获得
    @Nullable
	protected Object getSingleton(String beanName, boolean allowEarlyReference) {
		// Quick check for existing instance without full singleton lock
        //从一级缓存中获取
		Object singletonObject = this.singletonObjects.get(beanName);
         //一级缓存没有 并且要获取的bean正在创建中
		if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
             //从二级缓存中获取
			singletonObject = this.earlySingletonObjects.get(beanName);
             //二级缓存也没用 允许提前暴露
			if (singletonObject == null && allowEarlyReference) {
				synchronized (this.singletonObjects) {
					// Consistent creation of early reference within full singleton lock
					singletonObject = this.singletonObjects.get(beanName);
					if (singletonObject == null) {
						singletonObject = this.earlySingletonObjects.get(beanName);
						if (singletonObject == null) {
                              //从三级缓存中获得表达式
							ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
							if (singletonFactory != null) {
                                   //调用方法 获取提前暴露的bean
								singletonObject = singletonFactory.getObject();
                                   //放入二级缓存 移出三级缓存
								this.earlySingletonObjects.put(beanName, singletonObject);
								this.singletonFactories.remove(beanName);
							}
						}
					}
				}
			}
		}
		return singletonObject;
	}


}
```

### AbstractAutowireCapableBeanFactory

```java
public abstract class AbstractAutowireCapableBeanFactory extends AbstractBeanFactory
       implements AutowireCapableBeanFactory {
    
    //三级缓存中的表达式 用于获取bean的代理对象 如果不需要代理 直接返回未填充属性的bean
    protected Object getEarlyBeanReference(String beanName, RootBeanDefinition mbd, Object bean) {
		Object exposedObject = bean;
		if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
			for (SmartInstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().smartInstantiationAware) {
				exposedObject = bp.getEarlyBeanReference(exposedObject, beanName);
			}
		}
		return exposedObject;
	}
}
```

## IOC流程

以`AnnotationConfigApplicationContext`的创建为例

- 创建`AnnotationConfigApplicationContext`，负责`beanDefinition`的扫描注册以及内部`bean`的创建注册工作
  - 创建`PathMatchingResourcePatternResolver`作为资源匹配加载器，内置`AntPathMatcher`使用`Ant`风格进行资源路径匹配，使用类加载器`classLoader`的双亲委派加载核心类库`jre/lib`下、扩展类库`jre/lib/ext`以及`classpath`下的类和资源文件。
  - 创建`DefaultListableBeanFactory`负责管理创建`bean`和`beanDefinition`
  - 创建`StandardEnvironment`装载`systemEnvironment`和`systemProperties`的`key-value`属性以及`.yml .properties`文件`key-value`属性，内置`PropertySourcesPropertyResolver`负责利用`environment`中的`key-value`属性解析替换`#{user.name}`占位符的值
  - 创建`AnnotationBeanNameGenerator`作为默认的`beanName`生成策略，要么使用指定的`@Component(value="xxx")`，要么使用类名首字母小写。
  - 创建`ConfigurationClassPostProcessor、AutowiredAnnotationBeanPostProcessor、CommonAnnotationBeanPostProcessor`，注册到容器中
- 扫描`basePackages`下的类
  - 将`basePackage`封装成`Ant`风格`classpath*:com/zcq/demo/getbean/autowired/**/*.class`，利用类加载器加载资源`cl.getResources(path)`筛选匹配路径的资源
  - 读取类文件，筛选匹配`@Component、@ManagedBean、@Named`注解标注的类，计算`@Conditional`满足条件的，不是接口和抽象类的或者抽象类带有`@Lookup`注解的，被封装成`ScannedGenericBeanDefinition`加入候选`beanDefinition`集合
  - 处理候选`beanDefinition`的`@Scope`注解，设置`singleton`或者`prototype`，利用`AnnotationBeanNameGenerator`生成`beanName`
  - 处理候选`beanDefinition`注解`@Lazy、@Primary、@DependsOn、@Role、@Description`，设置到`beanDefinition`的属性中
  - 将处理完的`beanDefinition`注册到容器`beanFactory`中
- 设置`AnnotationConfigApplicationContext`的刷新时间、状态
- 设置`beanFactory`的`spel`表达式解析器、默认的属性编辑器注册器、处理`Aware`接口的`BeanPostProcessor`，注册`environment、systemProperties、systemEnvironment`到容器`beanFactory`中
- 处理执行`BeanDefinitionRegistryPostProcessor、BeanFactoryPostProcessor`，调用`postProcessBeanDefinitionRegistry`和`postProcessBeanFactory`
  - `ConfigurationClassPostProcessor`执行`postProcessBeanDefinitionRegistry`时，扫描所有的`beanDefinition`，对包含`@Configuration`或者`@Component、@ComponentScan、@Import、@ImportResource、@Bean`注解的加入候选处理集合
  - 依据`@Order`对候选处理集合排序，循环解析候选类，首先判断`@Conditional`注解的条件是否满足
    - 对于带有`@Component`注解的类，递归处理其内部类的`@Component、@ComponentScan、@Import、@ImportResource、@Bean、@PropertySource`注解
    - 对于带有`@PropertySource、@PropertySources`注解的类，解析替换指定的文件路径占位符和`name`，加载指定的文件到`Environment`中
    - 对于带有`@ComponentScan、@ComponentScans`注解的类，计算`@Conditional`的条件，扫描并注册指定的`basePackages`，迭代处理新扫描进入容器的`@Component、@ComponentScan、@Import、@ImportResource、@Bean、@PropertySource`注解
    - 对于带有`@Import`注解的类，递归扫描收集当前类及其父类所有导入的类
      - 如果导入的类属于`ImportSelector`，如果属于`DeferredImportSelector`加入集合稍后处理，否则调用`selectImports`导入类，递归处理导入类的`@Import`注解
      - 如果导入的类属于`ImportBeanDefinitionRegistrar`，加入集合稍后处理
      - 如果导入的类不属于上述两种类型，那么去递归处理它身上的`@Component、@ComponentScan、@Import、@ImportResource、@Bean、@PropertySource`注解，将此类加入到`this.configurationClasses`，稍后加入到容器中
    - 对于带有`@ImportResource`注解的类，读取`locations`的值加入到集合中，稍后进行处理
    - 对于带有`@Bean`注解的类，读取`locations`的值加入到集合中，稍后进行处理
    - 处理下接口的默认方法是否带有`@Bean`注解，加入集合稍后处理
    - 递归父类
    - 处理`DeferredImportSelector`类型的类
  - 上述处理完成的类，处理需要导入到容器的类
    - 循环所有处理完成的类，首先判断`@Conditional`注解的条件是否满足
    - 如果当前类是被导入进来的，那么将这个类注册到容器中
    - 如果当前类的`beanMethod`集合不为空，循环遍历，首先判断方法上的`@Conditional`注解的条件是否满足
      - 解析`@Bean`的属性`name`确定`beanName`，没有的话使用方法名作为`beanName`，创建`ConfigurationClassBeanDefinition`
      - 如果是静态方法，设置`factoryMethodName`为此方法名称，设置当前类为`beanClass` （稍后使用静态工厂的创建方法创建）
      - 如果不是静态方法，设置`factoryMethodName`为此方法名称，设置当前类为`factoryBeanName`（稍后使用实例工厂的创建方法创建）
      - 处理`@Lazy、@Primary、@DependsOn、@Role、@Description`，设置到`beanDefinition`的属性中，解析设置`@Bean`的属性值，注册到容器中
    - 如果当前类的`importedResources`集合不为空，循环遍历，以`xml`方式或者`.groovy`方式读取文件导入`beanDefinition`
    - 如果当前类`importBeanDefinitionRegistrars`集合不为空，遍历调用`registerBeanDefinitions`注册`beanDefinition`
  - 递归所有未处理的`beanDefinition`
- 遍历`beanFactory`中的所有`BeanPostProcessor`，实例化创建`BeanPostProcessor`
- 设置定义国际化 创建`messageSource`的对象
- 创建注册`ApplicationEventMulticaster`
- 注册所有的`ApplicationListener`到`ApplicationEventMulticaster`，发布`earlyApplicationEvents`
- 循环遍历所有`beanDefinition`，合并父子`beanDefinition`，将所有的`GenericBeanDefinition`变成`RootBeanDefinition`，对于非抽象的、单例的、非懒加载的`beanDefinition`
  - 如果当前类是`FactoryBean`，在容器中创建名为`beanName`的实例，如果设置了`isEagerInit`，那么创建调用`beanName`对应`getObject`的实例，存放在`factoryBeanObjectCache`中
  - 如果当前类不是`FactoryBean`，正常创建创建名为`beanName`的实例，`getBean()->doGetBean()`
    - 转换别名，去掉`&`前缀，从一级缓存中取，如果有实例，针对`FatoryBean`做处理，如果获取的名字中包含`&`，返回`FatoryBean`的实例，否则调用`getObject`，创建`beanName`的实例存放在`factoryBeanObjectCache`中
    - 有父容器，优先让父容器进行创建
    - 标记创建中，合并父子`beanDefinition`，获取`@DependsOn`依赖的`beanName`，优先创建
    - `createBean()`
    - 解析获得`beanDefinition`的`class`对象，设置`beanClass`
    - 如果有`MethodOverides`的话进行检查
    - 如果有`InstantiationAwareBeanPostProcessor`，可以使用`applyBeanPostProcessorsBeforeInstantiation`创建`bean`实例，并使用`applyBeanPostProcessorsAfterInitialization`处理实例，返回实例
    - `doCreateBean()`
    - `createBeanInstance()`
      - 如果有`Supplier`，调用`beanDefinition`设置的`Supplier`创建`bean`实例
      - 如果有`FactoryMethod`
        - 如果是实例工厂，获取实例工厂对象
        - 如果是静态工厂，获取静态工厂类
        - 获取所有重载的工厂方法，如果只有一个工厂方法并且没有构造参数`constructorArgumentValues`，直接调用当前工厂方法实例化
        - 如果有多个工厂方法和构造参数，需要遍历工厂方法，将构造参数转换成对应工厂方法参数列表的类型，没有构造参数的需要调用`beanFactory.resolveDependency()`来根据类型进行获取`bean`设置参数列表
        - 比较权重值筛选出参数列表类型和构造参数最匹配的工厂方法，调用工厂方法和参数进行实例化
      - 获取缓存中已经解析到的参数和构造方法，如果获取到，直接使用参数和构造方法进行实例化
      - 如果有`InstantiationAwareBeanPostProcessor`，使用`determineCandidateConstructors`获取决定构造方法
        - `AutowiredAnnotationBeanPostProcessor`在这里负责`@Lookup`注解的处理和`@Autowired`标注构造方法的收集
        - 遍历此类及其父类所有方法，获取`@Lookup`标注的方法封装`LookupOverride`加入`MethodOverrides`
        - 遍历所有构造方法，收集所有`@Autowired`标注的构造方法，如果`@Autowired(required=true)`，不允许其他任何`@Autowired`构造方法存在，`@Autowired(required=false)`可以共存
        - 将收集到的构造方法返回
      - 如果有选中的构造方法、有构造参数`constructorArgumentValues`那么就筛选选中的构造方法来实例化
        - 获取所有重载的构造方法，如果只有一个默认构造方法并且没有构造参数`constructorArgumentValues`，直接调用默认构造方法实例化
        - 如果有多个构造方法和构造参数，需要遍历构造方法，将构造参数转换成对应构造方法参数列表的类型，没有构造参数的需要调用`beanFactory.resolveDependency()`来根据类型进行获取`bean`设置参数列表
        - 比较权重值筛选出参数列表类型和构造参数最匹配的构造方法，调用构造方法和参数进行实例化
      - 没有选中的构造方法和构造参数，调用默认的构造方法进行实例化
        - 实例化时，如果没有`MethodOverrides`，使用默认的构造方法直接实例化
        - 否则创建当前类的CGlib代理子类，设置`LookupOverrideMethodInterceptor、ReplaceOverrideMethodInterceptor`来处理调用`Lookup`和`ReplaceMethod`标注的方法时，做覆盖处理
    - 处理所有的`MergedBeanDefinitionPostProcessor`，调用`postProcessMergedBeanDefinition`
      - `InitDestroyAnnotationBeanPostProcessor`查找所有的方法，将`@PostConstruct、@PreDestroy`标注的方法封装为`LifecycleMetadata`，设置到`beanDefinition`的`externallyManagedInitMethods`和`externallyManagedDestroyMethods`中
      - `CommonAnnotationBeanPostProcessor`查找所有的属性和方法，将`@Resource、@EJB、@WebServiceRef`标注的方法封装为`InjectionMetadata`，设置到`beanDefinition`的`externallyManagedConfigMembers`中，方法的参数数量只能是1个
      - `AutowiredAnnotationBeanPostProcessor`查找所有的属性和方法，将`@Autowired、@Value、@Inject`标注的方法封装为`InjectionMetadata`，设置到`beanDefinition`的`externallyManagedConfigMembers`中
    - `populateBean()`
      - 如果有`InstantiationAwareBeanPostProcessor`，执行`postProcessAfterInstantiation`
      - 如果有`propertyValues`，对`pvs`进行`AUTOWIRE_BY_NAME`和`AUTOWIRE_BY_TYPE`的属性填充解析
      - 如果有`InstantiationAwareBeanPostProcessor`，执行`postProcessProperties`，对`@Resource、@EJB、@WebServiceRef、@Autowired、@Value、@Inject`标注的字段和方法做属性填充
        - `CommonAnnotationBeanPostProcessor`负责将上述封装的`@Resource、@EJB、@WebServiceRef`进行属性和方法注入
          - 遍历所有`InjectionMetadata`，如果是字段，如果是懒加载，，否则如果允许类型匹配并且容器中不包含`beanName`的实例，尝试使用类型匹配`beanFactory.resolveDependency()`，正常使用`beanFactory.resolveBeanByName(beanName)`即`getBean(beanName)`
          - 如果是方法，如果是懒加载，，否则如果允许类型匹配并且容器中不包含`beanName`的实例，尝试使用类型匹配`beanFactory.resolveDependency()`，正常使用`beanFactory.resolveBeanByName(beanName)`即`getBean(beanName)`
        - `AutowiredAnnotationBeanPostProcessor`负责上述封装的`@Autowired、@Value、@Inject`进行属性和方法注入
          - 遍历所有`InjectionMetadata`，如果是字段
            - 使用`beanFactory.resolveDependency()`进行类型匹配，如果是懒加载，创建代理对象返回
              - 获取注解中的`value`值，在`@Autowired、@Value、@Inject`中只有`@Value`有`value`值，对`value`值利用`environment`进行占位符解析替换，如果是`spel`表达式，再利用`spel`解析器计算一下，最后将值转换成字段一致的类型。
              - 如果类型是`Stream、Collection、数组、Map类型`，对内置类型进行单一类型注入查找，返回集合
              - 如果类型是单一类型，获取容器中指定类型的所有`beanDefinition`，根据`@Qualifier`的值匹配，返回匹配的`beanName`
              - 根据`@Primary`和`@Priority`筛选`beanName`
              - 通过`getBean(beanName)`获取bean，检查bean类型是否匹配，返回bean
          - 如果是方法，
            - 遍历参数列表，使用`beanFactory.resolveDependency()`进行类型匹配，如果是懒加载，创建代理对象返回
    - `initializeBean()`
      - 对实现了`BeanNameAware、BeanClassLoaderAware、BeanFactoryAware`接口的bean进行处理
      - 调用`beanPostProcessor`的`postProcessBeforeInitialization`
        - `ApplicationContextAwareProcessor`对实现了`EnvironmentAware、EmbeddedValueResolverAware、ResourceLoaderAware、ApplicationEventPublisherAware、MessageSourceAware、ApplicationContextAware`接口的bean进行处理
        - `InitDestroyAnnotationBeanPostProcessor`调用`@PostConstruct`标注的方法
      - `invokeInitMethods()`
        - 如果bean实现了`InitializingBean`接口，调用`afterPropertiesSet`方法
        - 如果bean设置了`initMethodName`，调用`initMethod`执行
      - 处理`BeanPostProcessor`的`postProcessAfterInitialization`
        - `ApplicationListenerDetector`如果bean属于ApplicationListener 加入事件多播器中
- 循环所有创建好的`bean` 如果属于`SmartInitializingSingleton` 调用`afterSingletonsInstantiated`方法







## AOP

### @EnableAspectJAutoProxy

![image-20240110095544889](./images/image-20240110095544889.png)

不论是xml方式还是注解方式 都需要注册一个AspectAutoProxyCreator 而xml注册的是

![image-20240110095705893](./images/image-20240110095705893.png)

xml是在解析<aop>标签的时候进行注册的 

而在注解情况下 想要@Aspect等注解生效 就需要使用@EnableAspectJAutoProxy 注册

![image-20240110095817749](./images/image-20240110095817749.png)

也就是说 不论使用任何方式 都需要先创建AspectAutoProxyCreator 

![image-20240110100526692](./images/image-20240110100526692.png)

- 如果是纯xml文件来进行aop的代理工作 那么就采用config标签 内部会注册AspectJAwareAdvisorAutoProxyCreator 这个类来处理 

- 如果是xml+注解搭配的形式 那么就采用 config标签+aspectj-autoproxy标签 内部会注册 AspectJAwareAdvisorAutoProxyCreator 这个类和AnnotationAwareAspectJAutoProxyCreator这两个类 但是beanDefinitionMap的key都是org.springframework.aop.config.internalAutoProxyCreator 这个name 所以第二个会替换第一个 只剩下AnnotationAwareAspectJAutoProxyCreator

  ![image-20240110101210570](./images/image-20240110101210570.png)

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

![image-20240117174717970](./images/image-20240117174717970.png)



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

<img src="./images/image-20240131094507649.png" alt="image-20240131094507649" style="zoom: 80%;" />

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

![image-20240119090132037](./images/image-20240119090132037.png)

![image-20240119090154559](./images/image-20240119090154559.png)

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

![image-20240119153747485](./images/image-20240119153747485.png)

![image-20240119153333007](./images/image-20240119153333007.png)

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

创建advisor对象的工作，实际上是在`AnnotationAwareAspectJAutoProxyCreator`里面完成，而`AnnotationAwareAspectJAutoProxyCreator`继承了`BeanPostProcessor`

![image-20240119142311855](./images/image-20240119142311855.png)

实际处理的地方在`doGetBean()->createBean()->resolveBeforeInstantiation()`

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

![image-20240119170300972](./images/image-20240119170300972.png)

这里有几个对象需要进行说明，在创建advisor的beanDefinition的时候，分为BeanDefinition类型的参数和RuntimeBeanReference类型，其中MethodLocatingFactoryBean、SimpleBeanFactoryAwareAspectInstanceFactory为BeanDefinition，而AspectJExpressionPointcut为RuntimeBeanReference，RuntimeBeanReference指向的是一个在运行时的bean对象，一般有beanDefinition存放在beanFactory的beanDefinitionMap中，而这里指的BeanDefinition一般没有在Map中，生成的也就是innerBean打头的bean，不会存放在beanFactory的一级缓存中。



`InstantiationModelAwarePointcutAdvisorImpl`中包含`PointcutAdvisor`和`Advice`

<img src="./images/image-20250407141645657.png" alt="image-20250407141645657" style="zoom: 33%;" />

<img src="./images/image-20250407141431961.png" alt="image-20250407141431961" style="zoom:33%;" />

##### 注解方式

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
                    //遍历所有的beanName
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
                        //判断当前bean是否是Aspect切面 是否包含@Aspect注解
						if (this.advisorFactory.isAspect(beanType)) {
                               //加入切面集合
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
		//在当前方法上去找是否有@Pointcut、@Around、@Before、@After、@AfterReturning、@AfterThrowing的注解 
        AspectJAnnotation<?> aspectJAnnotation =
				AbstractAspectJAdvisorFactory.findAspectJAnnotationOnMethod(candidateAdviceMethod);
		//如果没找到 返回null
        if (aspectJAnnotation == null) {
			return null;
		}
		//如果找到了 那么就封装一个AspectJExpressionPointcut对象
		AspectJExpressionPointcut ajexp =
				new AspectJExpressionPointcut(candidateAspectClass, new String[0], new Class<?>[0]);
        //把注解的切入点表达式传进去 这里给的可能是“myPointCut()”这样的表达式
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
        //找到目标对象的所有advisor 封装成数组 即被切入点表达式覆盖的
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
             //获取回调的类型
			Class<?>[] types = new Class<?>[callbacks.length];
			for (int x = 0; x < types.length; x++) {
				types[x] = callbacks[x].getClass();
			}
             //设置拦截过滤器和回调类型
			// fixedInterceptorMap only populated at this point, after getCallbacks call above
			enhancer.setCallbackFilter(new ProxyCallbackFilter(
					this.advised.getConfigurationOnlyCopy(), this.fixedInterceptorMap, this.fixedInterceptorOffset));
			enhancer.setCallbackTypes(types);

             //创建代理对象
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
        //新建一个DynamicAdvisedInterceptor对象作为aop拦截器 this.advised中包含advisors对象
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

<img src="./images/image-20250407145614868.png" alt="image-20250407145614868" style="zoom:50%;" />

```java
//生成的代理对象
public class MyComponet$$SpringCGLIB$$0 extends MyComponet implements SpringProxy, Advised, Factory {
    private boolean CGLIB$BOUND;
    public static Object CGLIB$FACTORY_DATA;
    private static final ThreadLocal CGLIB$THREAD_CALLBACKS;
    private static final Callback[] CGLIB$STATIC_CALLBACKS;
    private MethodInterceptor CGLIB$CALLBACK_0;
    private MethodInterceptor CGLIB$CALLBACK_1;
    private NoOp CGLIB$CALLBACK_2;
    private Dispatcher CGLIB$CALLBACK_3;
    private Dispatcher CGLIB$CALLBACK_4;
    private MethodInterceptor CGLIB$CALLBACK_5;
    private MethodInterceptor CGLIB$CALLBACK_6;
    private static Object CGLIB$CALLBACK_FILTER;
    private static final Method CGLIB$test$0$Method;
    private static final MethodProxy CGLIB$test$0$Proxy;
    private static final Object[] CGLIB$emptyArgs;
    private static final Method CGLIB$equals$1$Method;
    private static final MethodProxy CGLIB$equals$1$Proxy;
    private static final Method CGLIB$toString$2$Method;
    private static final MethodProxy CGLIB$toString$2$Proxy;
    private static final Method CGLIB$hashCode$3$Method;
    private static final MethodProxy CGLIB$hashCode$3$Proxy;
    private static final Method CGLIB$clone$4$Method;
    private static final MethodProxy CGLIB$clone$4$Proxy;

	//在生成代理类时，通过CallbackFillter来决定使用CGLIB$CALLBACK_*来拦截对应方法
    //test方法使用CGLIB$CALLBACK_0拦截 即DynamicUnadvisedInterceptor
    public final void test() {
        MethodInterceptor var10000 = this.CGLIB$CALLBACK_0;
        if (var10000 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_0;
        }

        if (var10000 != null) {
            //将当前代理对象 被代理方法 参数 代理方法
            var10000.intercept(this, CGLIB$test$0$Method, CGLIB$emptyArgs, CGLIB$test$0$Proxy);
        } else {
            super.test();
        }
    }
}
```



##### CglibAopProxy

```java
class CglibAopProxy implements AopProxy, Serializable {

    //用于处理aop的方法拦截器
	private static class DynamicAdvisedInterceptor implements MethodInterceptor, Serializable {
		
        //代理的advisor对象
		private final AdvisedSupport advised;

		public DynamicAdvisedInterceptor(AdvisedSupport advised) {
			this.advised = advised;
		}

        //处理aop
		@Override
		@Nullable
		public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
			Object oldProxy = null;
			boolean setProxyContext = false;
			Object target = null;
			TargetSource targetSource = this.advised.getTargetSource();
			try {
                 //是否暴露代理对象
				if (this.advised.exposeProxy) {
					// Make invocation available if necessary.
					oldProxy = AopContext.setCurrentProxy(proxy);
					setProxyContext = true;
				}
				// Get as late as possible to minimize the time we "own" the target, in case it comes from a pool...
                 //获取目标对象
				target = targetSource.getTarget();
				Class<?> targetClass = (target != null ? target.getClass() : null);
                 //根据当前方法来获取对应的Advisor调用链
				List<Object> chain = this.advised.getInterceptorsAndDynamicInterceptionAdvice(method, targetClass);
				Object retVal;
				// Check whether we only have one InvokerInterceptor: that is,
				// no real advice, but just reflective invocation of the target.
				if (chain.isEmpty()) {
					// We can skip creating a MethodInvocation: just invoke the target directly.
					// Note that the final invoker must be an InvokerInterceptor, so we know
					// it does nothing but a reflective operation on the target, and no hot
					// swapping or fancy proxying.
					Object[] argsToUse = AopProxyUtils.adaptArgumentsIfNecessary(method, args);
					retVal = AopUtils.invokeJoinpointUsingReflection(target, method, argsToUse);
				}
				else {
                     //调用拦截器链
					// We need to create a method invocation...
					retVal = new CglibMethodInvocation(proxy, target, method, args, targetClass, chain, methodProxy).proceed();
				}
				return processReturnType(proxy, target, method, args, retVal);
			}
			finally {
				if (target != null && !targetSource.isStatic()) {
					targetSource.releaseTarget(target);
				}
				if (setProxyContext) {
					// Restore old proxy.
					AopContext.setCurrentProxy(oldProxy);
				}
			}
		}

		@Override
		public boolean equals(@Nullable Object other) {
			return (this == other ||
					(other instanceof DynamicAdvisedInterceptor dynamicAdvisedInterceptor &&
							this.advised.equals(dynamicAdvisedInterceptor.advised)));
		}

		/**
		 * CGLIB uses this to drive proxy creation.
		 */
		@Override
		public int hashCode() {
			return this.advised.hashCode();
		}
	}

	//封装调用advice链的对象
	private static class CglibMethodInvocation extends ReflectiveMethodInvocation {

		public CglibMethodInvocation(Object proxy, @Nullable Object target, Method method,
				Object[] arguments, @Nullable Class<?> targetClass,
				List<Object> interceptorsAndDynamicMethodMatchers, MethodProxy methodProxy) {

			super(proxy, target, method, arguments, targetClass, interceptorsAndDynamicMethodMatchers);
		}

		@Override
		@Nullable
		public Object proceed() throws Throwable {
			try {
                  //调用ReflectiveMethodInvocation的proceed方法
				return super.proceed();
			}
			catch (RuntimeException ex) {
				throw ex;
			}
			catch (Exception ex) {
				if (ReflectionUtils.declaresException(getMethod(), ex.getClass()) ||
						KotlinDetector.isKotlinType(getMethod().getDeclaringClass())) {
					// Propagate original exception if declared on the target method
					// (with callers expecting it). Always propagate it for Kotlin code
					// since checked exceptions do not have to be explicitly declared there.
					throw ex;
				}
				else {
					// Checked exception thrown in the interceptor but not declared on the
					// target method signature -> apply an UndeclaredThrowableException,
					// aligned with standard JDK dynamic proxy behavior.
					throw new UndeclaredThrowableException(ex);
				}
			}
		}
	}


}
```



##### DefaultAdvisorChainFactory

```java
public class DefaultAdvisorChainFactory implements AdvisorChainFactory, Serializable {

    //获取Advisor调用链
    @Override
	public List<Object> getInterceptorsAndDynamicInterceptionAdvice(
			Advised config, Method method, @Nullable Class<?> targetClass) {

		// This is somewhat tricky... We have to process introductions first,
		// but we need to preserve order in the ultimate list.
		AdvisorAdapterRegistry registry = GlobalAdvisorAdapterRegistry.getInstance();
		Advisor[] advisors = config.getAdvisors();
		List<Object> interceptorList = new ArrayList<>(advisors.length);
		Class<?> actualClass = (targetClass != null ? targetClass : method.getDeclaringClass());
		Boolean hasIntroductions = null;

        //遍历所有的advisors对象
		for (Advisor advisor : advisors) {
			if (advisor instanceof PointcutAdvisor pointcutAdvisor) {
				// Add it conditionally.
                 //确定当前类是否匹配切入点表达式
				if (config.isPreFiltered() || pointcutAdvisor.getPointcut().getClassFilter().matches(actualClass)) {
					MethodMatcher mm = pointcutAdvisor.getPointcut().getMethodMatcher();
					boolean match;
                      //确定当前方法是否匹配切入点表达式
					if (mm instanceof IntroductionAwareMethodMatcher iamm) {
						if (hasIntroductions == null) {
							hasIntroductions = hasMatchingIntroductions(advisors, actualClass);
						}
						match = iamm.matches(method, actualClass, hasIntroductions);
					}
					else {
						match = mm.matches(method, actualClass);
					}
                      //如果类和当前方法都匹配切入点表达式
					if (match) {
                          //获取当前advice对象
						MethodInterceptor[] interceptors = registry.getInterceptors(advisor);
						if (mm.isRuntime()) {
							// Creating a new object instance in the getInterceptors() method
							// isn't a problem as we normally cache created chains.
							for (MethodInterceptor interceptor : interceptors) {
								interceptorList.add(new InterceptorAndDynamicMethodMatcher(interceptor, mm));
							}
						}
						else {
                               //加入集合中
							interceptorList.addAll(Arrays.asList(interceptors));
						}
					}
				}
			}
			else if (advisor instanceof IntroductionAdvisor ia) {
				if (config.isPreFiltered() || ia.getClassFilter().matches(actualClass)) {
					Interceptor[] interceptors = registry.getInterceptors(advisor);
					interceptorList.addAll(Arrays.asList(interceptors));
				}
			}
			else {
				Interceptor[] interceptors = registry.getInterceptors(advisor);
				interceptorList.addAll(Arrays.asList(interceptors));
			}
		}
		//返回当前的advice链
		return interceptorList;
	}


}
```



##### ReflectiveMethodInvocation

```java
//advice链的封装调用类
public class ReflectiveMethodInvocation implements ProxyMethodInvocation, Cloneable {
    
    //代理对象
	protected final Object proxy;

    //目标对象
	@Nullable
	protected final Object target;
	
    //目标方法
	protected final Method method;
	//参数
	protected Object[] arguments;
	//目标类
	@Nullable
	private final Class<?> targetClass;

	@Nullable
	private Map<String, Object> userAttributes;

	//封装的advice调用链
	protected final List<?> interceptorsAndDynamicMethodMatchers;

	//当前链的下标
	private int currentInterceptorIndex = -1;
    
    
    @Override
	@Nullable
	public Object proceed() throws Throwable {
		// We start with an index of -1 and increment early.
         //当执行最后一个advice的时候
		if (this.currentInterceptorIndex == this.interceptorsAndDynamicMethodMatchers.size() - 1) {
             //执行真正的目标方法
			return invokeJoinpoint();
		}
		
        //根据下标获取当前正在执行的advice
		Object interceptorOrInterceptionAdvice =
				this.interceptorsAndDynamicMethodMatchers.get(++this.currentInterceptorIndex);
         
		if (interceptorOrInterceptionAdvice instanceof InterceptorAndDynamicMethodMatcher dm) {
			// Evaluate dynamic method matcher here: static part will already have
			// been evaluated and found to match.
			Class<?> targetClass = (this.targetClass != null ? this.targetClass : this.method.getDeclaringClass());
			if (dm.matcher().matches(this.method, targetClass, this.arguments)) {
				return dm.interceptor().invoke(this);
			}
			else {
				// Dynamic matching failed.
				// Skip this interceptor and invoke the next in the chain.
				return proceed();
			}
		}
		else {
             //正常就走这里 调用advice的invoke方法
			// It's an interceptor, so we just invoke it: The pointcut will have
			// been evaluated statically before this object was constructed.
			return ((MethodInterceptor) interceptorOrInterceptionAdvice).invoke(this);
		}
	}

    //调用实际被代理的方法 method.invoke
	@Nullable
	protected Object invokeJoinpoint() throws Throwable {
        //这里用的是目标对象，目标方法，参数 
		return AopUtils.invokeJoinpointUsingReflection(this.target, this.method, this.arguments);
	}
}
```

##### ExposeInvocationInterceptor

```java
public final class ExposeInvocationInterceptor implements MethodInterceptor, PriorityOrdered, Serializable {

    //记录当前的ReflectiveMethodInvocation对象
    private static final ThreadLocal<MethodInvocation> invocation =
			new NamedThreadLocal<>("Current AOP method invocation");
    
    //记录当前的ReflectiveMethodInvocation对象
	@Override
	@Nullable
	public Object invoke(MethodInvocation mi) throws Throwable {
		MethodInvocation oldInvocation = invocation.get();
		invocation.set(mi);
		try {
             //调用ReflectiveMethodInvocation的proceed方法
			return mi.proceed();
		}
		finally {
			invocation.set(oldInvocation);
		}
	}


}
```

##### AspectJAroundAdvice

以`@Around`为例

```java
public class AspectJAroundAdvice extends AbstractAspectJAdvice implements MethodInterceptor, Serializable {

    public AspectJAroundAdvice(
          Method aspectJAroundAdviceMethod, AspectJExpressionPointcut pointcut, AspectInstanceFactory aif) {

       super(aspectJAroundAdviceMethod, pointcut, aif);
    }


    @Override
    public boolean isBeforeAdvice() {
       return false;
    }

    @Override
    public boolean isAfterAdvice() {
       return false;
    }

    @Override
    protected boolean supportsProceedingJoinPoint() {
       return true;
    }

    //invoke方法
    @Override
    @Nullable
    public Object invoke(MethodInvocation mi) throws Throwable {
       if (!(mi instanceof ProxyMethodInvocation pmi)) {
          throw new IllegalStateException("MethodInvocation is not a Spring ProxyMethodInvocation: " + mi);
       }
       //将ReflectiveMethodInvocation对象封装成ProceedingJoinPoint对象
       ProceedingJoinPoint pjp = lazyGetProceedingJoinPoint(pmi);
       JoinPointMatch jpm = getJoinPointMatch(pmi);
       //调用advice的around方法
       return invokeAdviceMethod(pjp, jpm, null, null);
    }

	
    protected ProceedingJoinPoint lazyGetProceedingJoinPoint(ProxyMethodInvocation rmi) {
       return new MethodInvocationProceedingJoinPoint(rmi);
    }

}
```

##### AbstractAspectJAdvice

```java
public abstract class AbstractAspectJAdvice implements Advice, AspectJPrecedenceInformation, Serializable {
    
    //调用advice的方法
    protected Object invokeAdviceMethod(JoinPoint jp, @Nullable JoinPointMatch jpMatch,
			@Nullable Object returnValue, @Nullable Throwable t) throws Throwable {
		//根据绑定的参数调用advice的方法
		return invokeAdviceMethodWithGivenArgs(argBinding(jp, jpMatch, returnValue, t));
	}

    //调用advice的方法
	protected Object invokeAdviceMethodWithGivenArgs(Object[] args) throws Throwable {
		Object[] actualArgs = args;
		if (this.aspectJAdviceMethod.getParameterCount() == 0) {
			actualArgs = null;
		}
		try {
			ReflectionUtils.makeAccessible(this.aspectJAdviceMethod);
             //method.invoke方法
			return this.aspectJAdviceMethod.invoke(this.aspectInstanceFactory.getAspectInstance(), actualArgs);
		}
		catch (IllegalArgumentException ex) {
			throw new AopInvocationException("Mismatch on arguments to advice method [" +
					this.aspectJAdviceMethod + "]; pointcut expression [" +
					this.pointcut.getPointcutExpression() + "]", ex);
		}
		catch (InvocationTargetException ex) {
			throw ex.getTargetException();
		}
	}
    //参数绑定
	protected Object[] argBinding(JoinPoint jp, @Nullable JoinPointMatch jpMatch,
			@Nullable Object returnValue, @Nullable Throwable ex) {
		//计算绑定的参数
		calculateArgumentBindings();

		// AMC start
		Object[] adviceInvocationArgs = new Object[this.parameterTypes.length];
		int numBound = 0;

        //设置JoinPoint到对应参数下标上
		if (this.joinPointArgumentIndex != -1) {
			adviceInvocationArgs[this.joinPointArgumentIndex] = jp;
			numBound++;
		}
		else if (this.joinPointStaticPartArgumentIndex != -1) {
			adviceInvocationArgs[this.joinPointStaticPartArgumentIndex] = jp.getStaticPart();
			numBound++;
		}
		//如果有绑定的参数 将绑定的参数设置到对应下标上
		if (!CollectionUtils.isEmpty(this.argumentBindings)) {
			// binding from pointcut match
			if (jpMatch != null) {
				PointcutParameter[] parameterBindings = jpMatch.getParameterBindings();
				for (PointcutParameter parameter : parameterBindings) {
					String name = parameter.getName();
					Integer index = this.argumentBindings.get(name);
					adviceInvocationArgs[index] = parameter.getBinding();
					numBound++;
				}
			}
			// binding from returning clause
			if (this.returningName != null) {
				Integer index = this.argumentBindings.get(this.returningName);
				adviceInvocationArgs[index] = returnValue;
				numBound++;
			}
			// binding from thrown exception
			if (this.throwingName != null) {
				Integer index = this.argumentBindings.get(this.throwingName);
				adviceInvocationArgs[index] = ex;
				numBound++;
			}
		}

		if (numBound != this.parameterTypes.length) {
			throw new IllegalStateException("Required to bind " + this.parameterTypes.length +
					" arguments, but only bound " + numBound + " (JoinPointMatch " +
					(jpMatch == null ? "was NOT" : "WAS") + " bound in invocation)");
		}

		return adviceInvocationArgs;
	}


}
```







### @Configuration标注的类被代理保证@Bean单例

Spring 的 `@Configuration` 类被设计为使用 CGLIB（Code Generation Library）进行代理。CGLIB 是一个强大的、高性能的代码生成库，它可以在运行时扩展 Java 类与实现 Java 接口。当一个类被 `@Configuration` 注解标注后，Spring 会为该类创建一个 CGLIB 代理子类，这个代理子类会拦截所有 `@Bean` 方法的调用。当调用 `@Configuration` 类中的 `@Bean` 方法时，CGLIB 代理会进行拦截。如果该 `@Bean` 方法对应的 Bean 已经在 Spring 容器中创建过（即已经是单例实例），代理会直接返回容器中的实例，而不是再次执行 `@Bean` 方法的方法体来创建新的实例。

核心处理逻辑在`ConfigurationClassPostProcessor`中的`postProcessBeanFactory`

```java
@Override
public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    int factoryId = System.identityHashCode(beanFactory);
    if (this.factoriesPostProcessed.contains(factoryId)) {
       throw new IllegalStateException(
             "postProcessBeanFactory already called on this post-processor against " + beanFactory);
    }
    this.factoriesPostProcessed.add(factoryId);
    if (!this.registriesPostProcessed.contains(factoryId)) {
       // BeanDefinitionRegistryPostProcessor hook apparently not supported...
       // Simply call processConfigurationClasses lazily at this point then.
       processConfigBeanDefinitions((BeanDefinitionRegistry) beanFactory);
    }
	
    //对@Configuration注解的类进行代理
    enhanceConfigurationClasses(beanFactory);
    beanFactory.addBeanPostProcessor(new ImportAwareBeanPostProcessor(beanFactory));
}


	public void enhanceConfigurationClasses(ConfigurableListableBeanFactory beanFactory) {
		StartupStep enhanceConfigClasses = this.applicationStartup.start("spring.context.config-classes.enhance");
		Map<String, AbstractBeanDefinition> configBeanDefs = new LinkedHashMap<>();
		for (String beanName : beanFactory.getBeanDefinitionNames()) {
			BeanDefinition beanDef = beanFactory.getBeanDefinition(beanName);
			Object configClassAttr = beanDef.getAttribute(ConfigurationClassUtils.CONFIGURATION_CLASS_ATTRIBUTE);
			AnnotationMetadata annotationMetadata = null;
			MethodMetadata methodMetadata = null;
			if (beanDef instanceof AnnotatedBeanDefinition annotatedBeanDefinition) {
				annotationMetadata = annotatedBeanDefinition.getMetadata();
				methodMetadata = annotatedBeanDefinition.getFactoryMethodMetadata();
			}
			if ((configClassAttr != null || methodMetadata != null) &&
					(beanDef instanceof AbstractBeanDefinition abd) && !abd.hasBeanClass()) {
				// Configuration class (full or lite) or a configuration-derived @Bean method
				// -> eagerly resolve bean class at this point, unless it's a 'lite' configuration
				// or component class without @Bean methods.
				boolean liteConfigurationCandidateWithoutBeanMethods =
						(ConfigurationClassUtils.CONFIGURATION_CLASS_LITE.equals(configClassAttr) &&
							annotationMetadata != null && !ConfigurationClassUtils.hasBeanMethods(annotationMetadata));
				if (!liteConfigurationCandidateWithoutBeanMethods) {
					try {
						abd.resolveBeanClass(this.beanClassLoader);
					}
					catch (Throwable ex) {
						throw new IllegalStateException(
								"Cannot load configuration class: " + beanDef.getBeanClassName(), ex);
					}
				}
			}
             //如果是full 即@Configuration注解修饰
			if (ConfigurationClassUtils.CONFIGURATION_CLASS_FULL.equals(configClassAttr)) {
				if (!(beanDef instanceof AbstractBeanDefinition abd)) {
					throw new BeanDefinitionStoreException("Cannot enhance @Configuration bean definition '" +
							beanName + "' since it is not stored in an AbstractBeanDefinition subclass");
				}
				else if (logger.isWarnEnabled() && beanFactory.containsSingleton(beanName)) {
					logger.warn("Cannot enhance @Configuration bean definition '" + beanName +
							"' since its singleton instance has been created too early. The typical cause " +
							"is a non-static @Bean method with a BeanDefinitionRegistryPostProcessor " +
							"return type: Consider declaring such methods as 'static' and/or marking the " +
							"containing configuration class as 'proxyBeanMethods=false'.");
				}
				configBeanDefs.put(beanName, abd);
			}
		}
		if (configBeanDefs.isEmpty()) {
			// nothing to enhance -> return immediately
			enhanceConfigClasses.end();
			return;
		}

        //代理
		ConfigurationClassEnhancer enhancer = new ConfigurationClassEnhancer();
		for (Map.Entry<String, AbstractBeanDefinition> entry : configBeanDefs.entrySet()) {
			AbstractBeanDefinition beanDef = entry.getValue();
			// If a @Configuration class gets proxied, always proxy the target class
			beanDef.setAttribute(AutoProxyUtils.PRESERVE_TARGET_CLASS_ATTRIBUTE, Boolean.TRUE);
			// Set enhanced subclass of the user-specified bean class
			Class<?> configClass = beanDef.getBeanClass();
             //返回代理类
			Class<?> enhancedClass = enhancer.enhance(configClass, this.beanClassLoader);
			if (configClass != enhancedClass) {
				if (logger.isTraceEnabled()) {
					logger.trace(String.format("Replacing bean definition '%s' existing class '%s' with " +
							"enhanced class '%s'", entry.getKey(), configClass.getName(), enhancedClass.getName()));
				}
				beanDef.setBeanClass(enhancedClass);
			}
		}
		enhanceConfigClasses.tag("classCount", () -> String.valueOf(configBeanDefs.keySet().size())).end();
	}


```

### 代理失效

当我们在一个被代理的方法内部去调用另一个被代理的方法时，会出现代理失效的情况

- 这就比较有趣了，因为CGlib本身是基于目标类生成子类，在子类重写被代理的方法做增强，所以按理说当使用代理类对象调用`query()`时应该是这样的
- `子类query()->父类query()->子类update()->父类update()`，两个方法都走了代理增强，没有代理失效

- 但实际情况是，当生成代理子类时，父类对象虽然没有被设置到容器中，但是被存到了`TargetSource`对象中，当代理拦截器准备调用父类方法时
- 会使用`父类method.invoke(父类对象，参数)`的形式调用，所以根本不会调用到子类代理中去，此时存在两个对象，一个父类原生对象，一个代理增强子类对象

```java
@Component
public class MyService {

    @Resource
    JdbcTemplate jdbcTemplate;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void query(){
        jdbcTemplate.query("select * from gamer", (rs, rowNum) -> {
            System.out.println(rs.getString("name"));
            return null;
        });
        update();
    }

    @Transactional(propagation = Propagation.NEVER)
    public void update(){
        jdbcTemplate.update("insert into gamer(name) values (?)", "zcq");
    }
}
```

### AOP全流程

- 在处理`ConfigurationClassPostProcessor`的`postProcessBeanDefinitionRegistry`时，针对`@Import`注解的解析时，扫描到`@EnableAspectJAutoProxy`注解中的`@Import(AspectJAutoProxyRegistrar.class)`类，加入到待处理的`ImportBeanDefinitionRegistrar`集合中，稍后进行`loadBeanDefinitionsFromRegistrars`时调用`AspectJAutoProxyRegistrar`的`registerBeanDefinitions`方法
- 注册`AnnotationAwareAspectJAutoProxyCreator`的`beanDefinition`到容器中，处理`proxyTargetClass`和`exposeProxy`属性
- `registerBeanPostProcessors`将`AnnotationAwareAspectJAutoProxyCreator`创建
- 在处理`createBean`方法时的`resolveBeforeInstantiation`时，调用`AnnotationAwareAspectJAutoProxyCreator`的`postProcessBeforeInstantiation`方法
- 针对非`@Aspect`的`bean`，扫描容器中的所有`Advisors`，找出所有的`Advisors`，如果没有，扫描找出容器中包含`@Aspect`注解的类，遍历其中的非`@Pointcut`标注的方法，获取`@Around、@Before、@After、@AfterReturning、@AfterThrowing`注解，创建`AspectJExpressionPointcut`对象，将注解中的切入点表达式信息设置到`AspectJExpressionPointcut`中，根据`@Around、@Before、@After、@AfterReturning、@AfterThrowing`注解分别创建`AspectJAroundAdvice、AspectJMethodBeforeAdvice、AspectJAfterAdvice、AspectJAfterReturningAdvice、AspectJAfterReturningAdvice`
- 将`AspectJExpressionPointcut`和`Advice`对象封装到`InstantiationModelAwarePointcutAdvisorImpl`中，根据`切面名称->advisors`缓存起来
- 在`initializeBean`的`applyBeanPostProcessorsAfterInitialization`中调用`AnnotationAwareAspectJAutoProxyCreator`的`postProcessAfterInitialization`方法
- `wrapIfNecessary`，通过`getAdvicesAndAdvisorsForBean`获取所有切入点表达式符合当前类及其方法的`advisors`，加入`ExposeInvocationInterceptor`到`advisors`中，对符合条件的`advisors`拓扑排序
- 根据是否有满足一定条件的接口来确定使用jdk代理`JdkDynamicAopProxy`还是cglib代理`ObjenesisCglibAopProxy`，一般使用cglib代理
- 创建`Enhancer`对象，设置继承父类为目标类，设置加入`SpringProxy、Advised、DecoratingProxy`等接口、设置CGLIB代理命名策略，设置`Callback`数组`DynamicAdvisedInterceptor、StaticUnadvisedInterceptor、SerializableNoOp、StaticDispatcher、AdvisedDispatcher、EqualsInterceptor、HashCodeInterceptor`，其中`DynamicAdvisedInterceptor`用于AOP的`advisors`调用处理工作，`EqualsInterceptor`和`HashCodeInterceptor`确保使用的是目标对象而不是代理对象的`equals`和`hashcode`方法
- 设置`ProxyCallbackFilter`为`CallbackFilter`，用于在生成代理类文件时使用不同的`Callback`，创建代理类
- 在代理对象的方法时，会根据代理类方法中的`CGLIB$CALLBACK_*`来决定调用哪个下标的`Callback`，`DynamicAdvisedInterceptor`为`CGLIB$CALLBACK_0`
- 首先遍历当前代理类所包含的`advisors`，找出切入点匹配当前调用方法的`advisors`，串行构成一条`adivsors`的链
- 创建`CglibMethodInvocation`，调用父类`ReflectiveMethodInvocation`的`proceed()`方法
- 根据当前调用`adivisor`的下标来决定是否调用目标方法，当已经调用到最后一个`adivisor`时，调用目标方法
- 获取当前下标的`adivisor`，下标+1，调用`adivisor`的`invoke`方法，执行`advice`的方法，传入对应参数，再调用父类`ReflectiveMethodInvocation`的`proceed()`方法调用下一个下标的`advisor`
- 调用链路完成，返回结果



## 事务

### 事务源码

#### 准备事务对象

核心注解`@EnableTransactionManagement`

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
//启动事务的整个核心就是通过此注解将TransactionManagementConfigurationSelector导入进入容器
@Import(TransactionManagementConfigurationSelector.class)
public @interface EnableTransactionManagement {

	boolean proxyTargetClass() default false;
	
	AdviceMode mode() default AdviceMode.PROXY;
	
	int order() default Ordered.LOWEST_PRECEDENCE;
}
```

##### TransactionManagementConfigurationSelector

```java
//将所需要的事务对象通过此类进行导入
public class TransactionManagementConfigurationSelector extends AdviceModeImportSelector<EnableTransactionManagement> {

    @Override
    protected String[] selectImports(AdviceMode adviceMode) {
       //默认导入AutoProxyRegistrar和ProxyTransactionManagementConfiguration
       return switch (adviceMode) {
          case PROXY -> new String[] {AutoProxyRegistrar.class.getName(),
                ProxyTransactionManagementConfiguration.class.getName()};
          case ASPECTJ -> new String[] {determineTransactionAspectClass()};
       };
    }

    private String determineTransactionAspectClass() {
       return (ClassUtils.isPresent("jakarta.transaction.Transactional", getClass().getClassLoader()) ?
             TransactionManagementConfigUtils.JTA_TRANSACTION_ASPECT_CONFIGURATION_CLASS_NAME :
             TransactionManagementConfigUtils.TRANSACTION_ASPECT_CONFIGURATION_CLASS_NAME);
    }

}
```

##### AutoProxyRegistrar

```java

//在没有配置AutoProxyCreator时创建一个默认的InfrastructureAdvisorAutoProxyCreator用于事务
public class AutoProxyRegistrar implements ImportBeanDefinitionRegistrar {
    
    
	@Override
	public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
		boolean candidateFound = false;
		Set<String> annTypes = importingClassMetadata.getAnnotationTypes();
		for (String annType : annTypes) {
			AnnotationAttributes candidate = AnnotationConfigUtils.attributesFor(importingClassMetadata, annType);
			if (candidate == null) {
				continue;
			}
             //读取@EnableTransactionManagement注解的属性
			Object mode = candidate.get("mode");
			Object proxyTargetClass = candidate.get("proxyTargetClass");
			if (mode != null && proxyTargetClass != null && AdviceMode.class == mode.getClass() &&
					Boolean.class == proxyTargetClass.getClass()) {
				candidateFound = true;
				if (mode == AdviceMode.PROXY) {
                      //注册InfrastructureAdvisorAutoProxyCreator
					AopConfigUtils.registerAutoProxyCreatorIfNecessary(registry);
					if ((Boolean) proxyTargetClass) {
						AopConfigUtils.forceAutoProxyCreatorToUseClassProxying(registry);
						return;
					}
				}
			}
		}
		if (!candidateFound && logger.isInfoEnabled()) {
			String name = getClass().getSimpleName();
			logger.info(String.format("%s was imported but no annotations were found " +
					"having both 'mode' and 'proxyTargetClass' attributes of type " +
					"AdviceMode and boolean respectively. This means that auto proxy " +
					"creator registration and configuration may not have occurred as " +
					"intended, and components may not be proxied as expected. Check to " +
					"ensure that %s has been @Import'ed on the same class where these " +
					"annotations are declared; otherwise remove the import of %s " +
					"altogether.", name, name, name));
		}
	}

}
```

<img src="./images/image-20250409163208203.png" alt="image-20250409163208203" style="zoom:50%;" />

##### InfrastructureAdvisorAutoProxyCreator

`InfrastructureAdvisorAutoProxyCreator`作为`AbstractAdvisorAutoProxyCreator`的子类，只有基础的根据`advisor`的`bean`代理的能力，不包括扫描`@Aspect`注解等功能

```java
public class InfrastructureAdvisorAutoProxyCreator extends AbstractAdvisorAutoProxyCreator {

    @Nullable
    private ConfigurableListableBeanFactory beanFactory;


    @Override
    protected void initBeanFactory(ConfigurableListableBeanFactory beanFactory) {
       super.initBeanFactory(beanFactory);
       this.beanFactory = beanFactory;
    }

    @Override
    protected boolean isEligibleAdvisorBean(String beanName) {
       return (this.beanFactory != null && this.beanFactory.containsBeanDefinition(beanName) &&
             this.beanFactory.getBeanDefinition(beanName).getRole() == BeanDefinition.ROLE_INFRASTRUCTURE);
    }

}
```

##### ProxyTransactionManagementConfiguration

```java
@Configuration(proxyBeanMethods = false)
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@ImportRuntimeHints(TransactionRuntimeHints.class)
public class ProxyTransactionManagementConfiguration extends AbstractTransactionManagementConfiguration {

    //注册BeanFactoryTransactionAttributeSourceAdvisor
    @Bean(name = TransactionManagementConfigUtils.TRANSACTION_ADVISOR_BEAN_NAME)
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public BeanFactoryTransactionAttributeSourceAdvisor transactionAdvisor(
          TransactionAttributeSource transactionAttributeSource, TransactionInterceptor transactionInterceptor) {

       BeanFactoryTransactionAttributeSourceAdvisor advisor = new BeanFactoryTransactionAttributeSourceAdvisor();
       advisor.setTransactionAttributeSource(transactionAttributeSource);
       advisor.setAdvice(transactionInterceptor);
       if (this.enableTx != null) {
          advisor.setOrder(this.enableTx.<Integer>getNumber("order"));
       }
       return advisor;
    }

    //注册AnnotationTransactionAttributeSource
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public TransactionAttributeSource transactionAttributeSource() {
       // Accept protected @Transactional methods on CGLIB proxies, as of 6.0.
       return new AnnotationTransactionAttributeSource(false);
    }

    //注册TransactionInterceptor
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public TransactionInterceptor transactionInterceptor(TransactionAttributeSource transactionAttributeSource) {
       TransactionInterceptor interceptor = new TransactionInterceptor();
       interceptor.setTransactionAttributeSource(transactionAttributeSource);
       if (this.txManager != null) {
          interceptor.setTransactionManager(this.txManager);
       }
       return interceptor;
    }

}
```

##### AbstractTransactionManagementConfiguration

```java
@Configuration
public abstract class AbstractTransactionManagementConfiguration implements ImportAware {

    @Nullable
    protected AnnotationAttributes enableTx;

    /**
     * Default transaction manager, as configured through a {@link TransactionManagementConfigurer}.
     */
    @Nullable
    protected TransactionManager txManager;


    @Override
    public void setImportMetadata(AnnotationMetadata importMetadata) {
       this.enableTx = AnnotationAttributes.fromMap(
             importMetadata.getAnnotationAttributes(EnableTransactionManagement.class.getName()));
       if (this.enableTx == null) {
          throw new IllegalArgumentException(
                "@EnableTransactionManagement is not present on importing class " + importMetadata.getClassName());
       }
    }

    @Autowired(required = false)
    void setConfigurers(Collection<TransactionManagementConfigurer> configurers) {
       if (CollectionUtils.isEmpty(configurers)) {
          return;
       }
       if (configurers.size() > 1) {
          throw new IllegalStateException("Only one TransactionManagementConfigurer may exist");
       }
       TransactionManagementConfigurer configurer = configurers.iterator().next();
       this.txManager = configurer.annotationDrivenTransactionManager();
    }

	//注册TransactionalEventListenerFactory
    @Bean(name = TransactionManagementConfigUtils.TRANSACTIONAL_EVENT_LISTENER_FACTORY_BEAN_NAME)
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static TransactionalEventListenerFactory transactionalEventListenerFactory() {
       return new RestrictedTransactionalEventListenerFactory();
    }

}
```

##### BeanFactoryTransactionAttributeSourceAdvisor

```java
//事务的advisor对象
public class BeanFactoryTransactionAttributeSourceAdvisor extends AbstractBeanFactoryPointcutAdvisor {

    //事务切入点对象
    private final TransactionAttributeSourcePointcut pointcut = new TransactionAttributeSourcePointcut();


    /**
     * Set the transaction attribute source which is used to find transaction
     * attributes. This should usually be identical to the source reference
     * set on the transaction interceptor itself.
     * @see TransactionInterceptor#setTransactionAttributeSource
     */
    public void setTransactionAttributeSource(TransactionAttributeSource transactionAttributeSource) {
       this.pointcut.setTransactionAttributeSource(transactionAttributeSource);
    }

    /**
     * Set the {@link ClassFilter} to use for this pointcut.
     * Default is {@link ClassFilter#TRUE}.
     */
    public void setClassFilter(ClassFilter classFilter) {
       this.pointcut.setClassFilter(classFilter);
    }

    @Override
    public Pointcut getPointcut() {
       return this.pointcut;
    }

}
```



###### TransactionAttributeSourcePointcut

```java
//事务advisor的切入点对象 负责类和方法的匹配
final class TransactionAttributeSourcePointcut extends StaticMethodMatcherPointcut implements Serializable {

    @Nullable
    private TransactionAttributeSource transactionAttributeSource;


    public TransactionAttributeSourcePointcut() {
       setClassFilter(new TransactionAttributeSourceClassFilter());
    }


    public void setTransactionAttributeSource(@Nullable TransactionAttributeSource transactionAttributeSource) {
       this.transactionAttributeSource = transactionAttributeSource;
    }
	
    //方法匹配
    @Override
    public boolean matches(Method method, Class<?> targetClass) {
       //调用getTransactionAttribute 是否有@Transaction注解 是否能获取到注解的全部属性
       return (this.transactionAttributeSource == null ||
             this.transactionAttributeSource.getTransactionAttribute(method, targetClass) != null);
    }

    @Override
    public boolean equals(@Nullable Object other) {
       return (this == other || (other instanceof TransactionAttributeSourcePointcut that &&
             ObjectUtils.nullSafeEquals(this.transactionAttributeSource, that.transactionAttributeSource)));
    }

    @Override
    public int hashCode() {
       return TransactionAttributeSourcePointcut.class.hashCode();
    }

    @Override
    public String toString() {
       return getClass().getName() + ": " + this.transactionAttributeSource;
    }


	//类匹配器
    private class TransactionAttributeSourceClassFilter implements ClassFilter {

       @Override
       public boolean matches(Class<?> clazz) {
           //如果属于TransactionalProxy、TransactionManager、PersistenceExceptionTranslator 不做事务代理
          if (TransactionalProxy.class.isAssignableFrom(clazz) ||
                TransactionManager.class.isAssignableFrom(clazz) ||
                PersistenceExceptionTranslator.class.isAssignableFrom(clazz)) {
             return false;
          }
           //判断是否是候选类
          return (transactionAttributeSource == null || transactionAttributeSource.isCandidateClass(clazz));
       }

       private TransactionAttributeSource getTransactionAttributeSource() {
          return transactionAttributeSource;
       }

       @Override
       public boolean equals(@Nullable Object other) {
          return (this == other || (other instanceof TransactionAttributeSourceClassFilter that &&
                ObjectUtils.nullSafeEquals(transactionAttributeSource, that.getTransactionAttributeSource())));
       }

       @Override
       public int hashCode() {
          return TransactionAttributeSourceClassFilter.class.hashCode();
       }

       @Override
       public String toString() {
          return TransactionAttributeSourceClassFilter.class.getName() + ": " + transactionAttributeSource;
       }

    }

}
```



###### AnnotationTransactionAttributeSource

```java
//获取事务属性对象TransactionAttribute的类
public class AnnotationTransactionAttributeSource extends AbstractFallbackTransactionAttributeSource
       implements Serializable {

    private static final boolean jta12Present;

    private static final boolean ejb3Present;

    static {
       ClassLoader classLoader = AnnotationTransactionAttributeSource.class.getClassLoader();
       jta12Present = ClassUtils.isPresent("jakarta.transaction.Transactional", classLoader);
       ejb3Present = ClassUtils.isPresent("jakarta.ejb.TransactionAttribute", classLoader);
    }

    private final boolean publicMethodsOnly;

    private final Set<TransactionAnnotationParser> annotationParsers;


    /**
     * Create a default AnnotationTransactionAttributeSource, supporting
     * public methods that carry the {@code Transactional} annotation
     * or the EJB3 {@link jakarta.ejb.TransactionAttribute} annotation.
     */
    public AnnotationTransactionAttributeSource() {
       this(true);
    }

   
    public AnnotationTransactionAttributeSource(boolean publicMethodsOnly) {
       this.publicMethodsOnly = publicMethodsOnly;
       if (jta12Present || ejb3Present) {
          this.annotationParsers = new LinkedHashSet<>(4);
          this.annotationParsers.add(new SpringTransactionAnnotationParser());
          if (jta12Present) {
             this.annotationParsers.add(new JtaTransactionAnnotationParser());
          }
          if (ejb3Present) {
             this.annotationParsers.add(new Ejb3TransactionAnnotationParser());
          }
       }
       else {
           //默认使用SpringTransactionAnnotationParser解析器
          this.annotationParsers = Collections.singleton(new SpringTransactionAnnotationParser());
       }
    }

   
    public AnnotationTransactionAttributeSource(TransactionAnnotationParser annotationParser) {
       this.publicMethodsOnly = true;
       Assert.notNull(annotationParser, "TransactionAnnotationParser must not be null");
       this.annotationParsers = Collections.singleton(annotationParser);
    }

  
    public AnnotationTransactionAttributeSource(TransactionAnnotationParser... annotationParsers) {
       this.publicMethodsOnly = true;
       Assert.notEmpty(annotationParsers, "At least one TransactionAnnotationParser needs to be specified");
       this.annotationParsers = new LinkedHashSet<>(Arrays.asList(annotationParsers));
    }


    public AnnotationTransactionAttributeSource(Set<TransactionAnnotationParser> annotationParsers) {
       this.publicMethodsOnly = true;
       Assert.notEmpty(annotationParsers, "At least one TransactionAnnotationParser needs to be specified");
       this.annotationParsers = annotationParsers;
    }

	//是否候选类
    @Override
    public boolean isCandidateClass(Class<?> targetClass) {
       for (TransactionAnnotationParser parser : this.annotationParsers) {
          if (parser.isCandidateClass(targetClass)) {
             return true;
          }
       }
       return false;
    }

    @Override
    @Nullable
    protected TransactionAttribute findTransactionAttribute(Class<?> clazz) {
       return determineTransactionAttribute(clazz);
    }

    @Override
    @Nullable
    protected TransactionAttribute findTransactionAttribute(Method method) {
       return determineTransactionAttribute(method);
    }

	//能否获取到@Transaction注解的属性 封装成TransactionAttribute
    @Nullable
    protected TransactionAttribute determineTransactionAttribute(AnnotatedElement element) {
       for (TransactionAnnotationParser parser : this.annotationParsers) {
          TransactionAttribute attr = parser.parseTransactionAnnotation(element);
          if (attr != null) {
             return attr;
          }
       }
       return null;
    }


    @Override
    protected boolean allowPublicMethodsOnly() {
       return this.publicMethodsOnly;
    }


    @Override
    public boolean equals(@Nullable Object other) {
       return (this == other || (other instanceof AnnotationTransactionAttributeSource otherTas &&
             this.annotationParsers.equals(otherTas.annotationParsers) &&
             this.publicMethodsOnly == otherTas.publicMethodsOnly));
    }

    @Override
    public int hashCode() {
       return this.annotationParsers.hashCode();
    }

}
```

###### SpringTransactionAnnotationParser



```java
public class SpringTransactionAnnotationParser implements TransactionAnnotationParser, Serializable {

    @Override
    public boolean isCandidateClass(Class<?> targetClass) {
       return AnnotationUtils.isCandidateClass(targetClass, Transactional.class);
    }

    //解析@Transactional 封装成TransactionAttribute
    @Override
    @Nullable
    public TransactionAttribute parseTransactionAnnotation(AnnotatedElement element) {
       AnnotationAttributes attributes = AnnotatedElementUtils.findMergedAnnotationAttributes(
             element, Transactional.class, false, false);
       if (attributes != null) {
          return parseTransactionAnnotation(attributes);
       }
       else {
          return null;
       }
    }

    public TransactionAttribute parseTransactionAnnotation(Transactional ann) {
       return parseTransactionAnnotation(AnnotationUtils.getAnnotationAttributes(ann, false, false));
    }

    //解析@Transactional 封装成TransactionAttribute
    protected TransactionAttribute parseTransactionAnnotation(AnnotationAttributes attributes) {
       RuleBasedTransactionAttribute rbta = new RuleBasedTransactionAttribute();

       Propagation propagation = attributes.getEnum("propagation");
       rbta.setPropagationBehavior(propagation.value());
       Isolation isolation = attributes.getEnum("isolation");
       rbta.setIsolationLevel(isolation.value());

       rbta.setTimeout(attributes.getNumber("timeout").intValue());
       String timeoutString = attributes.getString("timeoutString");
       Assert.isTrue(!StringUtils.hasText(timeoutString) || rbta.getTimeout() < 0,
             "Specify 'timeout' or 'timeoutString', not both");
       rbta.setTimeoutString(timeoutString);

       rbta.setReadOnly(attributes.getBoolean("readOnly"));
       rbta.setQualifier(attributes.getString("value"));
       rbta.setLabels(Set.of(attributes.getStringArray("label")));

       List<RollbackRuleAttribute> rollbackRules = new ArrayList<>();
       for (Class<?> rbRule : attributes.getClassArray("rollbackFor")) {
          rollbackRules.add(new RollbackRuleAttribute(rbRule));
       }
       for (String rbRule : attributes.getStringArray("rollbackForClassName")) {
          rollbackRules.add(new RollbackRuleAttribute(rbRule));
       }
       for (Class<?> rbRule : attributes.getClassArray("noRollbackFor")) {
          rollbackRules.add(new NoRollbackRuleAttribute(rbRule));
       }
       for (String rbRule : attributes.getStringArray("noRollbackForClassName")) {
          rollbackRules.add(new NoRollbackRuleAttribute(rbRule));
       }
       rbta.setRollbackRules(rollbackRules);

       return rbta;
    }


    @Override
    public boolean equals(@Nullable Object other) {
       return (other instanceof SpringTransactionAnnotationParser);
    }

    @Override
    public int hashCode() {
       return SpringTransactionAnnotationParser.class.hashCode();
    }

}
```

#### 生成代理对象

在`doCreateBean`的`initializeBean`时`applyBeanPostProcessorsAfterInitialization`中会调用`postProcessAfterInitialization`

##### AbstractAutoProxyCreator

```java
public abstract class AbstractAutoProxyCreator extends ProxyProcessorSupport
		implements SmartInstantiationAwareBeanPostProcessor, BeanFactoryAware {
    
	@Override
	public Object postProcessAfterInitialization(@Nullable Object bean, String beanName) {
		if (bean != null) {
			Object cacheKey = getCacheKey(bean.getClass(), beanName);
			if (this.earlyBeanReferences.remove(cacheKey) != bean) {
				return wrapIfNecessary(bean, beanName, cacheKey);
			}
		}
		return bean;
	}
    
    protected Object wrapIfNecessary(Object bean, String beanName, Object cacheKey) {
		if (StringUtils.hasLength(beanName) && this.targetSourcedBeans.contains(beanName)) {
			return bean;
		}
		if (Boolean.FALSE.equals(this.advisedBeans.get(cacheKey))) {
			return bean;
		}
		if (isInfrastructureClass(bean.getClass()) || shouldSkip(bean.getClass(), beanName)) {
			this.advisedBeans.put(cacheKey, Boolean.FALSE);
			return bean;
		}
		
        //获取符合条件的advisor对象 这里能够获取到BeanFactoryTransactionAttributeSourceAdvisor
		// Create proxy if we have advice.
		Object[] specificInterceptors = getAdvicesAndAdvisorsForBean(bean.getClass(), beanName, null);
		if (specificInterceptors != DO_NOT_PROXY) {
			this.advisedBeans.put(cacheKey, Boolean.TRUE);
             //根据advisor生成代理对象
			Object proxy = createProxy(
					bean.getClass(), beanName, specificInterceptors, new SingletonTargetSource(bean));
			this.proxyTypes.put(cacheKey, proxy.getClass());
			return proxy;
		}

		this.advisedBeans.put(cacheKey, Boolean.FALSE);
		return bean;
	}

}
```



#### 执行拦截器链

##### CglibAopProxy

```JAVA
class CglibAopProxy implements AopProxy, Serializable {

    //用于处理aop的方法拦截器
	private static class DynamicAdvisedInterceptor implements MethodInterceptor, Serializable {
		
        //代理的advisor对象
		private final AdvisedSupport advised;

		public DynamicAdvisedInterceptor(AdvisedSupport advised) {
			this.advised = advised;
		}

        //处理aop
		@Override
		@Nullable
		public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
			Object oldProxy = null;
			boolean setProxyContext = false;
			Object target = null;
			TargetSource targetSource = this.advised.getTargetSource();
			try {
                 //是否暴露代理对象
				if (this.advised.exposeProxy) {
					// Make invocation available if necessary.
					oldProxy = AopContext.setCurrentProxy(proxy);
					setProxyContext = true;
				}
				// Get as late as possible to minimize the time we "own" the target, in case it comes from a pool...
				target = targetSource.getTarget();
				Class<?> targetClass = (target != null ? target.getClass() : null);
                 //根据当前方法来获取对应的Advisor调用链 这里只能获取到一个advisor 没有ExposeInvocationInterceptor
				List<Object> chain = this.advised.getInterceptorsAndDynamicInterceptionAdvice(method, targetClass);
				Object retVal;
				// Check whether we only have one InvokerInterceptor: that is,
				// no real advice, but just reflective invocation of the target.
				if (chain.isEmpty()) {
					// We can skip creating a MethodInvocation: just invoke the target directly.
					// Note that the final invoker must be an InvokerInterceptor, so we know
					// it does nothing but a reflective operation on the target, and no hot
					// swapping or fancy proxying.
					Object[] argsToUse = AopProxyUtils.adaptArgumentsIfNecessary(method, args);
					retVal = AopUtils.invokeJoinpointUsingReflection(target, method, argsToUse);
				}
				else {
                     //调用拦截器链
					// We need to create a method invocation...
					retVal = new CglibMethodInvocation(proxy, target, method, args, targetClass, chain, methodProxy).proceed();
				}
				return processReturnType(proxy, target, method, args, retVal);
			}
			finally {
				if (target != null && !targetSource.isStatic()) {
					targetSource.releaseTarget(target);
				}
				if (setProxyContext) {
					// Restore old proxy.
					AopContext.setCurrentProxy(oldProxy);
				}
			}
		}

		@Override
		public boolean equals(@Nullable Object other) {
			return (this == other ||
					(other instanceof DynamicAdvisedInterceptor dynamicAdvisedInterceptor &&
							this.advised.equals(dynamicAdvisedInterceptor.advised)));
		}

		/**
		 * CGLIB uses this to drive proxy creation.
		 */
		@Override
		public int hashCode() {
			return this.advised.hashCode();
		}
	}

	//封装调用advice链的对象
	private static class CglibMethodInvocation extends ReflectiveMethodInvocation {

		public CglibMethodInvocation(Object proxy, @Nullable Object target, Method method,
				Object[] arguments, @Nullable Class<?> targetClass,
				List<Object> interceptorsAndDynamicMethodMatchers, MethodProxy methodProxy) {

			super(proxy, target, method, arguments, targetClass, interceptorsAndDynamicMethodMatchers);
		}

		@Override
		@Nullable
		public Object proceed() throws Throwable {
			try {
                  //调用ReflectiveMethodInvocation的proceed方法
				return super.proceed();
			}
			catch (RuntimeException ex) {
				throw ex;
			}
			catch (Exception ex) {
				if (ReflectionUtils.declaresException(getMethod(), ex.getClass()) ||
						KotlinDetector.isKotlinType(getMethod().getDeclaringClass())) {
					// Propagate original exception if declared on the target method
					// (with callers expecting it). Always propagate it for Kotlin code
					// since checked exceptions do not have to be explicitly declared there.
					throw ex;
				}
				else {
					// Checked exception thrown in the interceptor but not declared on the
					// target method signature -> apply an UndeclaredThrowableException,
					// aligned with standard JDK dynamic proxy behavior.
					throw new UndeclaredThrowableException(ex);
				}
			}
		}
	}


}
```

##### TransactionInterceptor

```java
public class TransactionInterceptor extends TransactionAspectSupport implements MethodInterceptor, Serializable {
    
    
	@Override
	@Nullable
	public Object invoke(MethodInvocation invocation) throws Throwable {
		// Work out the target class: may be {@code null}.
		// The TransactionAttributeSource should be passed the target class
		// as well as the method, which may be from an interface.
		Class<?> targetClass = (invocation.getThis() != null ? AopUtils.getTargetClass(invocation.getThis()) : null);

		// Adapt to TransactionAspectSupport's invokeWithinTransaction...
		return invokeWithinTransaction(invocation.getMethod(), targetClass, new CoroutinesInvocationCallback() {
			@Override
			@Nullable
			public Object proceedWithInvocation() throws Throwable {
				return invocation.proceed();
			}
			@Override
			public Object getTarget() {
				return invocation.getThis();
			}
			@Override
			public Object[] getArguments() {
				return invocation.getArguments();
			}
		});
	}


}
```

##### TransactionAspectSupport

```java
public abstract class TransactionAspectSupport implements BeanFactoryAware, InitializingBean {

    @Nullable
	protected Object invokeWithinTransaction(Method method, @Nullable Class<?> targetClass,
			final InvocationCallback invocation) throws Throwable {

		// If the transaction attribute is null, the method is non-transactional.
		TransactionAttributeSource tas = getTransactionAttributeSource();
         //获取当前方法的TransactionAttribute
		final TransactionAttribute txAttr = (tas != null ? tas.getTransactionAttribute(method, targetClass) : null);
		final TransactionManager tm = determineTransactionManager(txAttr);

		if (this.reactiveAdapterRegistry != null && tm instanceof ReactiveTransactionManager rtm) {
			boolean isSuspendingFunction = KotlinDetector.isSuspendingFunction(method);
			boolean hasSuspendingFlowReturnType = isSuspendingFunction &&
					COROUTINES_FLOW_CLASS_NAME.equals(new MethodParameter(method, -1).getParameterType().getName());
			if (isSuspendingFunction && !(invocation instanceof CoroutinesInvocationCallback)) {
				throw new IllegalStateException("Coroutines invocation not supported: " + method);
			}
			CoroutinesInvocationCallback corInv = (isSuspendingFunction ? (CoroutinesInvocationCallback) invocation : null);

			ReactiveTransactionSupport txSupport = this.transactionSupportCache.computeIfAbsent(method, key -> {
				Class<?> reactiveType =
						(isSuspendingFunction ? (hasSuspendingFlowReturnType ? Flux.class : Mono.class) : method.getReturnType());
				ReactiveAdapter adapter = this.reactiveAdapterRegistry.getAdapter(reactiveType);
				if (adapter == null) {
					throw new IllegalStateException("Cannot apply reactive transaction to non-reactive return type: " +
							method.getReturnType());
				}
				return new ReactiveTransactionSupport(adapter);
			});

			InvocationCallback callback = invocation;
			if (corInv != null) {
				callback = () -> KotlinDelegate.invokeSuspendingFunction(method, corInv);
			}
			return txSupport.invokeWithinTransaction(method, targetClass, callback, txAttr, rtm);
		}

         //将事务管理器转换成PlatformTransactionManager类型
		PlatformTransactionManager ptm = asPlatformTransactionManager(tm);
		final String joinpointIdentification = methodIdentification(method, targetClass, txAttr);

		if (txAttr == null || !(ptm instanceof CallbackPreferringPlatformTransactionManager cpptm)) {
			// Standard transaction demarcation with getTransaction and commit/rollback calls.
             //创建TransactionInfo
			TransactionInfo txInfo = createTransactionIfNecessary(ptm, txAttr, joinpointIdentification);

			Object retVal;
			try {
				// This is an around advice: Invoke the next interceptor in the chain.
				// This will normally result in a target object being invoked.
                 //调用实际的sql方法
				retVal = invocation.proceedWithInvocation();
			}
			catch (Throwable ex) {
				// target invocation exception
                  //抛出异常后的事务回滚
				completeTransactionAfterThrowing(txInfo, ex);
				throw ex;
			}
			finally {
                 //清理当前事务
				cleanupTransactionInfo(txInfo);
			}

			if (retVal != null && txAttr != null) {
				TransactionStatus status = txInfo.getTransactionStatus();
				if (status != null) {
					if (retVal instanceof Future<?> future && future.isDone()) {
						try {
							future.get();
						}
						catch (ExecutionException ex) {
							if (txAttr.rollbackOn(ex.getCause())) {
								status.setRollbackOnly();
							}
						}
						catch (InterruptedException ex) {
							Thread.currentThread().interrupt();
						}
					}
					else if (vavrPresent && VavrDelegate.isVavrTry(retVal)) {
						// Set rollback-only in case of Vavr failure matching our rollback rules...
						retVal = VavrDelegate.evaluateTryFailure(retVal, txAttr, status);
					}
				}
			}
			//事务提交
			commitTransactionAfterReturning(txInfo);
			return retVal;
		}

		else {
			Object result;
			final ThrowableHolder throwableHolder = new ThrowableHolder();

			// It's a CallbackPreferringPlatformTransactionManager: pass a TransactionCallback in.
			try {
				result = cpptm.execute(txAttr, status -> {
					TransactionInfo txInfo = prepareTransactionInfo(ptm, txAttr, joinpointIdentification, status);
					try {
						Object retVal = invocation.proceedWithInvocation();
						if (retVal != null && vavrPresent && VavrDelegate.isVavrTry(retVal)) {
							// Set rollback-only in case of Vavr failure matching our rollback rules...
							retVal = VavrDelegate.evaluateTryFailure(retVal, txAttr, status);
						}
						return retVal;
					}
					catch (Throwable ex) {
						if (txAttr.rollbackOn(ex)) {
							// A RuntimeException: will lead to a rollback.
							if (ex instanceof RuntimeException runtimeException) {
								throw runtimeException;
							}
							else {
								throw new ThrowableHolderException(ex);
							}
						}
						else {
							// A normal return value: will lead to a commit.
							throwableHolder.throwable = ex;
							return null;
						}
					}
					finally {
						cleanupTransactionInfo(txInfo);
					}
				});
			}
			catch (ThrowableHolderException ex) {
				throw ex.getCause();
			}
			catch (TransactionSystemException ex2) {
				if (throwableHolder.throwable != null) {
					logger.error("Application exception overridden by commit exception", throwableHolder.throwable);
					ex2.initApplicationException(throwableHolder.throwable);
				}
				throw ex2;
			}
			catch (Throwable ex2) {
				if (throwableHolder.throwable != null) {
					logger.error("Application exception overridden by commit exception", throwableHolder.throwable);
				}
				throw ex2;
			}

			// Check result state: It might indicate a Throwable to rethrow.
			if (throwableHolder.throwable != null) {
				throw throwableHolder.throwable;
			}
			return result;
		}
	}
    
    
    //获取TransactionManager
    @Nullable
	protected TransactionManager determineTransactionManager(@Nullable TransactionAttribute txAttr) {
		// Do not attempt to lookup tx manager if no tx attributes are set
		if (txAttr == null || this.beanFactory == null) {
			return getTransactionManager();
		}
		//如果属性中设置了特定的事务管理器
		String qualifier = txAttr.getQualifier();
		if (StringUtils.hasText(qualifier)) {
             //获取特定名称的事务管理器
			return determineQualifiedTransactionManager(this.beanFactory, qualifier);
		}
		else if (StringUtils.hasText(this.transactionManagerBeanName)) {
			return determineQualifiedTransactionManager(this.beanFactory, this.transactionManagerBeanName);
		}
		else {
			TransactionManager defaultTransactionManager = getTransactionManager();
			if (defaultTransactionManager == null) {
				defaultTransactionManager = this.transactionManagerCache.get(DEFAULT_TRANSACTION_MANAGER_KEY);
				if (defaultTransactionManager == null) {
                      //从容器中获取TransactionManager的事务管理器
					defaultTransactionManager = this.beanFactory.getBean(TransactionManager.class);
                     //放入缓存中
					this.transactionManagerCache.putIfAbsent(
							DEFAULT_TRANSACTION_MANAGER_KEY, defaultTransactionManager);
				}
			}
			return defaultTransactionManager;
		}
	}
    
    
    //创建事务
    protected TransactionInfo createTransactionIfNecessary(@Nullable PlatformTransactionManager tm,
			@Nullable TransactionAttribute txAttr, final String joinpointIdentification) {

        //将TransactionAttribute封装成DelegatingTransactionAttribute
		// If no name specified, apply method identification as transaction name.
		if (txAttr != null && txAttr.getName() == null) {
			txAttr = new DelegatingTransactionAttribute(txAttr) {
				@Override
				public String getName() {
					return joinpointIdentification;
				}
			};
		}

		TransactionStatus status = null;
		if (txAttr != null) {
			if (tm != null) {
                  //获取事务
				status = tm.getTransaction(txAttr);
			}
			else {
				if (logger.isDebugEnabled()) {
					logger.debug("Skipping transactional joinpoint [" + joinpointIdentification +
							"] because no transaction manager has been configured");
				}
			}
		}
        //将事务管理器、事务属性、事务状态等信息封装成TransactionInfo 存入当前线程
		return prepareTransactionInfo(tm, txAttr, joinpointIdentification, status);
	}
    
    //将事务管理器、事务属性、事务状态等信息封装成TransactionInfo 存入当前线程
	protected TransactionInfo prepareTransactionInfo(@Nullable PlatformTransactionManager tm,
			@Nullable TransactionAttribute txAttr, String joinpointIdentification,
			@Nullable TransactionStatus status) {

		TransactionInfo txInfo = new TransactionInfo(tm, txAttr, joinpointIdentification);
		if (txAttr != null) {
			// We need a transaction for this method...
			if (logger.isTraceEnabled()) {
				logger.trace("Getting transaction for [" + txInfo.getJoinpointIdentification() + "]");
			}
			// The transaction manager will flag an error if an incompatible tx already exists.
			txInfo.newTransactionStatus(status);
		}
		else {
			// The TransactionInfo.hasTransaction() method will return false. We created it only
			// to preserve the integrity of the ThreadLocal stack maintained in this class.
			if (logger.isTraceEnabled()) {
				logger.trace("No need to create transaction for [" + joinpointIdentification +
						"]: This method is not transactional.");
			}
		}

		// We always bind the TransactionInfo to the thread, even if we didn't create
		// a new transaction here. This guarantees that the TransactionInfo stack
		// will be managed correctly even if no transaction was created by this aspect.
		txInfo.bindToThread();
		return txInfo;
	}

    //使用事务管理器事务提交
	protected void commitTransactionAfterReturning(@Nullable TransactionInfo txInfo) {
		if (txInfo != null && txInfo.getTransactionStatus() != null) {
			if (logger.isTraceEnabled()) {
				logger.trace("Completing transaction for [" + txInfo.getJoinpointIdentification() + "]");
			}
			txInfo.getTransactionManager().commit(txInfo.getTransactionStatus());
		}
	}
    //使用事务管理器回滚
    protected void completeTransactionAfterThrowing(@Nullable TransactionInfo txInfo, Throwable ex) {
		if (txInfo != null && txInfo.getTransactionStatus() != null) {
			if (logger.isTraceEnabled()) {
				logger.trace("Completing transaction for [" + txInfo.getJoinpointIdentification() +
						"] after exception: " + ex);
			}
			if (txInfo.transactionAttribute != null && txInfo.transactionAttribute.rollbackOn(ex)) {
				try {
					txInfo.getTransactionManager().rollback(txInfo.getTransactionStatus());
				}
				catch (TransactionSystemException ex2) {
					logger.error("Application exception overridden by rollback exception", ex);
					ex2.initApplicationException(ex);
					throw ex2;
				}
				catch (RuntimeException | Error ex2) {
					logger.error("Application exception overridden by rollback exception", ex);
					throw ex2;
				}
			}
			else {
				// We don't roll back on this exception.
				// Will still roll back if TransactionStatus.isRollbackOnly() is true.
				try {
					txInfo.getTransactionManager().commit(txInfo.getTransactionStatus());
				}
				catch (TransactionSystemException ex2) {
					logger.error("Application exception overridden by commit exception", ex);
					ex2.initApplicationException(ex);
					throw ex2;
				}
				catch (RuntimeException | Error ex2) {
					logger.error("Application exception overridden by commit exception", ex);
					throw ex2;
				}
			}
		}
	}

}
```

##### AbstractPlatformTransactionManager

```java
public abstract class AbstractPlatformTransactionManager
       implements PlatformTransactionManager, ConfigurableTransactionManager, Serializable {

    //获取事务
	@Override
	public final TransactionStatus getTransaction(@Nullable TransactionDefinition definition)
			throws TransactionException {

		// Use defaults if no transaction definition given.
		TransactionDefinition def = (definition != null ? definition : TransactionDefinition.withDefaults());
		
        //调用子类doGetTransaction获取事务
		Object transaction = doGetTransaction();
		boolean debugEnabled = logger.isDebugEnabled();

        //判断是否持有Connection 即存在事务
		if (isExistingTransaction(transaction)) {
			// Existing transaction found -> check propagation behavior to find out how to behave.
			return handleExistingTransaction(def, transaction, debugEnabled);
		}
		
        //校验超时时间设置大小
		// Check definition settings for new transaction.
		if (def.getTimeout() < TransactionDefinition.TIMEOUT_DEFAULT) {
			throw new InvalidTimeoutException("Invalid transaction timeout", def.getTimeout());
		}
		
        //如果没找到事务 但是传播特性是MANDATORY 报错
		// No existing transaction found -> check propagation behavior to find out how to proceed.
		if (def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_MANDATORY) {
			throw new IllegalTransactionStateException(
					"No existing transaction found for transaction marked with propagation 'mandatory'");
		}
        //否则传播特性是REQUIRED REQUIRES_NEW PROPAGATION_NESTED
		else if (def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRED ||
				def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW ||
				def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_NESTED) {
             //挂起事务 这里挂起空
			SuspendedResourcesHolder suspendedResources = suspend(null);
			if (debugEnabled) {
				logger.debug("Creating new transaction with name [" + def.getName() + "]: " + def);
			}
			try {
                 //开启一个新事务
				return startTransaction(def, transaction, false, debugEnabled, suspendedResources);
			}
			catch (RuntimeException | Error ex) {
				resume(null, suspendedResources);
				throw ex;
			}
		}
		else {
			// Create "empty" transaction: no actual transaction, but potentially synchronization.
			if (def.getIsolationLevel() != TransactionDefinition.ISOLATION_DEFAULT && logger.isWarnEnabled()) {
				logger.warn("Custom isolation level specified but no actual transaction initiated; " +
						"isolation level will effectively be ignored: " + def);
			}
			boolean newSynchronization = (getTransactionSynchronization() == SYNCHRONIZATION_ALWAYS);
			return prepareTransactionStatus(def, null, true, newSynchronization, debugEnabled, null);
		}
	}
    
    
    private TransactionStatus startTransaction(TransactionDefinition definition, Object transaction,
			boolean nested, boolean debugEnabled, @Nullable SuspendedResourcesHolder suspendedResources) {

        //创建TransactionStatus
		boolean newSynchronization = (getTransactionSynchronization() != SYNCHRONIZATION_NEVER);
		DefaultTransactionStatus status = newTransactionStatus(
				definition, transaction, true, newSynchronization, nested, debugEnabled, suspendedResources);
        //发布事务事件
		this.transactionExecutionListeners.forEach(listener -> listener.beforeBegin(status));
		try {
             //获取连接 开启事务
			doBegin(transaction, definition);
		}
		catch (RuntimeException | Error ex) {
			this.transactionExecutionListeners.forEach(listener -> listener.afterBegin(status, ex));
			throw ex;
		}
         //设置当前事务的一些状态到线程中
		prepareSynchronization(status, definition);
		this.transactionExecutionListeners.forEach(listener -> listener.afterBegin(status, null));
		return status;
	}
    
    //创建一个DefaultTransactionStatus
    private DefaultTransactionStatus newTransactionStatus(
			TransactionDefinition definition, @Nullable Object transaction, boolean newTransaction,
			boolean newSynchronization, boolean nested, boolean debug, @Nullable Object suspendedResources) {

		boolean actualNewSynchronization = newSynchronization &&
				!TransactionSynchronizationManager.isSynchronizationActive();
		return new DefaultTransactionStatus(definition.getName(), transaction, newTransaction,
				actualNewSynchronization, nested, definition.isReadOnly(), debug, suspendedResources);
	}
    
    //设置当前事务的一些状态到线程中
	private void prepareSynchronization(DefaultTransactionStatus status, TransactionDefinition definition) {
		if (status.isNewSynchronization()) {
			TransactionSynchronizationManager.setActualTransactionActive(status.hasTransaction());
			TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(
					definition.getIsolationLevel() != TransactionDefinition.ISOLATION_DEFAULT ?
							definition.getIsolationLevel() : null);
			TransactionSynchronizationManager.setCurrentTransactionReadOnly(definition.isReadOnly());
			TransactionSynchronizationManager.setCurrentTransactionName(definition.getName());
			TransactionSynchronizationManager.initSynchronization();
		}
	}

}
```



##### DataSourceTransactionManager

```java
public class DataSourceTransactionManager extends AbstractPlatformTransactionManager
       implements ResourceTransactionManager, InitializingBean {

    @Nullable
	private DataSource dataSource;

	public DataSourceTransactionManager(DataSource dataSource) {
		this();
		setDataSource(dataSource);
		afterPropertiesSet();
	}
    
    //获取事务
	@Override
	protected Object doGetTransaction() {
         //创建持有Connection的对象
		DataSourceTransactionObject txObject = new DataSourceTransactionObject();
         //设置是否允许事务保存点
		txObject.setSavepointAllowed(isNestedTransactionAllowed());
         //获取缓存中的Connection
		ConnectionHolder conHolder =
				(ConnectionHolder) TransactionSynchronizationManager.getResource(obtainDataSource());
		txObject.setConnectionHolder(conHolder, false);
		return txObject;
	}
    
    @Override
	protected void doBegin(Object transaction, TransactionDefinition definition) {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;
		Connection con = null;

		try {
             //如果没有Connection
			if (!txObject.hasConnectionHolder() ||
					txObject.getConnectionHolder().isSynchronizedWithTransaction()) {
                 //基于数据源获取Connection
				Connection newCon = obtainDataSource().getConnection();
				if (logger.isDebugEnabled()) {
					logger.debug("Acquired Connection [" + newCon + "] for JDBC transaction");
				}
                 //设置Connection
				txObject.setConnectionHolder(new ConnectionHolder(newCon), true);
			}
			//设置事务同步
			txObject.getConnectionHolder().setSynchronizedWithTransaction(true);
			con = txObject.getConnectionHolder().getConnection();
            
			//设置事务只读和隔离级别
			Integer previousIsolationLevel = DataSourceUtils.prepareConnectionForTransaction(con, definition);
			txObject.setPreviousIsolationLevel(previousIsolationLevel);
			txObject.setReadOnly(definition.isReadOnly());

			// Switch to manual commit if necessary. This is very expensive in some JDBC drivers,
			// so we don't want to do it unnecessarily (for example if we've explicitly
			// configured the connection pool to set it already).
             //关闭自动提交
			if (con.getAutoCommit()) {
				txObject.setMustRestoreAutoCommit(true);
				if (logger.isDebugEnabled()) {
					logger.debug("Switching JDBC Connection [" + con + "] to manual commit");
				}
				con.setAutoCommit(false);
			}
			//设置事务只读
			prepareTransactionalConnection(con, definition);
			txObject.getConnectionHolder().setTransactionActive(true);
			//设置事务超时时间
			int timeout = determineTimeout(definition);
			if (timeout != TransactionDefinition.TIMEOUT_DEFAULT) {
				txObject.getConnectionHolder().setTimeoutInSeconds(timeout);
			}
			//将当前数据源和Connection绑定到当前线程中
			// Bind the connection holder to the thread.
			if (txObject.isNewConnectionHolder()) {
				TransactionSynchronizationManager.bindResource(obtainDataSource(), txObject.getConnectionHolder());
			}
		}

		catch (Throwable ex) {
			if (txObject.isNewConnectionHolder()) {
				DataSourceUtils.releaseConnection(con, obtainDataSource());
				txObject.setConnectionHolder(null, false);
			}
			throw new CannotCreateTransactionException("Could not open JDBC Connection for transaction", ex);
		}
	}

}
```

##### TransactionSynchronizationManager

```java
public abstract class TransactionSynchronizationManager {
    
	private static final ThreadLocal<Map<Object, Object>> resources =
			new NamedThreadLocal<>("Transactional resources");

	private static final ThreadLocal<Set<TransactionSynchronization>> synchronizations =
			new NamedThreadLocal<>("Transaction synchronizations");

	private static final ThreadLocal<String> currentTransactionName =
			new NamedThreadLocal<>("Current transaction name");

	private static final ThreadLocal<Boolean> currentTransactionReadOnly =
			new NamedThreadLocal<>("Current transaction read-only status");

	private static final ThreadLocal<Integer> currentTransactionIsolationLevel =
			new NamedThreadLocal<>("Current transaction isolation level");

	private static final ThreadLocal<Boolean> actualTransactionActive =
			new NamedThreadLocal<>("Actual transaction active");

	@Nullable
	public static Object getResource(Object key) {
		Object actualKey = TransactionSynchronizationUtils.unwrapResourceIfNecessary(key);
		return doGetResource(actualKey);
	}
    
    @Nullable
	private static Object doGetResource(Object actualKey) {
		Map<Object, Object> map = resources.get();
		if (map == null) {
			return null;
		}
		Object value = map.get(actualKey);
		// Transparently remove ResourceHolder that was marked as void...
		if (value instanceof ResourceHolder resourceHolder && resourceHolder.isVoid()) {
			map.remove(actualKey);
			// Remove entire ThreadLocal if empty...
			if (map.isEmpty()) {
				resources.remove();
			}
			value = null;
		}
		return value;
	}


}
```

##### DefaultTransactionStatus

```java
public class DefaultTransactionStatus extends AbstractTransactionStatus {
    
    //事务对象 DataSourceTransactionObject
	@Nullable
	private final Object transaction;

    //是否新事务
	private final boolean newTransaction;

	private final boolean newSynchronization;

	private final boolean nested;
	//是否可读
	private final boolean readOnly;
	//挂起的事务    
	@Nullable
	private final Object suspendedResources;

    //是否回滚
    private boolean rollbackOnly = false;
	//是否完成
	private boolean completed = false;
	//保存点
	@Nullable
	private Object savepoint;


}
```

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



### spring中常用注解

**@Lazy**

`@Lazy` 注解用于控制 Bean 的加载时机。在 Spring 中，默认情况下，所有单例 Bean 在应用上下文启动时就会被创建和初始化。使用 `@Lazy` 注解可以将 Bean 的创建延迟到第一次被使用时

**@DependsOn**

`@DependsOn` 注解用于指定一个 Bean 依赖于其他一个或多个 Bean。当使用该注解时，Spring 会确保在创建被注解的 Bean 之前，先创建并初始化它所依赖的 Bean。

**@Role**

在大型的 Spring 项目中，会有大量的 Bean。使用 `@Role` 注解对 Bean 进行分类，可以帮助开发者更好地理解和管理这些 Bean

- **`BeanDefinition.ROLE_APPLICATION`**：值为 `0`，表示这是一个应用级别的 Bean，通常是开发者自定义的业务 Bean，比如服务层、数据访问层的 Bean 等。
- **`BeanDefinition.ROLE_SUPPORT`**：值为 `1`，表示这是一个支持性的 Bean，一般是用于辅助应用功能的 Bean，像配置类、工具类等。
- **`BeanDefinition.ROLE_INFRASTRUCTURE`**：值为 `2`，表示这是一个基础设施级别的 Bean，通常是 Spring 框架内部使用的 Bean，开发者一般不需要直接使用。



**@Primary**

当 Spring 容器中存在多个类型相同的 Bean 时，在进行依赖注入时就会产生歧义，因为 Spring 不知道应该注入哪一个 Bean。`@Primary` 注解可以标记在某个 Bean 定义上，当发生依赖注入歧义时，Spring 会优先选择被 `@Primary` 注解标记的 Bean 进行注入。

**@Description**

`@Description` 注解用于为 Bean 添加描述信息。这些描述信息通常用于文档生成或者在开发工具中显示，方便开发者理解 Bean 的用途和功能。

**@Named**

`@Named` 注解来自 Java 依赖注入规范（CDI，Contexts and Dependency Injection），该规范是 Java EE（现 Jakarta EE）的一部分，主要用于在 Java 应用中实现依赖注入和组件管理。使用 `@Named` 注解可以将一个类标记为一个可被注入的组件。Spring 框架也支持 `@Named` 注解，它的作用类似于 Spring 中的 `@Component` 注解。

**@ManagedBean**

`@ManagedBean` 注解来自 JavaServer Faces（JSF）规范，JSF 是一个用于构建 Java Web 应用程序的用户界面的框架。Spring 框架也支持 `@ManagedBean` 注解，它的作用类似于 Spring 中的 `@Component` 注解。





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

