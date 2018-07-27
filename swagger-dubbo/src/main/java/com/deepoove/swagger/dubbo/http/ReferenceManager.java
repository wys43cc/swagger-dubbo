package com.deepoove.swagger.dubbo.http;

import com.alibaba.dubbo.common.utils.ReflectUtils;
import com.alibaba.dubbo.config.ApplicationConfig;
import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.dubbo.config.spring.extension.SpringExtensionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ReferenceManager {
    
    private static Logger logger = LoggerFactory.getLogger(ReferenceManager.class);

    @SuppressWarnings("rawtypes")
//    private static Collection<ReferenceBean> services;

    private static Map<Class<?>, Object> interfaceMapProxy = new ConcurrentHashMap<Class<?>, Object>();
    private static Map<Class<?>, Object> interfaceMapRef = new ConcurrentHashMap<Class<?>, Object>();

    private static ReferenceManager instance;
    private static ApplicationConfig application;

    private ReferenceManager() {}

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public synchronized static ReferenceManager getInstance() {
        if (null != instance) return instance;
        instance = new ReferenceManager();
//        services = new HashSet<>();
        try {
            Field field = SpringExtensionFactory.class.getDeclaredField("contexts");
            field.setAccessible(true);
            Set<ApplicationContext> contexts = (Set<ApplicationContext>)field.get(new SpringExtensionFactory());
            for (ApplicationContext context : contexts){
                Map<String, Object> beansWithAnnotation = context.getBeansWithAnnotation(Component.class);
                beansWithAnnotation.values().stream().forEach(bean->{
                    Map<String, Field> beanPropertyFields = ReflectUtils.getBeanPropertyFields(bean.getClass());
                    beanPropertyFields.values().stream().forEach(field1 -> {
                        Reference annotation = field1.getAnnotation(Reference.class);
                        if(annotation!=null) {
                            try {
                                interfaceMapRef.put(field1.getClass(),field1.get(bean));
                            } catch (IllegalAccessException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                });
                if (application == null) application = context.getBean(ApplicationConfig.class);
            }
        } catch (Exception e) {
            logger.error("Get All Dubbo Service Error", e);
            return instance;
        }

        return instance;
    }

    public Object getProxy(String interfaceClass) {
        Set<Entry<Class<?>, Object>> entrySet = interfaceMapProxy.entrySet();
        for (Entry<Class<?>, Object> entry : entrySet) {
            if (entry.getKey().getName().equals(interfaceClass)) { return entry.getValue(); }
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

    public ApplicationConfig getApplication() {
        return application;
    }

    public Map<Class<?>, Object> getInterfaceMapRef() {
        return interfaceMapRef;
    }

}
