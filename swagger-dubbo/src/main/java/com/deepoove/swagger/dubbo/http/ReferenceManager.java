package com.deepoove.swagger.dubbo.http;

import com.alibaba.dubbo.common.utils.ReflectUtils;
import com.alibaba.dubbo.config.ApplicationConfig;
import com.alibaba.dubbo.config.ReferenceConfig;
import com.alibaba.dubbo.config.spring.ReferenceBean;
import com.alibaba.dubbo.config.spring.beans.factory.annotation.ReferenceAnnotationBeanPostProcessor;
import com.alibaba.dubbo.config.spring.extension.SpringExtensionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ReferenceManager {
    
    private static Logger logger = LoggerFactory.getLogger(ReferenceManager.class);

    @SuppressWarnings("rawtypes")
    private static Collection<ReferenceBean> services;

    private static Map<Class<?>, Object> interfaceMapProxy = new ConcurrentHashMap<Class<?>, Object>();
    private static Map<Class<?>, Object> interfaceMapRef = new ConcurrentHashMap<Class<?>, Object>();

    private static ReferenceManager instance;
    private static ApplicationConfig application;

    private ReferenceManager() {}

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public synchronized static ReferenceManager getInstance() {
        if (null != instance) return instance;
        instance = new ReferenceManager();
        services = new HashSet<>();
        try {
            Field field = SpringExtensionFactory.class.getDeclaredField("contexts");
            field.setAccessible(true);
            Set<ApplicationContext> contexts = (Set<ApplicationContext>)field.get(new SpringExtensionFactory());
            for (ApplicationContext context : contexts){
                context.getBeansOfType(ReferenceAnnotationBeanPostProcessor.class)
                        .values().stream().forEach(referenceAnnotationBeanPostProcessor ->
                        {
                            Field referenceBeansCache = ReflectUtils.getBeanPropertyFields(ReferenceAnnotationBeanPostProcessor.class).get("referenceBeansCache");
                            referenceBeansCache.setAccessible(true);
                            try {
                                ConcurrentMap<String, ReferenceBean<?>> stringReferenceBeanConcurrentMap = (ConcurrentMap<String, ReferenceBean<?>>) referenceBeansCache.get(referenceAnnotationBeanPostProcessor);
                                services.addAll(stringReferenceBeanConcurrentMap.values());
                            } catch (IllegalAccessException e) {
                                e.printStackTrace();
                            }

                        }
                );
//                services.addAll(context.getBeansOfType(ReferenceBean.class).values());
            }
        } catch (Exception e) {
            logger.error("Get All Dubbo Service Error", e);
            return instance;
        }
        for (ReferenceBean<?> bean : services) {
            interfaceMapRef.putIfAbsent(bean.getInterfaceClass(), bean.get());
        }

        //
        if (!services.isEmpty()) {
            ReferenceBean<?> bean = services.toArray(new ReferenceBean[services.size()])[0];
			application = bean.getApplication();
        }

        return instance;
    }

    public Object getProxy(String interfaceClass) {
        Set<Entry<Class<?>, Object>> entrySet = interfaceMapProxy.entrySet();
        for (Entry<Class<?>, Object> entry : entrySet) {
            if (entry.getKey().getName().equals(interfaceClass)) { return entry.getValue(); }
        }

        for (ReferenceBean<?> service : services) {
            if (interfaceClass.equals(service.getInterfaceClass().getName())) {
                ReferenceConfig<Object> reference = new ReferenceConfig<Object>();
                reference.setApplication(service.getApplication());
                reference.setRegistry(service.getRegistry());
                reference.setRegistries(service.getRegistries());
                reference.setInterface(service.getInterfaceClass());
                reference.setVersion(service.getVersion());
                interfaceMapProxy.put(service.getInterfaceClass(), reference.get());
                return reference.get();
            }
        }
        return null;
    }

    public Entry<Class<?>, Object> getRef(String interfaceClass) {
        Set<Entry<Class<?>, Object>> entrySet = interfaceMapRef.entrySet();
        for (Entry<Class<?>, Object> entry : entrySet) {
            if (entry.getKey().getName().equals(interfaceClass)) { return entry; }
        }
        return null;
    }

    @SuppressWarnings("rawtypes")
    public Collection<ReferenceBean> getServices() {
        return services;
    }

    public ApplicationConfig getApplication() {
        return application;
    }

    public Map<Class<?>, Object> getInterfaceMapRef() {
        return interfaceMapRef;
    }

}
