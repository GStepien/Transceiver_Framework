/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.tf.factories;

import gs.tf.core.ClosedMultiTaskChain;
import gs.tf.core.ConcurrentTask;
import gs.tf.core.MultiTask;
import gs.utils.GenericFactory;
import org.apache.commons.collections4.list.UnmodifiableList;
import org.apache.commons.collections4.map.UnmodifiableMap;
import org.apache.commons.collections4.set.UnmodifiableSet;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

public abstract class AbstractFactory<INPUT_CONFIG_TYPE, CONFIG_TYPE> implements Factory<CONFIG_TYPE>{

    private final CONFIG_TYPE m_config;

    private final UnmodifiableSet<String> m_concurrentTaskNames;
    private final UnmodifiableSet<String> m_multiTaskNames;
    private final UnmodifiableSet<String> m_closedMultiTaskChainNames;
    private final UnmodifiableSet<String> m_otherObjectNames;

    private final UnmodifiableList<String> m_concurrentTaskNamesList;
    private final UnmodifiableList<String> m_multiTaskNamesList;
    private final UnmodifiableList<String> m_closedMultiTaskChainNamesList;
    private final UnmodifiableList<String> m_otherObjectNamesList;

    private final UnmodifiableMap<String, String> m_nameToClassnameMap;
    // Pair holds: right: Static method name that creates object associated with the key. Left: The class containing that method.
    private final UnmodifiableMap<String, ImmutablePair<String, String>> m_nameToStaticCreatorMap;
    private final UnmodifiableMap<String, String[]> m_nameToConstrArgClassnamesMap;

    public AbstractFactory(INPUT_CONFIG_TYPE inConfig) throws Exception{
        CONFIG_TYPE config = this.createConfigFromInConfig(inConfig);
        if(config == null){
            throw new NullPointerException();
        }
        this.m_config = config;

        List<String> concurrentTaskNames =  this.createConcurrentTaskNamesFromConfig(this.m_config);
        if(concurrentTaskNames == null){
            throw new NullPointerException();
        }
        else{
            for(String entry : concurrentTaskNames){
                if(entry == null){
                    throw new NullPointerException();
                }
            }
        }
        this.m_concurrentTaskNamesList = (UnmodifiableList<String>) UnmodifiableList.unmodifiableList(new ArrayList<>(concurrentTaskNames));
        this.m_concurrentTaskNames = (UnmodifiableSet<String>) UnmodifiableSet.unmodifiableSet(new HashSet<>(concurrentTaskNames));

        List<String> multiTaskNames =  this.createMultiTaskNamesFromConfig(this.m_config);
        if(multiTaskNames == null){
            throw new NullPointerException();
        }
        else{
            for(String entry : multiTaskNames){
                if(entry == null){
                    throw new NullPointerException();
                }
            }
        }
        this.m_multiTaskNamesList = (UnmodifiableList<String>) UnmodifiableList.unmodifiableList(new ArrayList<>(multiTaskNames));
        this.m_multiTaskNames = (UnmodifiableSet<String>) UnmodifiableSet.unmodifiableSet(new HashSet<>(multiTaskNames));

        List<String> otherObjectNames =  this.createOtherObjectNamesFromConfig(this.m_config);
        if(otherObjectNames == null){
            throw new NullPointerException();
        }
        else{
            for(String entry : otherObjectNames){
                if(entry == null){
                    throw new NullPointerException();
                }
            }
        }
        this.m_otherObjectNamesList = (UnmodifiableList<String>) UnmodifiableList.unmodifiableList(new ArrayList<>(otherObjectNames));
        this.m_otherObjectNames = (UnmodifiableSet<String>) UnmodifiableSet.unmodifiableSet(new HashSet<>(otherObjectNames));

        List<String> closedMultiTaskChainNames =  this.createClosedMultiTaskChainNameFromConfig(this.m_config);
        if(closedMultiTaskChainNames == null){
            throw new NullPointerException();
        }
        else{
            for(String entry : closedMultiTaskChainNames){
                if(entry == null){
                    throw new NullPointerException();
                }
            }
        }
        this.m_closedMultiTaskChainNamesList = (UnmodifiableList<String>) UnmodifiableList.unmodifiableList(new ArrayList<>(closedMultiTaskChainNames));
        this.m_closedMultiTaskChainNames = (UnmodifiableSet<String>) UnmodifiableSet.unmodifiableSet(new HashSet<>(closedMultiTaskChainNames));

        final int numObjects = this.m_otherObjectNames.size() +
                this.m_concurrentTaskNames.size() +
                this.m_multiTaskNames.size() +
                this.m_closedMultiTaskChainNames.size();

        Set<String> allNames = new HashSet<>();
        allNames.addAll(this.m_concurrentTaskNames);
        allNames.addAll(this.m_multiTaskNames);
        allNames.addAll(this.m_closedMultiTaskChainNames);
        allNames.addAll(this.m_otherObjectNames);

        assert(allNames.size() <= numObjects);
        if(allNames.size() != numObjects){
            throw new IllegalArgumentException("Object names overlap.");
        }

        Map<String, String> nameToClassnameMap = this.createNameToClassnameMapFromConfig(this.m_config);
        if(nameToClassnameMap == null){
            throw new NullPointerException();
        }
        else{
            for(String key : nameToClassnameMap.keySet()){
                if(key == null){
                    throw new NullPointerException();
                }
                else if(nameToClassnameMap.get(key) == null){
                    throw new NullPointerException();
                }
            }
        }
        if(!nameToClassnameMap.keySet().containsAll(allNames) ||
                !allNames.containsAll(nameToClassnameMap.keySet())){
            throw new IllegalArgumentException("Inconsistent or missing class names.");
        }
        this.m_nameToClassnameMap = (UnmodifiableMap<String, String>) UnmodifiableMap.unmodifiableMap(nameToClassnameMap);


        Map<String, ImmutablePair<String, String>> nameToStaticCreatorMap = this.createNameToStaticMethodMapFromConfig(this.m_config);
        if(nameToStaticCreatorMap == null){
            throw new NullPointerException();
        }
        else{
            for(String key : nameToStaticCreatorMap.keySet()){
                if(key == null){
                    throw new NullPointerException();
                }
                else if(nameToStaticCreatorMap.get(key) == null){
                    throw new NullPointerException();
                }
                else if(nameToStaticCreatorMap.get(key).getLeft() == null){
                    throw new NullPointerException();
                }
                else if(nameToStaticCreatorMap.get(key).getRight() == null){
                    throw new NullPointerException();
                }
            }
        }
        if(!allNames.containsAll(nameToStaticCreatorMap.keySet())){
            throw new IllegalArgumentException("Inconsistent or missing class names.");
        }
        this.m_nameToStaticCreatorMap = (UnmodifiableMap<String, ImmutablePair<String, String>>) UnmodifiableMap.unmodifiableMap(nameToStaticCreatorMap);

        Map<String, String[]> nameToConstrArgClassnamesMap = this.createNameToConstrArgClassnamesMapFromConfig(this.m_config);
        if(nameToConstrArgClassnamesMap == null){
            throw new NullPointerException();
        }
        else{
            for(String key : nameToConstrArgClassnamesMap.keySet()){
                if(key == null){
                    throw new NullPointerException();
                }
                else if(nameToConstrArgClassnamesMap.get(key) == null){
                    throw new NullPointerException();
                }

                for(String argclassname : nameToConstrArgClassnamesMap.get(key)){
                    if(argclassname == null){
                        throw new NullPointerException();
                    }
                }
            }
        }
        if(!nameToConstrArgClassnamesMap.keySet().containsAll(allNames) ||
            !allNames.containsAll(nameToConstrArgClassnamesMap.keySet())){
            throw new IllegalArgumentException("Inconsistent or missing class names.");
        }
        this.m_nameToConstrArgClassnamesMap = (UnmodifiableMap<String, String[]>) UnmodifiableMap.unmodifiableMap(nameToConstrArgClassnamesMap);
    }

    protected abstract CONFIG_TYPE createConfigFromInConfig(INPUT_CONFIG_TYPE inConfig) throws Exception;
    protected abstract List<String> createConcurrentTaskNamesFromConfig(CONFIG_TYPE config);
    protected abstract List<String> createMultiTaskNamesFromConfig(CONFIG_TYPE config);
    protected abstract List<String> createClosedMultiTaskChainNameFromConfig(CONFIG_TYPE config);
    protected abstract List<String> createOtherObjectNamesFromConfig(CONFIG_TYPE config);

    protected abstract Map<String, ImmutablePair<String,String>> createNameToStaticMethodMapFromConfig(CONFIG_TYPE config);
    protected abstract Map<String, String> createNameToClassnameMapFromConfig(CONFIG_TYPE config);
    protected abstract Map<String, String[]> createNameToConstrArgClassnamesMapFromConfig(CONFIG_TYPE config);

    protected abstract Object[] createConstructorArgsFor(String name, String[] argClassnames, CONFIG_TYPE config);

    @Override
    public Set<String> getConcurrentTaskNames() {
        return this.m_concurrentTaskNames;
    }

    @SuppressWarnings("unchecked")
    protected <T> T createObject(String name){
        Pair<String, String> staticCreator = this.m_nameToStaticCreatorMap.get(name);
        if(staticCreator != null){
            return (T)GenericFactory.createObject(staticCreator.getLeft(),
                    staticCreator.getRight(),
                    this.m_nameToConstrArgClassnamesMap.get(name),
                    this.createConstructorArgsFor(name, this.m_nameToConstrArgClassnamesMap.get(name), this.m_config));

        }
        else {
            return (T)GenericFactory.createObject(this.m_nameToClassnameMap.get(name),
                    this.m_nameToConstrArgClassnamesMap.get(name),
                    this.createConstructorArgsFor(name, this.m_nameToConstrArgClassnamesMap.get(name), this.m_config));

        }
    }

    @Override
    public ConcurrentTask createConcurrentTask(String name) {
        if(!this.m_concurrentTaskNames.contains(name)){
            throw new IllegalArgumentException();
        }
        return this.createObject(name);
    }

    @Override
    public List<String> getMultiTaskNamesList() {
        return this.m_multiTaskNamesList;
    }

    @Override
    public Set<String> getMultiTaskNames() {
        return this.m_multiTaskNames;
    }

    @Override
    public MultiTask<?> createMultiTask(String name) {
        if(!this.m_multiTaskNames.contains(name)) {
            throw new IllegalArgumentException();
        }
        return this.createObject(name);
    }

    @Override
    public List<String> getClosedMultiTaskChainNamesList() {
        return this.m_closedMultiTaskChainNamesList;
    }

    @Override
    public Set<String> getClosedMultiTaskChainNames() {
        return this.m_closedMultiTaskChainNames;
    }

    @Override
    public ClosedMultiTaskChain createClosedMultiTaskChain(String name) {
        if(!this.m_closedMultiTaskChainNames.contains(name)) {
            throw new IllegalArgumentException();
        }
        return this.createObject(name);
    }

    @Override
    public List<String> getOtherObjectNamesList() {
        return this.m_otherObjectNamesList;
    }

    @Override
    public Set<String> getOtherObjectNames(){
        return this.m_otherObjectNames;
    }

    @Override
    public ConcurrentTask createOtherObject(String name){
        if(!this.m_otherObjectNames.contains(name)) {
            throw new IllegalArgumentException();
        }
        return this.createObject(name);
    }

    @Override
    public List<String> getConcurrentTaskNamesList() {
        return this.m_concurrentTaskNamesList;
    }

    @Override
    public CONFIG_TYPE getConfiguration() {
        return this.m_config;
    }
}
