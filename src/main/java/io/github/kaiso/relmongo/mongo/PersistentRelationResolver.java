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

package io.github.kaiso.relmongo.mongo;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;

import org.springframework.cglib.proxy.Callback;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.Factory;
import org.springframework.cglib.proxy.NoOp;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.objenesis.ObjenesisStd;

import io.github.kaiso.relmongo.lazy.LazyLoadingProxy;
import io.github.kaiso.relmongo.lazy.RelMongoLazyLoader;

public final class PersistentRelationResolver {

    private static ObjenesisStd objenesisStd = new ObjenesisStd(true);

    private PersistentRelationResolver() {
        super();
    }

    public static Object lazyLoader(Class<?> type, MongoOperations mongoOperations, List<Object> ids,
        String property, Class<?> targetClass, Object original, Object parent, String fieldName) {

        RelMongoLazyLoader lazyLoader = new RelMongoLazyLoader(ids, property, mongoOperations,
            targetClass, type, fieldName, original, parent);

        if (Collection.class.isAssignableFrom(type)) {
            // Java 23+ blocks CGLIB's reflection-based ClassLoader.defineClass for interface-only
            // proxies (no superclass anchor). Use JDK Proxy instead, which handles pure interfaces
            // natively without needing --add-opens java.base/java.lang.
            Class<?>[] interfaces = type.isInterface()
                ? new Class<?>[]{ LazyLoadingProxy.class, type }
                : new Class<?>[]{ LazyLoadingProxy.class, List.class };
            return Proxy.newProxyInstance(
                targetClass.getClassLoader(),
                interfaces,
                new LazyCollectionInvocationHandler(lazyLoader));
        }

        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(targetClass);
        enhancer.setInterfaces(new Class[] { LazyLoadingProxy.class, NoOp.class });
        enhancer.setAttemptLoad(true);
        enhancer.setCallbackType(RelMongoLazyLoader.class);
        @SuppressWarnings("unchecked")
        Factory factory = (Factory) objenesisStd.newInstance(enhancer.createClass());
        factory.setCallbacks(new Callback[] { lazyLoader });
        return factory.newInstance(lazyLoader);
    }

    private static final class LazyCollectionInvocationHandler implements InvocationHandler {

        private final RelMongoLazyLoader lazyLoader;
        private volatile Object delegate;

        LazyCollectionInvocationHandler(RelMongoLazyLoader lazyLoader) {
            this.lazyLoader = lazyLoader;
        }

        private Object getDelegate() throws Exception {
            if (delegate == null) {
                synchronized (this) {
                    if (delegate == null) {
                        delegate = lazyLoader.loadObject();
                    }
                }
            }
            return delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("getTarget".equals(method.getName()) && method.getParameterCount() == 0) {
                return getDelegate();
            }
            try {
                return method.invoke(getDelegate(), args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

}
