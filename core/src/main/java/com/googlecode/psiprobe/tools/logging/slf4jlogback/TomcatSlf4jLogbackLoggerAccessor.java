/*
 * Licensed under the GPL License. You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * THIS PACKAGE IS PROVIDED "AS IS" AND WITHOUT ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 * WITHOUT LIMITATION, THE IMPLIED WARRANTIES OF MERCHANTIBILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE.
 */

package com.googlecode.psiprobe.tools.logging.slf4jlogback;

import com.googlecode.psiprobe.tools.logging.DefaultAccessor;

import org.apache.commons.beanutils.MethodUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A wrapper for a TomcatSlf4jLogback logger.
 * 
 * @author Jeremy Landis
 */
public class TomcatSlf4jLogbackLoggerAccessor extends DefaultAccessor {

  /**
   * Returns all appenders of this logger.
   * 
   * @return a list of {@link TomcatSlf4jLogbackAppenderAccessor}s
   */
  public List<TomcatSlf4jLogbackAppenderAccessor> getAppenders() {
    List<TomcatSlf4jLogbackAppenderAccessor> appenders =
        new ArrayList<TomcatSlf4jLogbackAppenderAccessor>();

    try {
      Iterator<Object> it =
          (Iterator<Object>) MethodUtils.invokeMethod(getTarget(), "iteratorForAppenders", null);
      while (it.hasNext()) {
        Object appender = it.next();
        List<Object> siftedAppenders = getSiftedAppenders(appender);
        if (siftedAppenders != null) {
          for (Object siftedAppender : siftedAppenders) {
            wrapAndAddAppender(siftedAppender, appenders);
          }
        } else {
          wrapAndAddAppender(appender, appenders);
        }
      }
    } catch (Exception e) {
      log.error(getTarget().getClass().getName() + "#getAppenders() failed", e);
    }
    return appenders;
  }

  /**
   * Returns the appender of this logger with the given name.
   * 
   * @param name the name of the appender to return
   * @return the appender with the given name, or null if no such appender exists for this logger
   */
  public TomcatSlf4jLogbackAppenderAccessor getAppender(String name) {
    try {
      Object appender = MethodUtils.invokeMethod(getTarget(), "getAppender", name);
      if (appender == null) {
        List<TomcatSlf4jLogbackAppenderAccessor> appenders = getAppenders();
        for (TomcatSlf4jLogbackAppenderAccessor appender1 : appenders) {
          TomcatSlf4jLogbackAppenderAccessor wrappedAppender = appender1;
          if (wrappedAppender.getIndex().equals(name)) {
            return wrappedAppender;
          }
        }
      }
      return wrapAppender(appender);
    } catch (Exception e) {
      log.error(getTarget().getClass().getName() + "#getAppender() failed", e);
    }
    return null;
  }

  /**
   * Checks if is context.
   *
   * @return true, if is context
   */
  public boolean isContext() {
    return false;
  }

  /**
   * Checks if is root.
   *
   * @return true, if is root
   */
  public boolean isRoot() {
    return "ROOT".equals(getName());
  }

  /**
   * Gets the name.
   *
   * @return the name
   */
  public String getName() {
    return (String) getProperty(getTarget(), "name", null);
  }

  /**
   * Gets the log level of this logger.
   * 
   * @return the level of this logger
   */
  public String getLevel() {
    try {
      Object level = MethodUtils.invokeMethod(getTarget(), "getLevel", null);
      return (String) MethodUtils.invokeMethod(level, "toString", null);
    } catch (Exception e) {
      log.error(getTarget().getClass().getName() + "#getLevel() failed", e);
    }
    return null;
  }

  /**
   * Sets the log level of this logger.
   * 
   * @param newLevelStr the name of the new level
   */
  public void setLevel(String newLevelStr) {
    try {
      Object level = MethodUtils.invokeMethod(getTarget(), "getLevel", null);
      Object newLevel = MethodUtils.invokeMethod(level, "toLevel", newLevelStr);
      MethodUtils.invokeMethod(getTarget(), "setLevel", newLevel);
    } catch (Exception e) {
      log.error(getTarget().getClass().getName() + "#setLevel(\"" + newLevelStr + "\") failed", e);
    }
  }

  /**
   * Gets the sifted appenders.
   *
   * @param appender the appender
   * @return the sifted appenders
   * @throws Exception the exception
   */
  private List<Object> getSiftedAppenders(Object appender) throws Exception {
    if ("org.apache.juli.logging.ch.qos.logback.classic.sift.SiftingAppender".equals(appender
        .getClass().getName())) {

      Object tracker = MethodUtils.invokeMethod(appender, "getAppenderTracker", null);
      if (tracker != null) {
        return (List<Object>) MethodUtils.invokeMethod(tracker, "valueList", null);
      } else {
        return new ArrayList<Object>();
      }
    } else {
      return null;
    }
  }

  /**
   * Wrap and add appender.
   *
   * @param appender the appender
   * @param appenders the appenders
   */
  private void wrapAndAddAppender(Object appender,
      List<TomcatSlf4jLogbackAppenderAccessor> appenders) {

    TomcatSlf4jLogbackAppenderAccessor appenderAccessor = wrapAppender(appender);
    if (appenderAccessor != null) {
      appenders.add(appenderAccessor);
    }
  }

  /**
   * Wrap appender.
   *
   * @param appender the appender
   * @return the tomcat slf4j logback appender accessor
   */
  private TomcatSlf4jLogbackAppenderAccessor wrapAppender(Object appender) {
    try {
      if (appender == null) {
        throw new IllegalArgumentException("appender is null");
      }
      TomcatSlf4jLogbackAppenderAccessor appenderAccessor =
          new TomcatSlf4jLogbackAppenderAccessor();

      appenderAccessor.setTarget(appender);
      appenderAccessor.setLoggerAccessor(this);
      appenderAccessor.setApplication(getApplication());
      return appenderAccessor;
    } catch (Exception e) {
      log.error("Could not wrap appender: " + appender, e);
    }
    return null;
  }

}
