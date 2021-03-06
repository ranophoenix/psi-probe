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

package com.googlecode.psiprobe.beans.stats.collectors;

import com.googlecode.psiprobe.beans.ContainerWrapperBean;
import com.googlecode.psiprobe.model.ApplicationResource;
import com.googlecode.psiprobe.model.DataSourceInfo;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The Class DatasourceStatsCollectorBean.
 *
 * @author Mark Lewis
 */
public class DatasourceStatsCollectorBean extends AbstractStatsCollectorBean {

  /** The Constant PREFIX_ESTABLISHED. */
  private static final String PREFIX_ESTABLISHED = "ds.est.";
  
  /** The Constant PREFIX_BUSY. */
  private static final String PREFIX_BUSY = "ds.busy.";

  /** The logger. */
  private final Log logger = LogFactory.getLog(DatasourceStatsCollectorBean.class);
  
  /** The container wrapper. */
  private ContainerWrapperBean containerWrapper;

  /**
   * Gets the container wrapper.
   *
   * @return the container wrapper
   */
  public ContainerWrapperBean getContainerWrapper() {
    return containerWrapper;
  }

  /**
   * Sets the container wrapper.
   *
   * @param containerWrapper the new container wrapper
   */
  public void setContainerWrapper(ContainerWrapperBean containerWrapper) {
    this.containerWrapper = containerWrapper;
  }

  @Override
  public void collect() throws Exception {
    long currentTime = System.currentTimeMillis();
    if (containerWrapper == null) {
      logger.error("Cannot collect data source stats. Container wrapper is not set.");
    } else {
      for (ApplicationResource ds : getContainerWrapper().getDataSources()) {
        String appName = ds.getApplicationName();
        String name = (appName == null ? "" : appName) + "/" + ds.getName();
        DataSourceInfo dsi = ds.getDataSourceInfo();
        int numEstablished = dsi.getEstablishedConnections();
        int numBusy = dsi.getBusyConnections();
        logger.trace("Collecting stats for datasource: " + name);
        buildAbsoluteStats(PREFIX_ESTABLISHED + name, numEstablished, currentTime);
        buildAbsoluteStats(PREFIX_BUSY + name, numBusy, currentTime);
      }
      logger.debug("datasource stats collected in " + (System.currentTimeMillis() - currentTime)
          + "ms");
    }
  }

  /**
   * Reset.
   *
   * @throws Exception the exception
   */
  public void reset() throws Exception {
    if (containerWrapper == null) {
      logger.error("Cannot reset application stats. Container wrapper is not set.");
    } else {
      for (ApplicationResource ds : getContainerWrapper().getDataSources()) {
        reset(ds.getName());
      }
    }
  }

  /**
   * Reset.
   *
   * @param name the name
   * @throws Exception the exception
   */
  public void reset(String name) throws Exception {
    resetStats(PREFIX_ESTABLISHED + name);
    resetStats(PREFIX_BUSY + name);
  }

}
