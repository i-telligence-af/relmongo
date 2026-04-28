package io.github.kaiso.relmongo.config;

import io.github.kaiso.relmongo.annotation.FetchType;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;

/**
 * Defers the conditional registration of RelMongo beans to after all @Configuration
 * classes have been fully processed, avoiding ordering issues with @Conditional beans
 * defined in other modules/JARs (e.g. on a Tomcat WAR deployment).
 */
public class RelMongoDeferredRegistrar implements BeanDefinitionRegistryPostProcessor {

    private final String mongoTemplateRef;
    private final boolean useForceFetchType;
    private final FetchType forceFetchType;

    public RelMongoDeferredRegistrar(String mongoTemplateRef, boolean useForceFetchType, FetchType forceFetchType) {
        this.mongoTemplateRef = mongoTemplateRef;
        this.useForceFetchType = useForceFetchType;
        this.forceFetchType = forceFetchType;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        if (!registry.containsBeanDefinition(mongoTemplateRef)) {
            return;
        }

        BeanDefinitionBuilder postProcessorDefinitionBuilder = BeanDefinitionBuilder
            .rootBeanDefinition(RelMongoBeanPostProcessor.class);
        postProcessorDefinitionBuilder.addConstructorArgValue(mongoTemplateRef);
        if (useForceFetchType) {
            postProcessorDefinitionBuilder.addConstructorArgValue(forceFetchType);
        }

        String beanName = mongoTemplateRef + "$RelMongo$BeanPostProcessor";
        if (!registry.containsBeanDefinition(beanName)) {
            registry.registerBeanDefinition(beanName, postProcessorDefinitionBuilder.getBeanDefinition());
        }

        registerIndexCreator(registry);
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
    }

    private void registerIndexCreator(BeanDefinitionRegistry registry) {
        String generateBeanName = mongoTemplateRef + "$RelMongo$OK$RLMIndexCreator";
        if (!registry.containsBeanDefinition(generateBeanName)) {
            BeanDefinitionBuilder idxCreatorDefinitionBuilder = BeanDefinitionBuilder
                .rootBeanDefinition(RelMongoPersistentEntityIndexCreator.class);
            idxCreatorDefinitionBuilder.addConstructorArgReference(mongoTemplateRef);
            idxCreatorDefinitionBuilder.setLazyInit(false);
            AbstractBeanDefinition idxCreatorDefinition = idxCreatorDefinitionBuilder.getBeanDefinition();
            registry.registerBeanDefinition(generateBeanName, idxCreatorDefinition);
        }
    }
}
