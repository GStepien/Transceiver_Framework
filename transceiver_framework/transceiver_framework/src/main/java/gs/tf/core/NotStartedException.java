/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.tf.core;

public class NotStartedException extends IllegalStatusException{
    private static final long serialVersionUID = 949234972882979304L;

    NotStartedException(){super();}
    NotStartedException(String message){super(message);}
    NotStartedException(String message, Throwable cause){super(message, cause);}
    NotStartedException(Throwable cause){super(cause);}
}
