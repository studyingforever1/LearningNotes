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
	
```





