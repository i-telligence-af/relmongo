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

package io.github.kaiso.relmongo.events.processor;

import io.github.kaiso.relmongo.events.callback.PersistentPropertyCascadingRemoveCallback;
import io.github.kaiso.relmongo.events.callback.PersistentPropertyCascadingSaveCallback;
import io.github.kaiso.relmongo.events.callback.PersistentPropertyConvertingCallback;
import io.github.kaiso.relmongo.events.callback.PersistentPropertyLoadingCallback;
import io.github.kaiso.relmongo.events.callback.PersistentPropertyPostLoadingCallback;
import io.github.kaiso.relmongo.events.callback.PersistentPropertySavingCallback;
import io.github.kaiso.relmongo.model.LoadableObjectsMetadata;
import io.github.kaiso.relmongo.mongo.PersistentRelationResolver;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.AfterConvertEvent;
import org.springframework.data.mongodb.core.mapping.event.AfterLoadEvent;
import org.springframework.data.mongodb.core.mapping.event.AfterSaveEvent;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertEvent;
import org.springframework.data.mongodb.core.mapping.event.BeforeDeleteEvent;
import org.springframework.data.mongodb.core.mapping.event.BeforeSaveEvent;
import org.springframework.util.ReflectionUtils;

import java.util.List;

/**
 * 
 * @author Kais OMRI
 *
 */
public class RelMongoProcessor extends AbstractMongoEventListener<Object> {

    @Autowired
    private MongoOperations mongoOperations;

    @Override
    public void onAfterLoad(AfterLoadEvent<Object> event) {
        PersistentPropertyLoadingCallback callback = new PersistentPropertyLoadingCallback(event.getSource());
        ReflectionUtils.doWithFields(event.getType(), callback);
        List<LoadableObjectsMetadata> loadableObjects = callback.getLoadableObjects();
        if (!loadableObjects.isEmpty()) {
            PersistentRelationResolver.resolveOnLoading(mongoOperations, loadableObjects, event.getSource());
        }
        super.onAfterLoad(event);
    }

    @Override
    public void onBeforeSave(BeforeSaveEvent<Object> event) {
        PersistentPropertySavingCallback callback = new PersistentPropertySavingCallback(event.getDocument());
        ReflectionUtils.doWithFields(event.getSource().getClass(), callback);
        super.onBeforeSave(event);
    }

    @Override
    public void onBeforeConvert(BeforeConvertEvent<Object> event) {
        PersistentPropertyConvertingCallback callback = new PersistentPropertyConvertingCallback(event.getSource());
        ReflectionUtils.doWithFields(event.getSource().getClass(), callback);
        super.onBeforeConvert(event);
    }

    @Override
    public void onAfterConvert(AfterConvertEvent<Object> event) {
        PersistentPropertyPostLoadingCallback callback = new PersistentPropertyPostLoadingCallback(event.getSource(), event.getDocument(), mongoOperations);
        ReflectionUtils.doWithFields(event.getSource().getClass(), callback);
        super.onAfterConvert(event);
    }

    @Override
    public void onAfterSave(AfterSaveEvent<Object> event) {
        super.onAfterSave(event);
        PersistentPropertyCascadingSaveCallback callback = new PersistentPropertyCascadingSaveCallback(event.getSource(), mongoOperations);
        ReflectionUtils.doWithFields(event.getSource().getClass(), callback);

    }

    @Override
    public void onBeforeDelete(BeforeDeleteEvent<Object> event) {
        super.onBeforeDelete(event);
        PersistentPropertyCascadingRemoveCallback callback = new PersistentPropertyCascadingRemoveCallback(event.getDocument(), mongoOperations,
                event.getType());
        callback.doProcessing();
    }

}
