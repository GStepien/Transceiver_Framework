/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.utils.datatypes;

public class GenericTimeComparable implements TimeComparable {
    private final long m_timestamp;

    public GenericTimeComparable(long timestamp){
        this.m_timestamp = timestamp;
    }

    @Override
    public long getTimestamp() {
        return this.m_timestamp;
    }
}
