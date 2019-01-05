/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.tf.core;

public class UnacceptedConcurrentTaskException extends Exception{
    private static final long serialVersionUID = 1840589618718242809L;

    UnacceptedConcurrentTaskException(){super();}
    UnacceptedConcurrentTaskException(String message){super(message);}
    UnacceptedConcurrentTaskException(String message, Throwable cause){super(message, cause);}
    UnacceptedConcurrentTaskException(Throwable cause){super(cause);}
}
