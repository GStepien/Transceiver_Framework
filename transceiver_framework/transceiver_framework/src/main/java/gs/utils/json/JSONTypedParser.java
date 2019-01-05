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

public class JSONTypedParser {

    private final JSONParser m_jsonParser;

    public JSONTypedParser(){
        this.m_jsonParser = new JSONParser();
    }

    public JSONTypedObject parseJSONObject(String jsonString) throws JSONException {
        Object result;
        try {
            result = this.m_jsonParser.parse(jsonString);
        } catch (ParseException e) {
            throw new JSONException(e);
        }

        if(!(result instanceof JSONObject)){
            throw new JSONException("JSON parser did return object of type: "+result.getClass().getName());
        }
        else{
            return new JSONTypedObject((JSONObject)result);
        }
    }

    public JSONTypedArray parseJSONArray(String jsonString) throws JSONException {
        Object result;
        try {
            result = this.m_jsonParser.parse(jsonString);
        } catch (ParseException e) {
            throw new JSONException(e);
        }

        if(!(result instanceof JSONArray)){
            throw new JSONException("JSON parser did return object of type: "+result.getClass().getName());
        }
        else{
            return new JSONTypedArray((JSONArray) result);
        }
    }
}
