package io.github.kaiso.relmongo.config;

import io.github.kaiso.relmongo.annotation.FetchType;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.Assert;

import java.util.Map;

@Order(Ordered.LOWEST_PRECEDENCE)
public class RelMongoProcessorRegistrar implements ImportBeanDefinitionRegistrar {

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry, BeanNameGenerator importBeanNameGenerator) {
        Map<String, Object> annotationAttributes = importingClassMetadata.getAnnotationAttributes(EnableRelMongo.class.getName());
        if (annotationAttributes == null) {
            return;
        }
        String mongoTemplateRef = (String) annotationAttributes.get("mongoTemplateRef");
        Assert.notNull(mongoTemplateRef, "mongoTemplateRef in @EnableRelMongo must not be null!");

        String[] beanDefNames = registry.getBeanDefinitionNames();

        System.out.println("RelMongoProcessorRegistrar.registerBeanDefinitions: beanDefNames.length=" + beanDefNames.length);
        for ( String beanDefName : beanDefNames ){
            System.out.println("\tRelMongoProcessorRegistrar.registerBeanDefinitions: beanDefName=" + beanDefName);
        }

        try {
            BeanDefinition mongoTemplateDef = registry.getBeanDefinition(mongoTemplateRef);
        } catch ( NoSuchBeanDefinitionException e ){
            return;
        }

        boolean useForceFetchType = (boolean) annotationAttributes.get("useForceFetchType");

        FetchType forceFetchType = (FetchType) annotationAttributes.get("forceFetchType");

        BeanDefinitionBuilder postProcessorDefinitionBuilder = BeanDefinitionBuilder
            .rootBeanDefinition(RelMongoBeanPostProcessor.class);
        postProcessorDefinitionBuilder
                .addConstructorArgValue(mongoTemplateRef)
        ;

        if ( useForceFetchType ){
            postProcessorDefinitionBuilder
                .addConstructorArgValue(forceFetchType)
            ;
        } else {
        }

        String beanName = mongoTemplateRef + "$RelMongo$BeanPostProcessor";
        if (!registry.containsBeanDefinition(beanName)) {
            registry.registerBeanDefinition(beanName,
                postProcessorDefinitionBuilder.getBeanDefinition());
        }
        /*
         * BeanDefinitionBuilder relMongoProcessorDefinitionBuilder =
         * BeanDefinitionBuilder
         * .rootBeanDefinition(RelMongoProcessor.class);
         * 
         * relMongoProcessorDefinitionBuilder.addConstructorArgReference(
         * mongoTemplateRef);
         * 
         * AbstractBeanDefinition rlmpBeanDefinition =
         * relMongoProcessorDefinitionBuilder.getBeanDefinition();
         * 
         * String generateBeanName = BeanUtils.getProcessorBeanName(mongoTemplateRef);
         * 
         * if (!registry.containsBeanDefinition(generateBeanName)) {
         * registry.registerBeanDefinition(generateBeanName, rlmpBeanDefinition);
         * }
         */

        registerIndexCreator(registry, mongoTemplateRef);

    }

    private void registerIndexCreator(BeanDefinitionRegistry registry, String mongoTemplateRef) {

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
