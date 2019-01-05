/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.utils.datatypes;

public interface TimeComparable extends Comparable<TimeComparable> {

    long getTimestamp();

    public default int compareTo(TimeComparable o) {
        if(this == o){
            return 0;
        }
        long otherTimestamp = o.getTimestamp();

        return Long.compare(this.getTimestamp(), otherTimestamp);
    }
}
