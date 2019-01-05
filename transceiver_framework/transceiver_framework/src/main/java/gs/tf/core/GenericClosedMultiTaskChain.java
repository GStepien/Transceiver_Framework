/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.tf.core;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public class GenericClosedMultiTaskChain 
	extends
        GenericMultiTaskChain<
                    TransmitterTask<?>,
                MultiTransmitterTask<?,?>,
                    ReceiverTask<?>,
                MultiReceiverTask<?,?>>
	implements 
		ClosedMultiTaskChain {


    public GenericClosedMultiTaskChain(Collection<? extends MultiTask<?>> multiTasks,
                                       Map<? extends MultiTask<?>, ? extends Collection<? extends MultiTask<?>>> multiTaskConnectionDAG,
                                       ExecutorService executorService) throws UnacceptedConcurrentTaskException {
        super(multiTasks, multiTaskConnectionDAG, executorService);
        
    }
}
