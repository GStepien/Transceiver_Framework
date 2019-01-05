/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.utils.drivers;


public abstract class AbstractDriver<ARGS_TYPE> implements Driver<ARGS_TYPE> {
    private boolean m_initialized = false;
    private final Object INITIALIZED_LOCK = new Object();
    private ARGS_TYPE m_args = null;

    @Override
    public final boolean isInitialized(){
        synchronized (INITIALIZED_LOCK){
            return this.m_initialized;
        }
    }

    @Override
    public ARGS_TYPE getArgs(){
        this.errorIfNotInitialized();
        return this.m_args;
    }

    @Override
    public final void initialize(ARGS_TYPE args){
        synchronized (INITIALIZED_LOCK) {
            this.errorIfInitialized();
            this.m_args = args;
            this.initialize2(args);
            this.m_initialized = true;
        }
    }

    @Override
    public final void execute(){
        this.errorIfNotInitialized();
        this.execute2();
    }

    protected final void errorIfNotInitialized(){
        synchronized (INITIALIZED_LOCK){
            if (!this.m_initialized) {
                throw new IllegalStateException("Instance not yet initialized.");
            }
        }
    }

    protected final void errorIfInitialized(){
        synchronized (INITIALIZED_LOCK){
            if (this.m_initialized) {
                throw new IllegalStateException("Instance already initialized.");
            }
        }
    }

    protected abstract void execute2();
    protected abstract void initialize2(ARGS_TYPE args);
}
