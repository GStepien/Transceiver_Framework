/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.utils;

import java.util.logging.Filter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class Logging {

    private static final Logger LOGGER = Logger.getLogger(Logging.class.getName());

    public static class LevelFilter implements Filter{

        private final int m_minLevel;
        private final int m_maxLevel;

        public LevelFilter(Level minLevel, Level maxLevel){
            this(minLevel.intValue(), maxLevel.intValue());
        }

        public LevelFilter(int minLevel, int maxLevel){
            if(minLevel > maxLevel){
                LOGGER.log(Level.WARNING, "Minimal level bigger than maximal level. Filter filters everything.");
            }

            this.m_minLevel = minLevel;
            this.m_maxLevel = maxLevel;
        }

        @Override
        public boolean isLoggable(LogRecord record) {
            if(record == null){
                throw new NullPointerException();
            }

            int levelInt = record.getLevel().intValue();
            return levelInt >= this.m_minLevel && levelInt <= this.m_maxLevel;
        }
    }

    private Logging(){}

}
