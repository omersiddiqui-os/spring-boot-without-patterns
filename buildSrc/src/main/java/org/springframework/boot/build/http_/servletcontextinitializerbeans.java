/*
 * Copyright 2012-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.web.servlet;

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.servlet.Filter;
import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.Servlet;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * A collection {@link ServletContextInitializer}s obtained from a
 * {@link ListableBeanFactory}. Includes all {@link ServletContextInitializer} beans and
 * also adapts {@link Servlet}, {@link Filter} and certain {@link EventListener} beans.
 * <p>
 * Items are sorted so that adapted beans are top ({@link Servlet}, {@link Filter} then
 * {@link EventListener}) and direct {@link ServletContextInitializer} beans are at the
 * end. Further sorting is applied within these groups using the
 * {@link AnnotationAwareOrderComparator}.
 *
 * @since 1.4.0
 */
public class ServletContextInitializerBeans extends AbstractCollection<ServletContextInitializer> {

    private static final String DISPATCHER_SERVLET_NAME = "dispatcherServlet";

    private static final Log logger = LogFactory.getLog(ServletContextInitializerBeans.class);

    private final Seen seen = new Seen();

    private final MultiValueMap<Class<?>, ServletContextInitializer> initializers;

    private final List<Class<? extends ServletContextInitializer>> initializerTypes;

    private final List<ServletContextInitializer> sortedList;

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

    private void addServletContextInitializerBeans(ListableBeanFactory beanFactory) {
        for (Class<? extends ServletContextInitializer> initializerType : this.initializerTypes) {
            for (Entry<String, ? extends ServletContextInitializer> initializerBean : getOrderedBeansOfType(beanFactory,
                    initializerType)) {
                addServletContextInitializerBean(initializerBean.getKey(), initializerBean.getValue(), beanFactory);
            }
        }
    }

    private void addServletContextInitializerBean(String beanName, ServletContextInitializer initializer,
                                                  ListableBeanFactory beanFactory) {
        if (initializer instanceof ServletRegistrationBean<?> servletRegistrationBean) {
            Servlet source = servletRegistrationBean.getServlet();
            addServletContextInitializerBean(Servlet.class, beanName, servletRegistrationBean, beanFactory, source);
        } else if (initializer instanceof FilterRegistrationBean<?> filterRegistrationBean) {
            Filter source = filterRegistrationBean.getFilter();
            addServletContextInitializerBean(Filter.class, beanName, filterRegistrationBean, beanFactory, source);
        } else if (initializer instanceof DelegatingFilterProxyRegistrationBean registrationBean) {
            String source = registrationBean.getTargetBeanName();
            addServletContextInitializerBean(Filter.class, beanName, registrationBean, beanFactory, source);
        } else if (initializer instanceof ServletListenerRegistrationBean<?> registrationBean) {
            EventListener source = registrationBean.getListener();
            addServletContextInitializerBean(EventListener.class, beanName, registrationBean, beanFactory, source);
        } else {
            addServletContextInitializerBean(ServletContextInitializer.class, beanName, initializer, beanFactory,
                    initializer);
        }
    }

    private void addServletContextInitializerBean(Class<?> type, String beanName, ServletContextInitializer initializer,
                                                  ListableBeanFactory beanFactory, Object source) {
        this.initializers.add(type, initializer);
        if (source != null) {
            this.seen.add(type, source);
        }
        if (logger.isTraceEnabled()) {
            String resourceDescription = getResourceDescription(beanName, beanFactory);
            int order = getOrder(initializer);
            logger.trace("Added existing " + type.getSimpleName() + " initializer bean '" + beanName + "'; order="
                    + order + ", resource=" + resourceDescription);
        }
    }

    private String getResourceDescription(String beanName, ListableBeanFactory beanFactory) {
        if (beanFactory instanceof BeanDefinitionRegistry registry) {
            return registry.getBeanDefinition(beanName).getResourceDescription();
        }
        return "unknown";
    }

    protected void addAdaptableBeans(ListableBeanFactory beanFactory) {
        MultipartConfigElement multipartConfig = getMultipartConfig(beanFactory);
        addServletBeans(beanFactory, multipartConfig);
        addFilterBeans(beanFactory);
        addListenerBeans(beanFactory);
    }

    private void addServletBeans(ListableBeanFactory beanFactory, MultipartConfigElement multipartConfig) {
        String[] beanNames = beanFactory.getBeanNamesForType(Servlet.class, true, false);
        for (String beanName : beanNames) {
            Servlet servlet = beanFactory.getBean(beanName, Servlet.class);
            if (this.seen.add(Servlet.class, servlet)) {
                ServletRegistrationBean<Servlet> registrationBean = new ServletRegistrationBean<>(servlet, "/");
                registrationBean.setName(beanName);
                registrationBean.setMultipartConfig(multipartConfig);
                this.initializers.add(Servlet.class, registrationBean);
            }
        }
    }

    private void addFilterBeans(ListableBeanFactory beanFactory) {
        String[] beanNames = beanFactory.getBeanNamesForType(Filter.class, true, false);
        for (String beanName : beanNames) {
            Filter filter = beanFactory.getBean(beanName, Filter.class);
            if (this.seen.add(Filter.class, filter)) {
                FilterRegistrationBean<Filter> registrationBean = new FilterRegistrationBean<>(filter);
                registrationBean.setName(beanName);
                this.initializers.add(Filter.class, registrationBean);
            }
        }
    }

    private void addListenerBeans(ListableBeanFactory beanFactory) {
        for (Class<?> listenerType : ServletListenerRegistrationBean.getSupportedTypes()) {
            String[] beanNames = beanFactory.getBeanNamesForType(listenerType, true, false);
            for (String beanName : beanNames) {
                EventListener listener = (EventListener) beanFactory.getBean(beanName, listenerType);
                if (this.seen.add(EventListener.class, listener)) {
                    ServletListenerRegistrationBean<EventListener> registrationBean = new ServletListenerRegistrationBean<>(listener);
                    registrationBean.setName(beanName);
                    this.initializers.add(EventListener.class, registrationBean);
                }
            }
        }
    }

    private MultipartConfigElement getMultipartConfig(ListableBeanFactory beanFactory) {
        List<Entry<String, MultipartConfigElement>> beans = getOrderedBeansOfType(beanFactory,
                MultipartConfigElement.class);
        return beans.isEmpty() ? null : beans.get(0).getValue();
    }

    private int getOrder(Object value) {
        return new AnnotationAwareOrderComparator() {
            @Override
            public int getOrder(Object obj) {
                return super.getOrder(obj);
            }
        }.getOrder(value);
    }

    private <T> List<Entry<String, T>> getOrderedBeansOfType(ListableBeanFactory beanFactory, Class<T> type) {
        return getOrderedBeansOfType(beanFactory, type, Seen.empty());
    }

    private <T> List<Entry<String, T>> getOrderedBeansOfType(ListableBeanFactory beanFactory, Class<T> type,
                                                             Seen seen) {
        String[] names = beanFactory.getBeanNamesForType(type, true, false);
        Map<String, T> map = new LinkedHashMap<>();
        for (String name : names) {
            if (!seen.contains(type, name) && !ScopedProxyUtils.isScopedTarget(name)) {
                T bean = beanFactory.getBean(name, type);
                if (!seen.contains(type, bean)) {
                    map.put(name, bean);
                }
            }
        }
        List<Entry<String, T>> beans = new ArrayList<>(map.entrySet());
        beans.sort((o1, o2) -> AnnotationAwareOrderComparator.INSTANCE.compare(o1.getValue(), o2.getValue()));
        return beans;
    }

    private void logMappings(MultiValueMap<Class<?>, ServletContextInitializer> initializers) {
        if (logger.isDebugEnabled()) {
            logMappings("filters", initializers, Filter.class, FilterRegistrationBean.class);
            logMappings("servlets", initializers, Servlet.class, ServletRegistrationBean.class);
        }
    }

    private void logMappings(String name, MultiValueMap<Class<?>, ServletContextInitializer> initializers,
                             Class<?> type, Class<? extends RegistrationBean> registrationType) {
        List<ServletContextInitializer> registrations = new ArrayList<>();
        registrations.addAll(initializers.getOrDefault(registrationType, Collections.emptyList()));
        registrations.addAll(initializers.getOrDefault(type, Collections.emptyList()));
        String info = registrations.stream().map(Object::toString).collect(Collectors.joining(", "));
        logger.debug("Mapping " + name + ": " + info);
    }

    @Override
    public Iterator<ServletContextInitializer> iterator() {
        return this.sortedList.iterator();
    }

    @Override
    public int size() {
        return this.sortedList.size();
    }

    private static final class Seen {

        private final Map<Class<?>, Set<Object>> seen = new HashMap<>();

        boolean add(Class<?> type, Object object) {
            if (contains(type, object)) {
                return false;
            }
            return this.seen.computeIfAbsent(type, (ignore) -> new HashSet<>()).add(object);
        }

        boolean contains(Class<?> type, Object object) {
            if (this.seen.isEmpty()) {
                return false;
            }
            if (type != ServletContextInitializer.class
                    && this.seen.getOrDefault(type, Collections.emptySet()).contains(object)) {
                return true;
            }
            return this.seen.getOrDefault(ServletContextInitializer.class, Collections.emptySet()).contains(object);
        }

        static Seen empty() {
            return new Seen();
        }

    }

}
