package io.github.kaiso.relmongo.config;

import java.util.AbstractMap;
import org.springframework.data.mapping.PersistentProperty;
import org.springframework.data.mapping.model.FieldNamingStrategy;
import org.springframework.data.mapping.model.MutablePersistentEntity;
import org.springframework.data.mapping.model.Property;
import org.springframework.data.mapping.model.PropertyNameFieldNamingStrategy;
import org.springframework.data.mapping.model.SimpleTypeHolder;
import org.springframework.data.mongodb.core.mapping.BasicMongoPersistentEntity;
import org.springframework.data.mongodb.core.mapping.CachingMongoPersistentProperty;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.data.mongodb.core.mapping.MongoPersistentProperty;
import org.springframework.data.mongodb.core.mapping.MongoSimpleTypes;
import org.springframework.data.util.TypeInformation;
import org.springframework.lang.Nullable;

public class RelMongoMappingContext extends MongoMappingContext {
    private static final FieldNamingStrategy DEFAULT_NAMING_STRATEGY = (FieldNamingStrategy)PropertyNameFieldNamingStrategy.INSTANCE;

    private FieldNamingStrategy fieldNamingStrategy = DEFAULT_NAMING_STRATEGY;

    private boolean autoIndexCreation = true;

    public RelMongoMappingContext() {
        setSimpleTypeHolder(MongoSimpleTypes.HOLDER);
    }

    public void setFieldNamingStrategy(@Nullable FieldNamingStrategy fieldNamingStrategy) {
        this.fieldNamingStrategy = (fieldNamingStrategy == null) ? DEFAULT_NAMING_STRATEGY : fieldNamingStrategy;
    }

    protected boolean shouldCreatePersistentEntityFor(TypeInformation<?> type) {
        return (!MongoSimpleTypes.HOLDER.isSimpleType(type.getType()) && !AbstractMap.class.isAssignableFrom(type.getType()));
    }

    public MongoPersistentProperty createPersistentProperty(Property property, MongoPersistentEntity<?> owner, SimpleTypeHolder simpleTypeHolder) {
        return (MongoPersistentProperty)new CachingMongoPersistentProperty(property, owner, simpleTypeHolder, this.fieldNamingStrategy);
    }

    protected <T> BasicMongoPersistentEntity<T> createPersistentEntity(TypeInformation<T> typeInformation) {
        return new BasicMongoPersistentEntity(typeInformation);
    }

    public boolean isAutoIndexCreation() {
        return false;
    }

    public boolean hasAutoIndexEnabled() {
        return this.autoIndexCreation;
    }

    public void setAutoIndexCreation(boolean autoCreateIndexes) {
        this.autoIndexCreation = autoCreateIndexes;
    }
}
