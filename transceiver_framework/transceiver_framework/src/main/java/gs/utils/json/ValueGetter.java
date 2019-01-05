/**
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */

package gs.utils.json;

public interface ValueGetter {

    public Object get(Object key);

    public boolean containsKey(Object key);

}
