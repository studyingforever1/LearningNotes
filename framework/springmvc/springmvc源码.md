## springmvc

整个springmvc实际上就是一个特殊的servlet

<img src="./images/springmvc请求流程.jpg" style="zoom: 50%;" />

## 启动流程

tomcat负责加载web.xml的配置文件

- spring容器启动加载spring.xml文件
- springmvc容器启动加载springmvc.xml文件，springmvc容器继承spring容器，可以使用父容器的bean。

<img src="./images/image-20251028101835921.png" alt="image-20251028101835921" style="zoom: 33%;" />



### spring容器启动

整个`web.xml`文件通过tomcat进行读取解析，`listener`标签中的`ContextLoaderListener`负责spring容器的启动。

- tomcat调度执行`ContextLoaderListener`中的`contextInitialized`方法
- 执行父类`ContextLoader`中的`initWebApplicationContext`方法
- 通过`ContextLoader.properties`获取默认的spring容器类`XmlWebApplicationContext`
- 初始化设置`XmlWebApplicationContext`，调用`refresh`方法刷新容器

**web.xml文件**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<web-app xmlns="http://xmlns.jcp.org/xml/ns/javaee"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/javaee http://xmlns.jcp.org/xml/ns/javaee/web-app_4_0.xsd"
         version="4.0">
    
<!--    用于spring容器的文件配置-->
    <context-param>
        <param-name>contextConfigLocation</param-name>
        <param-value>classpath:spring-config.xml</param-value>
    </context-param>

<!--   DispatcherServlet配置 启动springmvc的入口-->
    <servlet>
        <servlet-name>mvc-test</servlet-name>
        <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
        <!--SpringMVC配置文件-->
        <init-param>
            <param-name>contextConfigLocation</param-name>
            <param-value>classpath:springmvc-config.xml</param-value>
        </init-param>
        <load-on-startup>1</load-on-startup>
        <multipart-config>
            <max-file-size>20848820</max-file-size>
            <max-request-size>418018841</max-request-size>
            <file-size-threshold>1048576</file-size-threshold>
        </multipart-config>
    </servlet>

    <servlet-mapping>
        <servlet-name>mvc-test</servlet-name>
        <url-pattern>/</url-pattern>
    </servlet-mapping>

<!--    启动spring容器的入口-->
    <listener>
        <listener-class>org.springframework.web.context.ContextLoaderListener</listener-class>
    </listener>
</web-app>
```

**ContextLoaderListener**

```java

public class ContextLoaderListener extends ContextLoader implements ServletContextListener {

	
	public ContextLoaderListener() {
	}

	
	public ContextLoaderListener(WebApplicationContext context) {
		super(context);
	}


    //tomcat负责调度当前listener，调用初始化方法contextInitialized
	/**
	 * Initialize the root web application context.
	 */
	@Override
	public void contextInitialized(ServletContextEvent event) {
		initWebApplicationContext(event.getServletContext());
	}


	/**
	 * Close the root web application context.
	 */
	@Override
	public void contextDestroyed(ServletContextEvent event) {
		closeWebApplicationContext(event.getServletContext());
		ContextCleanupListener.cleanupAttributes(event.getServletContext());
	}

}

```

**ContextLoader**

```java
public class ContextLoader {

	public static final String CONFIG_LOCATION_PARAM = "contextConfigLocation";

	public static final String CONTEXT_CLASS_PARAM = "contextClass";
    
	private static final String DEFAULT_STRATEGIES_PATH = "ContextLoader.properties";

	@Nullable
	private static Properties defaultStrategies;
    
	@Nullable
	private WebApplicationContext context;

    
    //创建初始化spring容器
	public WebApplicationContext initWebApplicationContext(ServletContext servletContext) {
		if (servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE) != null) {
			throw new IllegalStateException(
					"Cannot initialize context because there is already a root application context present - " +
					"check whether you have multiple ContextLoader* definitions in your web.xml!");
		}

		servletContext.log("Initializing Spring root WebApplicationContext");
		Log logger = LogFactory.getLog(ContextLoader.class);
		if (logger.isInfoEnabled()) {
			logger.info("Root WebApplicationContext: initialization started");
		}
		long startTime = System.currentTimeMillis();

		try {
			// Store context in local instance variable, to guarantee that
			// it is available on ServletContext shutdown.
			if (this.context == null) {
                  //创建spring容器
				this.context = createWebApplicationContext(servletContext);
			}
			if (this.context instanceof ConfigurableWebApplicationContext cwac && !cwac.isActive()) {
				// The context has not yet been refreshed -> provide services such as
				// setting the parent context, setting the application context id, etc
                 //设置父类，没有设置为null
				if (cwac.getParent() == null) {
					// The context instance was injected without an explicit parent ->
					// determine parent for root web application context, if any.
					ApplicationContext parent = loadParentContext(servletContext);
					cwac.setParent(parent);
				}
                 //刷新spring容器
				configureAndRefreshWebApplicationContext(cwac, servletContext);
			}
			servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, this.context);

			ClassLoader ccl = Thread.currentThread().getContextClassLoader();
			if (ccl == ContextLoader.class.getClassLoader()) {
				currentContext = this.context;
			}
			else if (ccl != null) {
				currentContextPerThread.put(ccl, this.context);
			}

			if (logger.isInfoEnabled()) {
				long elapsedTime = System.currentTimeMillis() - startTime;
				logger.info("Root WebApplicationContext initialized in " + elapsedTime + " ms");
			}

			return this.context;
		}
		catch (RuntimeException | Error ex) {
			logger.error("Context initialization failed", ex);
			servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, ex);
			throw ex;
		}
	}
    
    //创建spring容器
	protected WebApplicationContext createWebApplicationContext(ServletContext sc) {
		Class<?> contextClass = determineContextClass(sc);
		if (!ConfigurableWebApplicationContext.class.isAssignableFrom(contextClass)) {
			throw new ApplicationContextException("Custom context class [" + contextClass.getName() +
					"] is not of type [" + ConfigurableWebApplicationContext.class.getName() + "]");
		}
         //反射创建spring容器
		return (ConfigurableWebApplicationContext) BeanUtils.instantiateClass(contextClass);
	}
    
    //获取spring容器的类
	protected Class<?> determineContextClass(ServletContext servletContext) {
        //如果设置了contextClass属性标签 那么用contextClass的
		String contextClassName = servletContext.getInitParameter(CONTEXT_CLASS_PARAM);
		if (contextClassName != null) {
			try {
				return ClassUtils.forName(contextClassName, ClassUtils.getDefaultClassLoader());
			}
			catch (ClassNotFoundException ex) {
				throw new ApplicationContextException(
						"Failed to load custom context class [" + contextClassName + "]", ex);
			}
		}
		else {
			if (defaultStrategies == null) {
				// Load default strategy implementations from properties file.
				// This is currently strictly internal and not meant to be customized
				// by application developers.
				try {
                      //通过ContextLoader.properties获取默认的spring容器类
					ClassPathResource resource = new ClassPathResource(DEFAULT_STRATEGIES_PATH, ContextLoader.class);
					defaultStrategies = PropertiesLoaderUtils.loadProperties(resource);
				}
				catch (IOException ex) {
					throw new IllegalStateException("Could not load 'ContextLoader.properties': " + ex.getMessage());
				}
			}
             //默认是XmlWebApplicationContext
			contextClassName = defaultStrategies.getProperty(WebApplicationContext.class.getName());
			try {
				return ClassUtils.forName(contextClassName, ContextLoader.class.getClassLoader());
			}
			catch (ClassNotFoundException ex) {
				throw new ApplicationContextException(
						"Failed to load default context class [" + contextClassName + "]", ex);
			}
		}
	}
    
	protected void configureAndRefreshWebApplicationContext(ConfigurableWebApplicationContext wac, ServletContext sc) {
	    //设置spring容器的id
        if (ObjectUtils.identityToString(wac).equals(wac.getId())) {
			// The application context id is still set to its original default value
			// -> assign a more useful id based on available information
			String idParam = sc.getInitParameter(CONTEXT_ID_PARAM);
			if (idParam != null) {
				wac.setId(idParam);
			}
			else {
				// Generate default id...
				wac.setId(ConfigurableWebApplicationContext.APPLICATION_CONTEXT_ID_PREFIX +
						ObjectUtils.getDisplayString(sc.getContextPath()));
			}
		}

        //设置servlet上下文
		wac.setServletContext(sc);
         //获取web.xml中配置的spring容器的配置文件contextConfigLocation
		String configLocationParam = sc.getInitParameter(CONFIG_LOCATION_PARAM);
		if (configLocationParam != null) {
             //设置到spring容器中
			wac.setConfigLocation(configLocationParam);
		}

		// The wac environment's #initPropertySources will be called in any case when the context
		// is refreshed; do it eagerly here to ensure servlet property sources are in place for
		// use in any post-processing or initialization that occurs below prior to #refresh
		ConfigurableEnvironment env = wac.getEnvironment();
		if (env instanceof ConfigurableWebEnvironment cwe) {
			cwe.initPropertySources(sc, null);
		}

		customizeContext(sc, wac);
         //容器刷新
		wac.refresh();
	}


}
```









### springmvc容器启动

springmvc容器的入口是基于`DispatcherServlet`的初始化

- `DispatcherServlet`继承了`FrameworkServlet`，`FrameworkServlet`继承了`HttpServletBean`，tomcat启动调用`HttpServletBean`的`init`方法
- `FrameworkServlet`中重写了`initServletBean`方法，对springmvc容器进行创建，将spring作为父容器设置到springmvc容器中
- 刷新springmvc容器，加载配置文件。



<img src="./images/image-20251029095657053.png" alt="image-20251029095657053" style="zoom: 50%;" />

**HttpServletBean**

```java
public abstract class HttpServletBean extends HttpServlet implements EnvironmentCapable, EnvironmentAware {
    
    //tomcat启动调用`HttpServletBean`的`init`方法
	@Override
	public final void init() throws ServletException {

         //将requiredProperties中的属性包装进入当前DispatcherServlet中
		// Set bean properties from init parameters.
		PropertyValues pvs = new ServletConfigPropertyValues(getServletConfig(), this.requiredProperties);
		if (!pvs.isEmpty()) {
			try {
				BeanWrapper bw = PropertyAccessorFactory.forBeanPropertyAccess(this);
				ResourceLoader resourceLoader = new ServletContextResourceLoader(getServletContext());
				bw.registerCustomEditor(Resource.class, new ResourceEditor(resourceLoader, getEnvironment()));
				initBeanWrapper(bw);
				bw.setPropertyValues(pvs, true);
			}
			catch (BeansException ex) {
				if (logger.isErrorEnabled()) {
					logger.error("Failed to set bean properties on servlet '" + getServletName() + "'", ex);
				}
				throw ex;
			}
		}

        //初始化springmvc容器
		// Let subclasses do whatever initialization they like.
		initServletBean();
	}
    
    
    protected void initServletBean() throws ServletException {
	}
}
```



**FrameworkServlet**

```java
public abstract class FrameworkServlet extends HttpServletBean implements ApplicationContextAware {

    
	@Override
	protected final void initServletBean() throws ServletException {
		getServletContext().log("Initializing Spring " + getClass().getSimpleName() + " '" + getServletName() + "'");
		if (logger.isInfoEnabled()) {
			logger.info("Initializing Servlet '" + getServletName() + "'");
		}
		long startTime = System.currentTimeMillis();

		try {
             //创建springmvc容器
			this.webApplicationContext = initWebApplicationContext();
			initFrameworkServlet();
		}
		catch (ServletException | RuntimeException ex) {
			logger.error("Context initialization failed", ex);
			throw ex;
		}

		if (logger.isDebugEnabled()) {
			String value = this.enableLoggingRequestDetails ?
					"shown which may lead to unsafe logging of potentially sensitive data" :
					"masked to prevent unsafe logging of potentially sensitive data";
			logger.debug("enableLoggingRequestDetails='" + this.enableLoggingRequestDetails +
					"': request parameters and headers will be " + value);
		}

		if (logger.isInfoEnabled()) {
			logger.info("Completed initialization in " + (System.currentTimeMillis() - startTime) + " ms");
		}
	}

	protected WebApplicationContext initWebApplicationContext() {
        //获取spring容器当作父容器
		WebApplicationContext rootContext =
				WebApplicationContextUtils.getWebApplicationContext(getServletContext());
		WebApplicationContext wac = null;

        //如果有springmvc容器 直接初始化
		if (this.webApplicationContext != null) {
			// A context instance was injected at construction time -> use it
			wac = this.webApplicationContext;
			if (wac instanceof ConfigurableWebApplicationContext cwac && !cwac.isActive()) {
				// The context has not yet been refreshed -> provide services such as
				// setting the parent context, setting the application context id, etc
				if (cwac.getParent() == null) {
					// The context instance was injected without an explicit parent -> set
					// the root application context (if any; may be null) as the parent
					cwac.setParent(rootContext);
				}
				configureAndRefreshWebApplicationContext(cwac);
			}
		}
         //没有springmvc容器，在属性中查找一下
		if (wac == null) {
			// No context instance was injected at construction time -> see if one
			// has been registered in the servlet context. If one exists, it is assumed
			// that the parent context (if any) has already been set and that the
			// user has performed any initialization such as setting the context id
			wac = findWebApplicationContext();
		}
        //没有springmvc容器，创建
		if (wac == null) {
			// No context instance is defined for this servlet -> create a local one
			wac = createWebApplicationContext(rootContext);
		}

		if (!this.refreshEventReceived) {
			// Either the context is not a ConfigurableApplicationContext with refresh
			// support or the context injected at construction time had already been
			// refreshed -> trigger initial onRefresh manually here.
			synchronized (this.onRefreshMonitor) {
				onRefresh(wac);
			}
		}

		if (this.publishContext) {
			// Publish the context as a servlet context attribute.
			String attrName = getServletContextAttributeName();
			getServletContext().setAttribute(attrName, wac);
		}

		return wac;
	}

    
    //创建一个springmvc容器
	protected WebApplicationContext createWebApplicationContext(@Nullable ApplicationContext parent) {
        //获取到XmlWebApplicationContext
		Class<?> contextClass = getContextClass();
		if (!ConfigurableWebApplicationContext.class.isAssignableFrom(contextClass)) {
			throw new ApplicationContextException(
					"Fatal initialization error in servlet with name '" + getServletName() +
					"': custom WebApplicationContext class [" + contextClass.getName() +
					"] is not of type ConfigurableWebApplicationContext");
		}
         //创建XmlWebApplicationContext
		ConfigurableWebApplicationContext wac =
				(ConfigurableWebApplicationContext) BeanUtils.instantiateClass(contextClass);

        //设置环境
		wac.setEnvironment(getEnvironment());
        //将spring容器设置为springmvc的父容器
		wac.setParent(parent);
        //设置contextConfigLocation中的配置文件
		String configLocation = getContextConfigLocation();
		if (configLocation != null) {
			wac.setConfigLocation(configLocation);
		}
        //配置刷新springmvc容器
		configureAndRefreshWebApplicationContext(wac);

		return wac;
	}
    
    //配置刷新springmvc容器
	protected void configureAndRefreshWebApplicationContext(ConfigurableWebApplicationContext wac) {
		if (ObjectUtils.identityToString(wac).equals(wac.getId())) {
			// The application context id is still set to its original default value
			// -> assign a more useful id based on available information
			if (this.contextId != null) {
				wac.setId(this.contextId);
			}
			else {
				// Generate default id...
				wac.setId(ConfigurableWebApplicationContext.APPLICATION_CONTEXT_ID_PREFIX +
						ObjectUtils.getDisplayString(getServletContext().getContextPath()) + '/' + getServletName());
			}
		}
		//设置servlet上下文
		wac.setServletContext(getServletContext());
        //设置当前DispatchServlet的配置
		wac.setServletConfig(getServletConfig());
		wac.setNamespace(getNamespace());
        //设置一个ContextRefreshEvent时间的监听器
		wac.addApplicationListener(new SourceFilteringListener(wac, new ContextRefreshListener()));

		// The wac environment's #initPropertySources will be called in any case when the context
		// is refreshed; do it eagerly here to ensure servlet property sources are in place for
		// use in any post-processing or initialization that occurs below prior to #refresh
		ConfigurableEnvironment env = wac.getEnvironment();
		if (env instanceof ConfigurableWebEnvironment cwe) {
			cwe.initPropertySources(getServletContext(), getServletConfig());
		}

		postProcessWebApplicationContext(wac);
		applyInitializers(wac);
        //刷新springmvc容器
		wac.refresh();
	}
    
}
```





### 初始化九大内置组件

- 在springmvc容器刷新时，加入`SourceFilteringListener`监听器，用于在`ContextRefreshedEvent`事件触发时回调

- `ContextRefreshListener`中调用`FrameworkServlet.this.onApplicationEvent(event);`方法

- 调用`DispatcherServlet`中的`onRefresh`方法，再调用`initStrategies`方法对九大内置组件进行初始化

  **`initMultipartResolver`**：初始化 **文件上传解析器**，用于识别和处理 `multipart/form-data` 类型的请求（例如表单文件上传）。

  **`initLocaleResolver`**：初始化 **区域解析器**，根据请求头、Cookie 或 Session 判断当前用户的语言和地区设置，用于国际化。

  **`initThemeResolver`**：初始化 **主题解析器**，决定当前 Web 应用使用的主题（如样式、配色、模板资源等）。

  **`initHandlerMappings`**：初始化 **处理器映射器**，将请求的 URL 与对应的控制器（Handler）建立映射关系，用来找到执行请求的控制器。

  **`initHandlerAdapters`**：初始化 **处理器适配器**，负责调用不同类型的控制器方法，将请求参数绑定并执行对应的业务逻辑。

  **`initHandlerExceptionResolvers`**：初始化 **异常解析器**，在控制器抛出异常时负责捕获并将其转换为合适的错误响应或视图。

  **`initRequestToViewNameTranslator`**：初始化 **请求到视图名转换器**，在控制器未显式返回视图名时自动根据请求路径推断视图名称。

  **`initViewResolvers`**：初始化 **视图解析器**，根据控制器返回的视图名解析出具体的视图资源（如 JSP、Thymeleaf、Freemarker 模板等）。

  **`initFlashMapManager`**：初始化 **Flash 属性管理器**，用于在重定向请求之间暂存一次性数据（如操作成功消息或表单参数）。

**SourceFilteringListener**

```java
public class SourceFilteringListener implements GenericApplicationListener {

    //触发事件
	@Override
	public void onApplicationEvent(ApplicationEvent event) {
        //触发事件的容器是否是springmvc容器
		if (event.getSource() == this.source) {
			onApplicationEventInternal(event);
		}
	}

	protected void onApplicationEventInternal(ApplicationEvent event) {
		if (this.delegate == null) {
			throw new IllegalStateException(
					"Must specify a delegate object or override the onApplicationEventInternal method");
		}
         //调用ContextRefreshListener
		this.delegate.onApplicationEvent(event);
	}

}
```

**FrameworkServlet**

```java
public abstract class FrameworkServlet extends HttpServletBean implements ApplicationContextAware {

	private class ContextRefreshListener implements ApplicationListener<ContextRefreshedEvent> {

		@Override
		public void onApplicationEvent(ContextRefreshedEvent event) {
            //调用FrameworkServlet中的onApplicationEvent
			FrameworkServlet.this.onApplicationEvent(event);
		}
	}

	public void onApplicationEvent(ContextRefreshedEvent event) {
		this.refreshEventReceived = true;
		synchronized (this.onRefreshMonitor) {
            //调用onRefresh
			onRefresh(event.getApplicationContext());
		}
	}
    //DispatcherServlet重写onRefresh
	protected void onRefresh(ApplicationContext context) {
		// For subclasses: do nothing by default.
	}
}
```

**DispatcherServlet**

```java
public class DispatcherServlet extends FrameworkServlet {

	@Override
	protected void onRefresh(ApplicationContext context) {
		initStrategies(context);
	}

    //初始化九大组件
	protected void initStrategies(ApplicationContext context) {
		initMultipartResolver(context);
		initLocaleResolver(context);
		initThemeResolver(context);
		initHandlerMappings(context);
		initHandlerAdapters(context);
		initHandlerExceptionResolvers(context);
		initRequestToViewNameTranslator(context);
		initViewResolvers(context);
		initFlashMapManager(context);
	}
    
    //初始化文件上传解析器
	private void initMultipartResolver(ApplicationContext context) {
		try {
			this.multipartResolver = context.getBean(MULTIPART_RESOLVER_BEAN_NAME, MultipartResolver.class);
			if (logger.isTraceEnabled()) {
				logger.trace("Detected " + this.multipartResolver);
			}
			else if (logger.isDebugEnabled()) {
				logger.debug("Detected " + this.multipartResolver.getClass().getSimpleName());
			}
		}
		catch (NoSuchBeanDefinitionException ex) {
			// Default is no multipart resolver.
			this.multipartResolver = null;
			if (logger.isTraceEnabled()) {
				logger.trace("No MultipartResolver '" + MULTIPART_RESOLVER_BEAN_NAME + "' declared");
			}
		}
	}
    
    //初始化区域解析器
    private void initLocaleResolver(ApplicationContext context) {
		try {
			this.localeResolver = context.getBean(LOCALE_RESOLVER_BEAN_NAME, LocaleResolver.class);
			if (logger.isTraceEnabled()) {
				logger.trace("Detected " + this.localeResolver);
			}
			else if (logger.isDebugEnabled()) {
				logger.debug("Detected " + this.localeResolver.getClass().getSimpleName());
			}
		}
		catch (NoSuchBeanDefinitionException ex) {
             //容器中没有的情况下通过DispatcherServlet.properties获取默认的
			// We need to use the default.
			this.localeResolver = getDefaultStrategy(context, LocaleResolver.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No LocaleResolver '" + LOCALE_RESOLVER_BEAN_NAME +
						"': using default [" + this.localeResolver.getClass().getSimpleName() + "]");
			}
		}
	}
    //初始化主题解析器
	@Deprecated
	private void initThemeResolver(ApplicationContext context) {
		try {
			this.themeResolver = context.getBean(THEME_RESOLVER_BEAN_NAME, ThemeResolver.class);
			if (logger.isTraceEnabled()) {
				logger.trace("Detected " + this.themeResolver);
			}
			else if (logger.isDebugEnabled()) {
				logger.debug("Detected " + this.themeResolver.getClass().getSimpleName());
			}
		}
		catch (NoSuchBeanDefinitionException ex) {
			// We need to use the default.
			this.themeResolver = getDefaultStrategy(context, ThemeResolver.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No ThemeResolver '" + THEME_RESOLVER_BEAN_NAME +
						"': using default [" + this.themeResolver.getClass().getSimpleName() + "]");
			}
		}
	}
    //初始化 处理器映射器
	private void initHandlerMappings(ApplicationContext context) {
		this.handlerMappings = null;

		if (this.detectAllHandlerMappings) {
			// Find all HandlerMappings in the ApplicationContext, including ancestor contexts.
			Map<String, HandlerMapping> matchingBeans =
					BeanFactoryUtils.beansOfTypeIncludingAncestors(context, HandlerMapping.class, true, false);
			if (!matchingBeans.isEmpty()) {
				this.handlerMappings = new ArrayList<>(matchingBeans.values());
				// We keep HandlerMappings in sorted order.
				AnnotationAwareOrderComparator.sort(this.handlerMappings);
			}
		}
		else {
			try {
				HandlerMapping hm = context.getBean(HANDLER_MAPPING_BEAN_NAME, HandlerMapping.class);
				this.handlerMappings = Collections.singletonList(hm);
			}
			catch (NoSuchBeanDefinitionException ex) {
				// Ignore, we'll add a default HandlerMapping later.
			}
		}

		// Ensure we have at least one HandlerMapping, by registering
		// a default HandlerMapping if no other mappings are found.
		if (this.handlerMappings == null) {
			this.handlerMappings = getDefaultStrategies(context, HandlerMapping.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No HandlerMappings declared for servlet '" + getServletName() +
						"': using default strategies from DispatcherServlet.properties");
			}
		}

		for (HandlerMapping mapping : this.handlerMappings) {
			if (mapping.usesPathPatterns()) {
				this.parseRequestPath = true;
				break;
			}
		}
	}

	/**
	 * Initialize the HandlerAdapters used by this class.
	 * <p>If no HandlerAdapter beans are defined in the BeanFactory for this namespace,
	 * we default to SimpleControllerHandlerAdapter.
	 */
    //初始化 处理器适配器
	private void initHandlerAdapters(ApplicationContext context) {
		this.handlerAdapters = null;

		if (this.detectAllHandlerAdapters) {
			// Find all HandlerAdapters in the ApplicationContext, including ancestor contexts.
			Map<String, HandlerAdapter> matchingBeans =
					BeanFactoryUtils.beansOfTypeIncludingAncestors(context, HandlerAdapter.class, true, false);
			if (!matchingBeans.isEmpty()) {
				this.handlerAdapters = new ArrayList<>(matchingBeans.values());
				// We keep HandlerAdapters in sorted order.
				AnnotationAwareOrderComparator.sort(this.handlerAdapters);
			}
		}
		else {
			try {
				HandlerAdapter ha = context.getBean(HANDLER_ADAPTER_BEAN_NAME, HandlerAdapter.class);
				this.handlerAdapters = Collections.singletonList(ha);
			}
			catch (NoSuchBeanDefinitionException ex) {
				// Ignore, we'll add a default HandlerAdapter later.
			}
		}

		// Ensure we have at least some HandlerAdapters, by registering
		// default HandlerAdapters if no other adapters are found.
		if (this.handlerAdapters == null) {
			this.handlerAdapters = getDefaultStrategies(context, HandlerAdapter.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No HandlerAdapters declared for servlet '" + getServletName() +
						"': using default strategies from DispatcherServlet.properties");
			}
		}
	}

	/**
	 * Initialize the HandlerExceptionResolver used by this class.
	 * <p>If no bean is defined with the given name in the BeanFactory for this namespace,
	 * we default to no exception resolver.
	 */
    //初始化异常解析器
	private void initHandlerExceptionResolvers(ApplicationContext context) {
		this.handlerExceptionResolvers = null;

		if (this.detectAllHandlerExceptionResolvers) {
			// Find all HandlerExceptionResolvers in the ApplicationContext, including ancestor contexts.
			Map<String, HandlerExceptionResolver> matchingBeans = BeanFactoryUtils
					.beansOfTypeIncludingAncestors(context, HandlerExceptionResolver.class, true, false);
			if (!matchingBeans.isEmpty()) {
				this.handlerExceptionResolvers = new ArrayList<>(matchingBeans.values());
				// We keep HandlerExceptionResolvers in sorted order.
				AnnotationAwareOrderComparator.sort(this.handlerExceptionResolvers);
			}
		}
		else {
			try {
				HandlerExceptionResolver her =
						context.getBean(HANDLER_EXCEPTION_RESOLVER_BEAN_NAME, HandlerExceptionResolver.class);
				this.handlerExceptionResolvers = Collections.singletonList(her);
			}
			catch (NoSuchBeanDefinitionException ex) {
				// Ignore, no HandlerExceptionResolver is fine too.
			}
		}

		// Ensure we have at least some HandlerExceptionResolvers, by registering
		// default HandlerExceptionResolvers if no other resolvers are found.
		if (this.handlerExceptionResolvers == null) {
			this.handlerExceptionResolvers = getDefaultStrategies(context, HandlerExceptionResolver.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No HandlerExceptionResolvers declared in servlet '" + getServletName() +
						"': using default strategies from DispatcherServlet.properties");
			}
		}
	}

	/**
	 * Initialize the RequestToViewNameTranslator used by this servlet instance.
	 * <p>If no implementation is configured then we default to DefaultRequestToViewNameTranslator.
	 */
    //初始化请求到视图名转换器
	private void initRequestToViewNameTranslator(ApplicationContext context) {
		try {
			this.viewNameTranslator =
					context.getBean(REQUEST_TO_VIEW_NAME_TRANSLATOR_BEAN_NAME, RequestToViewNameTranslator.class);
			if (logger.isTraceEnabled()) {
				logger.trace("Detected " + this.viewNameTranslator.getClass().getSimpleName());
			}
			else if (logger.isDebugEnabled()) {
				logger.debug("Detected " + this.viewNameTranslator);
			}
		}
		catch (NoSuchBeanDefinitionException ex) {
			// We need to use the default.
			this.viewNameTranslator = getDefaultStrategy(context, RequestToViewNameTranslator.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No RequestToViewNameTranslator '" + REQUEST_TO_VIEW_NAME_TRANSLATOR_BEAN_NAME +
						"': using default [" + this.viewNameTranslator.getClass().getSimpleName() + "]");
			}
		}
	}

	/**
	 * Initialize the ViewResolvers used by this class.
	 * <p>If no ViewResolver beans are defined in the BeanFactory for this
	 * namespace, we default to InternalResourceViewResolver.
	 */
    //初始化视图解析器
	private void initViewResolvers(ApplicationContext context) {
		this.viewResolvers = null;

		if (this.detectAllViewResolvers) {
			// Find all ViewResolvers in the ApplicationContext, including ancestor contexts.
			Map<String, ViewResolver> matchingBeans =
					BeanFactoryUtils.beansOfTypeIncludingAncestors(context, ViewResolver.class, true, false);
			if (!matchingBeans.isEmpty()) {
				this.viewResolvers = new ArrayList<>(matchingBeans.values());
				// We keep ViewResolvers in sorted order.
				AnnotationAwareOrderComparator.sort(this.viewResolvers);
			}
		}
		else {
			try {
				ViewResolver vr = context.getBean(VIEW_RESOLVER_BEAN_NAME, ViewResolver.class);
				this.viewResolvers = Collections.singletonList(vr);
			}
			catch (NoSuchBeanDefinitionException ex) {
				// Ignore, we'll add a default ViewResolver later.
			}
		}

		// Ensure we have at least one ViewResolver, by registering
		// a default ViewResolver if no other resolvers are found.
		if (this.viewResolvers == null) {
			this.viewResolvers = getDefaultStrategies(context, ViewResolver.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No ViewResolvers declared for servlet '" + getServletName() +
						"': using default strategies from DispatcherServlet.properties");
			}
		}
	}

	/**
	 * Initialize the {@link FlashMapManager} used by this servlet instance.
	 * <p>If no implementation is configured then we default to
	 * {@code org.springframework.web.servlet.support.DefaultFlashMapManager}.
	 */
    //初始化Flash 属性管理器
	private void initFlashMapManager(ApplicationContext context) {
		try {
			this.flashMapManager = context.getBean(FLASH_MAP_MANAGER_BEAN_NAME, FlashMapManager.class);
			if (logger.isTraceEnabled()) {
				logger.trace("Detected " + this.flashMapManager.getClass().getSimpleName());
			}
			else if (logger.isDebugEnabled()) {
				logger.debug("Detected " + this.flashMapManager);
			}
		}
		catch (NoSuchBeanDefinitionException ex) {
			// We need to use the default.
			this.flashMapManager = getDefaultStrategy(context, FlashMapManager.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No FlashMapManager '" + FLASH_MAP_MANAGER_BEAN_NAME +
						"': using default [" + this.flashMapManager.getClass().getSimpleName() + "]");
			}
		}
	}


}
```



#### DispatcherServlet.properties

```properties
# Default implementation classes for DispatcherServlet's strategy interfaces.
# Used as fallback when no matching beans are found in the DispatcherServlet context.
# Not meant to be customized by application developers.

org.springframework.web.servlet.LocaleResolver=org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver

org.springframework.web.servlet.ThemeResolver=org.springframework.web.servlet.theme.FixedThemeResolver

org.springframework.web.servlet.HandlerMapping=org.springframework.web.servlet.handler.BeanNameUrlHandlerMapping,\
    org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping,\
    org.springframework.web.servlet.function.support.RouterFunctionMapping

org.springframework.web.servlet.HandlerAdapter=org.springframework.web.servlet.mvc.HttpRequestHandlerAdapter,\
    org.springframework.web.servlet.mvc.SimpleControllerHandlerAdapter,\
    org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter,\
    org.springframework.web.servlet.function.support.HandlerFunctionAdapter


org.springframework.web.servlet.HandlerExceptionResolver=org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver,\
    org.springframework.web.servlet.mvc.annotation.ResponseStatusExceptionResolver,\
    org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver

org.springframework.web.servlet.RequestToViewNameTranslator=org.springframework.web.servlet.view.DefaultRequestToViewNameTranslator

org.springframework.web.servlet.ViewResolver=org.springframework.web.servlet.view.InternalResourceViewResolver

org.springframework.web.servlet.FlashMapManager=org.springframework.web.servlet.support.SessionFlashMapManager
```







## 请求处理流程

1. 当接收到对应的请求时，由`tomcat`调用`DispatcherServlet`中的`service`方法，不同请求类型的最终处理方式都是`processRequest`，再调用`doService`中的`doDispatch`
2. 处理`mutipart`类型的请求
   1. 检查当前请求的`content-type`是否是`multipart/form-data`类型
   2. 将普通请求包装成`StandardMultipartHttpServletRequest`类型，并且将其中的普通参数和文件参数解析分别保存
3. 获取`url`对应的`handler`
   1. 先从`BeanNameUrlHandlerMapping`中获取
      1. 解析出请求的`uri`，根据`uri`直接匹配对应的`handler`
      2. 找不到的话就根据`pathPatternHandlerMap`匹配`uri`的路径
      3. 找不到的话判断是否是`/`匹配根`handler`，还没有就匹配默认的`handler`
   2. 从`RequestMappingHandlerMapping`中获取
      1. 解析出请求的`uri`，根据`uri`直接匹配对应的`handler`
      2. 如果找到匹配的handler，那么就检查是否与`@RequstMapping`注解中的限制符合
         1. 检查`RequestMethod`是否匹配，不匹配去掉当前handler
         2. 检查`params`属性中的表达式是否匹配，不匹配去掉handler
         3. 检查`headers`属性中的表达式是否匹配，不匹配去掉handler
         4. 检查`consumes`属性是否匹配，不匹配去掉handler
         5. 检查`produces`属性是否匹配，不匹配去掉handler
         6. 检查自定义的`condition`是否匹配，不匹配去掉handler
      3. 如果有多个匹配的handler，排序找出最匹配的handler，如果有两个以上并列就报错。
      4. 返回最佳匹配handler
   3. 如果没找到handler，获取默认的handler
   4. 将handler封装成`HandlerExecutionChain`对象，将`adaptedInterceptors`中的`MappedInterceptor`类型都添加到`HandlerExecutionChain`
   5. 如果请求方法上含有跨域配置，读取跨域配置，添加`CorsInterceptor`到`HandlerExecutionChain`中



```JAVA
public abstract class FrameworkServlet extends HttpServletBean implements ApplicationContextAware {
    
	private static final Set<String> HTTP_SERVLET_METHODS =
			Set.of("DELETE", "HEAD", "GET", "OPTIONS", "POST", "PUT", "TRACE");

    
	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		if (HTTP_SERVLET_METHODS.contains(request.getMethod())) {
             //常规请求走这里
			super.service(request, response);
		}
		else {
			processRequest(request, response);
		}
	}

}
```

```java
public abstract class HttpServlet extends GenericServlet {

    //根据不同的请求方法进行处理
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String method = req.getMethod();

        if (method.equals(METHOD_GET)) {
            long lastModified = getLastModified(req);
            if (lastModified == -1) {
                // servlet doesn't support if-modified-since, no reason
                // to go through further expensive logic
                doGet(req, resp);
            } else {
                long ifModifiedSince = req.getDateHeader(HEADER_IFMODSINCE);
                if (ifModifiedSince < lastModified) {
                    // If the servlet mod time is later, call doGet()
                    // Round down to the nearest second for a proper compare
                    // A ifModifiedSince of -1 will always be less
                    maybeSetLastModified(resp, lastModified);
                    doGet(req, resp);
                } else {
                    resp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                }
            }

        } else if (method.equals(METHOD_HEAD)) {
            long lastModified = getLastModified(req);
            maybeSetLastModified(resp, lastModified);
            doHead(req, resp);

        } else if (method.equals(METHOD_POST)) {
            doPost(req, resp);

        } else if (method.equals(METHOD_PUT)) {
            doPut(req, resp);

        } else if (method.equals(METHOD_DELETE)) {
            doDelete(req, resp);

        } else if (method.equals(METHOD_OPTIONS)) {
            doOptions(req, resp);

        } else if (method.equals(METHOD_TRACE)) {
            doTrace(req, resp);

        } else if (method.equals(METHOD_PATCH)) {
            doPatch(req, resp);

        } else {
            //
            // Note that this means NO servlet supports whatever
            // method was requested, anywhere on this server.
            //

            String errMsg = lStrings.getString("http.method_not_implemented");
            Object[] errArgs = new Object[1];
            errArgs[0] = method;
            errMsg = MessageFormat.format(errMsg, errArgs);

            resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, errMsg);
        }
    }

    //几个常见方法的处理都是由processRequest来处理
	@Override
	protected final void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		processRequest(request, response);
	}

	/**
	 * Delegate POST requests to {@link #processRequest}.
	 * @see #doService
	 */
	@Override
	protected final void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		processRequest(request, response);
	}

	/**
	 * Delegate PUT requests to {@link #processRequest}.
	 * @see #doService
	 */
	@Override
	protected final void doPut(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		processRequest(request, response);
	}

	/**
	 * Delegate DELETE requests to {@link #processRequest}.
	 * @see #doService
	 */
	@Override
	protected final void doDelete(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		processRequest(request, response);
	}

	protected final void processRequest(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		long startTime = System.currentTimeMillis();
		Throwable failureCause = null;

         //保留旧的LocaleContext和RequestAttributes
		LocaleContext previousLocaleContext = LocaleContextHolder.getLocaleContext();
		LocaleContext localeContext = buildLocaleContext(request);

		RequestAttributes previousAttributes = RequestContextHolder.getRequestAttributes();
		ServletRequestAttributes requestAttributes = buildRequestAttributes(request, response, previousAttributes);

		WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request);
		asyncManager.registerCallableInterceptor(FrameworkServlet.class.getName(), new RequestBindingInterceptor());

         //将新的LocaleContext和RequestAttributes设置到上下文中
		initContextHolders(request, localeContext, requestAttributes);

		try {
             //实际处理请求的方法
			doService(request, response);
		}
		catch (ServletException | IOException ex) {
			failureCause = ex;
			throw ex;
		}
		catch (Throwable ex) {
			failureCause = ex;
			throw new ServletException("Request processing failed: " + ex, ex);
		}

		finally {
			resetContextHolders(request, previousLocaleContext, previousAttributes);
			if (requestAttributes != null) {
				requestAttributes.requestCompleted();
			}
			logResult(request, response, failureCause, asyncManager);
			publishRequestHandledEvent(request, response, startTime, failureCause);
		}
	}
    
}
```



```java
public class DispatcherServlet extends FrameworkServlet {
    
	@Override
	protected void doService(HttpServletRequest request, HttpServletResponse response) throws Exception {
		logRequest(request);

		// Keep a snapshot of the request attributes in case of an include,
		// to be able to restore the original attributes after the include.
		Map<String, Object> attributesSnapshot = null;
		if (WebUtils.isIncludeRequest(request)) {
			attributesSnapshot = new HashMap<>();
			Enumeration<?> attrNames = request.getAttributeNames();
			while (attrNames.hasMoreElements()) {
				String attrName = (String) attrNames.nextElement();
				if (this.cleanupAfterInclude || attrName.startsWith(DEFAULT_STRATEGIES_PREFIX)) {
					attributesSnapshot.put(attrName, request.getAttribute(attrName));
				}
			}
		}

        //设置属性到request上 
		// Make framework objects available to handlers and view objects.
		request.setAttribute(WEB_APPLICATION_CONTEXT_ATTRIBUTE, getWebApplicationContext());
		request.setAttribute(LOCALE_RESOLVER_ATTRIBUTE, this.localeResolver);
		request.setAttribute(THEME_RESOLVER_ATTRIBUTE, this.themeResolver);
		request.setAttribute(THEME_SOURCE_ATTRIBUTE, getThemeSource());

        //初始化FlashMap 用于重定向到本地时的参数携带
		if (this.flashMapManager != null) {
			FlashMap inputFlashMap = this.flashMapManager.retrieveAndUpdate(request, response);
			if (inputFlashMap != null) {
				request.setAttribute(INPUT_FLASH_MAP_ATTRIBUTE, Collections.unmodifiableMap(inputFlashMap));
			}
			request.setAttribute(OUTPUT_FLASH_MAP_ATTRIBUTE, new FlashMap());
			request.setAttribute(FLASH_MAP_MANAGER_ATTRIBUTE, this.flashMapManager);
		}

        //解析请求路径
		RequestPath previousRequestPath = null;
		if (this.parseRequestPath) {
			previousRequestPath = (RequestPath) request.getAttribute(ServletRequestPathUtils.PATH_ATTRIBUTE);
			ServletRequestPathUtils.parseAndCache(request);
		}

		try {
             //核心处理方法
			doDispatch(request, response);
		}
		finally {
			if (!WebAsyncUtils.getAsyncManager(request).isConcurrentHandlingStarted()) {
				// Restore the original attribute snapshot, in case of an include.
				if (attributesSnapshot != null) {
					restoreAttributesAfterInclude(request, attributesSnapshot);
				}
			}
			if (this.parseRequestPath) {
				ServletRequestPathUtils.setParsedRequestPath(previousRequestPath, request);
			}
		}
	}
    
	protected void doDispatch(HttpServletRequest request, HttpServletResponse response) throws Exception {
		HttpServletRequest processedRequest = request;
		HandlerExecutionChain mappedHandler = null;
		boolean multipartRequestParsed = false;

		WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request);

		try {
			ModelAndView mv = null;
			Exception dispatchException = null;

			try {
                 //检查是否是mutipart类型的请求
				processedRequest = checkMultipart(request);
                 //是否是mutipart请求
				multipartRequestParsed = (processedRequest != request);
				//获取当前请求的处理器
				// Determine handler for the current request.
				mappedHandler = getHandler(processedRequest);
				if (mappedHandler == null) {
					noHandlerFound(processedRequest, response);
					return;
				}
				
                 //获取当前handler的适配器
				// Determine handler adapter for the current request.
				HandlerAdapter ha = getHandlerAdapter(mappedHandler.getHandler());

                 //处理http缓存相关
				// Process last-modified header, if supported by the handler.
				String method = request.getMethod();
				boolean isGet = HttpMethod.GET.matches(method);
				if (isGet || HttpMethod.HEAD.matches(method)) {
					long lastModified = ha.getLastModified(request, mappedHandler.getHandler());
					if (new ServletWebRequest(request, response).checkNotModified(lastModified) && isGet) {
						return;
					}
				}
				//调用拦截器的preHandle方法
				if (!mappedHandler.applyPreHandle(processedRequest, response)) {
					return;
				}

				// Actually invoke the handler.
				mv = ha.handle(processedRequest, response, mappedHandler.getHandler());

				if (asyncManager.isConcurrentHandlingStarted()) {
					return;
				}

				applyDefaultViewName(processedRequest, mv);
				mappedHandler.applyPostHandle(processedRequest, response, mv);
			}
			catch (Exception ex) {
				dispatchException = ex;
			}
			catch (Throwable err) {
				// As of 4.3, we're processing Errors thrown from handler methods as well,
				// making them available for @ExceptionHandler methods and other scenarios.
				dispatchException = new ServletException("Handler dispatch failed: " + err, err);
			}
			processDispatchResult(processedRequest, response, mappedHandler, mv, dispatchException);
		}
		catch (Exception ex) {
			triggerAfterCompletion(processedRequest, response, mappedHandler, ex);
		}
		catch (Throwable err) {
			triggerAfterCompletion(processedRequest, response, mappedHandler,
					new ServletException("Handler processing failed: " + err, err));
		}
		finally {
			if (asyncManager.isConcurrentHandlingStarted()) {
				// Instead of postHandle and afterCompletion
				if (mappedHandler != null) {
					mappedHandler.applyAfterConcurrentHandlingStarted(processedRequest, response);
				}
				asyncManager.setMultipartRequestParsed(multipartRequestParsed);
			}
			else {
				// Clean up any resources used by a multipart request.
				if (multipartRequestParsed || asyncManager.isMultipartRequestParsed()) {
					cleanupMultipart(processedRequest);
				}
			}
		}
	}

}
```









### 文件上传解析器

#### 初始化

```java
public class DispatcherServlet extends FrameworkServlet {
    
	public static final String MULTIPART_RESOLVER_BEAN_NAME = "multipartResolver";

    //初始化multipartResolver
	private void initMultipartResolver(ApplicationContext context) {
		try {
			this.multipartResolver = context.getBean(MULTIPART_RESOLVER_BEAN_NAME, MultipartResolver.class);
			if (logger.isTraceEnabled()) {
				logger.trace("Detected " + this.multipartResolver);
			}
			else if (logger.isDebugEnabled()) {
				logger.debug("Detected " + this.multipartResolver.getClass().getSimpleName());
			}
		}
		catch (NoSuchBeanDefinitionException ex) {
			// Default is no multipart resolver.
			this.multipartResolver = null;
			if (logger.isTraceEnabled()) {
				logger.trace("No MultipartResolver '" + MULTIPART_RESOLVER_BEAN_NAME + "' declared");
			}
		}
	}
    
    //检查是否是mutipart类型的请求
	protected HttpServletRequest checkMultipart(HttpServletRequest request) throws MultipartException {
         //检查当前请求的content-type是否是multipart/form-data类型
		if (this.multipartResolver != null && this.multipartResolver.isMultipart(request)) {
			if (WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class) != null) {
				if (DispatcherType.REQUEST.equals(request.getDispatcherType())) {
					logger.trace("Request already resolved to MultipartHttpServletRequest, e.g. by MultipartFilter");
				}
			}
			else if (hasMultipartException(request)) {
				logger.debug("Multipart resolution previously failed for current request - " +
						"skipping re-resolution for undisturbed error rendering");
			}
			else {
				try {
                      //对请求进行包装和解析
					return this.multipartResolver.resolveMultipart(request);
				}
				catch (MultipartException ex) {
					if (request.getAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE) != null) {
						logger.debug("Multipart resolution failed for error dispatch", ex);
						// Keep processing error dispatch with regular request handle below
					}
					else {
						throw ex;
					}
				}
			}
		}
		// If not returned before: return original request.
		return request;
	}


}
```

#### 请求处理

```java
public class StandardServletMultipartResolver implements MultipartResolver {

    //检查当前请求的content-type是否是multipart/form-data类型
	@Override
	public boolean isMultipart(HttpServletRequest request) {
		return StringUtils.startsWithIgnoreCase(request.getContentType(),
				(this.strictServletCompliance ? MediaType.MULTIPART_FORM_DATA_VALUE : "multipart/"));
	}

    //将普通请求包装成StandardMultipartHttpServletRequest类型
	@Override
	public MultipartHttpServletRequest resolveMultipart(HttpServletRequest request) throws MultipartException {
		return new StandardMultipartHttpServletRequest(request, this.resolveLazily);
	}
}
```

```java
public class StandardMultipartHttpServletRequest extends AbstractMultipartHttpServletRequest {

	public StandardMultipartHttpServletRequest(HttpServletRequest request, boolean lazyParsing)
			throws MultipartException {

		super(request);
		if (!lazyParsing) {
             //解析请求
			parseRequest(request);
		}
	}
    
	private void parseRequest(HttpServletRequest request) {
		try {
             //获取mutipart请求中的各个部分
			Collection<Part> parts = request.getParts();
			this.multipartParameterNames = new LinkedHashSet<>(parts.size());
			MultiValueMap<String, MultipartFile> files = new LinkedMultiValueMap<>(parts.size());
			for (Part part : parts) {
                 //获取解析各个部分的Content-Disposition
				String headerValue = part.getHeader(HttpHeaders.CONTENT_DISPOSITION);
				ContentDisposition disposition = ContentDisposition.parse(headerValue);
                 //有filename字段的 都是文件
				String filename = disposition.getFilename();
				if (filename != null) {
					files.add(part.getName(), new StandardMultipartFile(part, filename));
				}
				else {
                      //否则都是普通参数
					this.multipartParameterNames.add(part.getName());
				}
			}
             //将文件类型的参数保存
			setMultipartFiles(files);
		}
		catch (Throwable ex) {
			handleParseFailure(ex);
		}
	}
}
```











### 处理器映射器

#### 初始化

1. 容器刷新时会初始化`initHandlerMappings`，默认根据`DispatcherServlet.properties`文件创建`handlerMapping`
2. 容器解析xml文件时，会读取`<mvc:interceptors>`标签并且交给`InterceptorsBeanDefinitionParser`处理，`InterceptorsBeanDefinitionParser`会将每个`interceptor`包装成一个`MappedInterceptor`类型的bean
3. `BeanNameUrlHandlerMapping,RequestMappingHandlerMapping,RouterFunctionMapping`都继承自`AbstractHandlerMapping`，`AbstractHandlerMapping`是实现了`ApplicationContextAware`
   1. `BeanNameUrlHandlerMapping`的创建
      1. `BeanNameUrlHandlerMapping`在`createBean`时，会在`initializeBean`调用`ApplicationContextAwareProcessor`处理`ApplicationContextAware`
      2. 然后调用到`AbstractDetectingUrlHandlerMapping`子类实现的`initApplicationContext`中
      3. 调用到父类`AbstractHandlerMapping`的`initApplicationContext`，收集`MappedInterceptor`类型的bean到`mappedInterceptors`
      4. 调用`detectHandlers`，将`/`开头的bean处理
         1. 如果url是 / ，那么设置为根handler
         2. 如果url是 /*，那么设置为默认handler
         3. 放到url - handler中
         4. 将路径解析一下然后放到路径匹配-handler中

   2. `RequestMappingHandlerMapping`的创建
      1. `RequestMappingHandlerMapping`在`createBean`时，会在`initializeBean`调用`invokeInitMethods`处理`InitializingBean`
      2. `afterPropertiesSet`中的`initHandlerMethods`对所有bean进行循环处理
      3. 如果bean上有`@Controller`注解标识，那么对此类的所有方法获取处理`@RequestMapping`注解，包装成`RequestMappingInfo`
      4. 将方法和`RequestMappingInfo`注册到`mappingRegistry`
         1. 加入直接的url-handler中
         2. 加入name-handler中
         3. 如果有跨域配置，加入跨域集合中
         4. 加入总注册map中


```java
public class DispatcherServlet extends FrameworkServlet {
    
	private static final String DEFAULT_STRATEGIES_PATH = "DispatcherServlet.properties";

    //初始化handlerMapping
	private void initHandlerMappings(ApplicationContext context) {
		this.handlerMappings = null;

        //如果设置了从容器中获取全部的handlerMapping
		if (this.detectAllHandlerMappings) {
			// Find all HandlerMappings in the ApplicationContext, including ancestor contexts.
			Map<String, HandlerMapping> matchingBeans =
					BeanFactoryUtils.beansOfTypeIncludingAncestors(context, HandlerMapping.class, true, false);
			if (!matchingBeans.isEmpty()) {
				this.handlerMappings = new ArrayList<>(matchingBeans.values());
				// We keep HandlerMappings in sorted order.
				AnnotationAwareOrderComparator.sort(this.handlerMappings);
			}
		}
        //否则只取一个
		else {
			try {
				HandlerMapping hm = context.getBean(HANDLER_MAPPING_BEAN_NAME, HandlerMapping.class);
				this.handlerMappings = Collections.singletonList(hm);
			}
			catch (NoSuchBeanDefinitionException ex) {
				// Ignore, we'll add a default HandlerMapping later.
			}
		}

        //如果容器中取不到，就从默认中获取
		// Ensure we have at least one HandlerMapping, by registering
		// a default HandlerMapping if no other mappings are found.
		if (this.handlerMappings == null) {
			this.handlerMappings = getDefaultStrategies(context, HandlerMapping.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No HandlerMappings declared for servlet '" + getServletName() +
						"': using default strategies from DispatcherServlet.properties");
			}
		}

		for (HandlerMapping mapping : this.handlerMappings) {
			if (mapping.usesPathPatterns()) {
				this.parseRequestPath = true;
				break;
			}
		}
	}

    //从DispatcherServlet.properties中获取默认的handlerMapping类
    //BeanNameUrlHandlerMapping,RequestMappingHandlerMapping,RouterFunctionMapping
	@SuppressWarnings("unchecked")
	protected <T> List<T> getDefaultStrategies(ApplicationContext context, Class<T> strategyInterface) {
		if (defaultStrategies == null) {
			try {
				// Load default strategy implementations from properties file.
				// This is currently strictly internal and not meant to be customized
				// by application developers.
				ClassPathResource resource = new ClassPathResource(DEFAULT_STRATEGIES_PATH, DispatcherServlet.class);
				defaultStrategies = PropertiesLoaderUtils.loadProperties(resource);
			}
			catch (IOException ex) {
				throw new IllegalStateException("Could not load '" + DEFAULT_STRATEGIES_PATH + "': " + ex.getMessage());
			}
		}

		String key = strategyInterface.getName();
		String value = defaultStrategies.getProperty(key);
		if (value != null) {
			String[] classNames = StringUtils.commaDelimitedListToStringArray(value);
			List<T> strategies = new ArrayList<>(classNames.length);
			for (String className : classNames) {
				try {
					Class<?> clazz = ClassUtils.forName(className, DispatcherServlet.class.getClassLoader());
                      //通过mvc容器createBean方法创建handlerMapping对象
					Object strategy = createDefaultStrategy(context, clazz);
					strategies.add((T) strategy);
				}
				catch (ClassNotFoundException ex) {
					throw new BeanInitializationException(
							"Could not find DispatcherServlet's default strategy class [" + className +
							"] for interface [" + key + "]", ex);
				}
				catch (LinkageError err) {
					throw new BeanInitializationException(
							"Unresolvable class definition for DispatcherServlet's default strategy class [" +
							className + "] for interface [" + key + "]", err);
				}
			}
			return strategies;
		}
		else {
			return Collections.emptyList();
		}
	}


}
```



**AbstractDetectingUrlHandlerMapping**

```java
public abstract class AbstractDetectingUrlHandlerMapping extends AbstractUrlHandlerMapping {
    
    //由AwareProcesser调用
	@Override
	public void initApplicationContext() throws ApplicationContextException {
		super.initApplicationContext();
		detectHandlers();
	}
    
    //获取全部的urlHandler
	protected void detectHandlers() throws BeansException {
		ApplicationContext applicationContext = obtainApplicationContext();
        //获取所有的beanNames
		String[] beanNames = (this.detectHandlersInAncestorContexts ?
				BeanFactoryUtils.beanNamesForTypeIncludingAncestors(applicationContext, Object.class) :
				applicationContext.getBeanNamesForType(Object.class));

        //逐个判断各个beanName是不是/beanName 这样的格式
		// Take any bean name that we can determine URLs for.
		for (String beanName : beanNames) {
			String[] urls = determineUrlsForHandler(beanName);
			if (!ObjectUtils.isEmpty(urls)) {
				// URL paths found: Let's consider it a handler.
                 //如果是 注册到urlHandler中
				registerHandler(urls, beanName);
			}
		}

		if (mappingsLogger.isDebugEnabled()) {
			mappingsLogger.debug(formatMappingName() + " " + getHandlerMap());
		}
		else if ((logger.isDebugEnabled() && !getHandlerMap().isEmpty()) || logger.isTraceEnabled()) {
			logger.debug("Detected " + getHandlerMap().size() + " mappings in " + formatMappingName());
		}
	}
}
```

**AbstractUrlHandlerMapping**

```java
public abstract class AbstractUrlHandlerMapping extends AbstractHandlerMapping implements MatchableHandlerMapping {

    //将对应的url和handler注册
	protected void registerHandler(String[] urlPaths, String beanName) throws BeansException, IllegalStateException {
		Assert.notNull(urlPaths, "URL path array must not be null");
		for (String urlPath : urlPaths) {
			registerHandler(urlPath, beanName);
		}
	}

	protected void registerHandler(String urlPath, Object handler) throws BeansException, IllegalStateException {
		Assert.notNull(urlPath, "URL path must not be null");
		Assert.notNull(handler, "Handler object must not be null");
		Object resolvedHandler = handler;

        //根据beanName获取bean
		// Eagerly resolve handler if referencing singleton via name.
		if (!this.lazyInitHandlers && handler instanceof String handlerName) {
			ApplicationContext applicationContext = obtainApplicationContext();
			if (applicationContext.isSingleton(handlerName)) {
				resolvedHandler = applicationContext.getBean(handlerName);
			}
		}

        //如果对应url的bean存在 报错
		Object mappedHandler = this.handlerMap.get(urlPath);
		if (mappedHandler != null) {
			if (mappedHandler != resolvedHandler) {
				throw new IllegalStateException(
						"Cannot map " + getHandlerDescription(handler) + " to URL path [" + urlPath +
						"]: There is already " + getHandlerDescription(mappedHandler) + " mapped.");
			}
		}
		else {
             //如果url是 / ，那么设置为根handler
			if (urlPath.equals("/")) {
				if (logger.isTraceEnabled()) {
					logger.trace("Root mapping to " + getHandlerDescription(handler));
				}
				setRootHandler(resolvedHandler);
			}
             //如果url是 /*，那么设置为默认handler
			else if (urlPath.equals("/*")) {
				if (logger.isTraceEnabled()) {
					logger.trace("Default mapping to " + getHandlerDescription(handler));
				}
				setDefaultHandler(resolvedHandler);
			}
			else {
                 //设置url - handler
				this.handlerMap.put(urlPath, resolvedHandler);
                 //如果有路径解析器
				if (getPatternParser() != null) {
                      //将路径解析一下然后放到路径匹配-handler中
					this.pathPatternHandlerMap.put(getPatternParser().parse(urlPath), resolvedHandler);
				}
				if (logger.isTraceEnabled()) {
					logger.trace("Mapped [" + urlPath + "] onto " + getHandlerDescription(handler));
				}
			}
		}
	}

}
```











#### 继承关系

<img src=".\images\image-20251103100014993.png" alt="image-20251103100014993" style="zoom:50%;" />

| 类名                               | 主要作用                                                     | 特点                                                         |
| ---------------------------------- | ------------------------------------------------------------ | ------------------------------------------------------------ |
| **`BeanNameUrlHandlerMapping`**    | 通过 **bean 的名称** 来匹配 URL。                            | 如果某个 bean 名字以 `/` 开头，例如 `/hello`，就会被当作处理该路径的 handler。 |
| **`RequestMappingHandlerMapping`** | Spring MVC 最常用的映射器，通过 `@RequestMapping`、`@GetMapping` 等注解来匹配。 | 是基于注解的映射机制。                                       |
| **`RouterFunctionMapping`**        | Spring WebFlux 风格的函数式路由支持。                        | 支持类似 `RouterFunctions.route(GET("/hello"), handler::handleHello)` 的写法。 |





#### 请求处理

```java
public class DispatcherServlet extends FrameworkServlet {


    //从handlerMapping中循环判断是否能获取到handler
	@Nullable
	protected HandlerExecutionChain getHandler(HttpServletRequest request) throws Exception {
		if (this.handlerMappings != null) {
			for (HandlerMapping mapping : this.handlerMappings) {
                 //找到一个handler就返回
				HandlerExecutionChain handler = mapping.getHandler(request);
				if (handler != null) {
					return handler;
				}
			}
		}
		return null;
	}
}
```

##### AbstractHandlerMapping

```java
public abstract class AbstractHandlerMapping extends WebApplicationObjectSupport
       implements HandlerMapping, Ordered, BeanNameAware {

	@Override
	@Nullable
	public final HandlerExecutionChain getHandler(HttpServletRequest request) throws Exception {
         //调用getHandlerInternal获取handler
		Object handler = getHandlerInternal(request);
         //使用默认的handler
		if (handler == null) {
			handler = getDefaultHandler();
		}
         //返回null
		if (handler == null) {
			return null;
		}
         //如果是beanName 就获取一次
		// Bean name or resolved handler?
		if (handler instanceof String handlerName) {
			handler = obtainApplicationContext().getBean(handlerName);
		}

		// Ensure presence of cached lookupPath for interceptors and others
		if (!ServletRequestPathUtils.hasCachedPath(request)) {
			initLookupPath(request);
		}
		//将adaptedInterceptors的拦截器设置到HandlerExecutionChain
		HandlerExecutionChain executionChain = getHandlerExecutionChain(handler, request);

		if (request.getAttribute(SUPPRESS_LOGGING_ATTRIBUTE) == null) {
			if (logger.isTraceEnabled()) {
				logger.trace("Mapped to " + handler);
			}
			else if (logger.isDebugEnabled() && !DispatcherType.ASYNC.equals(request.getDispatcherType())) {
				logger.debug("Mapped to " + executionChain.getHandler());
			}
		}

        //跨域的处理
		if (hasCorsConfigurationSource(handler) || CorsUtils.isPreFlightRequest(request)) {
			CorsConfiguration config = getCorsConfiguration(handler, request);
			if (getCorsConfigurationSource() != null) {
				CorsConfiguration globalConfig = getCorsConfigurationSource().getCorsConfiguration(request);
				config = (globalConfig != null ? globalConfig.combine(config) : config);
			}
			if (config != null) {
				config.validateAllowCredentials();
				config.validateAllowPrivateNetwork();
			}
			executionChain = getCorsHandlerExecutionChain(request, executionChain, config);
		}

		return executionChain;
	}
    
	@Nullable
	protected abstract Object getHandlerInternal(HttpServletRequest request) throws Exception;

}
```







###### AbstractUrlHandlerMapping

```java
public abstract class AbstractUrlHandlerMapping extends AbstractHandlerMapping implements MatchableHandlerMapping {
    
    //mvc容器启动后从xml文件加载的url-handler
    private final Map<String, Object> handlerMap = new LinkedHashMap<>();

    
	@Override
	@Nullable
	protected Object getHandlerInternal(HttpServletRequest request) throws Exception {
         //获取请求url的路径 /api/v1/getUserInfo
		String lookupPath = initLookupPath(request);
		Object handler;
         //如果使用了路径解析
		if (usesPathPatterns()) {
             //根据路径查找handler
			RequestPath path = ServletRequestPathUtils.getParsedRequestPath(request);
			handler = lookupHandler(path, lookupPath, request);
		}
		else {
			handler = lookupHandler(lookupPath, request);
		}
		if (handler == null) {
			// We need to care for the default handler directly, since we need to
			// expose the PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE for it as well.
			Object rawHandler = null;
             //如果路径就是一个 / 那么使用根handler
			if (StringUtils.matchesCharacter(lookupPath, '/')) {
				rawHandler = getRootHandler();
			}
             //使用默认的handler
			if (rawHandler == null) {
				rawHandler = getDefaultHandler();
			}
             //包装handler
			if (rawHandler != null) {
				// Bean name or resolved handler?
				if (rawHandler instanceof String handlerName) {
					rawHandler = obtainApplicationContext().getBean(handlerName);
				}
				validateHandler(rawHandler, request);
				handler = buildPathExposingHandler(rawHandler, lookupPath, lookupPath, null);
			}
		}
		return handler;
	}
    
    //查找handler
	@Nullable
	protected Object lookupHandler(
			RequestPath path, String lookupPath, HttpServletRequest request) throws Exception {

        //直接从map中查找对应url的handler
		Object handler = getDirectMatch(lookupPath, request);
		if (handler != null) {
			return handler;
		}

        //通过路径匹配对应的url，类似/users/*, /users/{id}这样的
		// Pattern match?
		List<PathPattern> matches = null;
		for (PathPattern pattern : this.pathPatternHandlerMap.keySet()) {
			if (pattern.matches(path.pathWithinApplication())) {
				matches = (matches != null ? matches : new ArrayList<>());
				matches.add(pattern);
			}
		}
		if (matches == null) {
			return null;
		}
		if (matches.size() > 1) {
			matches.sort(PathPattern.SPECIFICITY_COMPARATOR);
			if (logger.isTraceEnabled()) {
				logger.trace("Matching patterns " + matches);
			}
		}
		PathPattern pattern = matches.get(0);
		handler = this.pathPatternHandlerMap.get(pattern);
		if (handler instanceof String handlerName) {
			handler = obtainApplicationContext().getBean(handlerName);
		}
		validateHandler(handler, request);
		String pathWithinMapping = pattern.extractPathWithinPattern(path.pathWithinApplication()).value();
		pathWithinMapping = UrlPathHelper.defaultInstance.removeSemicolonContent(pathWithinMapping);
		PathPattern.PathMatchInfo pathMatchInfo = pattern.matchAndExtract(path);
		Map<String, String> uriVariables = (pathMatchInfo != null ? pathMatchInfo.getUriVariables(): null);
		return buildPathExposingHandler(handler, pattern.getPatternString(), pathWithinMapping, uriVariables);
	}

    //直接从map中查找对应url的handler
	@Nullable
	private Object getDirectMatch(String urlPath, HttpServletRequest request) throws Exception {
         //直接从map中查找对应url的handler
		Object handler = this.handlerMap.get(urlPath);
		if (handler != null) {
             //如果是beanName就获取一次
			// Bean name or resolved handler?
			if (handler instanceof String handlerName) {
				handler = obtainApplicationContext().getBean(handlerName);
			}
			validateHandler(handler, request);
             //将handler包装成HandlerExecutionChain对象，加入Interceptor
			return buildPathExposingHandler(handler, urlPath, urlPath, null);
		}
		return null;
	}
    
    //将handler包装成HandlerExecutionChain对象，加入Interceptor
	protected Object buildPathExposingHandler(Object rawHandler, String bestMatchingPattern,
			String pathWithinMapping, @Nullable Map<String, String> uriTemplateVariables) {

		HandlerExecutionChain chain = new HandlerExecutionChain(rawHandler);
		chain.addInterceptor(new PathExposingHandlerInterceptor(bestMatchingPattern, pathWithinMapping));
		if (!CollectionUtils.isEmpty(uriTemplateVariables)) {
			chain.addInterceptor(new UriTemplateVariablesHandlerInterceptor(uriTemplateVariables));
		}
		return chain;
	}
}
```

###### AbstractHandlerMethodMapping

```java
public abstract class AbstractHandlerMethodMapping<T> extends AbstractHandlerMapping implements InitializingBean {
    
    //mvc容器调用此方法
	@Override
	public void afterPropertiesSet() {
		initHandlerMethods();
	}
    
    
	protected void initHandlerMethods() {
         //获取mvc容器中所有bean
		for (String beanName : getCandidateBeanNames()) {
			if (!beanName.startsWith(SCOPED_TARGET_NAME_PREFIX)) {
				processCandidateBean(beanName);
			}
		}
		handlerMethodsInitialized(getHandlerMethods());
	}
    
	protected void processCandidateBean(String beanName) {
		Class<?> beanType = null;
		try {
			beanType = obtainApplicationContext().getType(beanName);
		}
		catch (Throwable ex) {
			// An unresolvable bean type, probably from a lazy bean - let's ignore it.
			if (logger.isTraceEnabled()) {
				logger.trace("Could not resolve type for bean '" + beanName + "'", ex);
			}
		}
         //针对@Controller注解标注的类
		if (beanType != null && isHandler(beanType)) {
			detectHandlerMethods(beanName);
		}
	}
    

    
	protected void detectHandlerMethods(Object handler) {
        //获取@Controller注解类
		Class<?> handlerType = (handler instanceof String beanName ?
				obtainApplicationContext().getType(beanName) : handler.getClass());

		if (handlerType != null) {
             //获取原生类
			Class<?> userType = ClassUtils.getUserClass(handlerType);
             //针对@RequestMapping和@HttpExchange注解 创建RequestMappingInfo
			Map<Method, T> methods = MethodIntrospector.selectMethods(userType,
					(MethodIntrospector.MetadataLookup<T>) method -> {
						try {
							return getMappingForMethod(method, userType);
						}
						catch (Throwable ex) {
							throw new IllegalStateException("Invalid mapping on handler class [" +
									userType.getName() + "]: " + method, ex);
						}
					});
			if (logger.isTraceEnabled()) {
				logger.trace(formatMappings(userType, methods));
			}
			else if (mappingsLogger.isDebugEnabled()) {
				mappingsLogger.debug(formatMappings(userType, methods));
			}
             //将url和方法注册到mappingRegistry
			methods.forEach((method, mapping) -> {
				Method invocableMethod = AopUtils.selectInvocableMethod(method, userType);
				registerHandlerMethod(handler, invocableMethod, mapping);
			});
		}
	}

    protected void registerHandlerMethod(Object handler, Method method, T mapping) {
		this.mappingRegistry.register(mapping, handler, method);
	}
    
    //获取handler
	@Override
	@Nullable
	protected HandlerMethod getHandlerInternal(HttpServletRequest request) throws Exception {
         //获取url
		String lookupPath = initLookupPath(request);
		this.mappingRegistry.acquireReadLock();
		try {
             //获取handler
			HandlerMethod handlerMethod = lookupHandlerMethod(lookupPath, request);
             //返回handler
			return (handlerMethod != null ? handlerMethod.createWithResolvedBean() : null);
		}
		finally {
			this.mappingRegistry.releaseReadLock();
		}
	}
    
	@Nullable
	protected HandlerMethod lookupHandlerMethod(String lookupPath, HttpServletRequest request) throws Exception {
		List<Match> matches = new ArrayList<>();
         //直接根据url从pathLookup中获取匹配项
		List<T> directPathMatches = this.mappingRegistry.getMappingsByDirectPath(lookupPath);
         //添加到匹配列表中
		if (directPathMatches != null) {
			addMatchingMappings(directPathMatches, matches, request);
		}
		if (matches.isEmpty()) {
			addMatchingMappings(this.mappingRegistry.getRegistrations().keySet(), matches, request);
		}
		if (!matches.isEmpty()) {
			Match bestMatch = matches.get(0);
             //如果有多个匹配项
			if (matches.size() > 1) {
				Comparator<Match> comparator = new MatchComparator(getMappingComparator(request));
				matches.sort(comparator);
                 //取最佳
				bestMatch = matches.get(0);
				if (logger.isTraceEnabled()) {
					logger.trace(matches.size() + " matching mappings: " + matches);
				}
				if (CorsUtils.isPreFlightRequest(request)) {
					for (Match match : matches) {
						if (match.hasCorsConfig()) {
							return PREFLIGHT_AMBIGUOUS_MATCH;
						}
					}
				}
				else {
                      //比较最佳和第二佳
					Match secondBestMatch = matches.get(1);
                      //如果两个比较相同 报错
					if (comparator.compare(bestMatch, secondBestMatch) == 0) {
						Method m1 = bestMatch.getHandlerMethod().getMethod();
						Method m2 = secondBestMatch.getHandlerMethod().getMethod();
						String uri = request.getRequestURI();
						throw new IllegalStateException(
								"Ambiguous handler methods mapped for '" + uri + "': {" + m1 + ", " + m2 + "}");
					}
				}
			}
             //处理返回handler
			request.setAttribute(BEST_MATCHING_HANDLER_ATTRIBUTE, bestMatch.getHandlerMethod());
			handleMatch(bestMatch.mapping, lookupPath, request);
			return bestMatch.getHandlerMethod();
		}
		else {
			return handleNoMatch(this.mappingRegistry.getRegistrations().keySet(), lookupPath, request);
		}
	}
	//加入匹配列表中
	private void addMatchingMappings(Collection<T> mappings, List<Match> matches, HttpServletRequest request) {
		for (T mapping : mappings) {
             //检查当前请求与各种条件是否匹配
			T match = getMatchingMapping(mapping, request);
             //全部匹配加入匹配列表
			if (match != null) {
				matches.add(new Match(match, this.mappingRegistry.getRegistrations().get(mapping)));
			}
		}
	}
}
```



**RequestMappingHandlerMapping**

```java
public class RequestMappingHandlerMapping extends RequestMappingInfoHandlerMapping
       implements MatchableHandlerMapping, EmbeddedValueResolverAware {

	@Override
	@Nullable
	protected RequestMappingInfo getMappingForMethod(Method method, Class<?> handlerType) {
        //读取方法上的@RequestMapping和@HttpExchange注解 创建RequestMappingInfo
		RequestMappingInfo info = createRequestMappingInfo(method);
		if (info != null) {
            //读取类上的@RequestMapping和@HttpExchange注解 创建RequestMappingInfo
			RequestMappingInfo typeInfo = createRequestMappingInfo(handlerType);
			if (typeInfo != null) {
                  //合并两个RequestMappingInfo
				info = typeInfo.combine(info);
			}
			if (info.isEmptyMapping()) {
				info = info.mutate().paths("", "/").options(this.config).build();
			}
			String prefix = getPathPrefix(handlerType);
			if (prefix != null) {
				info = RequestMappingInfo.paths(prefix).options(this.config).build().combine(info);
			}
		}
		return info;
	}
    
    //读取方法上的@RequestMapping和@HttpExchange注解 创建RequestMappingInfo
	@Nullable
	private RequestMappingInfo createRequestMappingInfo(AnnotatedElement element) {
		RequestMappingInfo requestMappingInfo = null;
		RequestCondition<?> customCondition = (element instanceof Class<?> clazz ?
				getCustomTypeCondition(clazz) : getCustomMethodCondition((Method) element));

		List<AnnotationDescriptor> descriptors = getAnnotationDescriptors(element);

		List<AnnotationDescriptor> requestMappings = descriptors.stream()
				.filter(desc -> desc.annotation instanceof RequestMapping).toList();
		if (!requestMappings.isEmpty()) {
			if (requestMappings.size() > 1 && logger.isWarnEnabled()) {
				logger.warn("Multiple @RequestMapping annotations found on %s, but only the first will be used: %s"
						.formatted(element, requestMappings));
			}
			requestMappingInfo = createRequestMappingInfo((RequestMapping) requestMappings.get(0).annotation, customCondition);
		}

		List<AnnotationDescriptor> httpExchanges = descriptors.stream()
				.filter(desc -> desc.annotation instanceof HttpExchange).toList();
		if (!httpExchanges.isEmpty()) {
			Assert.state(requestMappingInfo == null,
					() -> "%s is annotated with @RequestMapping and @HttpExchange annotations, but only one is allowed: %s"
							.formatted(element, Stream.of(requestMappings, httpExchanges).flatMap(List::stream).toList()));
			Assert.state(httpExchanges.size() == 1,
					() -> "Multiple @HttpExchange annotations found on %s, but only one is allowed: %s"
							.formatted(element, httpExchanges));
			requestMappingInfo = createRequestMappingInfo((HttpExchange) httpExchanges.get(0).annotation, customCondition);
		}

		return requestMappingInfo;
	}
    
    //读取@RequestMapping注解上的属性 构建RequestMappingInfo
	protected RequestMappingInfo createRequestMappingInfo(
			RequestMapping requestMapping, @Nullable RequestCondition<?> customCondition) {

		RequestMappingInfo.Builder builder = RequestMappingInfo
				.paths(resolveEmbeddedValuesInPatterns(requestMapping.path()))
				.methods(requestMapping.method())
				.params(requestMapping.params())
				.headers(requestMapping.headers())
				.consumes(requestMapping.consumes())
				.produces(requestMapping.produces())
				.mappingName(requestMapping.name());

		if (customCondition != null) {
			builder.customCondition(customCondition);
		}

		return builder.options(this.config).build();
	}
    
    //判断是否有@Controller注解
	@Override
	protected boolean isHandler(Class<?> beanType) {
		return AnnotatedElementUtils.hasAnnotation(beanType, Controller.class);
	}
    
    
    //根据@CrossOrigin注解创建CorsConfiguration
	@Override
	@Nullable
	protected CorsConfiguration initCorsConfiguration(Object handler, Method method, RequestMappingInfo mappingInfo) {
		HandlerMethod handlerMethod = createHandlerMethod(handler, method);
		Class<?> beanType = handlerMethod.getBeanType();
         //读取类上的@CrossOrigin
		CrossOrigin typeAnnotation = AnnotatedElementUtils.findMergedAnnotation(beanType, CrossOrigin.class);
        //读取方法上的@CrossOrigin
		CrossOrigin methodAnnotation = AnnotatedElementUtils.findMergedAnnotation(method, CrossOrigin.class);

		if (typeAnnotation == null && methodAnnotation == null) {
			return null;
		}
		//根据类上和方法的@CrossOrigin更新配置
		CorsConfiguration config = new CorsConfiguration();
		updateCorsConfig(config, typeAnnotation);
		updateCorsConfig(config, methodAnnotation);
		
         //如果没配置允许的方法类型 用当前方法的方法类型
		if (CollectionUtils.isEmpty(config.getAllowedMethods())) {
			for (RequestMethod allowedMethod : mappingInfo.getMethodsCondition().getMethods()) {
				config.addAllowedMethod(allowedMethod.name());
			}
		}
		return config.applyPermitDefaultValues();
	}

}
```

**MappingRegistry**

```java
public abstract class AbstractHandlerMethodMapping<T> extends AbstractHandlerMapping implements InitializingBean {


	class MappingRegistry {
    
    	//将路径和handler方法注册
		public void register(T mapping, Object handler, Method method) {
			this.readWriteLock.writeLock().lock();
			try {
                 //创建HandlerMethod
				HandlerMethod handlerMethod = createHandlerMethod(handler, method);
				validateMethodMapping(handlerMethod, mapping);

				// Enable method validation, if applicable
				handlerMethod = handlerMethod.createWithValidateFlags();
				
                 //将url和handler方法 注册到pathLookup
				Set<String> directPaths = AbstractHandlerMethodMapping.this.getDirectPaths(mapping);
				for (String path : directPaths) {
					this.pathLookup.add(path, mapping);
				}

				String name = null;
				if (getNamingStrategy() != null) {
					name = getNamingStrategy().getName(handlerMethod, mapping);
					addMappingName(name, handlerMethod);
				}

                 //处理@CrossOrigin注解
				CorsConfiguration corsConfig = initCorsConfiguration(handler, method, mapping);
				if (corsConfig != null) {
					corsConfig.validateAllowCredentials();
					corsConfig.validateAllowPrivateNetwork();
					this.corsLookup.put(handlerMethod, corsConfig);
				}
				//注册到registry
				this.registry.put(mapping,
						new MappingRegistration<>(mapping, handlerMethod, directPaths, name, corsConfig != null));
			}
			finally {
				this.readWriteLock.writeLock().unlock();
			}
		}
	}
    //创建HandlerMethod
	protected HandlerMethod createHandlerMethod(Object handler, Method method) {
		if (handler instanceof String beanName) {
			return new HandlerMethod(beanName,
					obtainApplicationContext().getAutowireCapableBeanFactory(),
					obtainApplicationContext(),
					method);
		}
		return new HandlerMethod(handler, method);
	}
}
```

**RequestMappingInfo**

在 Spring MVC 中，每个 `@RequestMapping` 注解都会被解析成一个 `RequestMappingInfo`

```java
public final class RequestMappingInfo implements RequestCondition<RequestMappingInfo> {

	@Nullable
	private final String name;

    
	@Nullable
	private final PathPatternsRequestCondition pathPatternsCondition;
	
	@Nullable
	private final PatternsRequestCondition patternsCondition;
	//请求方法匹配
	private final RequestMethodsRequestCondition methodsCondition;
	//@RequstMapping中的属性params匹配
	private final ParamsRequestCondition paramsCondition;
    //header匹配
	private final HeadersRequestCondition headersCondition;
    //consumes匹配
	private final ConsumesRequestCondition consumesCondition;
    //produces匹配
	private final ProducesRequestCondition producesCondition;
    //自定义匹配
	private final RequestConditionHolder customConditionHolder;

	private final int hashCode;

	private final BuilderConfiguration options;
    
    //检查当前请求与方法的各种条件匹配是否成立，不成立返回null
	@Override
	@Nullable
	public RequestMappingInfo getMatchingCondition(HttpServletRequest request) {
		RequestMethodsRequestCondition methods = this.methodsCondition.getMatchingCondition(request);
		if (methods == null) {
			return null;
		}
		ParamsRequestCondition params = this.paramsCondition.getMatchingCondition(request);
		if (params == null) {
			return null;
		}
		HeadersRequestCondition headers = this.headersCondition.getMatchingCondition(request);
		if (headers == null) {
			return null;
		}
		ConsumesRequestCondition consumes = this.consumesCondition.getMatchingCondition(request);
		if (consumes == null) {
			return null;
		}
		ProducesRequestCondition produces = this.producesCondition.getMatchingCondition(request);
		if (produces == null) {
			return null;
		}
		PathPatternsRequestCondition pathPatterns = null;
		if (this.pathPatternsCondition != null) {
			pathPatterns = this.pathPatternsCondition.getMatchingCondition(request);
			if (pathPatterns == null) {
				return null;
			}
		}
		PatternsRequestCondition patterns = null;
		if (this.patternsCondition != null) {
			patterns = this.patternsCondition.getMatchingCondition(request);
			if (patterns == null) {
				return null;
			}
		}
		RequestConditionHolder custom = this.customConditionHolder.getMatchingCondition(request);
		if (custom == null) {
			return null;
		}
         //包装返回新的RequestMappingInfo
		return new RequestMappingInfo(this.name, pathPatterns, patterns,
				methods, params, headers, consumes, produces, custom, this.options);
	}

    
}
```







### 处理器适配器

#### 初始化

```java
@SuppressWarnings("serial")
public class DispatcherServlet extends FrameworkServlet {
    
    //org.springframework.web.servlet.HandlerAdapter=org.springframework.web.servlet.mvc.HttpRequestHandlerAdapter,\
	//org.springframework.web.servlet.mvc.SimpleControllerHandlerAdapter,\
	//org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter,\
	//org.springframework.web.servlet.function.support.HandlerFunctionAdapter
    
    
    //初始化处理器适配器
	private void initHandlerAdapters(ApplicationContext context) {
		this.handlerAdapters = null;

		if (this.detectAllHandlerAdapters) {
			// Find all HandlerAdapters in the ApplicationContext, including ancestor contexts.
			Map<String, HandlerAdapter> matchingBeans =
					BeanFactoryUtils.beansOfTypeIncludingAncestors(context, HandlerAdapter.class, true, false);
			if (!matchingBeans.isEmpty()) {
				this.handlerAdapters = new ArrayList<>(matchingBeans.values());
				// We keep HandlerAdapters in sorted order.
				AnnotationAwareOrderComparator.sort(this.handlerAdapters);
			}
		}
		else {
			try {
				HandlerAdapter ha = context.getBean(HANDLER_ADAPTER_BEAN_NAME, HandlerAdapter.class);
				this.handlerAdapters = Collections.singletonList(ha);
			}
			catch (NoSuchBeanDefinitionException ex) {
				// Ignore, we'll add a default HandlerAdapter later.
			}
		}

		// Ensure we have at least some HandlerAdapters, by registering
		// default HandlerAdapters if no other adapters are found.
		if (this.handlerAdapters == null) {
			this.handlerAdapters = getDefaultStrategies(context, HandlerAdapter.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No HandlerAdapters declared for servlet '" + getServletName() +
						"': using default strategies from DispatcherServlet.properties");
			}
		}
	}



}
```

**RequestMappingHandlerAdapter**

```JAVA
public class RequestMappingHandlerAdapter extends AbstractHandlerMethodAdapter
		implements BeanFactoryAware, InitializingBean {

    @Override
	public void afterPropertiesSet() {
		// Do this first, it may add ResponseBody advice beans
         //初始化ControllerAdviceCache
		initControllerAdviceCache();
		initMessageConverters();
		
        //初始化参数解析器
		if (this.argumentResolvers == null) {
			List<HandlerMethodArgumentResolver> resolvers = getDefaultArgumentResolvers();
			this.argumentResolvers = new HandlerMethodArgumentResolverComposite().addResolvers(resolvers);
		}
        //初始化initBinder的参数解析器
		if (this.initBinderArgumentResolvers == null) {
			List<HandlerMethodArgumentResolver> resolvers = getDefaultInitBinderArgumentResolvers();
			this.initBinderArgumentResolvers = new HandlerMethodArgumentResolverComposite().addResolvers(resolvers);
		}
         //初始化返回值处理器
		if (this.returnValueHandlers == null) {
			List<HandlerMethodReturnValueHandler> handlers = getDefaultReturnValueHandlers();
			this.returnValueHandlers = new HandlerMethodReturnValueHandlerComposite().addHandlers(handlers);
		}
		if (BEAN_VALIDATION_PRESENT) {
			List<HandlerMethodArgumentResolver> resolvers = this.argumentResolvers.getResolvers();
			this.methodValidator = HandlerMethodValidator.from(
					this.webBindingInitializer, this.parameterNameDiscoverer,
					methodParamPredicate(resolvers, ModelAttributeMethodProcessor.class),
					methodParamPredicate(resolvers, RequestParamMethodArgumentResolver.class));
		}
	}
    
    //初始化ControllerAdviceCache
	private void initControllerAdviceCache() {
		if (getApplicationContext() == null) {
			return;
		}
		//找到mvc容器中所有@ControllerAdvice注解标注的类
		List<ControllerAdviceBean> adviceBeans = ControllerAdviceBean.findAnnotatedBeans(getApplicationContext());

		List<Object> requestResponseBodyAdviceBeans = new ArrayList<>();
		
         //遍历
		for (ControllerAdviceBean adviceBean : adviceBeans) {
			Class<?> beanType = adviceBean.getBeanType();
			if (beanType == null) {
				throw new IllegalStateException("Unresolvable type for ControllerAdviceBean: " + adviceBean);
			}
             //找到所有的@
			Set<Method> attrMethods = MethodIntrospector.selectMethods(beanType, MODEL_ATTRIBUTE_METHODS);
			if (!attrMethods.isEmpty()) {
				this.modelAttributeAdviceCache.put(adviceBean, attrMethods);
			}
             //找到所有的@InitBinder标注的方法
			Set<Method> binderMethods = MethodIntrospector.selectMethods(beanType, INIT_BINDER_METHODS);
			if (!binderMethods.isEmpty()) {
                 //加入initBinderAdviceCache
				this.initBinderAdviceCache.put(adviceBean, binderMethods);
			}
			if (RequestBodyAdvice.class.isAssignableFrom(beanType) || ResponseBodyAdvice.class.isAssignableFrom(beanType)) {
				requestResponseBodyAdviceBeans.add(adviceBean);
			}
		}

		if (!requestResponseBodyAdviceBeans.isEmpty()) {
			this.requestResponseBodyAdvice.addAll(0, requestResponseBodyAdviceBeans);
		}

		if (logger.isDebugEnabled()) {
			int modelSize = this.modelAttributeAdviceCache.size();
			int binderSize = this.initBinderAdviceCache.size();
			int reqCount = getBodyAdviceCount(RequestBodyAdvice.class);
			int resCount = getBodyAdviceCount(ResponseBodyAdvice.class);
			if (modelSize == 0 && binderSize == 0 && reqCount == 0 && resCount == 0) {
				logger.debug("ControllerAdvice beans: none");
			}
			else {
				logger.debug("ControllerAdvice beans: " + modelSize + " @ModelAttribute, " + binderSize +
						" @InitBinder, " + reqCount + " RequestBodyAdvice, " + resCount + " ResponseBodyAdvice");
			}
		}
	}

    //默认的参数解析器
	private List<HandlerMethodArgumentResolver> getDefaultArgumentResolvers() {
		List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>(30);

		// Annotation-based argument resolution
		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), false));
		resolvers.add(new RequestParamMapMethodArgumentResolver());
		resolvers.add(new PathVariableMethodArgumentResolver());
		resolvers.add(new PathVariableMapMethodArgumentResolver());
		resolvers.add(new MatrixVariableMethodArgumentResolver());
		resolvers.add(new MatrixVariableMapMethodArgumentResolver());
		resolvers.add(new ServletModelAttributeMethodProcessor(false));
		resolvers.add(new RequestResponseBodyMethodProcessor(getMessageConverters(), this.requestResponseBodyAdvice));
		resolvers.add(new RequestPartMethodArgumentResolver(getMessageConverters(), this.requestResponseBodyAdvice));
		resolvers.add(new RequestHeaderMethodArgumentResolver(getBeanFactory()));
		resolvers.add(new RequestHeaderMapMethodArgumentResolver());
		resolvers.add(new ServletCookieValueMethodArgumentResolver(getBeanFactory()));
		resolvers.add(new ExpressionValueMethodArgumentResolver(getBeanFactory()));
		resolvers.add(new SessionAttributeMethodArgumentResolver());
		resolvers.add(new RequestAttributeMethodArgumentResolver());

		// Type-based argument resolution
		resolvers.add(new ServletRequestMethodArgumentResolver());
		resolvers.add(new ServletResponseMethodArgumentResolver());
		resolvers.add(new HttpEntityMethodProcessor(getMessageConverters(), this.requestResponseBodyAdvice));
		resolvers.add(new RedirectAttributesMethodArgumentResolver());
		resolvers.add(new ModelMethodProcessor());
		resolvers.add(new MapMethodProcessor());
		resolvers.add(new ErrorsMethodArgumentResolver());
		resolvers.add(new SessionStatusMethodArgumentResolver());
		resolvers.add(new UriComponentsBuilderMethodArgumentResolver());
		if (KotlinDetector.isKotlinPresent()) {
			resolvers.add(new ContinuationHandlerMethodArgumentResolver());
		}

		// Custom arguments
		if (getCustomArgumentResolvers() != null) {
			resolvers.addAll(getCustomArgumentResolvers());
		}

		// Catch-all
		resolvers.add(new PrincipalMethodArgumentResolver());
		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), true));
		resolvers.add(new ServletModelAttributeMethodProcessor(true));

		return resolvers;
	}

    //默认的返回值处理器
	private List<HandlerMethodReturnValueHandler> getDefaultReturnValueHandlers() {
		List<HandlerMethodReturnValueHandler> handlers = new ArrayList<>(20);

		// Single-purpose return value types
		handlers.add(new ModelAndViewMethodReturnValueHandler());
		handlers.add(new ModelMethodProcessor());
		handlers.add(new ViewMethodReturnValueHandler());
		handlers.add(new ResponseBodyEmitterReturnValueHandler(getMessageConverters(),
				this.reactiveAdapterRegistry, this.taskExecutor, this.contentNegotiationManager));
		handlers.add(new StreamingResponseBodyReturnValueHandler());
		handlers.add(new HttpEntityMethodProcessor(getMessageConverters(),
				this.contentNegotiationManager, this.requestResponseBodyAdvice));
		handlers.add(new HttpHeadersReturnValueHandler());
		handlers.add(new CallableMethodReturnValueHandler());
		handlers.add(new DeferredResultMethodReturnValueHandler());
		handlers.add(new AsyncTaskMethodReturnValueHandler(this.beanFactory));

		// Annotation-based return value types
		handlers.add(new ServletModelAttributeMethodProcessor(false));
		handlers.add(new RequestResponseBodyMethodProcessor(getMessageConverters(),
				this.contentNegotiationManager, this.requestResponseBodyAdvice));

		// Multi-purpose return value types
		handlers.add(new ViewNameMethodReturnValueHandler());
		handlers.add(new MapMethodProcessor());

		// Custom return value types
		if (getCustomReturnValueHandlers() != null) {
			handlers.addAll(getCustomReturnValueHandlers());
		}

		// Catch-all
		if (!CollectionUtils.isEmpty(getModelAndViewResolvers())) {
			handlers.add(new ModelAndViewResolverMethodReturnValueHandler(getModelAndViewResolvers()));
		}
		else {
			handlers.add(new ServletModelAttributeMethodProcessor(true));
		}

		return handlers;
	}
	
    //初始化InitBinder的参数解析器
	private List<HandlerMethodArgumentResolver> getDefaultInitBinderArgumentResolvers() {
		List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>(20);

		// Annotation-based argument resolution
		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), false));
		resolvers.add(new RequestParamMapMethodArgumentResolver());
		resolvers.add(new PathVariableMethodArgumentResolver());
		resolvers.add(new PathVariableMapMethodArgumentResolver());
		resolvers.add(new MatrixVariableMethodArgumentResolver());
		resolvers.add(new MatrixVariableMapMethodArgumentResolver());
		resolvers.add(new ExpressionValueMethodArgumentResolver(getBeanFactory()));
		resolvers.add(new SessionAttributeMethodArgumentResolver());
		resolvers.add(new RequestAttributeMethodArgumentResolver());

		// Type-based argument resolution
		resolvers.add(new ServletRequestMethodArgumentResolver());
		resolvers.add(new ServletResponseMethodArgumentResolver());

		// Custom arguments
		if (getCustomArgumentResolvers() != null) {
			resolvers.addAll(getCustomArgumentResolvers());
		}

		// Catch-all
		resolvers.add(new PrincipalMethodArgumentResolver());
		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), true));

		return resolvers;
	}

}
```







#### 继承关系

<img src=".\images\image-20251105111225321.png" alt="image-20251105111225321" style="zoom:50%;" />

| 实例类型                         | 作用说明                                                     |
| -------------------------------- | ------------------------------------------------------------ |
| `HttpRequestHandlerAdapter`      | 适配实现了 `HttpRequestHandler` 接口的处理器。常见于直接处理 request/response 的组件（如静态资源处理）。 |
| `SimpleControllerHandlerAdapter` | 适配旧版的 `Controller` 接口（早期 Spring MVC 风格）。       |
| `RequestMappingHandlerAdapter`   | ✅ **最常用的适配器**。适配基于 `@RequestMapping`、`@GetMapping`、`@PostMapping` 等注解的控制器。 |
| `HandlerFunctionAdapter`         | 用于 **Spring 5+ 函数式端点**（`HandlerFunction`），即函数式 Web MVC 风格。 |



#### 请求处理

```java
@SuppressWarnings("serial")
public class DispatcherServlet extends FrameworkServlet {
    
    @Nullable
	private List<HandlerAdapter> handlerAdapters;

	//从四个适配器中获取能支持当前handler的适配器
    protected HandlerAdapter getHandlerAdapter(Object handler) throws ServletException {
		if (this.handlerAdapters != null) {
			for (HandlerAdapter adapter : this.handlerAdapters) {
				if (adapter.supports(handler)) {
					return adapter;
				}
			}
		}
		throw new ServletException("No adapter for handler [" + handler +
				"]: The DispatcherServlet configuration needs to include a HandlerAdapter that supports this handler");
	}

}
```

**HandlerExecutionChain**

```java
public class HandlerExecutionChain {
    
    private static final Log logger = LogFactory.getLog(HandlerExecutionChain.class);

	private final Object handler;

	private final List<HandlerInterceptor> interceptorList = new ArrayList<>();

	private int interceptorIndex = -1;
    
    //调用拦截器的preHandle方法
 	boolean applyPreHandle(HttpServletRequest request, HttpServletResponse response) throws Exception {
		for (int i = 0; i < this.interceptorList.size(); i++) {
			HandlerInterceptor interceptor = this.interceptorList.get(i);
			if (!interceptor.preHandle(request, response, this.handler)) {
				triggerAfterCompletion(request, response, null);
				return false;
			}
			this.interceptorIndex = i;
		}
		return true;
	}
    
    //将仅对 preHandle 调用已成功完成并返回 true 的所有拦截器调用 afterCompletion。
    //倒序调用已经处理拦截器的afterCompletion方法
	void triggerAfterCompletion(HttpServletRequest request, HttpServletResponse response, @Nullable Exception ex) {
		for (int i = this.interceptorIndex; i >= 0; i--) {
			HandlerInterceptor interceptor = this.interceptorList.get(i);
			try {
				interceptor.afterCompletion(request, response, this.handler, ex);
			}
			catch (Throwable ex2) {
				logger.error("HandlerInterceptor.afterCompletion threw exception", ex2);
			}
		}
	}
   
}
```

##### RequestMappingHandlerAdapter

```java
//处理@Controller注解的适配器
public class RequestMappingHandlerAdapter extends AbstractHandlerMethodAdapter
		implements BeanFactoryAware, InitializingBean {

	@Override
	@Nullable
	protected ModelAndView handleInternal(HttpServletRequest request,
			HttpServletResponse response, HandlerMethod handlerMethod) throws Exception {

		ModelAndView mav;
		checkRequest(request);

		// Execute invokeHandlerMethod in synchronized block if required.
		if (this.synchronizeOnSession) {
			HttpSession session = request.getSession(false);
			if (session != null) {
				Object mutex = WebUtils.getSessionMutex(session);
				synchronized (mutex) {
					mav = invokeHandlerMethod(request, response, handlerMethod);
				}
			}
			else {
				// No HttpSession available -> no mutex necessary
				mav = invokeHandlerMethod(request, response, handlerMethod);
			}
		}
		else {
			// No synchronization on session demanded at all...
			mav = invokeHandlerMethod(request, response, handlerMethod);
		}

		if (!response.containsHeader(HEADER_CACHE_CONTROL)) {
			if (getSessionAttributesHandler(handlerMethod).hasSessionAttributes()) {
				applyCacheSeconds(response, this.cacheSecondsForSessionAttributeHandlers);
			}
			else {
				prepareResponse(response);
			}
		}

		return mav;
	}


	//调用执行handler方法的核心
	@Nullable
	protected ModelAndView invokeHandlerMethod(HttpServletRequest request,
			HttpServletResponse response, HandlerMethod handlerMethod) throws Exception {

		WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request);
		AsyncWebRequest asyncWebRequest = WebAsyncUtils.createAsyncWebRequest(request, response);
		asyncWebRequest.setTimeout(this.asyncRequestTimeout);

		asyncManager.setTaskExecutor(this.taskExecutor);
		asyncManager.setAsyncWebRequest(asyncWebRequest);
		asyncManager.registerCallableInterceptors(this.callableInterceptors);
		asyncManager.registerDeferredResultInterceptors(this.deferredResultInterceptors);

		// Obtain wrapped response to enforce lifecycle rule from Servlet spec, section 2.3.3.4
		response = asyncWebRequest.getNativeResponse(HttpServletResponse.class);

		ServletWebRequest webRequest = (asyncWebRequest instanceof ServletWebRequest ?
				(ServletWebRequest) asyncWebRequest : new ServletWebRequest(request, response));
		//处理@InitBinder注解 创建WebDataBinderFactory
		WebDataBinderFactory binderFactory = getDataBinderFactory(handlerMethod);
        //创建模型工厂
		ModelFactory modelFactory = getModelFactory(handlerMethod, binderFactory);
		
         //创建包装handlerMethod
		ServletInvocableHandlerMethod invocableMethod = createInvocableHandlerMethod(handlerMethod);
         //设置参数解析器
		if (this.argumentResolvers != null) {
			invocableMethod.setHandlerMethodArgumentResolvers(this.argumentResolvers);
		}
         //设置返回值处理器
		if (this.returnValueHandlers != null) {
			invocableMethod.setHandlerMethodReturnValueHandlers(this.returnValueHandlers);
		}
         //设置绑定工厂
		invocableMethod.setDataBinderFactory(binderFactory);
         //设置参数名称发现器和方法校验
		invocableMethod.setParameterNameDiscoverer(this.parameterNameDiscoverer);
		invocableMethod.setMethodValidator(this.methodValidator);
		//创建ModelAndView容器
		ModelAndViewContainer mavContainer = new ModelAndViewContainer();
         //添加flashmap
		mavContainer.addAllAttributes(RequestContextUtils.getInputFlashMap(request));
         //初始化模型工厂
		modelFactory.initModel(webRequest, mavContainer, invocableMethod);
		mavContainer.setIgnoreDefaultModelOnRedirect(this.ignoreDefaultModelOnRedirect);

		if (asyncManager.hasConcurrentResult()) {
			Object result = asyncManager.getConcurrentResult();
			Object[] resultContext = asyncManager.getConcurrentResultContext();
			Assert.state(resultContext != null && resultContext.length > 0, "Missing result context");
			mavContainer = (ModelAndViewContainer) resultContext[0];
			asyncManager.clearConcurrentResult();
			LogFormatUtils.traceDebug(logger, traceOn -> {
				String formatted = LogFormatUtils.formatValue(result, !traceOn);
				return "Resume with async result [" + formatted + "]";
			});
			invocableMethod = invocableMethod.wrapConcurrentResult(result);
		}
		//
		invocableMethod.invokeAndHandle(webRequest, mavContainer);
		if (asyncManager.isConcurrentHandlingStarted()) {
			return null;
		}

		return getModelAndView(mavContainer, modelFactory, webRequest);
	}

    //处理@InitBinder注解 创建WebDataBinderFactory
	private WebDataBinderFactory getDataBinderFactory(HandlerMethod handlerMethod) throws Exception {
		Class<?> handlerType = handlerMethod.getBeanType();
         //从缓存中获取当前controller的@InitBinder注解的方法
		Set<Method> methods = this.initBinderCache.get(handlerType);
		if (methods == null) {
             //反射获取当前controller中的@InitBinder注解的方法
			methods = MethodIntrospector.selectMethods(handlerType, INIT_BINDER_METHODS);
			this.initBinderCache.put(handlerType, methods);
		}
		List<InvocableHandlerMethod> initBinderMethods = new ArrayList<>();
		// Global methods first
         //获取@ControllerAdvice类中全局@InitBinder注解标注的方法
		this.initBinderAdviceCache.forEach((controllerAdviceBean, methodSet) -> {
			if (controllerAdviceBean.isApplicableToBeanType(handlerType)) {
				Object bean = controllerAdviceBean.resolveBean();
				for (Method method : methodSet) {
					initBinderMethods.add(createInitBinderMethod(bean, method));
				}
			}
		});
         //将全局和局部的@InitBinder方法加入集合
		for (Method method : methods) {
			Object bean = handlerMethod.getBean();
			initBinderMethods.add(createInitBinderMethod(bean, method));
		}
         //创建DefaultDataBinderFactory
		DefaultDataBinderFactory factory = createDataBinderFactory(initBinderMethods);
		factory.setMethodValidationApplicable(this.methodValidator != null && handlerMethod.shouldValidateArguments());
		return factory;
	}
	
    //将@InitBinder方法封装成InvocableHandlerMethod
	private InvocableHandlerMethod createInitBinderMethod(Object bean, Method method) {
		InvocableHandlerMethod binderMethod = new InvocableHandlerMethod(bean, method);
		if (this.initBinderArgumentResolvers != null) {
             //设置InitBinder参数解析器
			binderMethod.setHandlerMethodArgumentResolvers(this.initBinderArgumentResolvers);
		}
		binderMethod.setDataBinderFactory(new DefaultDataBinderFactory(this.webBindingInitializer));
		binderMethod.setParameterNameDiscoverer(this.parameterNameDiscoverer);
		return binderMethod;
	}
}
```



##### InvocableHandlerMethod

```java
public class InvocableHandlerMethod extends HandlerMethod {




}
```









### 重定向与转发





### 拦截器

```java
public interface HandlerInterceptor {

    default boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {

		return true;
	}

	default void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
			@Nullable ModelAndView modelAndView) throws Exception {
	}
    
    default void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
			@Nullable Exception ex) throws Exception {
	}
}
```













## 常用注解

### @RequestBody

`@RequestBody` 用于将 **HTTP 请求体（Request Body）** 中的内容自动反序列化为 Java 对象，并绑定到方法参数上。

```java
//@RequestBody默认required = true 强制要求http请求中含有请求体
@PostMapping("/user")
public ResponseEntity<String> addUser(@RequestBody User user) {
    // user 已经从 JSON/XML 请求体转换而来
    return ResponseEntity.ok("Hello, " + user.getName());
}
//@RequestBody(required=false)时，http请求中可以不含有请求体，此时user对象为null
@PostMapping("/user")
public void saveUser(@RequestBody(required = false) User user) {
    // 当请求体为空时 user == null
}
```

```json
//含有请求体
POST /user HTTP/1.1
Content-Type: application/json

{
  "name": "Alice",
  "age": 22
}

//不含有请求体
POST /user HTTP/1.1
Host: example.com
Content-Type: application/json
Content-Length: 0


```

`@RequestBody`并不只读取json类型的请求体数据，`@RequestBody` 依赖 **HttpMessageConverter** 实现反序列化。

| Content-Type                  | Converter 类型                                               | 说明          |
| ----------------------------- | ------------------------------------------------------------ | ------------- |
| `application/json`            | `MappingJackson2HttpMessageConverter`                        | JSON ↔ Java   |
| `application/xml`, `text/xml` | `Jaxb2RootElementHttpMessageConverter` / `MappingJackson2XmlHttpMessageConverter` | XML ↔ Java    |
| `text/plain`                  | `StringHttpMessageConverter`                                 | 文本 ↔ String |





### @ResponseBody











### @CrossOrign

用于**允许跨域请求（CORS, Cross-Origin Resource Sharing）**，即让浏览器可以从一个不同的域名或端口访问你的后端接口。

在 Web 安全机制中，**浏览器默认禁止跨域请求**（同源策略）。
 例如：

- 前端页面来自 `http://localhost:3000`
- 后端接口在 `http://localhost:8080`

此时前端直接调用接口时会被浏览器拦截。

```java
@CrossOrigin(origins = "http://localhost:3000") //允许的来源 默认允许所有来源
@CrossOrigin(originPatterns = "http://*.example.com") //允许的来源 如果设置了 originPatterns，会覆盖 origins。
@CrossOrigin(allowedHeaders = {"Content-Type", "Authorization"})//允许客户端在请求中携带的请求头 默认允许所有请求头。
@CrossOrigin(exposedHeaders = "Authorization") //允许前端访问响应头中的哪些字段 默认情况下，浏览器无法访问大多数响应头。
@CrossOrigin(methods = {RequestMethod.GET, RequestMethod.POST}) //指定允许的 HTTP 请求方法 默认与控制器方法支持的请求方式一致。
@CrossOrigin(allowCredentials = "true") //是否允许携带凭证（如 Cookie）安全性较高，若允许 cookie，origins 不能是 "*"。否则浏览器会拒绝请求。
@CrossOrigin(allowPrivateNetwork = "true")//是否允许私有网络访问（如访问内网设备）
@CrossOrigin(maxAge = 3600) // 预检请求（OPTIONS 请求）的缓存时间（秒） 表示浏览器在 1 小时内不再重复发起预检请求。默认是 1800 秒（30 分钟）。
```

#### 预检请求

在浏览器安全机制中，当一个**跨域请求**看起来“有风险”时，浏览器不会直接发送真实请求。
 它会先发送一个 **HTTP OPTIONS 请求** 到服务器，来“问一问”：

> “你好服务器，我要从这个源（Origin）发一个跨域请求过去，
>  我能不能？允许哪些方法？能带哪些头？”

这个 OPTIONS 请求就是所谓的 **预检请求（preflight request）**。
 只有当服务器明确允许之后，浏览器才会继续发出真正的请求（如 `POST`, `PUT`, `DELETE` 等）。

**什么时候浏览器会触发预检请求**

只有当请求**不满足以下条件（即不是简单请求）**时，浏览器才会“预检”。

满足以下全部条件：

1. 请求方法是 `GET`、`HEAD` 或 `POST`
2. 请求中不携带自定义头、cookies、或非标准 MIME 类型。
3. 请求头只包含以下安全字段：

```json
Accept
Accept-Language
Content-Language
Content-Type（但必须是以下三种之一）
  - text/plain
  - multipart/form-data
  - application/x-www-form-urlencoded
```

简单请求直接发送，不会触发 OPTIONS 预检。

当以下情况之一出现时，会触发预检请求：

- 使用了 `PUT`、`DELETE`、`PATCH` 等方法；
- 使用了自定义请求头（如 `Authorization`、`X-Custom-Header`）；
- `Content-Type` 为 `application/json`；
- 请求带有凭证（`withCredentials = true`）；
- 涉及私有网络访问（如局域网设备）。



**预检请求**

浏览器发送

```http
OPTIONS /api/data HTTP/1.1
Origin: http://localhost:3000
Access-Control-Request-Method: POST
Access-Control-Request-Headers: Content-Type, Authorization
```

服务器返回

```java
@CrossOrigin(origins = "http://localhost:3000", allowedHeaders = "*", maxAge = 3600)

HTTP/1.1 200 OK
Access-Control-Allow-Origin: http://localhost:3000
Access-Control-Allow-Methods: GET, POST, PUT, DELETE
Access-Control-Allow-Headers: Content-Type, Authorization
Access-Control-Max-Age: 3600 //浏览器可以缓存这次预检结果 3600 秒（1 小时）在这段时间内，同样的跨域请求不需要再次发 OPTIONS 请求。
```

浏览器验证通过后，再发真实请求

```http
POST /api/data HTTP/1.1
Origin: http://localhost:3000
Authorization: Bearer token
Content-Type: application/json
```

| `@CrossOrigin` 参数 | 对应响应头                       | 作用                |
| ------------------- | -------------------------------- | ------------------- |
| `origins`           | Access-Control-Allow-Origin      | 允许的来源          |
| `methods`           | Access-Control-Allow-Methods     | 允许的 HTTP 方法    |
| `allowedHeaders`    | Access-Control-Allow-Headers     | 允许的请求头        |
| `maxAge`            | Access-Control-Max-Age           | 预检结果缓存时间    |
| `allowCredentials`  | Access-Control-Allow-Credentials | 是否允许携带 cookie |



### @InitBinder

`@InitBinder` 是 Spring MVC 提供的一个用于 **请求参数到 Java 对象绑定时进行定制化处理** 的注解。

- 自定义参数绑定逻辑；
- 注册属性编辑器（PropertyEditor）；
- 注册数据格式化器（Formatter）；
- 防止某些字段被绑定（例如防止前端篡改某些敏感字段）
- 在`@ControllerAdvice`中可以实现全局配置

```java
@Controller
@RequestMapping("/demo")
public class InitBinderDemoController {

    /**
     * 🧩 全局 Binder
     * 对所有绑定对象生效（无参数名限制）
     */
    @InitBinder
    public void globalBinder(WebDataBinder binder) {
        System.out.println("▶ 执行 globalBinder()");
        // 设置全局禁止绑定字段（如敏感信息）
        binder.setDisallowedFields("role", "id");

        // 设置允许绑定的字段（可选）
        // binder.setAllowedFields("name", "email", "birthday");

        // 这里可以配置全局格式化规则、校验规则等
    }

    /**
     * 🧩 针对 User 对象的专属 Binder
     * 仅当参数名为 “user” 时执行
     */
    @InitBinder("user")
    public void userBinder(WebDataBinder binder) {
        System.out.println("▶ 执行 userBinder()");

        // 设置字段默认前缀：请求参数需以 u. 开头
        // 如：u.name、u.email、u.birthday
        binder.setFieldDefaultPrefix("u.");

        // 自定义日期格式转换器
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        binder.registerCustomEditor(Date.class, new CustomDateEditor(dateFormat, false));

        // 可选：禁止某些字段被修改
        binder.setDisallowedFields("password");
    }

    /**
     * 🧩 针对 Address 对象的专属 Binder
     * 仅当参数名为 “address” 时执行
     */
    @InitBinder("address")
    public void addressBinder(WebDataBinder binder) {
        System.out.println("▶ 执行 addressBinder()");
        // 设置字段默认前缀：请求参数需以 a. 开头
        // 如：a.city、a.street
        binder.setFieldDefaultPrefix("a.");

        // 示例：仅允许绑定 city 和 street
        binder.setAllowedFields("city", "street");
    }

    /**
     * ✅ 表单提交处理
     * 演示多对象绑定（user + address）
     */
    @PostMapping("/submit")
    public String submitForm(@ModelAttribute("user") User user,
                             @ModelAttribute("address") Address address,
                             Model model) {
        System.out.println("🧍 User => " + user);
        System.out.println("🏠 Address => " + address);

        model.addAttribute("user", user);
        model.addAttribute("address", address);
        return "demoResult";
    }
}

```



### @ControllerAdvice

`@ControllerAdvice` 是 Spring MVC 中的一个“控制器增强器”（Controller Enhancer）。

1. 全局异常处理（`@ExceptionHandler`）；
2. 全局数据绑定（`@InitBinder`）；
3. 全局模型数据预设（`@ModelAttribute`）。







## 常见content-type



## HTTP缓存

HTTP 缓存的主要目的是 **减少网络请求，提高性能**，避免重复下载相同资源。浏览器、CDN 或代理服务器都可以做缓存。

缓存的核心原理是：

1. 客户端请求资源（如 HTML、CSS、JS、图片）。
2. 服务器返回资源，同时在响应头中告诉客户端 **是否可以缓存**、**缓存多久**。
3. 当客户端再次请求同一资源时，会根据缓存策略决定：
   - **直接使用缓存**
   - **向服务器验证缓存是否有效**
   - **重新下载资源**

### 请求流程

- 浏览器第一次请求

  ```http
  GET /style.css HTTP/1.1
  Host: example.com
  ```

- 服务器响应

  ```http
  HTTP/1.1 200 OK
  Last-Modified: Tue, 04 Nov 2025 12:00:00 GMT
  ETag: "abc123"
  Cache-Control: no-cache
  ```

- 第二次请求（协商缓存）

  ```http
  GET /style.css HTTP/1.1
  Host: example.com
  If-Modified-Since: Tue, 04 Nov 2025 12:00:00 GMT
  If-None-Match: "abc123"
  ```

- 服务器判断缓存是否有效：

  - 如果资源未改变，返回：

    ```http
    HTTP/1.1 304 Not Modified
    ```

  - 如果资源改变，返回：

    ```http
    HTTP/1.1 200 OK
    新资源
    ```

### HTTP头

| 头部              | 类型      | 作用                             | 典型用途                                            | 注意事项                                     |
| ----------------- | --------- | -------------------------------- | --------------------------------------------------- | -------------------------------------------- |
| **Cache-Control** | 响应/请求 | 控制缓存策略，精细化管理缓存行为 | 浏览器缓存、CDN缓存、代理缓存                       | HTTP/1.1 推荐使用，优先级高于 Expires        |
| **Expires**       | 响应      | 资源绝对过期时间（GMT）          | 静态资源强缓存                                      | HTTP/1.0 使用，HTTP/1.1 推荐用 Cache-Control |
| **ETag**          | 响应      | 资源唯一标识（通常是文件 hash）  | 协商缓存（If-Match / If-None-Match）                | 与 If-Match / If-None-Match 配合使用         |
| **Last-Modified** | 响应      | 资源最后修改时间                 | 协商缓存（If-Modified-Since / If-Unmodified-Since） | 精度到秒，配合时间戳条件请求头使用           |

**Cache-Control 常用指令**

| 指令              | 作用                               | 适用场景               |
| ----------------- | ---------------------------------- | ---------------------- |
| `max-age=<秒>`    | 资源在多少秒内是新鲜的             | 强缓存，静态资源       |
| `no-cache`        | 允许缓存，但每次请求需向服务器验证 | 动态资源，保证最新内容 |
| `no-store`        | 资源不缓存                         | 敏感数据，安全要求高   |
| `private`         | 仅浏览器缓存                       | 用户特定资源           |
| `public`          | 所有缓存（浏览器+代理）都可缓存    | 静态资源，CDN          |
| `must-revalidate` | 缓存过期后必须向服务器验证         | 动态内容控制           |

#### If-Match

**作用：**

- 客户端告诉服务器：**“只在资源的 ETag 与我提供的一致时才执行操作（通常是 PUT 或 DELETE）”**。
- 如果不一致 → 服务器返回 **412 Precondition Failed**。

**典型用途：**

- **乐观锁（optimistic locking）**
   确保客户端修改的是最新版本，防止覆盖其他人更新。

**示例：**

```http
PUT /resource/123 HTTP/1.1
If-Match: "abc123"
Content-Type: application/json

{ "name": "new name" }
```

- 当前资源的 ETag 是 `"abc123"` → 更新成功，返回 200 OK
- 当前资源的 ETag 不是 `"abc123"` → 返回 412 Precondition Failed

> 关键：If-Match **用于更新或删除操作**，保证数据一致性。

#### If-None-Match

**作用：**

- 客户端告诉服务器：**“如果资源的 ETag 和我提供的相同，就不要返回内容”**。
- 如果 ETag 相同 → 返回 **304 Not Modified**
- 如果不同 → 返回 **200 OK** 和资源内容

**典型用途：**

- **协商缓存**（浏览器缓存验证）
- **避免重复提交请求**

**示例：**

```http
GET /style.css HTTP/1.1
If-None-Match: "abc123"
```

- 如果服务器资源 ETag 仍为 `"abc123"` → 304 Not Modified → 浏览器直接使用缓存
- 如果服务器资源 ETag 改变 → 200 OK + 新资源

> 关键：If-None-Match **常用于 GET 或 HEAD 请求进行缓存验证**，和 412 不直接相关。

------

#### If-Modified-Since

**作用：**

- 客户端告诉服务器：**“只在资源自某个时间点后被修改过时才返回”**
- 如果资源自指定时间没有修改 → 304 Not Modified
- 如果修改过 → 返回 200 OK

**典型用途：**

- **协商缓存**（浏览器缓存验证）

**示例：**

```http
GET /style.css HTTP/1.1
If-Modified-Since: Tue, 04 Nov 2025 12:00:00 GMT
```

- 资源自 2025-11-04 12:00 后没有改 → 304 Not Modified
- 资源修改过 → 200 OK + 新内容

> 关键：基于 **时间戳** 的缓存验证。

------

#### If-Unmodified-Since

**作用：**

- 客户端告诉服务器：**“只在资源自某个时间后没有被修改时才执行操作（通常是 PUT/DELETE）”**
- 如果资源自指定时间被修改过 → 返回 **412 Precondition Failed**
- 如果未修改 → 执行操作

**典型用途：**

- 数据一致性校验，防止覆盖已更新的资源

**示例：**

```http
DELETE /resource/123 HTTP/1.1
If-Unmodified-Since: Tue, 04 Nov 2025 12:00:00 GMT
```

- 如果资源自指定时间未修改 → 执行删除
- 如果资源修改过 → 412 Precondition Failed

> 关键：基于 **时间戳** 的乐观锁。

| 请求头                | 作用                                 | 对应响应        | 典型使用场景        | 说明                                             |
| --------------------- | ------------------------------------ | --------------- | ------------------- | ------------------------------------------------ |
| `If-Modified-Since`   | 只有资源自指定时间后被修改才返回     | `Last-Modified` | GET / HEAD 协商缓存 | 基于时间戳的缓存验证                             |
| `If-Unmodified-Since` | 只有资源自指定时间未被修改才执行操作 | `Last-Modified` | PUT / DELETE 乐观锁 | 基于时间戳的数据一致性                           |
| `If-Match`            | 只有资源 ETag 匹配才执行操作         | `ETag`          | PUT / DELETE 乐观锁 | 保证更新的是最新资源                             |
| `If-None-Match`       | 只有资源 ETag 不匹配才返回内容       | `ETag`          | GET / HEAD 协商缓存 | 避免重复请求；如果匹配 → 304 Not Modified        |
| `Pragma`              | 兼容 HTTP/1.0 的缓存控制             | -               | no-cache            | 老旧浏览器用，作用类似 `Cache-Control: no-cache` |















