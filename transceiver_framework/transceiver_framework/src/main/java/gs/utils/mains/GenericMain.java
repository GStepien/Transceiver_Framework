/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.utils.mains;

import gs.utils.drivers.Driver;
import gs.tf.drivers.JSONClosedMultiTaskDriver;

public class GenericMain {

    @SuppressWarnings("unchecked")
    public static void main(String[] args){
        if(args.length > 2 || args.length < 1){
            throw new IllegalArgumentException("Unexpected number of arguments for main().");
        }

        Driver<String> driver;

        if(args.length == 1){
            driver = new JSONClosedMultiTaskDriver();
        }
        else {
            try {
                driver = (Driver<String>)Class.forName(args[1]).newInstance();
            } catch (InstantiationException |
                    IllegalAccessException |
                    ClassNotFoundException e) {
                throw new RuntimeException("Could not create instance of gs.utils.drivers.Driver subclass: \""+args[1]+"\"");
            }
        }
        driver.initialize(args[0]);
        driver.execute();
    }
}
