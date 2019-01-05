/**
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */

package gs.utils.json;

import gs.utils.GenericFactory;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.HashMap;


public class JSONTypedObject extends HashMap<String, Object> implements ValueGetter {

    private static final long serialVersionUID = 1550005067267367414L;

    public JSONTypedObject(){}

    private static JSONObject jsonObjectFromString(String jsonString) throws JSONException {
        try {
            Object result = (new JSONParser()).parse(jsonString);

            if(!(result instanceof JSONObject)){
                throw new JSONException("Unexpected type of JSON string.");
            }
            else{
                return (JSONObject) result;
            }
        } catch (ParseException e) {
            throw new JSONException(e);
        }
    }

    public JSONTypedObject(String jsonString) throws JSONException {
        this(JSONTypedObject.jsonObjectFromString(jsonString));
    }

    public JSONTypedObject(JSONObject jsonObject){
        super();

        if(jsonObject == null){
            throw new NullPointerException();
        }

        Object value;
        for(Object key : jsonObject.keySet()){
            if(!(key instanceof String)){
                throw new IllegalArgumentException("The key of a JSON object must be of type String.");
            }
            else{
                this.extend((String) key, jsonObject.get(key));
            }
        }
    }

    // If value is of type JSONObject -> transform to JSONTypedObject
    // If value is of type JSONArray -> transform to JSONTypedArray
    // Then:
    // If key exists and its assigned object is JSONTypedArray -> append object to JSONArray
    // Else if key exists, create new JSONTypedArray containing old object and the provided one
    // Else Simply put provided object under the provided key
    public JSONTypedObject extend(String key, Object value){
        if(key == null){
            throw new NullPointerException();
        }

        if(value != null){
            if(value instanceof JSONObject){
                value = new JSONTypedObject((JSONObject) value);
            }
            else if (value instanceof JSONArray){
                value = new JSONTypedArray((JSONArray) value);
            }
        }

        if(!this.containsKey(key)){
            this.put(key, value);
        }
        else{
            Object oldVal = this.get(key);
            if(!(oldVal instanceof JSONTypedArray)){
                JSONTypedArray newEntry = new JSONTypedArray();
                newEntry.add(oldVal);
                oldVal = newEntry;
            }

            assert(oldVal instanceof JSONTypedArray);

            ((JSONTypedArray) oldVal).add(value);
        }
        return this;
    }

    static void checkKeyPresence(ValueGetter valGet, Object key) throws JSONException{
        if(key == null){
            throw new NullPointerException();
        }
        if(!valGet.containsKey(key)){
            throw new JSONException("Key \""+key+"\" not present.");
        }
    }

    static void checkKeyPresenceAndValueNotNull(ValueGetter valGet, Object key) throws JSONException{
        JSONTypedObject.checkKeyPresence(valGet, key);
        if(valGet.get(key) == null) {
            throw new JSONException("Illegal null value encountered for key \""+key+"\".");
        }
    }

    static boolean getBoolean(ValueGetter valGet, Object key) throws JSONException{
        JSONTypedObject.checkKeyPresenceAndValueNotNull(valGet, key);
        Object value = valGet.get(key);
        assert(value != null);

        try {
            return (Boolean)GenericFactory.smartCast(Boolean.class, value);
        }
        catch(ClassCastException e){
            if(value instanceof String) {
                String strValue = ((String) value).trim();
                if (strValue.equalsIgnoreCase("true")) {
                    return true;
                } else if (strValue.equalsIgnoreCase("false")) {
                    return false;
                }
                else{
                    throw new JSONException("Could not parse Boolean from String: \""+strValue+"\"");
                }
            }
        }

        throw new JSONException("Key \""+key+"\" does not refer to a Boolean.");
    }

    public boolean getBoolean(String key) throws JSONException{
        return JSONTypedObject.getBoolean(this, key);
    }

    static double getDouble(ValueGetter valGet, Object key) throws JSONException{
        JSONTypedObject.checkKeyPresenceAndValueNotNull(valGet, key);
        Object value = valGet.get(key);
        assert(value != null);

        try {
            return (Double)GenericFactory.smartCast(Double.class, value);
        }
        catch(ClassCastException cce) {
            if(value instanceof String) {
                String strValue = ((String) value).trim();
                try {
                    return Double.parseDouble(strValue);
                } catch (NumberFormatException e) {
                    throw new JSONException("Could not parse Double from String: \"" + strValue + "\"", e);
                }
            }
        }

        throw new JSONException("Key \""+key+"\" does not refer to a Double.");
    }

    public double getDouble(String key) throws JSONException{
        return JSONTypedObject.getDouble(this, key);
    }

    static int getInt(ValueGetter valGet, Object key) throws JSONException{
        JSONTypedObject.checkKeyPresenceAndValueNotNull(valGet, key);
        Object value = valGet.get(key);
        assert(value != null);

        try {
            return (Integer)GenericFactory.smartCast(Integer.class, value);
        }
        catch(ClassCastException cce) {
            if (value instanceof String) {
                String strValue = ((String) value).trim();
                try {
                    return Integer.parseInt(strValue);
                } catch (NumberFormatException e) {
                    throw new JSONException("Could not parse Integer from String: \"" + strValue + "\"", e);
                }
            }
        }
        throw new JSONException("Key \""+key+"\" does not refer to an Integer.");
    }

    public int getInt(String key) throws JSONException{
        return JSONTypedObject.getInt(this, key);
    }

    static long getLong(ValueGetter valGet, Object key) throws JSONException{
        JSONTypedObject.checkKeyPresenceAndValueNotNull(valGet, key);
        Object value = valGet.get(key);
        assert(value != null);

        try {
            return (Long)GenericFactory.smartCast(Long.class, value);
        }
        catch(ClassCastException cce) {
            if (value instanceof String) {
                String strValue = ((String) value).trim();
                try {
                    return Long.parseLong(strValue);
                } catch (NumberFormatException e) {
                    throw new JSONException("Could not parse Long from String: \"" + strValue + "\"", e);
                }
            }
        }

        throw new JSONException("Key \""+key+"\" does not refer to a Long.");
    }

    public long getLong(String key) throws JSONException{
        return JSONTypedObject.getLong(this, key);
    }

    static String getString(ValueGetter valGet, Object key) throws JSONException{
        JSONTypedObject.checkKeyPresenceAndValueNotNull(valGet, key);
        Object value = valGet.get(key);
        assert(value != null);

        if(value instanceof String){
            return (String) value;
        }

        throw new JSONException("Key \""+key+"\" does not refer to a String.");
    }

    public String getString(String key) throws JSONException{
        return JSONTypedObject.getString(this, key);
    }

    static JSONTypedArray getJSONTypedArray(ValueGetter valGet, Object key) throws JSONException{
        JSONTypedObject.checkKeyPresenceAndValueNotNull(valGet, key);
        Object value = valGet.get(key);
        assert(value != null);

        if(value instanceof JSONTypedArray){
            return (JSONTypedArray) value;
        }

        throw new JSONException("Key \""+key+"\" does not refer to a JSONTypedArray.");
    }

    public JSONTypedArray getJSONTypedArray(String key) throws JSONException{
        return JSONTypedObject.getJSONTypedArray(this, key);
    }

    static JSONTypedObject getJSONTypedObject(ValueGetter valGet, Object key) throws JSONException{
        JSONTypedObject.checkKeyPresenceAndValueNotNull(valGet, key);
        Object value = valGet.get(key);
        assert(value != null);

        if(value instanceof JSONTypedObject){
            return (JSONTypedObject) value;
        }

        throw new JSONException("Key \""+key+"\" does not refer to a JSONTypedObject.");
    }

    public JSONTypedObject getJSONTypedObject(String key) throws JSONException{
        return JSONTypedObject.getJSONTypedObject(this, key);
    }

    public boolean isNull(String key) throws JSONException{
        JSONTypedObject.checkKeyPresence(this, key);
        return this.get(key) == null;
    }
}
