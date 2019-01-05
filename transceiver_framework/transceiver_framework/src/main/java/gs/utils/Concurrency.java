/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.utils;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

public class Concurrency {

    private static final Map<String, Long> s_nextID_map;

    static {
        s_nextID_map = new HashMap<>();
    }

    private Concurrency(){}

    public static synchronized long getNextID(String key){
        if(key == null){
            throw new NullPointerException();
        }
        else{
            if(!s_nextID_map.containsKey(key)){
                s_nextID_map.put(key, 0L);
            }

            assert(s_nextID_map.containsKey(key));
            long result = s_nextID_map.remove(key);
            s_nextID_map.put(key, (result+1L));
            return result;
        }
    }

    public static synchronized long getNextID(){
        return getNextID("");
    }

    public static long getMonitorOwner(Object obj) {
        if (Thread.holdsLock(obj)){
            return Thread.currentThread().getId();
        }

        for (java.lang.management.ThreadInfo ti :
                java.lang.management.ManagementFactory.getThreadMXBean()
                        .dumpAllThreads(true, false)) {
            for (java.lang.management.MonitorInfo mi : ti.getLockedMonitors()) {
                if (mi.getIdentityHashCode() == System.identityHashCode(obj)) {
                    return ti.getThreadId();
                }
            }
        }
        // No monitor on object
        return -1;
    }

    public interface ExceptionHandler {
        void handleException(Runnable r, Throwable t);
    }

    public static class PrintExceptionHandler implements ExceptionHandler{

        private final PrintStream m_out;

        public PrintExceptionHandler(PrintStream out){
            if(out == null){
                this.m_out = System.err;
            }
            else{
                this.m_out = out;
            }
        }

        @Override
        public void handleException(Runnable r, Throwable t) {
            if (t == null && r instanceof Future<?>) {
                try {
                    ((Future<?>) r).get();
                } catch (CancellationException ce) {
                    t = ce;
                } catch (ExecutionException ee) {
                    t = ee.getCause();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt(); // ignore/reset
                }
            }
            if (t != null) {
            	synchronized(this.m_out) {
            		t.printStackTrace(this.m_out);
            	}
            }
        }
    }

    public static class ExceptionHandlerThreadPoolExecutor extends ThreadPoolExecutor{

        public static ExceptionHandlerThreadPoolExecutor newCachedThreadPool(ExceptionHandler h){
            // Parameters copied over from Executors.newCachedThreadPool()
            ExceptionHandlerThreadPoolExecutor result =
                    new ExceptionHandlerThreadPoolExecutor(0, Integer.MAX_VALUE,
                            60L, TimeUnit.SECONDS,
                            new SynchronousQueue<>());

            result.setExceptionHandler(h);
            return result;
        }

        public static ExceptionHandlerThreadPoolExecutor newCachedThreadPoolExecutor(PrintStream out){
            return newCachedThreadPool(new PrintExceptionHandler(out));
        }

        public static ExceptionHandlerThreadPoolExecutor newCachedThreadPoolExecutor(){
            return newCachedThreadPool(new PrintExceptionHandler(null));
        }

        private ExceptionHandler m_exceptionHandler = null;
        private final ExceptionHandler m_fallbackExceptionHandler = new PrintExceptionHandler(null);

        public ExceptionHandlerThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
        }

        public ExceptionHandlerThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
        }

        public ExceptionHandlerThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
        }

        public ExceptionHandlerThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
        }

        public ExceptionHandler setExceptionHandler(ExceptionHandler h){
            ExceptionHandler old_h = this.m_exceptionHandler;
            this.m_exceptionHandler = h;
            return old_h;
        }

        public ExceptionHandler getExceptionHandler(){
            return this.m_exceptionHandler;
        }

        @Override
        protected void afterExecute(Runnable r, Throwable t) {
            super.afterExecute(r, t);
            if(this.m_exceptionHandler != null) {
                this.m_exceptionHandler.handleException(r, t);
            }
            else{
                this.m_fallbackExceptionHandler.handleException(r, t);
            }
        }
    }
}
