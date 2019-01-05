/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.tf.core;

public class IllegalStatusException extends Exception {
    private static final long serialVersionUID = 8090332880852359442L;

    IllegalStatusException(){super();}
    IllegalStatusException(String message){super(message);}
    IllegalStatusException(String message, Throwable cause){super(message, cause);}
    IllegalStatusException(Throwable cause){super(cause);}
}
