/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.tf.drivers;

import gs.utils.drivers.AbstractDriver;
import gs.tf.core.ClosedMultiTaskChain;
import gs.tf.core.NotStartedException;
import gs.tf.factories.JSONFactory;
import gs.utils.Logging;
import gs.utils.json.JSONTypedArray;
import gs.utils.json.JSONTypedObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.logging.*;


// TODO Synchronized GenericDemux...
// TODO In general, better, more meaningful exceptions?
// TODO Check which methods may be made final

// TODO: Generic type information gets lost due to creation via reflection!
//       -> Hard code GenericMulti* classes with certain generic types in order to ensure type safety at object building time

public class JSONClosedMultiTaskDriver extends AbstractDriver<String> {

    private static final Logger ROOT_LOGGER = Logger.getLogger("");
    private static final Logger LOGGER = Logger.getLogger(JSONClosedMultiTaskDriver.class.getName());
    private final List<ClosedMultiTaskChain> CLOSED_MULTI_TASK_CHAIN_LIST = Collections.synchronizedList(new LinkedList<>());

    // No need for volatile - accessed only via synchronization on SHUTDOWN_HOOK_LOCK
    private boolean m_shutdownHookEntered = false;
    private final Object SHUTDOWN_HOOK_LOCK = new Object();

    private volatile Thread m_mainThread;
    private volatile ManualResetLogManager m_logManager;
    private volatile String m_inputPath;

    // This custom log manager make sure that it does not reset until resetFinally() called manually
    // (reset() is usually called by a logger's own shutdown hook and therefore interferes with logger calls
    // during our own shutdown hook)
    public static class ManualResetLogManager extends LogManager {

        @Override
        public void reset() {
            // Do nothing
        }

        void resetFinally() {
            super.reset();
        }
    }

    @Override
    protected void initialize2(String jsonPath){
        if(jsonPath == null){
            throw new NullPointerException("Missing driver config json file path argument.");
        }
        {
            String loggingManager = System.getProperty("java.util.logging.manager");
            if (loggingManager == null ||
                    !loggingManager.equals(ManualResetLogManager.class.getName())) {
                throw new IllegalArgumentException("The system property \"java.util.logging.manager\" must be set to " +
                        "\"" + ManualResetLogManager.class.getName() + "\" before startup or during startup via the \"-D\" " +
                        "vm parameter, i.e.: '-Djava.util.logging.manager=\"" + ManualResetLogManager.class.getName() + "\"'.");
            }
        }
        if(LogManager.getLogManager() instanceof ManualResetLogManager){
            m_logManager = (ManualResetLogManager)LogManager.getLogManager();
            assert(m_logManager != null);
        }
        else{
            throw new IllegalStateException("Should not happen.");
        }

        this.m_inputPath = jsonPath;
        Runtime.getRuntime().addShutdownHook(new Thread(){
            @Override
            public void run(){
                synchronized (SHUTDOWN_HOOK_LOCK){
                    m_shutdownHookEntered = true;
                }
                // Interrupt main()-Thread
                m_mainThread.interrupt();

                if(CLOSED_MULTI_TASK_CHAIN_LIST.size() > 0){
                    ExecutorService executorService = CLOSED_MULTI_TASK_CHAIN_LIST.iterator().next().getExecutorService();
                    assert (executorService != null);
                    LOGGER.severe("Unexpected program termination. Attempting to stop running closed multi task chains.");
                    for (ClosedMultiTaskChain cmt : CLOSED_MULTI_TASK_CHAIN_LIST) {
                        assert(cmt.getExecutorService() == executorService);
                        if(cmt.isRunning()) {
                            try {
                                cmt.terminate(true);
                            } catch (NotStartedException e) {
                                throw new IllegalStateException("Should not happen", e);
                            }
                            while (!cmt.isTerminated()) {
                                Thread.yield();
                            }
                        }
                        else{
                            LOGGER.severe(cmt.getClass().getName()+" not even started.");
                        }
                    }
                    assert(!executorService.isShutdown() && !executorService.isTerminated());
                    executorService.shutdown();
                }
                m_logManager.resetFinally();
                // This is done by log manager's reset
               /* for(Handler h : ROOT_LOGGER.getHandlers()){
                    h.flush();
                    h.close();
                }*/
            }
        });
    }

    private void createFileFolder(String filePath){
        File directory = new File(Paths.get(filePath).getParent().toString());
        if(!directory.exists()){
            if(!directory.mkdirs()){
                throw new RuntimeException(new IOException("Could not create directories: "+directory));
            }
        }
    }

    @Override
    protected void execute2() {
        m_mainThread = Thread.currentThread();

        byte[] readAllBytes;
        try {
            readAllBytes = java.nio.file.Files.readAllBytes(Paths.get(this.m_inputPath));
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
        String json_string = new String(readAllBytes);
        JSONTypedObject jsonObject = new JSONTypedObject(json_string);
        JSONTypedArray jsonArray = jsonObject.getJSONTypedArray("root_configs");
        if(jsonArray.length() == 0){
            throw new IllegalArgumentException("No root configs provided.");
        }
        String[] rootConfigs = new String[jsonArray.length()];
        for(int i = 0; i < rootConfigs.length; i++){
            rootConfigs[i] = jsonArray.getString(i);
        }

        int rootConfigNo = 1;
        String transceiverStructureJSON;
        long runFor, sleepInterval, currentSleepInterval, sleptFor;
        JSONFactory factory;

        for(String rootConfig : rootConfigs) {
            synchronized (SHUTDOWN_HOOK_LOCK) {
                if (m_shutdownHookEntered) {
                    LOGGER.severe("Aborting due to premature shutdown hook execution.");
                    return;
                }

                if (rootConfig == null) {
                    throw new NullPointerException();
                }

                try {
                    readAllBytes = java.nio.file.Files.readAllBytes(Paths.get(rootConfig));
                } catch (IOException e) {
                    throw new IllegalArgumentException(e);
                }

                json_string = new String(readAllBytes);
                jsonObject = new JSONTypedObject(json_string);

                Level logLevel = Level.parse(jsonObject.getString("logger_level"));
                ROOT_LOGGER.setLevel(logLevel);
                for (Handler h : ROOT_LOGGER.getHandlers()) {
                    ROOT_LOGGER.removeHandler(h);
                    h.flush();
                    h.close();
                }
                ConsoleHandler consoleH = new ConsoleHandler();
                consoleH.setLevel(logLevel);
                consoleH.setFilter(new Logging.LevelFilter(Level.CONFIG, Level.SEVERE));
                consoleH.setFormatter(new SimpleFormatter());
                ROOT_LOGGER.addHandler(consoleH);

                String belowInfoLogFile = jsonObject.getString("below_info_log_file");
                String infoLogFile = jsonObject.getString("info_log_file");
                String warningAndAboveLogFile = jsonObject.getString("warning_and_above_log_file");

                try {
                    this.createFileFolder(belowInfoLogFile);
                    FileHandler belowInfoFH = new FileHandler(belowInfoLogFile, false);
                    belowInfoFH.setFilter(new Logging.LevelFilter(Level.FINEST, Level.CONFIG));
                    belowInfoFH.setFormatter(new SimpleFormatter());

                    this.createFileFolder(infoLogFile);
                    FileHandler infoFH = new FileHandler(infoLogFile, false);
                    infoFH.setFilter(new Logging.LevelFilter(Level.INFO, Level.INFO));
                    infoFH.setFormatter(new SimpleFormatter());

                    this.createFileFolder(warningAndAboveLogFile);
                    FileHandler warnAndAboveFH = new FileHandler(warningAndAboveLogFile, false);
                    warnAndAboveFH.setFilter(new Logging.LevelFilter(Level.WARNING, Level.SEVERE));
                    warnAndAboveFH.setFormatter(new SimpleFormatter());
                    ROOT_LOGGER.addHandler(belowInfoFH);
                    ROOT_LOGGER.addHandler(infoFH);
                    ROOT_LOGGER.addHandler(warnAndAboveFH);
                }
                catch (IOException e){
                    throw new RuntimeException(e);
                }

                LOGGER.info("Config " + rootConfigNo + "/" + rootConfigs.length + ": New main iteration with root config path: " + rootConfig);

                transceiverStructureJSON = jsonObject.getString("transceiver_structure_json");
                runFor = jsonObject.getLong("run_for");

                sleepInterval = jsonObject.getLong("main_sleep_interval");

                // TODO Create functionality to abort via key
                if (runFor <= 0) {
                    throw new IllegalArgumentException();
                }


                try {
                    factory = new JSONFactory(transceiverStructureJSON);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

                if (factory.getClosedMultiTaskChainNames().size() == 0) {
                    throw new IllegalArgumentException();
                }
            }

            for (String cmtName : factory.getClosedMultiTaskChainNamesList()) {
                synchronized (SHUTDOWN_HOOK_LOCK){
                    if(m_shutdownHookEntered){
                        LOGGER.severe("Aborting due to premature shutdown hook execution.");
                        return;
                    }
                    else {
                        CLOSED_MULTI_TASK_CHAIN_LIST.add(factory.createClosedMultiTaskChain(cmtName));
                    }
                }
            }
            assert(CLOSED_MULTI_TASK_CHAIN_LIST.size() > 0);
            ExecutorService executorService = CLOSED_MULTI_TASK_CHAIN_LIST.iterator().next().getExecutorService();
            assert (executorService != null);

            LOGGER.info("Config "+rootConfigNo+"/"+rootConfigs.length+": Starting all closed multi task chains.");
            for (ClosedMultiTaskChain cmt : CLOSED_MULTI_TASK_CHAIN_LIST) {
                assert (cmt.getExecutorService() == executorService);
                synchronized (SHUTDOWN_HOOK_LOCK) {
                    if (m_shutdownHookEntered) {
                        LOGGER.severe("Aborting due to premature shutdown hook execution.");
                        return;
                    } else {
                        executorService.submit(cmt);
                        while (cmt.isNotStarted()) {
                            Thread.yield();
                        }
                    }
                }
            }
            LOGGER.info("Config "+rootConfigNo+"/"+rootConfigs.length+": All closed multi task chains started. Main driver going to sleep.");

            for (sleptFor = 0; sleptFor < runFor;) {
            	synchronized (SHUTDOWN_HOOK_LOCK) {
            		if (m_shutdownHookEntered) {
            			LOGGER.severe("Aborting due to premature shutdown hook execution.");
            			return;
            		}
            	}
                currentSleepInterval = Math.min(sleepInterval, runFor - sleptFor);
                try {
                    Thread.sleep(currentSleepInterval);
                } catch (InterruptedException e) {
                	synchronized (SHUTDOWN_HOOK_LOCK) {
                		if(!m_shutdownHookEntered){
                			throw new IllegalStateException("This should not have happened.");
                		}
                		else{
                			LOGGER.severe("Sleeping interrupted. Aborting due to premature shutdown hook execution.");
                			return;
                		}
                	}
                }

                sleptFor += currentSleepInterval;

                LOGGER.info("Config "+rootConfigNo+"/"+rootConfigs.length+": Main slept for " + sleptFor + " ms / " + runFor + " ms ("+
                        (1.0*Math.round((1.0 * sleptFor)/runFor * 10000)/100)+ " %).");
            }
            assert(sleptFor == runFor);
            synchronized (SHUTDOWN_HOOK_LOCK) {
            	if (m_shutdownHookEntered) {
            		LOGGER.severe("Aborting due to premature shutdown hook execution.");
            		return;
            	}
            }
            LOGGER.info("Config "+rootConfigNo+"/"+rootConfigs.length+": Main done with sleeping. Terminating all multi task chains.");
            for (ClosedMultiTaskChain cmt : CLOSED_MULTI_TASK_CHAIN_LIST) {
                synchronized (SHUTDOWN_HOOK_LOCK) {
                    if (m_shutdownHookEntered) {
                        LOGGER.severe("Aborting due to premature shutdown hook execution.");
                        return;
                    } else {
                        if (!cmt.isRunning()) {
                            throw new RuntimeException(new IllegalStateException());
                        }

                        try {
                            cmt.terminate(false);
                        }
                        catch (NotStartedException e){
                            throw new IllegalStateException(e);
                        }
                        while (!cmt.isTerminated()) {
                            Thread.yield();
                        }
                    }
                }
            }
            synchronized (SHUTDOWN_HOOK_LOCK) {
                if (m_shutdownHookEntered) {
                    LOGGER.severe("Aborting due to premature shutdown hook execution.");
                    return;
                } else {
                    executorService.shutdown();
                    LOGGER.info("Config " + rootConfigNo + "/" + rootConfigs.length + ": All closed multi task chains terminated and executor service is shutdown.");
                    CLOSED_MULTI_TASK_CHAIN_LIST.clear();

                    rootConfigNo++;
                }
            }
        }
    }
}
