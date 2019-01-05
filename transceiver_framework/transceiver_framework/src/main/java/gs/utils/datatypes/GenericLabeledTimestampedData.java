/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.utils.datatypes;

public class GenericLabeledTimestampedData<DATA_TYPE> extends GenericTimestampedData<DATA_TYPE> implements LabeledTimestampedData<DATA_TYPE> {

    private final String m_label;

    protected GenericLabeledTimestampedData(long timestamp, String label, DATA_TYPE data) {
        super(timestamp, data);
        if(label == null){
            throw new NullPointerException();
        }
        this.m_label = label;
    }

    @Override
    public String getLabel() {
        return this.m_label;
    }
}
