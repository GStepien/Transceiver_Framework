/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.utils.datatypes;

import org.apache.commons.collections4.set.UnmodifiableSet;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class GenericTimestampedData<DATA_TYPE> extends GenericTimeComparable implements TimestampedData<DATA_TYPE> {

    private final DATA_TYPE m_data;

    private final ConcurrentMap<Object, Object> m_annotations;

    protected GenericTimestampedData(long timestamp, DATA_TYPE data){
        super(timestamp);
        if(data == null){
            throw new NullPointerException();
        }
        this.m_data = data;
        this.m_annotations = new ConcurrentHashMap<>();
    }

    public Object addAnnotation(Object key, Object annotation){
        if(key == null){
            throw new NullPointerException();
        }
        return this.m_annotations.put(key, annotation);
    }

    public Object getAnnotation(Object key){
        if(key == null){
            throw new NullPointerException();
        }
        return this.m_annotations.get(key);
    }

    public UnmodifiableSet<Object> getAnnotationKeys(){
        return (UnmodifiableSet<Object>) UnmodifiableSet.unmodifiableSet(this.m_annotations.keySet());
    }


    @Override
    public DATA_TYPE getData() {
        return this.m_data;
    }
}
