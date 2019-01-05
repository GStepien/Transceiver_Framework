/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.tf.core;

public class AlreadyConnectedException extends UnacceptedConcurrentTaskException {
    private static final long serialVersionUID = 7247382597456021205L;

    AlreadyConnectedException(){super();}
    AlreadyConnectedException(String message){super(message);}
    AlreadyConnectedException(String message, Throwable cause){super(message, cause);}
    AlreadyConnectedException(Throwable cause){super(cause);}
}
