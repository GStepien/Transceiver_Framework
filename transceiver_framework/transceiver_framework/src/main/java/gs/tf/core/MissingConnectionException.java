/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.tf.core;

public class MissingConnectionException extends Exception{
    private static final long serialVersionUID = -8407072970076068256L;

    MissingConnectionException(){super();}
    MissingConnectionException(String message){super(message);}
    MissingConnectionException(String message, Throwable cause){super(message, cause);}
    MissingConnectionException(Throwable cause){super(cause);}
}
