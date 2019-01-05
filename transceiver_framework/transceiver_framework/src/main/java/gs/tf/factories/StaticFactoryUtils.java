/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.tf.factories;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class StaticFactoryUtils {

    private StaticFactoryUtils(){}

    public static int sum(int a, int b){
        return a + b;
    }

    public static List<Integer> enumerateIndices(int from, int toExcl){
        if(from > toExcl){
            throw new IllegalArgumentException();
        }

        List<Integer> result = new ArrayList<>(toExcl - from);

        for(int i = from; i < toExcl; i++){
            result.add(i);
        }
        return result;
    }

    // Reads first line from file at provided path and parses it to int.
    public static int intFromFile(String path){
        if(path == null){
            throw new IllegalArgumentException();
        }

        try {
            BufferedReader reader = new BufferedReader(new FileReader(path));
            String firstLine = reader.readLine();
            if(firstLine == null){
                throw new IllegalArgumentException("File is empty.");
            }
            return Integer.parseInt(firstLine);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (NumberFormatException e){
            throw new RuntimeException("Could not parse first line as integer.", e);
        }
    }
}
