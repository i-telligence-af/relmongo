/**
*   Copyright 2018 Kais OMRI.
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

package io.github.kaiso.relmongo.util;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.Collection;
/**
 * 
 * @author Kais OMRI
 *
 */
public final class ReflectionsUtil {

    private ReflectionsUtil() {
        super();
    }

    public static Class<?> getGenericType(Field field) {
        if (Collection.class.isAssignableFrom(field.getType())) {
            try {
                return (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
            } catch (ClassCastException e) {
                //do nothing if the generic type is also generic we do not take it into account
            }
        }
        return field.getType();
    }

}
