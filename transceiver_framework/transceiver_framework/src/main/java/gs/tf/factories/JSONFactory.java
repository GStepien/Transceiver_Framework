/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.tf.factories;

import gs.utils.GenericFactory;
import gs.utils.Structure;
import gs.utils.json.JSONException;
import gs.utils.json.JSONTypedArray;
import gs.utils.json.JSONTypedObject;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Paths;
import java.util.*;

public class JSONFactory extends AbstractFactory<String, JSONTypedObject> {
    private JSONTypedObject m_otherObjects;
    private JSONTypedObject m_concurrentTasks;
    private JSONTypedObject m_multiTasks;
    private JSONTypedObject m_closedMultiTaskChains;

    private Map<String, JSONTypedObject> m_namesToJSONTypedObjectMap;

    private Map<String, Object> m_namesToReusableObjectsMap;

    public JSONFactory(String json_path) throws Exception {
        super(json_path);

        if(this.checkForCycles()){
            throw new JSONException("Circular reference dependency detected.");
        }
    }

    private boolean checkForCycles() throws JSONException{
        assert(this.m_otherObjects != null);
        assert(this.m_concurrentTasks != null);
        assert(this.m_multiTasks != null);
        assert(this.m_closedMultiTaskChains != null);
        assert(this.m_namesToJSONTypedObjectMap != null);

        Map<String, List<String>> dependencyMap = new HashMap<>();

        Set<JSONTypedObject> jsonObjects = new HashSet<>();
        jsonObjects.add(this.m_otherObjects);
        jsonObjects.add(this.m_concurrentTasks);
        jsonObjects.add(this.m_multiTasks);
        jsonObjects.add(this.m_closedMultiTaskChains);

        Set<String> keys = new HashSet<>();
        keys.addAll(this.m_otherObjects.keySet());
        keys.addAll(this.m_concurrentTasks.keySet());
        keys.addAll(this.m_multiTasks.keySet());
        keys.addAll(this.m_closedMultiTaskChains.keySet());

        // Build dependency map:
        JSONTypedObject obj, arg, creation, map_value;
        JSONTypedArray argTypes, args, reference_array, number_array, value_array;
        String argCreationType, reference, ref_name;
        List<String> dependencyList;
        Set<String> dependencySet;
        Object tempObj;
        int num;
        for(JSONTypedObject jsonObj : jsonObjects) {
            for (String key : jsonObj.keySet()) {
                obj = jsonObj.getJSONTypedObject(key);
                creation = obj.getJSONTypedObject("creation");
                argTypes = creation.getJSONTypedArray("argument_types");
                args = creation.getJSONTypedArray("arguments");
                if(argTypes.length() != args.length()){
                    throw new JSONException("Inconsistent argument lengths.");
                }
                for(int argNo = 0; argNo < args.length(); argNo++){
                    arg = args.getJSONTypedObject(argNo);
                    argCreationType = arg.getString("type");
                    switch (argCreationType){
                        case "constant": {
                            continue;
                        }
                        case "reference": {
                            dependencyList = new LinkedList<>();
                            reference = arg.getString("value");
                            if(!keys.contains(reference)){
                                throw new JSONException("Unknown reference name encountered: "+reference);
                            }
                            dependencyList.add(arg.getString("value"));
                            break;
                        }
                        case "reference_obj_array" : {
                            // Treated like "reference_list" - no break
                        }
                        case "reference_list": {
                            dependencySet = new HashSet<>();
                            dependencyList = new LinkedList<>();
                            reference_array = arg.getJSONTypedArray("values");
                            number_array = arg.getJSONTypedArray("numbers");
                            if(reference_array.length() != number_array.length()){
                                throw new JSONException("Inconsistent reference list length.");
                            }
                            for(int entryNo = 0; entryNo < reference_array.length(); entryNo++){
                                tempObj = number_array.get(entryNo);
                                if(tempObj instanceof String){
                                    try {
                                        num = this.<Integer>createObject((String) tempObj);
                                    } catch (Exception e) {
                                        throw new JSONException(e);
                                    }
                                    if(!dependencySet.contains(tempObj)) {
                                        dependencyList.add((String)tempObj);
                                        dependencySet.add((String)tempObj);
                                    }
                                }
                                else{ // tempObj instanceof Long or Integer
                                    num = number_array.getInt(entryNo);
                                }

                                if(num <= 0){
                                    throw new JSONException("Non-positive number provided.");
                                }

                                reference = reference_array.getString(entryNo);
                                if(!keys.contains(reference)){
                                    throw new JSONException("Unknown reference name encountered: "+reference);
                                }

                                if(!dependencySet.contains(reference)) {
                                    dependencyList.add(reference);
                                    dependencySet.add(reference);
                                }
                            }
                            break;
                        }
                        case "reference_map": {
                            dependencySet = new HashSet<>();
                            dependencyList = new LinkedList<>();
                            reference_array = arg.getJSONTypedArray("value");
                            for (int entryNo = 0; entryNo < reference_array.length(); entryNo++) {
                                map_value = reference_array.getJSONTypedObject(entryNo);
                                if(map_value.keySet().size() != 1){
                                    throw new JSONException("Map entry must have only one key.");
                                }
                                ref_name = map_value.keySet().iterator().next();

                                if (!keys.contains(ref_name)) {
                                    throw new JSONException("Unknown reference name encountered: " + ref_name);
                                }
                                if (!dependencySet.contains(ref_name)) {
                                    dependencyList.add(ref_name);
                                    dependencySet.add(ref_name);
                                }

                                value_array = map_value.getJSONTypedArray(ref_name);
                                for (int mapValueNo = 0; mapValueNo < value_array.length(); mapValueNo++) {
                                    reference = value_array.getString(mapValueNo);
                                    if (!keys.contains(reference)) {
                                        throw new JSONException("Unknown reference name encountered: " + reference);
                                    }
                                    if (!dependencySet.contains(reference)) {
                                        dependencyList.add(reference);
                                        dependencySet.add(reference);
                                    }
                                }
                            }
                            break;
                        }
                        default: {
                            throw new JSONException("Unknown argument creation type: " + argCreationType);
                        }
                    }
                    dependencyMap.put(key, dependencyList);
                }
            }
        }

        return Structure.hasCycles(dependencyMap);
    }

    @Override
    protected JSONTypedObject createConfigFromInConfig(String json_path) throws Exception {
        if(json_path == null){
            throw new NullPointerException();
        }

        byte[] readAllBytes;
        try {
            readAllBytes = java.nio.file.Files.readAllBytes(Paths.get(json_path));
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }

        String json_string = new String(readAllBytes);
        JSONTypedObject result = new JSONTypedObject(json_string);

        this.m_otherObjects = result.getJSONTypedObject("other_objects");
        this.m_concurrentTasks = result.getJSONTypedObject("concurrent_tasks");
        this.m_multiTasks = result.getJSONTypedObject("multi_tasks");
        this.m_closedMultiTaskChains = result.getJSONTypedObject("closed_multi_task_chain");
        if(this.m_closedMultiTaskChains.keySet().size() < 1){
            throw new JSONException("At least one key-value pair expected in \"closed_multi_task_chain\" object.");
        }
        Set<String> keySet = new HashSet<>();
        keySet.addAll(this.m_otherObjects.keySet());
        keySet.addAll(this.m_concurrentTasks.keySet());
        keySet.addAll(this.m_multiTasks.keySet());
        keySet.addAll(this.m_closedMultiTaskChains.keySet());

        final int keyNum = this.m_otherObjects.keySet().size() +
                this.m_concurrentTasks.keySet().size() +
                this.m_multiTasks.keySet().size() +
                this.m_closedMultiTaskChains.keySet().size();
        if(keySet.size() != keyNum){
            throw new JSONException("Overlapping object key names.");
        }

        this.m_namesToJSONTypedObjectMap = new HashMap<>();
        for(String key : this.m_otherObjects.keySet()){
            assert(!this.m_namesToJSONTypedObjectMap.keySet().contains(key));
            this.m_namesToJSONTypedObjectMap.put(key, this.m_otherObjects);
        }
        for(String key : this.m_concurrentTasks.keySet()){
            assert(!this.m_namesToJSONTypedObjectMap.keySet().contains(key));
            this.m_namesToJSONTypedObjectMap.put(key, this.m_concurrentTasks);
        }
        for(String key : this.m_multiTasks.keySet()){
            assert(!this.m_namesToJSONTypedObjectMap.keySet().contains(key));
            this.m_namesToJSONTypedObjectMap.put(key, this.m_multiTasks);
        }
        for(String key : this.m_closedMultiTaskChains.keySet()){
            assert(!this.m_namesToJSONTypedObjectMap.keySet().contains(key));
            this.m_namesToJSONTypedObjectMap.put(key, this.m_closedMultiTaskChains);
        }

        this.m_namesToReusableObjectsMap = new HashMap<>();

        return result;
    }

    @Override
    protected List<String> createConcurrentTaskNamesFromConfig(JSONTypedObject config) {
        assert(this.m_concurrentTasks != null);
        return new ArrayList<>(new TreeSet<>(this.m_concurrentTasks.keySet()));
    }

    @Override
    protected List<String> createMultiTaskNamesFromConfig(JSONTypedObject config) {
        assert(this.m_multiTasks != null);
        return  new ArrayList<>(new TreeSet<>(this.m_multiTasks.keySet()));
    }

    @Override
    protected List<String> createClosedMultiTaskChainNameFromConfig(JSONTypedObject config){
        assert(this.m_closedMultiTaskChains != null);
        return new ArrayList<>(new TreeSet<>(this.m_closedMultiTaskChains.keySet()));
    }

    @Override
    protected List<String> createOtherObjectNamesFromConfig(JSONTypedObject config) {
        assert(this.m_otherObjects != null);
        return  new ArrayList<>(new TreeSet<>(this.m_otherObjects.keySet()));
    }

    @Override
    protected Map<String, ImmutablePair<String, String>> createNameToStaticMethodMapFromConfig(JSONTypedObject config) {
        Map<String, ImmutablePair<String, String>> result = new HashMap<>();

        Set<JSONTypedObject> jsonObjects = new HashSet<>();
        jsonObjects.add(this.m_otherObjects);
        jsonObjects.add(this.m_concurrentTasks);
        jsonObjects.add(this.m_multiTasks);
        jsonObjects.add(this.m_closedMultiTaskChains);

        JSONTypedObject creationJSON;
        String creationType, classname, methodName;
        for(JSONTypedObject jsonObj : jsonObjects) {
            for (String key : jsonObj.keySet()) {
                creationJSON = jsonObj.getJSONTypedObject(key).getJSONTypedObject("creation");
                creationType = creationJSON.getString("type");
                switch (creationType){
                    case "static_method": {
                        classname = creationJSON.getString("method_location");
                        methodName = creationJSON.getString("methodname");
                        result.put(key, new ImmutablePair<>(classname, methodName));
                        break;
                    }
                    case "constructor": {
                        break;
                    }
                    default: {
                        throw new JSONException("Unknown creation type: "+creationType);
                    }
                }
            }
        }

        return result;
    }

    @Override
    protected Map<String, String> createNameToClassnameMapFromConfig(JSONTypedObject config) {
        Map<String, String> result = new HashMap<>();

        Set<JSONTypedObject> jsonObjects = new HashSet<>();
        jsonObjects.add(this.m_otherObjects);
        jsonObjects.add(this.m_concurrentTasks);
        jsonObjects.add(this.m_multiTasks);
        jsonObjects.add(this.m_closedMultiTaskChains);

        for(JSONTypedObject jsonObj : jsonObjects) {
            for (String key : jsonObj.keySet()) {
                result.put(key, jsonObj.getJSONTypedObject(key).getString("classname"));
            }
        }
        return result;
    }

    @Override
    protected Map<String, String[]> createNameToConstrArgClassnamesMapFromConfig(JSONTypedObject config) {
        Map<String, String[]> result = new HashMap<>();

        Set<JSONTypedObject> jsonObjects = new HashSet<>();
        jsonObjects.add(this.m_otherObjects);
        jsonObjects.add(this.m_concurrentTasks);
        jsonObjects.add(this.m_multiTasks);
        jsonObjects.add(this.m_closedMultiTaskChains);

        for(JSONTypedObject jsonObj : jsonObjects) {
            for (String key : jsonObj.keySet()) {
                JSONTypedArray array = jsonObj.getJSONTypedObject(key).getJSONTypedObject("creation").getJSONTypedArray("argument_types");
                List<String> argtypenames = new ArrayList<>(array.length());
                for (int argNo = 0; argNo < array.length(); argNo++) {
                    argtypenames.add(array.getString(argNo));
                }
                result.put(key, argtypenames.toArray(new String[0]));
            }
        }
        return result;
    }

    @Override
    protected Object[] createConstructorArgsFor(String name, String[] argClassnames, JSONTypedObject config){
        if(name == null || argClassnames == null || config == null){
            throw new NullPointerException();
        }

        assert(this.m_namesToJSONTypedObjectMap != null);
        if(!this.m_namesToJSONTypedObjectMap.keySet().contains(name)){
            throw new JSONException("Unknown object type.");
        }
        try {
            JSONTypedObject objectCreationConfig = this.m_namesToJSONTypedObjectMap.get(name).getJSONTypedObject(name).getJSONTypedObject("creation");
            JSONTypedArray argTypes = objectCreationConfig.getJSONTypedArray("argument_types");
            JSONTypedArray argValues = objectCreationConfig.getJSONTypedArray("arguments");
            JSONTypedObject arg, mapEntry;
            JSONTypedArray numbers, referenceArray, entryTypeArray, mapArray;
            Class<?> argClass, nonPrimitiveClass, entryClass, keyClass, valueClass;
            String argCreationType, referenceName;
            List<Object> entryList;
            Map<Object, List<Object>> entryMap;
            assert(argClassnames.length == argTypes.length());
            Object[] result = new Object[argClassnames.length];
            int num;
            Object tempObj, keyObject;
            Constructor<?> constr;
            if(argTypes.length() != argValues.length()){
                throw new JSONException("Argument and argument type lengths do not match for :"+name);
            }

            boolean asObjArray;

            for(int argNo = 0; argNo < argClassnames.length; argNo++){
                assert(argClassnames[argNo].equals(argTypes.getString(argNo)));
                arg = argValues.getJSONTypedObject(argNo);
                argClass = GenericFactory.getClass(argClassnames[argNo]);
                argCreationType = arg.getString("type");
                asObjArray = false;
                switch (argCreationType){
                    case "constant": {
                        if(argClass.isPrimitive()){
                            nonPrimitiveClass = GenericFactory.getWrapperClassFromPrimitiveName(argClass.getName());
                            constr = Objects.requireNonNull(nonPrimitiveClass).getConstructor(argClass);
                            result[argNo] = constr.newInstance(
                                    GenericFactory.smartCast(argClass, arg.get("value")));
                        }
                        else {
                            if(arg.isNull("value")){
                                result[argNo] = null;
                            }
                            else {
                                result[argNo] = arg.get("value");
                            }
                        }
                        break;
                    }
                    case "reference": {
                        referenceName = arg.getString("value");
                        result[argNo] = this.getArgObject(referenceName, argClass);
                        break;
                    }
                    case "reference_obj_array" : {
                        asObjArray = true;
                    }
                    case "reference_list" : {
                        entryClass = Class.forName(arg.getString("entry_type"));
                        numbers = arg.getJSONTypedArray("numbers");
                        referenceArray = arg.getJSONTypedArray("values");

                        if(numbers.length() != referenceArray.length()){
                            throw new JSONException("Length of reference list and numbers does not match.");
                        }
                        entryList = new LinkedList<>();

                        for(int entryNo = 0; entryNo < numbers.length(); entryNo++){
                            referenceName = referenceArray.getString(entryNo);
                            tempObj = numbers.get(entryNo);
                            if(tempObj instanceof String){
                                try {
                                    num = this.<Integer>createObject((String) tempObj);
                                } catch (Exception e) {
                                    throw new JSONException(e);
                                }
                            }
                            else{
                                num = (Integer)GenericFactory.smartCast(Integer.class, tempObj);
                            }
                            if(num <= 0){
                                throw new JSONException("Non positive numbers entry encountered.");
                            }

                            for(int subEntryNo = 0; subEntryNo < num; subEntryNo++){
                                entryList.add(this.getArgObject(referenceName, entryClass));
                            }
                        }

                        if(!asObjArray){
                            result[argNo] = new ArrayList<>(entryList);
                        }
                        else {
                            result[argNo] = entryList.toArray();
                        }

                        break;
                    }
                    case "reference_map" : {
                        entryTypeArray = arg.getJSONTypedArray("entry_types");
                        if(entryTypeArray.length() != 2){
                            throw new JSONException("Map entry types must be of length 2.");
                        }
                        keyClass = Class.forName(entryTypeArray.getString(0));
                        valueClass = Class.forName(entryTypeArray.getString(1));
                        mapArray = arg.getJSONTypedArray("value");
                        entryMap = new HashMap<>();
                        for(int entryNo = 0; entryNo < mapArray.length(); entryNo++){
                            mapEntry = mapArray.getJSONTypedObject(entryNo);

                            if(mapEntry.keySet().size() != 1){
                                throw new JSONException("More than one key in map entry.");
                            }
                            referenceName = mapEntry.keySet().iterator().next();
                            referenceArray = mapEntry.getJSONTypedArray(referenceName);
                            keyObject = this.getArgObject(referenceName, keyClass);

                            entryList = new ArrayList<>(referenceArray.length());

                            for(int valueNo = 0; valueNo < referenceArray.length(); valueNo++){
                                entryList.add(this.getArgObject(referenceArray.getString(valueNo), valueClass));
                            }
                            entryMap.put(keyObject, entryList);
                        }
                        result[argNo] = entryMap;
                        break;
                    }
                    default: {
                        throw new JSONException("Unknown argument creation type: "+argCreationType);
                    }
                }

                if(argClass.isPrimitive()){
                    argClass = GenericFactory.getWrapperClassFromPrimitiveName(argClass.getName());
                }
                result[argNo] = Objects.requireNonNull(argClass).cast(result[argNo]); // Cast for implicit typecheck

            }

            return result;
        }
        catch(Exception e){
            throw new JSONException("Malformed JSON file.", e);
        }
    }

    private Object getArgObject(String reference_name, Class<?> expectedClass){
        if(reference_name == null || expectedClass == null){
            throw new NullPointerException();
        }

        JSONTypedObject objectJSON = this.m_namesToJSONTypedObjectMap.get(reference_name).getJSONTypedObject(reference_name);
        if(objectJSON == null){
            throw new JSONException("Unknown reference name: "+reference_name);
        }

        Class<?> objectClass;
        try {
            objectClass = Class.forName(objectJSON.getString("classname"));
        } catch (ClassNotFoundException e) {
            throw new JSONException("Unknown class.", e);
        }
        if(expectedClass.isPrimitive()){
            expectedClass = GenericFactory.getWrapperClassFromPrimitiveName(expectedClass.getName());
        }
        if(objectClass.isPrimitive()){
            objectClass = GenericFactory.getWrapperClassFromPrimitiveName(objectClass.getName());
        }

        if(!Objects.requireNonNull(expectedClass).isAssignableFrom(Objects.requireNonNull(objectClass))){ // object class subclass of expected class?
            throw new JSONException("Incompatible expected and actual types.");
        }

        boolean reusable_as_arg = objectJSON.getBoolean("reuse_as_arg");
        Object result;
        if(reusable_as_arg){
            if(this.m_namesToReusableObjectsMap.keySet().contains(reference_name)){
                result = this.m_namesToReusableObjectsMap.get(reference_name);
            }
            else{
                result = this.createObject(reference_name);
                this.m_namesToReusableObjectsMap.put(reference_name, result);
            }
        }
        else{
            assert(!this.m_namesToReusableObjectsMap.keySet().contains(reference_name));
            result = this.createObject(reference_name);
        }

        objectClass.cast(result); // Implicit typecheck
        // At this point guaranteed: result class extends object class which extends expected class
        return result;
    }
}
