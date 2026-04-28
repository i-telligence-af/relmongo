package io.github.kaiso.relmongo.config;

import io.github.kaiso.relmongo.annotation.FetchType;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.Assert;

import java.util.Map;

public class RelMongoProcessorRegistrar implements ImportBeanDefinitionRegistrar {

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry, BeanNameGenerator importBeanNameGenerator) {
        Map<String, Object> annotationAttributes = importingClassMetadata.getAnnotationAttributes(EnableRelMongo.class.getName());
        if (annotationAttributes == null) {
            return;
        }
        String mongoTemplateRef = (String) annotationAttributes.get("mongoTemplateRef");
        Assert.notNull(mongoTemplateRef, "mongoTemplateRef in @EnableRelMongo must not be null!");

        boolean useForceFetchType = (boolean) annotationAttributes.get("useForceFetchType");
        FetchType forceFetchType = (FetchType) annotationAttributes.get("forceFetchType");

        // Delegate the conditional check and bean registration to a BeanDefinitionRegistryPostProcessor,
        // which runs after all @Configuration classes have been processed. This avoids an ordering
        // issue on Tomcat WAR deployments where @Conditional MongoTemplate beans defined in other
        // modules are not yet in the registry when this registrar fires.
        String deferredName = mongoTemplateRef + "$RelMongo$DeferredRegistrar";
        if (!registry.containsBeanDefinition(deferredName)) {
            BeanDefinitionBuilder builder = BeanDefinitionBuilder
                .rootBeanDefinition(RelMongoDeferredRegistrar.class);
            builder.addConstructorArgValue(mongoTemplateRef);
            builder.addConstructorArgValue(useForceFetchType);
            builder.addConstructorArgValue(forceFetchType);
            registry.registerBeanDefinition(deferredName, builder.getBeanDefinition());
        }
    }
}
