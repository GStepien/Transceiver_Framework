/**
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */

package gs.utils.json;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.ArrayList;

public class JSONTypedArray extends ArrayList<Object> implements ValueGetter {

    private static final long serialVersionUID = -4719698578706951671L;

    public JSONTypedArray(){}

    private static JSONArray jsonArrayFromString(String jsonString) throws JSONException {
        try {
            Object result = (new JSONParser()).parse(jsonString);

            if(!(result instanceof JSONArray)){
                throw new JSONException("Unexpected type of JSON string.");
            }
            else{
                return (JSONArray) result;
            }
        } catch (ParseException e) {
            throw new JSONException(e);
        }
    }

    public JSONTypedArray(String jsonString) throws JSONException {
        this(JSONTypedArray.jsonArrayFromString(jsonString));
    }

    public JSONTypedArray(JSONArray jsonArray){
        super();

        if(jsonArray == null){
            throw new NullPointerException();
        }

        for(int i = 0; i < jsonArray.size(); i++){
            this.extend(jsonArray.get(i));
        }
    }

    public int length(){
        return this.size();
    }

    @Override
    public Object get(Object key) {
        int index;
        if(key == null){
            throw new NullPointerException();
        }
        else if(key instanceof Integer){
            index = (Integer)key;
        }
        else if(key instanceof Long){
            index = Math.toIntExact((Long) key);
        }
        else {
            throw new IllegalArgumentException("The key must be of type Integer or Long.");
        }

        return this.get(index);
    }

    @Override
    public boolean containsKey(Object key){
        int index;
        if(key == null){
            return false;
        }
        else if(key instanceof Integer){
            index = (Integer) key;
        }
        else if(key instanceof Long){
            index = Math.toIntExact((Long) key);
        }
        else {
            return false;
        }

        return index >= 0 && index < this.size();
    }

    // If value is of type JSONObject -> transform to JSONTypedObject
    // If value is of type JSONArray -> transform to JSONTypedArray
    // Then:
    // Add at end of array
    public JSONTypedArray extend(Object value){

        if(value != null){
            if(value instanceof JSONObject){
                value = new JSONTypedObject((JSONObject) value);
            }
            else if (value instanceof JSONArray){
                value = new JSONTypedArray((JSONArray) value);
            }
        }

        this.add(value);

        return this;
    }

    public boolean getBoolean(int index) throws JSONException{
        return JSONTypedObject.getBoolean(this, index);
    }

    public double getDouble(int index) throws JSONException{
        return JSONTypedObject.getDouble(this, index);
    }

    public int getInt(int index) throws JSONException{
        return JSONTypedObject.getInt(this, index);
    }

    public long getLong(int index) throws JSONException{
        return JSONTypedObject.getLong(this, index);
    }

    public String getString(int index) throws JSONException{
        return JSONTypedObject.getString(this, index);
    }

    public JSONTypedArray getJSONTypedArray(int index) throws JSONException{
        return JSONTypedObject.getJSONTypedArray(this, index);
    }

    public JSONTypedObject getJSONTypedObject(int index) throws JSONException{
        return JSONTypedObject.getJSONTypedObject(this, index);
    }

    public boolean isNull(int index) throws JSONException{
        JSONTypedObject.checkKeyPresence(this, index);
        return this.get(index) == null;
    }
}
