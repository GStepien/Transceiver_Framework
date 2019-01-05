/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.utils.datatypes;

import org.apache.commons.collections4.set.UnmodifiableSet;

public interface AnnotatedData<DATA_TYPE> extends Data<DATA_TYPE> {

    Object addAnnotation(Object key, Object annotation);
    Object getAnnotation(Object key);
    UnmodifiableSet<Object> getAnnotationKeys();
}
