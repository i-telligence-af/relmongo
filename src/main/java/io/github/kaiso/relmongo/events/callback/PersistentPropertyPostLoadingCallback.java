/**
 *   Copyright 2018 Kais OMRI and authors.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.github.kaiso.relmongo.events.callback;

import io.github.kaiso.relmongo.annotation.*;
import io.github.kaiso.relmongo.events.processor.MappedByProcessor;
import io.github.kaiso.relmongo.exception.RelMongoConfigurationException;
import io.github.kaiso.relmongo.model.MappedByMetadata;
import io.github.kaiso.relmongo.mongo.DatabaseOperations;
import io.github.kaiso.relmongo.mongo.DocumentUtils;
import io.github.kaiso.relmongo.mongo.PersistentRelationResolver;
import io.github.kaiso.relmongo.util.AnnotationsUtils;
import io.github.kaiso.relmongo.util.ReflectionsUtil;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cglib.core.CodeGenerationException;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.ReflectionUtils.FieldCallback;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 *
 * @author Kais OMRI
 *
 */
public class PersistentPropertyPostLoadingCallback implements FieldCallback {

    private static final Logger LOGGER = LoggerFactory.getLogger(PersistentPropertyPostLoadingCallback.class);

    private Object source;
    private MongoOperations mongoOperations;
    private Document document;

    private final FetchType forceFetchType;

    public PersistentPropertyPostLoadingCallback(Object source, Document document, MongoOperations mongoOperations, FetchType forceFetchType) {
        super();
        this.source = source;
        this.mongoOperations = mongoOperations;
        this.document = document;
        this.forceFetchType = forceFetchType;
    }

    private void processNestedCollection(Collection<?> collection, Object fieldValue) {
        if ( !(fieldValue instanceof List) ) {
            return;
        }
        List<?> documentList = (List<?>) fieldValue;
        int i = 0;
        for ( Object item : collection ) {
            if ( item != null && i < documentList.size() && documentList.get(i) instanceof Document ) {
                PersistentPropertyPostLoadingCallback callback = new PersistentPropertyPostLoadingCallback(item, (Document) documentList.get(i), mongoOperations, forceFetchType);
                ReflectionUtils.doWithFields(item.getClass(), callback);
            }
            i++;
        }
    }

    private void processNestedMap(Map<?, ?> map, Object fieldValue) {
        if ( !(fieldValue instanceof Document) ) {
            return;
        }
        Document bsonMap = (Document) fieldValue;
        for ( Map.Entry<?, ?> entry : map.entrySet() ) {
            Object value = entry.getValue();
            if ( value == null ) continue;
            Object entryDocument = bsonMap.get(String.valueOf(entry.getKey()));
            if ( entryDocument instanceof Document ) {
                PersistentPropertyPostLoadingCallback callback = new PersistentPropertyPostLoadingCallback(value, (Document) entryDocument, mongoOperations, forceFetchType);
                ReflectionUtils.doWithFields(value.getClass(), callback);
            }
        }
    }

    private void handleEager(Field field, Class<?> type, List<Object> identifierList) {
        if (Collection.class.isAssignableFrom(field.getType())) {
            ReflectionUtils.setField(field, source,
                    DatabaseOperations.findByIds(mongoOperations, type, identifierList.toArray(new Object[identifierList.size()])));
        } else {
            ReflectionUtils.setField(field, source,
                    DatabaseOperations.findByPropertyValue(mongoOperations, type, "_id", identifierList.get(0)));
        }
        MappedByProcessor.processChild(source, source, field, type);
    }

    public void doWith(Field field) throws IllegalAccessException {
        ReflectionUtils.makeAccessible(field);

        if ( field.isAnnotationPresent(Nested.class) ) {
            Object object = field.get(source);
            if ( object != null ) {

                Object fieldValue = document.get(field.getName());

                if ( object instanceof Collection ) {
                    processNestedCollection((Collection<?>) object, fieldValue);
                } else if ( object instanceof Map ) {
                    processNestedMap((Map<?, ?>) object, fieldValue);
                } else if ( fieldValue instanceof Document ) {
                    PersistentPropertyPostLoadingCallback callback = new PersistentPropertyPostLoadingCallback(object, (Document) fieldValue, mongoOperations, forceFetchType);
                    ReflectionUtils.doWithFields(object.getClass(), callback);
                }

            }
            return;
        }

        if (!(field.isAnnotationPresent(OneToOne.class) || field.isAnnotationPresent(OneToMany.class) || field.isAnnotationPresent(ManyToOne.class))) {
            return;
        }

        Class<?> type = ReflectionsUtil.getGenericType(field);

        FetchType fetchType = AnnotationsUtils.getFetchType(field);
        if ( forceFetchType != null ) {
            fetchType = forceFetchType;
        }

        if (DocumentUtils.isLoaded(document.get(field.getName()))) {
            MappedByProcessor.processChild(source, source, field, type);
            return;
        }

        List<Object> identifierList = new ArrayList<>();
        MappedByMetadata mappedByInfos = AnnotationsUtils.getMappedByInfos(field);

        if (mappedByInfos.getMappedByValue() == null) {
            String joinPropertyName = AnnotationsUtils.getJoinProperty(field);
            if (field.isAnnotationPresent(OneToMany.class) && !Collection.class.isAssignableFrom(field.getType())) {
                throw new RelMongoConfigurationException("in @OneToMany, the field must be of type collection ");
            }
            Object relations = document.get(joinPropertyName);

            if (relations == null || (relations instanceof Document && ((Document) relations).keySet().isEmpty())
                    || (relations instanceof Collection && ((Collection<?>) relations).isEmpty())) {
                return;
            }
            if (relations instanceof Collection) {
                identifierList.addAll(((Collection<?>) relations).stream()
                        .map(DocumentUtils::mapIdentifier).collect(Collectors.toList()));
            } else {
                identifierList.add(DocumentUtils.mapIdentifier(relations));
            }
        } else {
            identifierList.add(DocumentUtils.mapIdentifier(document));
        }

        if (FetchType.LAZY.equals(fetchType) || mappedByInfos.getMappedByValue() != null) {
            // mappedBy fields are loaded only in lazy mode to avoid cycles in loading

            Object object = field.get(source);
            String fieldName = field.getName();

            try {
                Object value = PersistentRelationResolver.lazyLoader(field.getType(), mongoOperations,
                        identifierList, mappedByInfos.getMappedByJoinProperty(), type,
                        object, source, fieldName);
                ReflectionUtils.setField(field, source, value);
            } catch ( Exception e ){

                LOGGER.warn("Error while loading lazy field {} of type {}, falling back to FetchType." + FetchType.EAGER, fieldName, object.getClass());
                handleEager(field, type, identifierList);

            }

        } else if (FetchType.EAGER.equals(fetchType)) {
            handleEager(field, type, identifierList);
        }

    }

}