package io.github.kaiso.relmongo.config;

import io.github.kaiso.relmongo.annotation.FetchType;
import io.github.kaiso.relmongo.events.processor.RelMongoProcessor;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.event.MongoMappingEvent;

import java.lang.reflect.Field;

public class RelMongoBeanPostProcessor implements BeanPostProcessor {

    private final String mongoTemplateRef;

    private final FetchType forceFetchType;

    public RelMongoBeanPostProcessor(String mongoTemplateRef, FetchType forceFetchType) {
        super();
        this.mongoTemplateRef = mongoTemplateRef;
        this.forceFetchType = forceFetchType;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {

        if (bean instanceof MongoTemplate && beanName.equals(mongoTemplateRef)) {

            /*
             * Enhancer enhancer = new Enhancer();
             * enhancer.setSuperclass(bean.getClass());
             * enhancer.setCallbacks(new MethodInterceptor[] { new
             * RelMongoTemplateInvocationHandler() });
             *
             * Object proxy = enhancer.create(new Class<?>[] { MongoDbFactory.class,
             * MongoConverter.class },
             * new Object[] { ((MongoTemplate) bean).getMongoDbFactory(), ((MongoTemplate)
             * bean).getConverter() });
             *
             * ((MongoTemplate) proxy).setApplicationContext(applicationContext);
             *
             * return proxy;
             */

            try {
                Field ep = MongoTemplate.class.getDeclaredField("eventPublisher");
                ep.setAccessible(true);
                ep.set(bean, new RelMongoEventPublisher((MongoTemplate) bean, (ApplicationEventPublisher) ep.get(bean), forceFetchType));
            } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
                throw new BeanInitializationException("Fatal: failed to init the RelMongo Engine", e);
            }
        }

        return bean;
    }

    private static final class RelMongoEventPublisher implements ApplicationEventPublisher {

        private final RelMongoProcessor relMongoProcessor;
        private final MongoTemplate mongoTemplate;
        private final ApplicationEventPublisher eventPublisher;

        public RelMongoEventPublisher(MongoTemplate mongoTemplate, ApplicationEventPublisher eventPublisher, FetchType forceFetchType) {
            super();
            this.relMongoProcessor = new RelMongoProcessor(forceFetchType);
            this.mongoTemplate = mongoTemplate;
            this.eventPublisher = eventPublisher;
        }

        @Override
        public void publishEvent(Object event) {
            relMongoProcessor.onApplicationEvent((MongoMappingEvent<?>) event, mongoTemplate);
            eventPublisher.publishEvent(event);
        }

    }

}