/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.tf.factories;

import gs.tf.core.ClosedMultiTaskChain;
import gs.tf.core.ConcurrentTask;
import gs.tf.core.MultiTask;

import java.util.List;
import java.util.Set;

public interface Factory<CONFIG_TYPE> {

    List<String> getOtherObjectNamesList();
    Set<String> getOtherObjectNames();
    ConcurrentTask createOtherObject(String name);

    List<String> getConcurrentTaskNamesList();
    Set<String> getConcurrentTaskNames();
    ConcurrentTask createConcurrentTask(String name);

    List<String> getMultiTaskNamesList();
    Set<String> getMultiTaskNames();
    MultiTask<?> createMultiTask(String name);

    // List ordered by name
    List<String> getClosedMultiTaskChainNamesList();
    Set<String> getClosedMultiTaskChainNames();
    ClosedMultiTaskChain createClosedMultiTaskChain(String name);

    CONFIG_TYPE getConfiguration();

}
