/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.utils.r;

import java.io.IOException;

public class RException extends IOException {

    private static final long serialVersionUID = 7127956266032206738L;

    public RException(){super();}
    public RException(String message){super(message);}
    public RException(String message, Throwable cause){super(message, cause);}
    public RException(Throwable cause){super(cause);}

}
