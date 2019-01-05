/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.utils.drivers;

public interface Driver<ARGS_TYPE> {
    void initialize(ARGS_TYPE args);
    boolean isInitialized();
    // Only possible after initialization
    ARGS_TYPE getArgs();
    void execute();
}
