/**
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */

package gs.utils.json;

public class JSONException extends RuntimeException {

    private static final long serialVersionUID = 3715775237481765694L;

    public JSONException(){super();}
    public JSONException(String message){super(message);}
    public JSONException(String message, Throwable cause){super(message, cause);}
    public JSONException(Throwable cause){super(cause);}

}
