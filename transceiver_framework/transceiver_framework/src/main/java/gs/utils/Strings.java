/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.utils;

import java.util.regex.Pattern;

public class Strings {

    private Strings(){}

    public static final Pattern SPLIT_LINES_PATTERN = Pattern.compile("\\r?\\n");

}
