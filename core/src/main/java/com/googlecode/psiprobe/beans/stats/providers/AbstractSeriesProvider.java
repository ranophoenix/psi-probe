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

package com.googlecode.psiprobe.beans.stats.providers;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;

import java.util.List;

/**
 * The Class AbstractSeriesProvider.
 *
 * @author Vlad Ilyushchenko
 * @author Andy Shapoval
 */
public abstract class AbstractSeriesProvider implements SeriesProvider {

  /** The logger. */
  protected Log logger = LogFactory.getLog(getClass());

  /**
   * To series.
   *
   * @param legend the legend
   * @param stats the stats
   * @return the XY series
   */
  protected XYSeries toSeries(String legend, List<XYDataItem> stats) {
    XYSeries xySeries = new XYSeries(legend, true, false);
    synchronized (stats) {
      for (XYDataItem item : stats) {
        xySeries.addOrUpdate(item.getX(), item.getY());
      }
    }
    return xySeries;
  }

}
