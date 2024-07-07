/*
 * Copyright 2012-2023 the original author or authors.
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

package org.springframework.boot.builder;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.boot.ApplicationContextFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.BootstrapRegistry;
import org.springframework.boot.BootstrapRegistryInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.util.StringUtils;

public class SpringApplicationBuilder {

	private final SpringApplication application;

	private volatile ConfigurableApplicationContext context;

	private final AtomicBoolean running = new AtomicBoolean();

	private final Set<Class<?>> sources = new LinkedHashSet<>();

	private final Map<String, Object> defaultProperties = new LinkedHashMap<>();

	private ConfigurableEnvironment environment;

	private Set<String> additionalProfiles = new LinkedHashSet<>();

	private boolean registerShutdownHookApplied;

	public SpringApplicationBuilder(Class<?>... sources) {
		this(null, sources);
	}

	public SpringApplicationBuilder(ResourceLoader resourceLoader, Class<?>... sources) {
		this.application = new SpringApplication(resourceLoader, sources);
	}

	public ConfigurableApplicationContext context() {
		return this.context;
	}

	public SpringApplication application() {
		return this.application;
	}

	public ConfigurableApplicationContext run(String... args) {
		if (this.running.get()) {
			return this.context;
		}
		if (this.running.compareAndSet(false, true)) {
			this.context = this.application.run(args);
		}
		return this.context;
	}

	public SpringApplication build() {
		return build(new String[0]);
	}

	public SpringApplication build(String... args) {
		this.application.addPrimarySources(this.sources);
		return this.application;
	}

	public SpringApplicationBuilder sources(Class<?>... sources) {
		this.sources.addAll(new LinkedHashSet<>(Arrays.asList(sources)));
		this.application.addPrimarySources(this.sources);
		return this;
	}

	public SpringApplicationBuilder web(WebApplicationType webApplicationType) {
		this.application.setWebApplicationType(webApplicationType);
		return this;
	}

	public SpringApplicationBuilder logStartupInfo(boolean logStartupInfo) {
		this.application.setLogStartupInfo(logStartupInfo);
		return this;
	}

	public SpringApplicationBuilder banner(Banner banner) {
		this.application.setBanner(banner);
		return this;
	}

	public SpringApplicationBuilder bannerMode(Banner.Mode bannerMode) {
		this.application.setBannerMode(bannerMode);
		return this;
	}

	public SpringApplicationBuilder headless(boolean headless) {
		this.application.setHeadless(headless);
		return this;
	}

	public SpringApplicationBuilder registerShutdownHook(boolean registerShutdownHook) {
		this.registerShutdownHookApplied = true;
		this.application.setRegisterShutdownHook(registerShutdownHook);
		return this;
	}

	public SpringApplicationBuilder main(Class<?> mainApplicationClass) {
		this.application.setMainApplicationClass(mainApplicationClass);
		return this;
	}

	public SpringApplicationBuilder addCommandLineProperties(boolean addCommandLineProperties) {
		this.application.setAddCommandLineProperties(addCommandLineProperties);
		return this;
	}

	public SpringApplicationBuilder setAddConversionService(boolean addConversionService) {
		this.application.setAddConversionService(addConversionService);
		return this;
	}

	public SpringApplicationBuilder addBootstrapRegistryInitializer(BootstrapRegistryInitializer bootstrapRegistryInitializer) {
		this.application.addBootstrapRegistryInitializer(bootstrapRegistryInitializer);
		return this;
	}

	public SpringApplicationBuilder lazyInitialization(boolean lazyInitialization) {
		this.application.setLazyInitialization(lazyInitialization);
		return this;
	}

	public SpringApplicationBuilder properties(String... defaultProperties) {
		return properties(getMapFromKeyValuePairs(defaultProperties));
	}

	private Map<String, Object> getMapFromKeyValuePairs(String[] properties) {
		Map<String, Object> map = new HashMap<>();
		for (String property : properties) {
			int index = lowestIndexOf(property, ":", "=");
			String key = (index > 0) ? property.substring(0, index) : property;
			String value = (index > 0) ? property.substring(index + 1) : "";
			map.put(key, value);
		}
		return map;
	}

	private int lowestIndexOf(String property, String... candidates) {
		int index = -1;
		for (String candidate : candidates) {
			int candidateIndex = property.indexOf(candidate);
			if (candidateIndex > 0) {
				index = (index != -1) ? Math.min(index, candidateIndex) : candidateIndex;
			}
		}
		return index;
	}

	public SpringApplicationBuilder properties(Properties defaultProperties) {
		return properties(getMapFromProperties(defaultProperties));
	}

	private Map<String, Object> getMapFromProperties(Properties properties) {
		Map<String, Object> map = new HashMap<>();
		for (Object key : Collections.list(properties.propertyNames())) {
			map.put((String) key, properties.get(key));
		}
		return map;
	}

	public SpringApplicationBuilder properties(Map<String, Object> defaults) {
		this.defaultProperties.putAll(defaults);
		this.application.setDefaultProperties(this.defaultProperties);
		return this;
	}

	public SpringApplicationBuilder profiles(String... profiles) {
		this.additionalProfiles.addAll(Arrays.asList(profiles));
		this.application.setAdditionalProfiles(StringUtils.toStringArray(this.additionalProfiles));
		return this;
	}

	public SpringApplicationBuilder beanNameGenerator(BeanNameGenerator beanNameGenerator) {
		this.application.setBeanNameGenerator(beanNameGenerator);
		return this;
	}

	public SpringApplicationBuilder environment(ConfigurableEnvironment environment) {
		this.application.setEnvironment(environment);
		this.environment = environment;
		return this;
	}

	public SpringApplicationBuilder environmentPrefix(String environmentPrefix) {
		this.application.setEnvironmentPrefix(environmentPrefix);
		return this;
	}

	public SpringApplicationBuilder resourceLoader(ResourceLoader resourceLoader) {
		this.application.setResourceLoader(resourceLoader);
		return this;
	}

	public SpringApplicationBuilder initializers(ApplicationContextInitializer<?>... initializers) {
		this.application.addInitializers(initializers);
		return this;
	}

	public SpringApplicationBuilder listeners(ApplicationListener<?>... listeners) {
		this.application.addListeners(listeners);
		return this;
	}

	public SpringApplicationBuilder applicationStartup(ApplicationStartup applicationStartup) {
		this.application.setApplicationStartup(applicationStartup);
		return this;
	}

	public SpringApplicationBuilder allowCircularReferences(boolean allowCircularReferences) {
		this.application.setAllowCircularReferences(allowCircularReferences);
		return this;
	}
}
