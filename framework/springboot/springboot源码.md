# springboot



## 启动流程

1. 通过`main`启动`springboot`
2. 创建`SpringApplication`
   1. 初始化属性字段，判断`WebApplicationType`类型是`REACTIVE`、`SERVLET`
   2. 从`META-INF/spring.factories`读取`BootstrapRegistryInitializer`并实例化，设置给`bootstrapRegistryInitializers`
   3. 从`META-INF/spring.factories`读取`ApplicationContextInitializer`并实例化，设置给`initializers`
   4. 从`META-INF/spring.factories`读取`ApplicationListener`并实例化，设置给`listeners`
   5. 设置主类`mainApplicationClass`
3. `run`方法启动
   1. 创建`Startup`和`shutdown`钩子函数设置
   2. 创建`DefaultBootstrapContext`，调用所有的`BootstrapRegistryInitializer`
   3. 从`META-INF/spring.factories`读取`SpringApplicationRunListener`并实例化，如果有`applicationHook`也加入集合，封装成`SpringApplicationRunListeners`
   4. 发布`ApplicationStartingEvent`事件
      1. `LoggingApplicationListener`处理日志
      2. `BackgroundPreinitializingApplicationListener`什么也没做
   5. 将命令行参数`args`解析封装成`DefaultApplicationArguments`
   6. 准备环境
      1. 根据`WebApplicationType`创建环境`ApplicationServletEnvironment`
      2. 配置环境
         1. 创建`ApplicationConversionService`设置到环境中
            1. 设置默认的`Converter`
            2. 设置格式化的`Converter`
               1. 设置`@NumberFormat`的`AnnotationPrinterConverter`和`AnnotationParserConverter`
               2. 注册日期的常见`Converter`和`Formatter`
               3. 设置`@DateTimeFormat`的`AnnotationPrinterConverter`和`AnnotationParserConverter`
               4. 设置`@DurationFormat`的`AnnotationPrinterConverter`和`AnnotationParserConverter`
            3. 添加应用扩展的`Converter`和`Formatter`
         2. 处理命令行参数加入环境，添加`ApplicationInfoPropertySource`
         3. 添加`ConfigurationPropertySources`
         4. 发布`ApplicationEnvironmentPreparedEvent`事件
            1. `EnvironmentPostProcessorApplicationListener`处理`EnvironmentPostProcessor`
               1. `RandomValuePropertySourceEnvironmentPostProcessor`添加处理`random`属性的`RandomValuePropertySource`
               2. `SpringApplicationJsonEnvironmentPostProcessor`解析处理`spring.application.json`的`JsonPropertySource`
               3. `ConfigDataEnvironmentPostProcessor`导入解析`properties、xml、yml、yaml`的配置文件
            2. `LoggingApplicationListener`处理日志
            3. `BackgroundPreinitializingApplicationListener`启动线程处理`BackgroundPreinitializer`
   7. 打印`Banner`日志
   8. 创建`AnnotationConfigServletWebServerApplicationContext`
   9. 准备`AnnotationConfigServletWebServerApplicationContext`
      1. 设置`beanNameGenerator、resourceLoader、conversionService`
      2. 调用所有的`ApplicationContextInitializer`
      3. 发布`ApplicationContextInitializedEvent`事件
      4. 发布`BootstrapContextClosedEvent`事件
      5. 设置`allowCircularReferences`和`allowBeanDefinitionOverriding`
      6. 设置懒初始化和`keepAlive`，添加`PropertySourceOrderingBeanFactoryPostProcessor`
      7. 将主类作为`beanDefinition`加入容器
      8. 将`springApplication`的监听器全部加入容器，发布`ApplicationPreparedEvent`事件
   10. 刷新`AnnotationConfigServletWebServerApplicationContext`
       1. 将当前容器加入到`shutdownhook`的监视中，当`jvm`关闭时优雅等待容器关闭
       2. `refresh`
          1. 注册`request、session`作用域到容器中
          2. `invokeBeanFactoryPostProcessors`时处理`ConfigurationClassPostProcessor`
          3. 处理`springboot`启动主类上的注解`@SpringBootApplication`
             1. 扫描主类所在包下的类加入容器，递归处理注解
             2. 收集`@EnableAutoConfiguration`上的`@Import(AutoConfigurationImportSelector.class)`和`@AutoConfigurationPackage`上的`@Import(AutoConfigurationPackages.Registrar.class)`
             3. `Registrar`属于`ImportBeanDefinitionRegistrar`，加入集合稍后处理
             4. `AutoConfigurationImportSelector`属于`DeferredImportSelector`，加入`deferredImportSelectorHandler`稍后处理
             5. `deferredImportSelectorHandler`处理`AutoConfigurationImportSelector`
                1. 读取所有的`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`类
                2. 移除重复项和排除项，过滤类
                3. 发布`AutoConfigurationImportEvent`事件
                4. 所有导入类重新处理注解
             6. 处理`ImportBeanDefinitionRegistrar`
                1. `Registrar`注册`BasePackages`的`beanDefinition`到容器中
          4. `onRefresh`
             1. 将`WebApplicationContextInitializer`包装添加到`tomcat`中`addServletContainerInitializer`
             2. 当容器启动时，回调`WebApplicationContextInitializer`
             3. 获取创建容器中所有的`ServletContextInitializer`类，分别处理`ServletRegistrationBean、FilterRegistrationBean、ServletListenerRegistrationBean`，包括`DispatcherServletRegistrationBean`
             4. 调用`ServletContextInitializer`类的`onStartup`，将`Servlet、Filter、ServletListener`加入`ServletContext`
          5. 创建所有`bean`
   11. 发布`ApplicationStartedEvent`事件
   12. 查询容器中所有的`Runner`类，实例化并执行
   13. 发布`ApplicationReadyEvent`事件
   14. 在上述完成后，`DispatcherServlet`内部才开始初始化设置九大组件，原因没找到







### main

```java
@SpringBootApplication
//@ServletComponentScan
public class SpringbootcodestudyApplication {
	
    //通过main函数启动springboot容器
    public static void main(String[] args) {
        SpringApplication.run(SpringbootcodestudyApplication.class, args);
    }


//    @Bean
//    public ServletRegistrationBean<MyServlet> getServletRegistrationBean(){
//        ServletRegistrationBean<MyServlet> bean = new ServletRegistrationBean<>(new MyServlet());
//        bean.setLoadOnStartup(1);
//        return bean;
//    }

}
```



### SpringApplication

```java
public class SpringApplication {
    
    //创建SpringApplication并且启动
    public static ConfigurableApplicationContext run(Class<?>[] primarySources, String[] args) {
		return new SpringApplication(primarySources).run(args);
	}

    public SpringApplication(@Nullable ResourceLoader resourceLoader, Class<?>... primarySources) {
		this.resourceLoader = resourceLoader;
		Assert.notNull(primarySources, "'primarySources' must not be null");
		this.primarySources = new LinkedHashSet<>(Arrays.asList(primarySources));
         //判断WebApplicationType为SERVLET
		this.properties.setWebApplicationType(WebApplicationType.deduceFromClasspath());
         //从META-INF/spring.factories加载BootstrapRegistryInitializer类型的对象
		this.bootstrapRegistryInitializers = new ArrayList<>(
				getSpringFactoriesInstances(BootstrapRegistryInitializer.class));
         //从spring.factories中设置Initializers和Listeners
		setInitializers((Collection) getSpringFactoriesInstances(ApplicationContextInitializer.class));
		setListeners((Collection) getSpringFactoriesInstances(ApplicationListener.class));
         //设置main的类
		this.mainApplicationClass = deduceMainApplicationClass();
	}
    
	private <T> List<T> getSpringFactoriesInstances(Class<T> type) {
		return getSpringFactoriesInstances(type, null);
	}
	//从META-INF/spring.factories加载类
	private <T> List<T> getSpringFactoriesInstances(Class<T> type, @Nullable ArgumentResolver argumentResolver) {
		return SpringFactoriesLoader.forDefaultResourceLocation(getClassLoader()).load(type, argumentResolver);
	}
    
    
    //启动核心流程
    public ConfigurableApplicationContext run(String... args) {
         //创建启动耗时记录
		Startup startup = Startup.create();
         //设置是否注册jvm关闭钩子函数
		if (this.properties.isRegisterShutdownHook()) {
			SpringApplication.shutdownHook.enableShutdownHookAddition();
		}
         //创建BootstrapContext上下文
		DefaultBootstrapContext bootstrapContext = createBootstrapContext();
		ConfigurableApplicationContext context = null;
		configureHeadlessProperty();
         //获取SpringApplicationRunListeners
		SpringApplicationRunListeners listeners = getRunListeners(args);
         //发布 starting 事件
		listeners.starting(bootstrapContext, this.mainApplicationClass);
		try {
             //封装解析命令行参数 --key=value
			ApplicationArguments applicationArguments = new DefaultApplicationArguments(args);
			ConfigurableEnvironment environment = prepareEnvironment(listeners, bootstrapContext, applicationArguments);
             //打印banner图标
			Banner printedBanner = printBanner(environment);
             //创建ApplicationContext
			context = createApplicationContext();
			context.setApplicationStartup(this.applicationStartup);
             //准备设置AnnotationConfigServletWebServerApplicationContext
			prepareContext(bootstrapContext, context, environment, listeners, applicationArguments, printedBanner);
             //刷新容器
			refreshContext(context);
			afterRefresh(context, applicationArguments);
			Duration timeTakenToStarted = startup.started();
			if (this.properties.isLogStartupInfo()) {
				new StartupInfoLogger(this.mainApplicationClass, environment).logStarted(getApplicationLog(), startup);
			}
			listeners.started(context, timeTakenToStarted);
			callRunners(context, applicationArguments);
		}
		catch (Throwable ex) {
			throw handleRunFailure(context, ex, listeners);
		}
		try {
			if (context.isRunning()) {
				listeners.ready(context, startup.ready());
			}
		}
		catch (Throwable ex) {
			throw handleRunFailure(context, ex, null);
		}
		return context;
	}
    
    //创建BootstrapContext上下文对象
	private DefaultBootstrapContext createBootstrapContext() {
         //利用BootstrapRegistryInitializer初始化上下文
		DefaultBootstrapContext bootstrapContext = new DefaultBootstrapContext();
		this.bootstrapRegistryInitializers.forEach((initializer) -> initializer.initialize(bootstrapContext));
		return bootstrapContext;
	}
    
    //获取SpringApplicationRunListeners
	private SpringApplicationRunListeners getRunListeners(String[] args) {
		ArgumentResolver argumentResolver = ArgumentResolver.of(SpringApplication.class, this);
		argumentResolver = argumentResolver.and(String[].class, args);
         //从spring.factories中获取SpringApplicationRunListener
		List<SpringApplicationRunListener> listeners = getSpringFactoriesInstances(SpringApplicationRunListener.class,
				argumentResolver);
         //如果有SpringApplicationHook 从hook中获取listeners
		SpringApplicationHook hook = applicationHook.get();
		SpringApplicationRunListener hookListener = (hook != null) ? hook.getRunListener(this) : null;
		if (hookListener != null) {
			listeners = new ArrayList<>(listeners);
			listeners.add(hookListener);
		}
		return new SpringApplicationRunListeners(logger, listeners, this.applicationStartup);
	}
    
    //准备环境
	private ConfigurableEnvironment prepareEnvironment(SpringApplicationRunListeners listeners,
			DefaultBootstrapContext bootstrapContext, ApplicationArguments applicationArguments) {
		// Create and configure the environment
         //创建Environment
		ConfigurableEnvironment environment = getOrCreateEnvironment();
         //配置Environment
		configureEnvironment(environment, applicationArguments.getSourceArgs());
         //configurationProperties排序到第一位
		ConfigurationPropertySources.attach(environment);
         //发布ApplicationEnvironmentPreparedEvent事件
		listeners.environmentPrepared(bootstrapContext, environment);
        //ApplicationInfoPropertySource排序到最后一位
		ApplicationInfoPropertySource.moveToEnd(environment);
		DefaultPropertiesPropertySource.moveToEnd(environment);
		Assert.state(!environment.containsProperty("spring.main.environment-prefix"),
				"Environment prefix cannot be set via properties.");
		bindToSpringApplication(environment);
		if (!this.isCustomEnvironment) {
			EnvironmentConverter environmentConverter = new EnvironmentConverter(getClassLoader());
			environment = environmentConverter.convertEnvironmentIfNecessary(environment, deduceEnvironmentClass());
		}
         //排序
		ConfigurationPropertySources.attach(environment);
		return environment;
	}
    
	private ConfigurableEnvironment getOrCreateEnvironment() {
		if (this.environment != null) {
			return this.environment;
		}
         //根据SERVLET还是REACTIVE 来创建 ApplicationServletEnvironment和ApplicationReactiveWebEnvironment
		WebApplicationType webApplicationType = this.properties.getWebApplicationType();
		ConfigurableEnvironment environment = this.applicationContextFactory.createEnvironment(webApplicationType);
		if (environment == null && this.applicationContextFactory != ApplicationContextFactory.DEFAULT) {
			environment = ApplicationContextFactory.DEFAULT.createEnvironment(webApplicationType);
		}
         //返回
		return (environment != null) ? environment : new ApplicationEnvironment();
	}
    
    //配置Environment
	protected void configureEnvironment(ConfigurableEnvironment environment, String[] args) {
         //添加转换服务
		if (this.addConversionService) {
			environment.setConversionService(new ApplicationConversionService());
		}
         //配置PropertySources
		configurePropertySources(environment, args);
		configureProfiles(environment, args);
	}
    
    //配置PropertySources
    protected void configurePropertySources(ConfigurableEnvironment environment, String[] args) {
		MutablePropertySources sources = environment.getPropertySources();
         //合并defaultProperties和environment
		if (!CollectionUtils.isEmpty(this.defaultProperties)) {
			DefaultPropertiesPropertySource.addOrMerge(this.defaultProperties, sources);
		}
         //有命令行参数 加入environment
		if (this.addCommandLineProperties && args.length > 0) {
			String name = CommandLinePropertySource.COMMAND_LINE_PROPERTY_SOURCE_NAME;
			PropertySource<?> source = sources.get(name);
			if (source != null) {
				CompositePropertySource composite = new CompositePropertySource(name);
				composite
					.addPropertySource(new SimpleCommandLinePropertySource("springApplicationCommandLineArgs", args));
				composite.addPropertySource(source);
				sources.replace(name, composite);
			}
			else {
				sources.addFirst(new SimpleCommandLinePropertySource(args));
			}
		}
		environment.getPropertySources().addLast(new ApplicationInfoPropertySource(this.mainApplicationClass));
	}
    
    //根据WebApplicationType是SERVLET 创建 AnnotationConfigServletWebServerApplicationContext
	protected ConfigurableApplicationContext createApplicationContext() {
		ConfigurableApplicationContext context = this.applicationContextFactory
			.create(this.properties.getWebApplicationType());
		Assert.state(context != null, "ApplicationContextFactory created null context");
		return context;
	}
    
	private void prepareContext(DefaultBootstrapContext bootstrapContext, ConfigurableApplicationContext context,
			ConfigurableEnvironment environment, SpringApplicationRunListeners listeners,
			ApplicationArguments applicationArguments, @Nullable Banner printedBanner) {
         //设置环境
		context.setEnvironment(environment);
         //设置属性
		postProcessApplicationContext(context);
		addAotGeneratedInitializerIfNecessary(this.initializers);
         //调用ApplicationContextInitializer的初始化方法
		applyInitializers(context);
         //发布ApplicationContextInitializedEvent事件
		listeners.contextPrepared(context);
         //关闭bootstrapContext
		bootstrapContext.close(context);
         //打印启动日志
		if (this.properties.isLogStartupInfo()) {
			logStartupInfo(context);
			logStartupProfileInfo(context);
		}
         //注册命令行参数到beanfactory中
		// Add boot specific singleton beans
		ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
		beanFactory.registerSingleton("springApplicationArguments", applicationArguments);
		if (printedBanner != null) {
			beanFactory.registerSingleton("springBootBanner", printedBanner);
		}
         //设置循环依赖等属性
		if (beanFactory instanceof AbstractAutowireCapableBeanFactory autowireCapableBeanFactory) {
			autowireCapableBeanFactory.setAllowCircularReferences(this.properties.isAllowCircularReferences());
			if (beanFactory instanceof DefaultListableBeanFactory listableBeanFactory) {
				listableBeanFactory.setAllowBeanDefinitionOverriding(this.properties.isAllowBeanDefinitionOverriding());
			}
		}
         //设置懒初始化
		if (this.properties.isLazyInitialization()) {
			context.addBeanFactoryPostProcessor(new LazyInitializationBeanFactoryPostProcessor());
		}
         //应用存活
		if (this.properties.isKeepAlive()) {
			context.addApplicationListener(new KeepAlive());
		}
		context.addBeanFactoryPostProcessor(new PropertySourceOrderingBeanFactoryPostProcessor(context));
		if (!AotDetector.useGeneratedArtifacts()) {
			// Load the sources
			Set<Object> sources = getAllSources();
			Assert.state(!ObjectUtils.isEmpty(sources), "No sources defined");
             //将main方法所在的类包装成beanDefinition加入容器
			load(context, sources.toArray(new Object[0]));
		}
         //发布ApplicationPreparedEvent事件
		listeners.contextLoaded(context);
	}

	protected void postProcessApplicationContext(ConfigurableApplicationContext context) {
        //设置beanName生成器
		if (this.beanNameGenerator != null) {
			context.getBeanFactory()
				.registerSingleton(AnnotationConfigUtils.CONFIGURATION_BEAN_NAME_GENERATOR, this.beanNameGenerator);
		}
         //设置资源加载器
		if (this.resourceLoader != null) {
			if (context instanceof GenericApplicationContext genericApplicationContext) {
				genericApplicationContext.setResourceLoader(this.resourceLoader);
			}
			if (context instanceof DefaultResourceLoader defaultResourceLoader) {
				defaultResourceLoader.setClassLoader(this.resourceLoader.getClassLoader());
			}
		}
         //设置转换服务
		if (this.addConversionService) {
			context.getBeanFactory().setConversionService(context.getEnvironment().getConversionService());
		}
	}
    
    //调用执行ApplicationContextInitializer
	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected void applyInitializers(ConfigurableApplicationContext context) {
		for (ApplicationContextInitializer initializer : getInitializers()) {
			Class<?> requiredType = GenericTypeResolver.resolveTypeArgument(initializer.getClass(),
					ApplicationContextInitializer.class);
			Assert.state(requiredType != null,
					() -> "No generic type found for initializr of type " + initializer.getClass());
			Assert.state(requiredType.isInstance(context), "Unable to call initializer");
			initializer.initialize(context);
		}
	}
}
```

### SpringFactoriesLoader

```java
public class SpringFactoriesLoader {

    public static final String FACTORIES_RESOURCE_LOCATION = "META-INF/spring.factories";

	public static SpringFactoriesLoader forDefaultResourceLocation(@Nullable ClassLoader classLoader) {
		return forResourceLocation(FACTORIES_RESOURCE_LOCATION, classLoader);
	}
    
    //从META-INF/spring.factories加载类
    public static SpringFactoriesLoader forResourceLocation(String resourceLocation, @Nullable ClassLoader classLoader) {
		Assert.hasText(resourceLocation, "'resourceLocation' must not be empty");
		ClassLoader resourceClassLoader = (classLoader != null ? classLoader :
				SpringFactoriesLoader.class.getClassLoader());
		Map<String, Factories> factoriesCache = cache.computeIfAbsent(
				resourceClassLoader, key -> new ConcurrentReferenceHashMap<>());
		Factories factories = factoriesCache.computeIfAbsent(resourceLocation, key ->
				new Factories(loadFactoriesResource(resourceClassLoader, resourceLocation)));
		return new SpringFactoriesLoader(classLoader, factories.byType());
	}

    //从META-INF/spring.factories加载类
	protected static Map<String, List<String>> loadFactoriesResource(ClassLoader classLoader, String resourceLocation) {
		Map<String, List<String>> result = new LinkedHashMap<>();
		try {
			Enumeration<URL> urls = classLoader.getResources(resourceLocation);
			while (urls.hasMoreElements()) {
				UrlResource resource = new UrlResource(urls.nextElement());
				Properties properties = PropertiesLoaderUtils.loadProperties(resource);
				properties.forEach((name, value) -> {
					String[] factoryImplementationNames = StringUtils.commaDelimitedListToStringArray((String) value);
					List<String> implementations = result.computeIfAbsent(((String) name).trim(),
							key -> new ArrayList<>(factoryImplementationNames.length));
					Arrays.stream(factoryImplementationNames).map(String::trim).forEach(implementations::add);
				});
			}
			result.replaceAll(SpringFactoriesLoader::toDistinctUnmodifiableList);
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Unable to load factories from location [" + resourceLocation + "]", ex);
		}
		return Collections.unmodifiableMap(result);
	}
}
```



### SpringApplicationShutdownHook

**Spring Boot 用于实现 JVM 关闭时“优雅停机（graceful shutdown）”的核心机制**。它通过 **JVM Shutdown Hook** 统一管理 Spring 容器的关闭顺序、等待逻辑以及用户自定义的关闭回调。

```java
class SpringApplicationShutdownHook implements Runnable {

    private static final int SLEEP = 50;

    private static final long TIMEOUT = TimeUnit.MINUTES.toMillis(10);

    private static final Log logger = LogFactory.getLog(SpringApplicationShutdownHook.class);

    private final Handlers handlers = new Handlers();
	//当前 活跃且由 SpringApplication 管理的容器
    private final Set<ConfigurableApplicationContext> contexts = new LinkedHashSet<>();
	//已开始关闭但尚未完全 inactive 的容器
    //shutdownhook的生命周期长于applicationContext 使用弱引用对象 避免容器关闭后对象无法被GC回收
    private final Set<ConfigurableApplicationContext> closedContexts = Collections.newSetFromMap(new WeakHashMap<>());

    private final ApplicationContextClosedListener contextCloseListener = new ApplicationContextClosedListener();

    private final AtomicBoolean shutdownHookAdded = new AtomicBoolean();

    private volatile boolean shutdownHookAdditionEnabled;

    private boolean inProgress;
    
	void registerApplicationContext(ConfigurableApplicationContext context) {
		addRuntimeShutdownHookIfNecessary();
		synchronized (SpringApplicationShutdownHook.class) {
			assertNotInProgress();
			context.addApplicationListener(this.contextCloseListener);
			this.contexts.add(context);
		}
	}

	private void addRuntimeShutdownHookIfNecessary() {
		if (this.shutdownHookAdditionEnabled && this.shutdownHookAdded.compareAndSet(false, true)) {
			addRuntimeShutdownHook();
		}
	}
	
    //加入JVM的关闭钩子函数中 当JVM开始关闭时调用
	void addRuntimeShutdownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread(this, "SpringApplicationShutdownHook"));
	}

	void deregisterFailedApplicationContext(ConfigurableApplicationContext applicationContext) {
		synchronized (SpringApplicationShutdownHook.class) {
			Assert.state(!applicationContext.isActive(), "Cannot unregister active application context");
			SpringApplicationShutdownHook.this.contexts.remove(applicationContext);
		}
	}

	@Override
	public void run() {
		Set<ConfigurableApplicationContext> contexts;
		Set<ConfigurableApplicationContext> closedContexts;
		List<Handler> handlers;
		synchronized (SpringApplicationShutdownHook.class) {
			this.inProgress = true;
			contexts = new LinkedHashSet<>(this.contexts);
			closedContexts = new LinkedHashSet<>(this.closedContexts);
			handlers = new ArrayList<>(this.handlers.getActions());
			Collections.reverse(handlers);
		}
         //关闭容器
		contexts.forEach(this::closeAndWait);
		closedContexts.forEach(this::closeAndWait);
         //调用handler
		handlers.forEach(Handler::run);
	}
    
    //关闭容器
    private void closeAndWait(ConfigurableApplicationContext context) {
		if (!context.isActive()) {
			return;
		}
         //关闭容器 可能是异步关闭
		context.close();
		try {
             //循环等待容器关闭 超时报错
			int waited = 0;
			while (context.isActive()) {
				if (waited > TIMEOUT) {
					throw new TimeoutException();
				}
				Thread.sleep(SLEEP);
				waited += SLEEP;
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			logger.warn("Interrupted waiting for application context " + context + " to become inactive");
		}
		catch (TimeoutException ex) {
			logger.warn("Timed out waiting for application context " + context + " to become inactive", ex);
		}
	}
    
    //用户自定义的handler逻辑
    record Handler(Runnable runnable) {

		@Override
		public int hashCode() {
			return System.identityHashCode(this.runnable);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null || getClass() != obj.getClass()) {
				return false;
			}
			return this.runnable == ((Handler) obj).runnable;
		}

		void run() {
			this.runnable.run();
		}

	}

	/**
	 * {@link ApplicationListener} to track closed contexts.
	 * ContextClosedEvent事件的监听器
	 */
	private final class ApplicationContextClosedListener implements ApplicationListener<ContextClosedEvent> {

		@Override
		public void onApplicationEvent(ContextClosedEvent event) {
			// The ContextClosedEvent is fired at the start of a call to {@code close()}
			// and if that happens in a different thread then the context may still be
			// active. Rather than just removing the context, we add it to a {@code
			// closedContexts} set. This is weak set so that the context can be GC'd once
			// the {@code close()} method returns.
			synchronized (SpringApplicationShutdownHook.class) {
                 //将当前容器加入到closedContexts
				ApplicationContext applicationContext = event.getApplicationContext();
				SpringApplicationShutdownHook.this.contexts.remove(applicationContext);
				SpringApplicationShutdownHook.this.closedContexts
					.add((ConfigurableApplicationContext) applicationContext);
			}
		}

	}

}
```

### EventPublishingRunListener

用于发布`SpringApplicationEvent`事件的`SpringApplicationRunListener`

```java
class EventPublishingRunListener implements SpringApplicationRunListener, Ordered {

	private final SpringApplication application;

	private final String[] args;
	//事件发布器
	private final SimpleApplicationEventMulticaster initialMulticaster;

	EventPublishingRunListener(SpringApplication application, String[] args) {
		this.application = application;
		this.args = args;
		this.initialMulticaster = new SimpleApplicationEventMulticaster();
	}

	@Override
	public int getOrder() {
		return 0;
	}
    
    
	@Override
	public void starting(ConfigurableBootstrapContext bootstrapContext) {
		multicastInitialEvent(new ApplicationStartingEvent(bootstrapContext, this.application, this.args));
	}
	//发布事件
	private void multicastInitialEvent(ApplicationEvent event) {
         //从SpringApplication中将listeners都加入initialMulticaster
		refreshApplicationListeners();
         //发布事件
		this.initialMulticaster.multicastEvent(event);
	}
    
    //从SpringApplication中将listeners都加入initialMulticaster
    private void refreshApplicationListeners() {
		this.application.getListeners().forEach(this.initialMulticaster::addApplicationListener);
	}
    
}
```



### ApplicationConversionService

为 Spring Boot 应用的**类型转换**与**格式化**服务

```java
//整个转换服务的原理是 不论类型转换器和格式化器都封装成converter，负责由A->B类型的转换 
public class ApplicationConversionService extends FormattingConversionService {
	//创建ConversionService
	private ApplicationConversionService(@Nullable StringValueResolver embeddedValueResolver, boolean unmodifiable) {
         //设置${}参数解析器
		if (embeddedValueResolver != null) {
			setEmbeddedValueResolver(embeddedValueResolver);
		}
         //配置ConversionService
		configure(this);
		this.unmodifiable = unmodifiable;
	}
    
	public static void configure(FormatterRegistry registry) {
         //添加默认的转换器
		DefaultConversionService.addDefaultConverters(registry);
         //添加默认的格式化器 包括@NumberFormat和@DateTimeFormat的格式化
		DefaultFormattingConversionService.addDefaultFormatters(registry);
         //添加application用的转换器和格式化器
		addApplicationFormatters(registry);
		addApplicationConverters(registry);
	}
    
}
```



### DefaultFormattingConversionService

```java
public class DefaultFormattingConversionService extends FormattingConversionService {

    public static void addDefaultFormatters(FormatterRegistry formatterRegistry) {
         //@NumberFormat注解的格式化器
		// Default handling of number values
		formatterRegistry.addFormatterForFieldAnnotation(new NumberFormatAnnotationFormatterFactory());

		// Default handling of monetary values
		if (JSR_354_PRESENT) {
			formatterRegistry.addFormatter(new CurrencyUnitFormatter());
			formatterRegistry.addFormatter(new MonetaryAmountFormatter());
			formatterRegistry.addFormatterForFieldAnnotation(new Jsr354NumberFormatAnnotationFormatterFactory());
		}

		// Default handling of date-time values
        
		// 新时间api的@DateTimeFormat的格式化器
		// just handling JSR-310 specific date and time types
		new DateTimeFormatterRegistrar().registerFormatters(formatterRegistry);
        
		// 老时间api的@DateTimeFormat的格式化器
		// regular DateFormat-based Date, Calendar, Long converters
		new DateFormatterRegistrar().registerFormatters(formatterRegistry);
	}
}
```







### EnvironmentPostProcessorApplicationListener

```java
public class EnvironmentPostProcessorApplicationListener implements SmartApplicationListener, Ordered {

    //处理环境准备事件
	private void onApplicationEnvironmentPreparedEvent(ApplicationEnvironmentPreparedEvent event) {
		ConfigurableEnvironment environment = event.getEnvironment();
		SpringApplication application = event.getSpringApplication();
         //从spring.factories中获取EnvironmentPostProcessor
		List<EnvironmentPostProcessor> postProcessors = getEnvironmentPostProcessors(application.getResourceLoader(),
				event.getBootstrapContext());
		addAotGeneratedEnvironmentPostProcessorIfNecessary(postProcessors, application);
         //调用postProcessEnvironment
		for (EnvironmentPostProcessor postProcessor : postProcessors) {
			postProcessor.postProcessEnvironment(environment, application);
		}
	}
}
```

#### RandomValuePropertySourceEnvironmentPostProcessor

让配置文件和 @Value 中可以使用随机值占位符

```properties
app.instance-id=${random.uuid}
app.port=${random.int[10000,20000]}
```

```java
public class RandomValuePropertySourceEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    @Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
         //将RandomValuePropertySource放入环境中
		RandomValuePropertySource.addToEnvironment(environment, this.logger);
	}
}
```

#### ConfigDataEnvironmentPostProcessor

```java
public class ConfigDataEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    
	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		postProcessEnvironment(environment, application.getResourceLoader(), application.getAdditionalProfiles());
	}

	void postProcessEnvironment(ConfigurableEnvironment environment, @Nullable ResourceLoader resourceLoader,
			Collection<String> additionalProfiles) {
		this.logger.trace("Post-processing environment to add config data");
		resourceLoader = (resourceLoader != null) ? resourceLoader : new DefaultResourceLoader();
         //读取配置文件并加载到环境中
		getConfigDataEnvironment(environment, resourceLoader, additionalProfiles).processAndApply();
	}
	//创建ConfigDataEnvironment
	ConfigDataEnvironment getConfigDataEnvironment(ConfigurableEnvironment environment, ResourceLoader resourceLoader,
			Collection<String> additionalProfiles) {
		return new ConfigDataEnvironment(this.logFactory, this.bootstrapContext, environment, resourceLoader,
				additionalProfiles, this.environmentUpdateListener);
	}
}
```

#### ConfigDataEnvironment

```java
class ConfigDataEnvironment {
    //默认查找位置 
    static final ConfigDataLocation[] DEFAULT_SEARCH_LOCATIONS;
	static {
		List<ConfigDataLocation> locations = new ArrayList<>();
		locations.add(ConfigDataLocation.of("optional:classpath:/;optional:classpath:/config/"));
		locations.add(ConfigDataLocation.of("optional:file:./;optional:file:./config/;optional:file:./config/*/"));
		DEFAULT_SEARCH_LOCATIONS = locations.toArray(new ConfigDataLocation[0]);
	}
    
    ConfigDataEnvironment(DeferredLogFactory logFactory, ConfigurableBootstrapContext bootstrapContext,
			ConfigurableEnvironment environment, ResourceLoader resourceLoader, Collection<String> additionalProfiles,
			@Nullable ConfigDataEnvironmentUpdateListener environmentUpdateListener) {
		Binder binder = Binder.get(environment);
		this.logFactory = logFactory;
		this.logger = logFactory.getLog(getClass());
		this.notFoundAction = binder.bind(ON_NOT_FOUND_PROPERTY, ConfigDataNotFoundAction.class)
			.orElse(ConfigDataNotFoundAction.FAIL);
		this.bootstrapContext = bootstrapContext;
		this.environment = environment;
         //创建ConfigDataLocationResolvers
		this.resolvers = createConfigDataLocationResolvers(logFactory, bootstrapContext, binder, resourceLoader);
		this.additionalProfiles = additionalProfiles;
		this.environmentUpdateListener = (environmentUpdateListener != null) ? environmentUpdateListener
				: ConfigDataEnvironmentUpdateListener.NONE;
		this.loaders = new ConfigDataLoaders(logFactory, bootstrapContext,
				SpringFactoriesLoader.forDefaultResourceLocation(resourceLoader.getClassLoader()));
		this.contributors = createContributors(binder);
	}
    //查找spring.factories中的ConfigDataLocationResolver和内部的PropertySourceLoader
    protected ConfigDataLocationResolvers createConfigDataLocationResolvers(DeferredLogFactory logFactory,
			ConfigurableBootstrapContext bootstrapContext, Binder binder, ResourceLoader resourceLoader) {
		return new ConfigDataLocationResolvers(logFactory, bootstrapContext, binder, resourceLoader,
				SpringFactoriesLoader.forDefaultResourceLocation(resourceLoader.getClassLoader()));
	}
}
```

#### PropertiesPropertySourceLoader

负责处理`properties`和`xml`配置文件的加载

```java
public class PropertiesPropertySourceLoader implements PropertySourceLoader {

    private static final String XML_FILE_EXTENSION = ".xml";

    @Override
    public String[] getFileExtensions() {
       return new String[] { "properties", "xml" };
    }

    @Override
    public List<PropertySource<?>> load(String name, Resource resource) throws IOException {
       List<Map<String, ?>> properties = loadProperties(resource);
       if (properties.isEmpty()) {
          return Collections.emptyList();
       }
       List<PropertySource<?>> propertySources = new ArrayList<>(properties.size());
       for (int i = 0; i < properties.size(); i++) {
          String documentNumber = (properties.size() != 1) ? " (document #" + i + ")" : "";
          propertySources.add(new OriginTrackedMapPropertySource(name + documentNumber,
                Collections.unmodifiableMap(properties.get(i)), true));
       }
       return propertySources;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private List<Map<String, ?>> loadProperties(Resource resource) throws IOException {
       String filename = resource.getFilename();
       List<Map<String, ?>> result = new ArrayList<>();
       if (filename != null && filename.endsWith(XML_FILE_EXTENSION)) {
          result.add((Map) PropertiesLoaderUtils.loadProperties(resource));
       }
       else {
          List<Document> documents = new OriginTrackedPropertiesLoader(resource).load();
          documents.forEach((document) -> result.add(document.asMap()));
       }
       return result;
    }

}
```

#### YamlPropertySourceLoader

负责`yml、yaml`配置文件的加载

```java
public class YamlPropertySourceLoader implements PropertySourceLoader {

    @Override
    public String[] getFileExtensions() {
       return new String[] { "yml", "yaml" };
    }

    @Override
    public List<PropertySource<?>> load(String name, Resource resource) throws IOException {
       if (!ClassUtils.isPresent("org.yaml.snakeyaml.Yaml", getClass().getClassLoader())) {
          throw new IllegalStateException(
                "Attempted to load " + name + " but snakeyaml was not found on the classpath");
       }
       List<Map<String, Object>> loaded = new OriginTrackedYamlLoader(resource).load();
       if (loaded.isEmpty()) {
          return Collections.emptyList();
       }
       List<PropertySource<?>> propertySources = new ArrayList<>(loaded.size());
       for (int i = 0; i < loaded.size(); i++) {
          String documentNumber = (loaded.size() != 1) ? " (document #" + i + ")" : "";
          propertySources.add(new OriginTrackedMapPropertySource(name + documentNumber,
                Collections.unmodifiableMap(loaded.get(i)), true));
       }
       return propertySources;
    }

}
```



### AnnotationConfigServletWebServerApplicationContext

```java
public class AnnotationConfigServletWebServerApplicationContext extends ServletWebServerApplicationContext
       implements AnnotationConfigRegistry {
    
    private final AnnotatedBeanDefinitionReader reader;

	private final ClassPathBeanDefinitionScanner scanner;

	private final Set<Class<?>> annotatedClasses = new LinkedHashSet<>();

	private String @Nullable [] basePackages;
    
    public AnnotationConfigServletWebServerApplicationContext() {
		this.reader = new AnnotatedBeanDefinitionReader(this);
		this.scanner = new ClassPathBeanDefinitionScanner(this);
	}
    
    
    @Override
	protected void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		super.postProcessBeanFactory(beanFactory);
         //如果有basepackage和annotatedClasess
		if (!ObjectUtils.isEmpty(this.basePackages)) {
			this.scanner.scan(this.basePackages);
		}
		if (!this.annotatedClasses.isEmpty()) {
			this.reader.register(ClassUtils.toClassArray(this.annotatedClasses));
		}
	}
}
```





#### AutoConfigurationImportSelector

```java
//扫描处理
public class AutoConfigurationImportSelector implements DeferredImportSelector, BeanClassLoaderAware,
       ResourceLoaderAware, BeanFactoryAware, EnvironmentAware, Ordered {

    public AutoConfigurationImportSelector() {
		this(null);
	}

	AutoConfigurationImportSelector(@Nullable Class<?> autoConfigurationAnnotation) {
		this.autoConfigurationAnnotation = (autoConfigurationAnnotation != null) ? autoConfigurationAnnotation
				: AutoConfiguration.class;
	}
       
    protected AutoConfigurationEntry getAutoConfigurationEntry(AnnotationMetadata annotationMetadata) {
		if (!isEnabled(annotationMetadata)) {
			return EMPTY_ENTRY;
		}
		AnnotationAttributes attributes = getAttributes(annotationMetadata);
         //读取所有的META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports类
		List<String> configurations = getCandidateConfigurations(annotationMetadata, attributes);
         //合并重复的
		configurations = removeDuplicates(configurations);
         //移除排除项
		Set<String> exclusions = getExclusions(annotationMetadata, attributes);
		checkExcludedClasses(configurations, exclusions);
		configurations.removeAll(exclusions);
         //过滤类
		configurations = getConfigurationClassFilter().filter(configurations);
         //发布AutoConfigurationImportEvent事件
		fireAutoConfigurationImportEvents(configurations, exclusions);
		return new AutoConfigurationEntry(configurations, exclusions);
	}
     //获取所有的自动装配类       
	protected List<String> getCandidateConfigurations(AnnotationMetadata metadata,
			@Nullable AnnotationAttributes attributes) {
        //读取所有的META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports文件
		ImportCandidates importCandidates = ImportCandidates.load(this.autoConfigurationAnnotation,
				getBeanClassLoader());
		List<String> configurations = importCandidates.getCandidates();
		Assert.state(!CollectionUtils.isEmpty(configurations),
				"No auto configuration classes found in " + "META-INF/spring/"
						+ this.autoConfigurationAnnotation.getName() + ".imports. If you "
						+ "are using a custom packaging, make sure that file is correct.");
		return configurations;
	}
           
           
	private static final class AutoConfigurationGroup
			implements DeferredImportSelector.Group, BeanClassLoaderAware, BeanFactoryAware, ResourceLoaderAware {

        //处理入口 
    	@Override
		public void process(AnnotationMetadata annotationMetadata, DeferredImportSelector deferredImportSelector) {
			Assert.state(deferredImportSelector instanceof AutoConfigurationImportSelector,
					() -> String.format("Only %s implementations are supported, got %s",
							AutoConfigurationImportSelector.class.getSimpleName(),
							deferredImportSelector.getClass().getName()));
			AutoConfigurationImportSelector autoConfigurationImportSelector = (AutoConfigurationImportSelector) deferredImportSelector;
			AutoConfigurationReplacements autoConfigurationReplacements = autoConfigurationImportSelector
				.getAutoConfigurationReplacements();
			Assert.state(
					this.autoConfigurationReplacements == null
							|| this.autoConfigurationReplacements.equals(autoConfigurationReplacements),
					"Auto-configuration replacements must be the same for each call to process");
			this.autoConfigurationReplacements = autoConfigurationReplacements;
             //处理META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
			AutoConfigurationEntry autoConfigurationEntry = autoConfigurationImportSelector
				.getAutoConfigurationEntry(annotationMetadata);
			this.autoConfigurationEntries.add(autoConfigurationEntry);
			for (String importClassName : autoConfigurationEntry.getConfigurations()) {
				this.entries.putIfAbsent(importClassName, annotationMetadata);
			}
		}
    }
       
       
}
```





#### ServletWebServerApplicationContext

```java
public class ServletWebServerApplicationContext extends GenericWebApplicationContext
       implements ConfigurableWebServerApplicationContext {
    
    
	@Override
	protected void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        //注册处理ServletContext的Aware
		beanFactory.addBeanPostProcessor(new WebApplicationContextServletContextAwareProcessor(this));
		beanFactory.ignoreDependencyInterface(ServletContextAware.class);
         //注册request和session的作用域到容器
		registerWebApplicationScopes();
	}

	@Override
	public final void refresh() throws BeansException, IllegalStateException {
		try {
			super.refresh();
		}
		catch (RuntimeException ex) {
			WebServer webServer = this.webServer;
			if (webServer != null) {
				try {
					webServer.stop();
					webServer.destroy();
				}
				catch (RuntimeException stopOrDestroyEx) {
					ex.addSuppressed(stopOrDestroyEx);
				}
			}
			throw ex;
		}
	}

	@Override
	protected void onRefresh() {
		super.onRefresh();
		try {
             //创建web容器
			createWebServer();
		}
		catch (Throwable ex) {
			throw new ApplicationContextException("Unable to start web server", ex);
		}
	}

	private void createWebServer() {
		WebServer webServer = this.webServer;
         
		ServletContext servletContext = getServletContext();
		if (webServer == null && servletContext == null) {
			StartupStep createWebServer = getApplicationStartup().start("spring.boot.webserver.create");
             //从beanFactroy中获取TomcatServletWebServerFactory
			ServletWebServerFactory factory = getWebServerFactory();
			createWebServer.tag("factory", factory.getClass().toString());
             //创建web容器
			webServer = factory.getWebServer(getSelfInitializer());
			this.webServer = webServer;
			createWebServer.end();
			getBeanFactory().registerSingleton("webServerGracefulShutdown",
					new WebServerGracefulShutdownLifecycle(webServer));
			getBeanFactory().registerSingleton("webServerStartStop", new WebServerStartStopLifecycle(this, webServer));
		}
		else if (servletContext != null) {
			try {
				getSelfInitializer().onStartup(servletContext);
			}
			catch (ServletException ex) {
				throw new ApplicationContextException("Cannot initialize servlet context", ex);
			}
		}
		initPropertySources();
	}

}
```

#### WebApplicationContextUtils

```java
public abstract class WebApplicationContextUtils {

	public static void registerWebApplicationScopes(ConfigurableListableBeanFactory beanFactory,
			@Nullable ServletContext sc) {
		//注册request和session作用域
		beanFactory.registerScope(WebApplicationContext.SCOPE_REQUEST, new RequestScope());
		beanFactory.registerScope(WebApplicationContext.SCOPE_SESSION, new SessionScope());
         //注册application作用域
		if (sc != null) {
			ServletContextScope appScope = new ServletContextScope(sc);
			beanFactory.registerScope(WebApplicationContext.SCOPE_APPLICATION, appScope);
			// Register as ServletContext attribute, for ContextCleanupListener to detect it.
			sc.setAttribute(ServletContextScope.class.getName(), appScope);
		}
		//当出现@Autowired/@Resource标注的如下依赖注入项 使用Factory动态获取注入
		beanFactory.registerResolvableDependency(ServletRequest.class, new RequestObjectFactory());
		beanFactory.registerResolvableDependency(ServletResponse.class, new ResponseObjectFactory());
		beanFactory.registerResolvableDependency(HttpSession.class, new SessionObjectFactory());
		beanFactory.registerResolvableDependency(WebRequest.class, new WebRequestObjectFactory());
		if (JSF_PRESENT) {
			FacesDependencyRegistrar.registerFacesDependencies(beanFactory);
		}
	}
}
```

#### TomcatServletWebServerFactory

```java
public class TomcatServletWebServerFactory extends TomcatWebServerFactory
       implements ConfigurableTomcatWebServerFactory, ConfigurableServletWebServerFactory, ResourceLoaderAware {

    //创建Tomcat
	@Override
	public WebServer getWebServer(ServletContextInitializer... initializers) {
		Tomcat tomcat = createTomcat();
		prepareContext(tomcat.getHost(), initializers);
		return getTomcatWebServer(tomcat);
	}
    
    
    protected Tomcat createTomcat() {
		if (this.isDisableMBeanRegistry()) {
			Registry.disableRegistry();
		}
         //创建Tomcat
		Tomcat tomcat = new Tomcat();
         //创建tomcat的临时工作目录
		File baseDir = (getBaseDirectory() != null) ? getBaseDirectory() : createTempDir("tomcat");
		tomcat.setBaseDir(baseDir.getAbsolutePath());
         //注册 Server 生命周期监听器
		for (LifecycleListener listener : getDefaultServerLifecycleListeners()) {
			tomcat.getServer().addLifecycleListener(listener);
		}
         //使用Http11NioProtocol创建Connector
		Connector connector = new Connector(getProtocol());
		connector.setThrowOnFailure(true);
         //将 Connector 加入 Service
		tomcat.getService().addConnector(connector);
         //定制化connector
		customizeConnector(connector);
         //设置默认 Connector
		tomcat.setConnector(connector);
         //配置tomcat线程池
		registerConnectorExecutor(tomcat, connector);
         //禁止 Tomcat 自动扫描部署目录
		tomcat.getHost().setAutoDeploy(false);
         //配置 Engine
		configureEngine(tomcat.getEngine());
         //注册额外 Connector
		for (Connector additionalConnector : this.getAdditionalConnectors()) {
			tomcat.getService().addConnector(additionalConnector);
			registerConnectorExecutor(tomcat, additionalConnector);
		}
		return tomcat;
	}
    
    
    protected void customizeConnector(Connector connector) {
		int port = Math.max(getPort(), 0);
		connector.setPort(port);
		if (StringUtils.hasText(getServerHeader())) {
			connector.setProperty("server", getServerHeader());
		}
		if (connector.getProtocolHandler() instanceof AbstractProtocol<?> abstractProtocol) {
			customizeProtocol(abstractProtocol);
		}
		invokeProtocolHandlerCustomizers(connector.getProtocolHandler());
		if (getUriEncoding() != null) {
			connector.setURIEncoding(getUriEncoding().name());
		}
		if (getHttp2() != null && getHttp2().isEnabled()) {
			connector.addUpgradeProtocol(new Http2Protocol());
		}
		Ssl ssl = getSsl();
		if (Ssl.isEnabled(ssl)) {
			customizeSsl(connector, ssl);
		}
		TomcatConnectorCustomizer compression = new CompressionConnectorCustomizer(getCompression());
		compression.customize(connector);
		for (TomcatConnectorCustomizer customizer : this.getConnectorCustomizers()) {
			customizer.customize(connector);
		}
	}
    
    //准备上下文
	protected void prepareContext(Host host, ServletContextInitializer[] initializers) {
		DocumentRoot documentRoot = new DocumentRoot(logger);
		documentRoot.setDirectory(this.settings.getDocumentRoot());
		File documentRootFile = documentRoot.getValidDirectory();
         //TomcatEmbeddedContext代表需要部署在tomcat中的当前应用
		TomcatEmbeddedContext context = new TomcatEmbeddedContext();
		WebResourceRoot resourceRoot = (documentRootFile != null) ? new LoaderHidingResourceRoot(context)
				: new StandardRoot(context);
		ignoringNoSuchMethodError(() -> resourceRoot.setReadOnly(true));
		context.setResources(resourceRoot);
		String contextPath = this.settings.getContextPath().toString();
		context.setName(contextPath);
		context.setDisplayName(this.settings.getDisplayName());
		context.setPath(contextPath);
		File docBase = (documentRootFile != null) ? documentRootFile : createTempDir("tomcat-docbase");
		context.setDocBase(docBase.getAbsolutePath());
		context.addLifecycleListener(new FixContextListener());
		ClassLoader parentClassLoader = (this.resourceLoader != null) ? this.resourceLoader.getClassLoader()
				: ClassUtils.getDefaultClassLoader();
		context.setParentClassLoader(parentClassLoader);
		resetDefaultLocaleMapping(context);
		addLocaleMappings(context);
		context.setCreateUploadTargets(true);
		configureTldPatterns(context);
		WebappLoader loader = new WebappLoader();
		loader.setLoaderInstance(new TomcatEmbeddedWebappClassLoader(parentClassLoader));
		loader.setDelegate(true);
		context.setLoader(loader);
		if (this.settings.isRegisterDefaultServlet()) {
			addDefaultServlet(context);
		}
		if (shouldRegisterJspServlet()) {
			addJspServlet(context);
			addJasperInitializer(context);
		}
		context.addLifecycleListener(new StaticResourceConfigurer(context));
         //封装ServletContextInitializers
		ServletContextInitializers initializersToUse = ServletContextInitializers.from(this.settings, initializers);
		host.addChild(context);
		configureContext(context, initializersToUse);
		postProcessContext(context);
	}

    
    protected void configureContext(Context context, Iterable<ServletContextInitializer> initializers) {
         //将initializers包装到DeferredServletContainerInitializers
		DeferredServletContainerInitializers deferredInitializers = new DeferredServletContainerInitializers(
				initializers);
		if (context instanceof TomcatEmbeddedContext embeddedContext) {
			embeddedContext.setDeferredStartupExceptions(deferredInitializers);
			embeddedContext.setFailCtxIfServletStartFails(true);
		}
         //加入context的初始化回调中
		context.addServletContainerInitializer(deferredInitializers, NO_CLASSES);
		for (LifecycleListener lifecycleListener : this.getContextLifecycleListeners()) {
			context.addLifecycleListener(lifecycleListener);
		}
		for (Valve valve : this.getContextValves()) {
			context.getPipeline().addValve(valve);
		}
		for (ErrorPage errorPage : getErrorPages()) {
			org.apache.tomcat.util.descriptor.web.ErrorPage tomcatErrorPage = new org.apache.tomcat.util.descriptor.web.ErrorPage();
			tomcatErrorPage.setLocation(errorPage.getPath());
			tomcatErrorPage.setErrorCode(errorPage.getStatusCode());
			tomcatErrorPage.setExceptionType(errorPage.getExceptionName());
			context.addErrorPage(tomcatErrorPage);
		}
		setMimeMappings(context);
		configureSession(context);
		configureCookieProcessor(context);
		new DisableReferenceClearingContextCustomizer().customize(context);
		for (String webListenerClassName : getSettings().getWebListenerClassNames()) {
			context.addApplicationListener(webListenerClassName);
		}
		for (TomcatContextCustomizer customizer : this.getContextCustomizers()) {
			customizer.customize(context);
		}
	}
    
}
```

#### WebApplicationContextInitializer

```java
public class WebApplicationContextInitializer {

    public void initialize(ServletContext servletContext) throws ServletException {
		prepareWebApplicationContext(servletContext);
		registerApplicationScope(servletContext, this.context.getBeanFactory());
		WebApplicationContextUtils.registerEnvironmentBeans(this.context.getBeanFactory(), servletContext);
		for (ServletContextInitializer initializerBean : new ServletContextInitializerBeans(
				this.context.getBeanFactory())) {
			initializerBean.onStartup(servletContext);
		}
	}


}
```

#### ServletContextInitializerBeans

```java
public class ServletContextInitializerBeans extends AbstractCollection<ServletContextInitializer> {
    
    @SafeVarargs
	@SuppressWarnings("varargs")
	public ServletContextInitializerBeans(ListableBeanFactory beanFactory,
			Class<? extends ServletContextInitializer>... initializerTypes) {
		this.initializers = new LinkedMultiValueMap<>();
		this.initializerTypes = (initializerTypes.length != 0) ? Arrays.asList(initializerTypes)
				: Collections.singletonList(ServletContextInitializer.class);
		addServletContextInitializerBeans(beanFactory);
		addAdaptableBeans(beanFactory);
		this.sortedList = this.initializers.values()
			.stream()
			.flatMap((value) -> value.stream().sorted(AnnotationAwareOrderComparator.INSTANCE))
			.toList();
		logMappings(this.initializers);
	}
}
```







#### ServletContainerInitializer

**Servlet 3.0 及以后**规范中的一个核心扩展点，用于在 **Web 应用启动阶段**由 Servlet 容器（如 Tomcat、Jetty）回调执行初始化逻辑。

```java
public interface ServletContainerInitializer {

    /**
     * Receives notification during startup of a web application of the classes within the web application that matched
     * the criteria defined via the {@link jakarta.servlet.annotation.HandlesTypes} annotation.
     *
     * @param c   The (possibly null) set of classes that met the specified criteria
     * @param ctx The ServletContext of the web application in which the classes were discovered
     *
     * @throws ServletException If an error occurs
     */
    void onStartup(Set<Class<?>> c, ServletContext ctx) throws ServletException;
}
```

#### DeferredServletContainerInitializers

```java
class DeferredServletContainerInitializers
       implements ServletContainerInitializer, TomcatEmbeddedContext.DeferredStartupExceptions {

    private static final Log logger = LogFactory.getLog(DeferredServletContainerInitializers.class);

    private final Iterable<ServletContextInitializer> initializers;

    private volatile @Nullable Exception startUpException;

    DeferredServletContainerInitializers(Iterable<ServletContextInitializer> initializers) {
       this.initializers = initializers;
    }
    
    //tomcat回调此方法
    @Override
	public void onStartup(Set<Class<?>> classes, ServletContext servletContext) throws ServletException {
		try {
             //调用ServletContextInitializer的所有onStartup
			for (ServletContextInitializer initializer : this.initializers) {
				initializer.onStartup(servletContext);
			}
		}
		catch (Exception ex) {
			this.startUpException = ex;
			// Prevent Tomcat from logging and re-throwing when we know we can
			// deal with it in the main thread, but log for information here.
			if (logger.isErrorEnabled()) {
				logger.error("Error starting Tomcat context. Exception: " + ex.getClass().getName() + ". Message: "
						+ ex.getMessage());
			}
		}
	}
    
}
```

#### ServletContextInitializer

在 Servlet 3.0+ 环境中，由 Spring 在启动过程中以编程方式配置 `ServletContext`

```java
@FunctionalInterface
public interface ServletContextInitializer {

    /**
     * Configure the given {@link ServletContext} with any servlets, filters, listeners
     * context-params and attributes necessary for initialization.
     * @param servletContext the {@code ServletContext} to initialize
     * @throws ServletException if any call against the given {@code ServletContext}
     * throws a {@code ServletException}
     */
    void onStartup(ServletContext servletContext) throws ServletException;

}
```

```java
ServletContextInitializer
        ↑
   RegistrationBean
        ↑
 ┌─────────────────────────────────────────┐
 │ ServletRegistrationBean                 │
 │ FilterRegistrationBean                  │
 │ DelegatingFilterProxyRegistrationBean   │
 │ ServletListenerRegistrationBean         │
 └─────────────────────────────────────────┘
```

以“Spring Bean 友好”的方式，在 Servlet 3.0+ 环境中注册 Servlet / Filter / Listener，并由 Spring 生命周期统一管理。最终效果等价于调用 `ServletContext` 的动态注册 API

##### ServletRegistrationBean

```java
public class ServletRegistrationBean<T extends Servlet> extends DynamicRegistrationBean<ServletRegistration.Dynamic> {
    private static final String[] DEFAULT_MAPPINGS = { "/*" };

	private @Nullable T servlet;

	private Set<String> urlMappings = new LinkedHashSet<>();

	private boolean alwaysMapUrl = true;

	private int loadOnStartup = -1;

	private @Nullable MultipartConfigElement multipartConfig;
    
    //注册Servlet
    @Override
	protected ServletRegistration.Dynamic addRegistration(String description, ServletContext servletContext) {
		String name = getServletName();
		return servletContext.addServlet(name, this.servlet);
	}
}
```

##### FilterRegistrationBean

```java
public class FilterRegistrationBean<T extends Filter> extends AbstractFilterRegistrationBean<T> {

    private @Nullable T filter;
    
    //注册Filter
	@Override
	protected Dynamic addRegistration(String description, ServletContext servletContext) {
		Filter filter = getFilter();
		return servletContext.addFilter(getOrDeduceName(filter), filter);
	}
}
```

##### ServletListenerRegistrationBean

```java
public class ServletListenerRegistrationBean<T extends EventListener> extends RegistrationBean {
    
	private @Nullable T listener;
    
    //注册listener
    @Override
	protected void register(String description, ServletContext servletContext) {
		try {
			servletContext.addListener(this.listener);
		}
		catch (RuntimeException ex) {
			throw new IllegalStateException("Failed to add listener '" + this.listener + "' to servlet context", ex);
		}
	}


}
```















## Filter和Interceptor

```java
浏览器
  ↓
Filter
  ↓
DispatcherServlet
  ↓
Interceptor.preHandle
  ↓
Controller
  ↓
Interceptor.postHandle
  ↓
视图渲染
  ↓
Interceptor.afterCompletion
  ↓
Filter
  ↓
响应返回
```

### OncePerRequestFilter

**保证一个 Filter 在一次请求的处理过程中只执行一次**，无论请求是通过 REQUEST、ASYNC、ERROR 或 FORWARD/INCLUDE 分派类型进入。某些请求可能会多次进入Servlet。

```java
public abstract class OncePerRequestFilter extends GenericFilterBean {
}
```





## web自动装配

### WebMvcConfigurer

`WebMvcConfigurer` 是 **Spring MVC 的核心扩展接口之一**，用于在 **启用 Spring MVC时，对 MVC 运行时行为进行“定制而非重写”**。

```java
public interface WebMvcConfigurer {
}
```



### WebMvcConfigurationSupport

`WebMvcConfigurationSupport` 是 **Spring MVC 的底层配置支持类**，用于**完全接管 Spring MVC 的配置流程**。`@EnableWebMvc` 最终就是引入了 `WebMvcConfigurationSupport`。

```java
public class WebMvcConfigurationSupport implements ApplicationContextAware, ServletContextAware {
    
    //提供九大内置组件bean
    @Bean
	public RequestMappingHandlerMapping requestMappingHandlerMapping(
			@Qualifier("mvcContentNegotiationManager") ContentNegotiationManager contentNegotiationManager,
			@Qualifier("mvcApiVersionStrategy") @Nullable ApiVersionStrategy apiVersionStrategy,
			@Qualifier("mvcConversionService") FormattingConversionService conversionService,
			@Qualifier("mvcResourceUrlProvider") ResourceUrlProvider resourceUrlProvider) {

		RequestMappingHandlerMapping mapping = createRequestMappingHandlerMapping();
		mapping.setOrder(0);
		mapping.setContentNegotiationManager(contentNegotiationManager);
		mapping.setApiVersionStrategy(apiVersionStrategy);
		
         //定制化组件时调用子类的WebMvcConfigurer集合处理
		initHandlerMapping(mapping, conversionService, resourceUrlProvider);

		PathMatchConfigurer pathConfig = getPathMatchConfigurer();
		if (pathConfig.getPathPrefixes() != null) {
			mapping.setPathPrefixes(pathConfig.getPathPrefixes());
		}

		return mapping;
	}
    
    //子类继承的定制化接口
    protected void addInterceptors(InterceptorRegistry registry) {
	}
    
}
```

### DelegatingWebMvcConfiguration

```java
//处理所有WebMvcConfigurer的类
@Configuration(proxyBeanMethods = false)
public class DelegatingWebMvcConfiguration extends WebMvcConfigurationSupport {
	
    //WebMvcConfigurer的集合
    private final WebMvcConfigurerComposite configurers = new WebMvcConfigurerComposite();

	//将容器中自定义的WebMvcConfigurer全部加入集合中
    @Autowired(required = false)
    public void setConfigurers(List<WebMvcConfigurer> configurers) {
       if (!CollectionUtils.isEmpty(configurers)) {
          this.configurers.addWebMvcConfigurers(configurers);
       }
    }

	//定制化操作时逐个WebMvcConfigurer调用
    @Override
    protected void configurePathMatch(PathMatchConfigurer configurer) {
       this.configurers.configurePathMatch(configurer);
    }
}
```



### WebMvcAutoConfiguration

mvc自动装配类

```java
@AutoConfiguration(after = { DispatcherServletAutoConfiguration.class, TaskExecutionAutoConfiguration.class },
		afterName = "org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration")
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass({ Servlet.class, DispatcherServlet.class, WebMvcConfigurer.class })
@ConditionalOnMissingBean(WebMvcConfigurationSupport.class)
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE + 10)
@ImportRuntimeHints(WebResourcesRuntimeHints.class)
public final class WebMvcAutoConfiguration {

	/**
	 * Configuration equivalent to {@code @EnableWebMvc}.
	 * 配置等价于@EnableWebMvc 处理自定义WebMvcConfigurer和注册九大组件的入口
	 */
	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(WebProperties.class)
	static class EnableWebMvcConfiguration extends DelegatingWebMvcConfiguration implements ResourceLoaderAware {
    }




}
```







### ServletContext

一个 Web 应用在 Servlet 容器中的“全局运行上下文”，应用启动时创建 → 应用停止时销毁

```java
public interface ServletContext {
}
```



## 作用域

### RequestScope

```java
public class RequestScope extends AbstractRequestAttributesScope {

	@Override
	protected int getScope() {
		return RequestAttributes.SCOPE_REQUEST;
	}

	/**
	 * There is no conversation id concept for a request, so this method
	 * returns {@code null}.
	 */
	@Override
	@Nullable
	public String getConversationId() {
		return null;
	}

}
```



### SessionScope

```java
public class SessionScope extends AbstractRequestAttributesScope {

	@Override
	protected int getScope() {
		return RequestAttributes.SCOPE_SESSION;
	}

	@Override
	public String getConversationId() {
		return RequestContextHolder.currentRequestAttributes().getSessionId();
	}

	@Override
	public Object get(String name, ObjectFactory<?> objectFactory) {
		Object mutex = RequestContextHolder.currentRequestAttributes().getSessionMutex();
		synchronized (mutex) {
			return super.get(name, objectFactory);
		}
	}

	@Override
	@Nullable
	public Object remove(String name) {
		Object mutex = RequestContextHolder.currentRequestAttributes().getSessionMutex();
		synchronized (mutex) {
			return super.remove(name);
		}
	}

}
```

### AbstractRequestAttributesScope

```java
public abstract class AbstractRequestAttributesScope implements Scope {

    //由doGetBean时，处理非单例/多例的时候调用
    @Override
    public Object get(String name, ObjectFactory<?> objectFactory) {
       RequestAttributes attributes = RequestContextHolder.currentRequestAttributes();
        //获取请求/会话范围内的对象 (get)
       Object scopedObject = attributes.getAttribute(name, getScope());
        //获取不到
       if (scopedObject == null) {
           //创建对象
          scopedObject = objectFactory.getObject();
           //设置到请求/会话范围内
          attributes.setAttribute(name, scopedObject, getScope());
          // Retrieve object again, registering it for implicit session attribute updates.
          // As a bonus, we also allow for potential decoration at the getAttribute level.
          Object retrievedObject = attributes.getAttribute(name, getScope());
          if (retrievedObject != null) {
             // Only proceed with retrieved object if still present (the expected case).
             // If it disappeared concurrently, we return our locally created instance.
             scopedObject = retrievedObject;
          }
       }
       return scopedObject;
    }

    @Override
    @Nullable
    public Object remove(String name) {
       RequestAttributes attributes = RequestContextHolder.currentRequestAttributes();
       Object scopedObject = attributes.getAttribute(name, getScope());
       if (scopedObject != null) {
          attributes.removeAttribute(name, getScope());
          return scopedObject;
       }
       else {
          return null;
       }
    }

    @Override
    public void registerDestructionCallback(String name, Runnable callback) {
       RequestAttributes attributes = RequestContextHolder.currentRequestAttributes();
       attributes.registerDestructionCallback(name, callback, getScope());
    }

    @Override
    @Nullable
    public Object resolveContextualObject(String key) {
       RequestAttributes attributes = RequestContextHolder.currentRequestAttributes();
       return attributes.resolveReference(key);
    }


    /**
     * Template method that determines the actual target scope.
     * @return the target scope, in the form of an appropriate
     * {@link RequestAttributes} constant
     * @see RequestAttributes#SCOPE_REQUEST
     * @see RequestAttributes#SCOPE_SESSION
     */
    protected abstract int getScope();

}
```





## 参数校验

在springboot中，参数校验依靠`ValidationAutoConfiguration`自动装配类

```java
@AutoConfiguration
@ConditionalOnClass(ExecutableValidator.class)
@ConditionalOnResource(resources = "classpath:META-INF/services/jakarta.validation.spi.ValidationProvider")
@Import(PrimaryDefaultValidatorPostProcessor.class)
public final class ValidationAutoConfiguration {

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	@ConditionalOnMissingBean(Validator.class)
	static LocalValidatorFactoryBean defaultValidator(ApplicationContext applicationContext,
			ObjectProvider<ValidationConfigurationCustomizer> customizers) {
		LocalValidatorFactoryBean factoryBean = new LocalValidatorFactoryBean();
		factoryBean.setConfigurationInitializer((configuration) -> customizers.orderedStream()
			.forEach((customizer) -> customizer.customize(configuration)));
		MessageInterpolatorFactory interpolatorFactory = new MessageInterpolatorFactory(applicationContext);
		factoryBean.setMessageInterpolator(interpolatorFactory.getObject());
		return factoryBean;
	}

    //核心是这个MethodValidationPostProcessor 对带有@Validated注解的类进行代理 执行时动态校验
	@Bean
	@ConditionalOnMissingBean(search = SearchStrategy.CURRENT)
	static MethodValidationPostProcessor methodValidationPostProcessor(Environment environment,
			ObjectProvider<Validator> validator, ObjectProvider<MethodValidationExcludeFilter> excludeFilters) {
		FilteredMethodValidationPostProcessor processor = new FilteredMethodValidationPostProcessor(
				excludeFilters.orderedStream());
		boolean proxyTargetClass = environment.getProperty("spring.aop.proxy-target-class", Boolean.class, true);
		processor.setProxyTargetClass(proxyTargetClass);
		boolean adaptConstraintViolations = environment
			.getProperty("spring.validation.method.adapt-constraint-violations", Boolean.class, false);
		processor.setAdaptConstraintViolations(adaptConstraintViolations);
		processor.setValidatorProvider(validator);
		return processor;
	}

}
```

























