/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.tf.core;

public class DiscardingReceiverTask extends AbstractReceiverTask<Object> {
    public DiscardingReceiverTask(Integer inDataQueueCapacity, Long timeoutInterval) {
        super(inDataQueueCapacity, timeoutInterval);
    }

    @Override
    protected void processDataElement(Object dataElement) {
        // Do nothing
    }
}
