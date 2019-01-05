/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.tf.core;

public class JunctionTransmitterAlreadyFetchedException extends Exception{
    private static final long serialVersionUID = 6497749749686683670L;

    JunctionTransmitterAlreadyFetchedException(){super();}
    JunctionTransmitterAlreadyFetchedException(String message){super(message);}
    JunctionTransmitterAlreadyFetchedException(String message, Throwable cause){super(message, cause);}
    JunctionTransmitterAlreadyFetchedException(Throwable cause){super(cause);}
}
