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

package com.googlecode.psiprobe.tools;

import com.googlecode.psiprobe.beans.ContainerWrapperBean;
import com.googlecode.psiprobe.beans.ResourceResolver;
import com.googlecode.psiprobe.model.Application;
import com.googlecode.psiprobe.model.ApplicationParam;
import com.googlecode.psiprobe.model.ApplicationResource;
import com.googlecode.psiprobe.model.ApplicationSession;
import com.googlecode.psiprobe.model.Attribute;
import com.googlecode.psiprobe.model.FilterInfo;
import com.googlecode.psiprobe.model.ServletInfo;
import com.googlecode.psiprobe.model.ServletMapping;

import net.sf.javainetlocator.InetAddressLocator;
import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Session;
import org.apache.catalina.Wrapper;
import org.apache.catalina.core.StandardWrapper;
import org.apache.catalina.deploy.FilterDef;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.ClassUtils;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.naming.NamingException;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;

/**
 * The Class ApplicationUtils.
 *
 * @author Vlad Ilyushchenko
 * @author Andy Shapoval
 * @author Mark Lewis
 */
public class ApplicationUtils {

  /** The logger. */
  private static final Log logger = LogFactory.getLog(ApplicationUtils.class);

  /**
   * Gets the application.
   *
   * @param context the context
   * @param containerWrapper the container wrapper
   * @return the application
   */
  public static Application getApplication(Context context, ContainerWrapperBean containerWrapper) {
    return getApplication(context, null, false, containerWrapper);
  }

  /**
   * Creates Application instance from Tomcat Context object. If ResourceResolver is passed the
   * method will also collect additional information about the application such as session count,
   * session attribute count, application attribute count, servlet count, servlet stats summary and
   * datasource usage summary. Collecting additional information can be CPU intensive and time
   * consuming so this should be avoided unless absolutely required. Some datasource implementations
   * (c3p0) are known to be prone to internal deadlocks, so this method can also hang is datasource
   * usage stats is to be collected.
   *
   * @param context the context from which to create the Application
   * @param resourceResolver the resolver to use for resources associated with the given context
   * @param calcSize flag which controls whether to calculate session size
   * @param containerWrapper the wrapper for the context's root containing server
   * @return Application object
   */
  public static Application getApplication(Context context, ResourceResolver resourceResolver,
      boolean calcSize, ContainerWrapperBean containerWrapper) {

    // ContainerWrapperBean containerWrapper
    logger.debug("Querying webapp: " + context.getName());

    Application app = new Application();
    app.setName(context.getName().length() > 0 ? context.getName() : "/");
    app.setDocBase(context.getDocBase());
    app.setDisplayName(context.getDisplayName());

    app.setAvailable(containerWrapper.getTomcatContainer().getAvailable(context));
    app.setDistributable(context.getDistributable());
    app.setSessionTimeout(context.getSessionTimeout());
    app.setServletVersion(context.getServletContext().getMajorVersion() + "."
        + context.getServletContext().getMinorVersion());

    if (resourceResolver != null) {
      logger.debug("counting servlet attributes");

      int ctxAttrCount = 0;
      for (Enumeration e = context.getServletContext().getAttributeNames(); e.hasMoreElements(); e
          .nextElement()) {
        ctxAttrCount++;
      }
      app.setContextAttributeCount(ctxAttrCount);

      if (app.isAvailable()) {
        logger.debug("collecting session information");

        app.setSessionCount(context.getManager().findSessions().length);

        boolean serializable = true;
        int sessionAttributeCount = 0;
        long size = 0;

        for (Session session : context.getManager().findSessions()) {
          ApplicationSession appSession = getApplicationSession(session, calcSize, false);
          if (appSession != null) {
            sessionAttributeCount += appSession.getObjectCount();
            serializable = serializable && appSession.isSerializable();
            size += appSession.getSize();
          }
        }
        app.setSerializable(serializable);
        app.setSessionAttributeCount(sessionAttributeCount);
        app.setSize(size);
      }

      logger.debug("aggregating servlet stats");

      collectApplicationServletStats(context, app);

      if (resourceResolver.supportsPrivateResources() && app.isAvailable()) {
        int[] scores =
            getApplicationDataSourceUsageScores(context, resourceResolver, containerWrapper);
        app.setDataSourceBusyScore(scores[0]);
        app.setDataSourceEstablishedScore(scores[1]);
      }
    }

    return app;
  }

  /**
   * Calculates Sum of requestCount, errorCount and processingTime for all servlets for the given
   * application. It also works out minimum value of minTime and maximum value for maxTime for all
   * servlets.
   *
   * @param context the context whose stats will be collected
   * @param app the application in which to store the collected stats
   */
  public static void collectApplicationServletStats(Context context, Application app) {
    int svltCount = 0;
    int reqCount = 0;
    int errCount = 0;
    long procTime = 0;
    long minTime = Long.MAX_VALUE;
    long maxTime = 0;

    for (Container container : context.findChildren()) {
      if (container instanceof StandardWrapper) {
        StandardWrapper sw = (StandardWrapper) container;
        svltCount++;
        reqCount += sw.getRequestCount();
        errCount += sw.getErrorCount();
        procTime += sw.getProcessingTime();
        if (sw.getRequestCount() > 0) {
          minTime = Math.min(minTime, sw.getMinTime());
        }
        maxTime = Math.max(maxTime, sw.getMaxTime());
      }
    }
    app.setServletCount(svltCount);
    app.setRequestCount(reqCount);
    app.setErrorCount(errCount);
    app.setProcessingTime(procTime);
    app.setMinTime(minTime == Long.MAX_VALUE ? 0 : minTime);
    app.setMaxTime(maxTime);
  }

  /**
   * Gets the application data source usage scores.
   *
   * @param context the context
   * @param resolver the resolver
   * @param containerWrapper the container wrapper
   * @return the application data source usage scores
   */
  public static int[] getApplicationDataSourceUsageScores(Context context,
      ResourceResolver resolver, ContainerWrapperBean containerWrapper) {

    logger.debug("Calculating datasource usage score");

    int[] scores = new int[] {0, 0};
    List<ApplicationResource> appResources;
    try {
      appResources = resolver.getApplicationResources(context, containerWrapper);
    } catch (NamingException e) {
      throw new RuntimeException(e);
    }
    for (ApplicationResource appResource : appResources) {
      if (appResource.getDataSourceInfo() != null) {
        scores[0] = Math.max(scores[0], appResource.getDataSourceInfo().getBusyScore());
        scores[1] = Math.max(scores[1], appResource.getDataSourceInfo().getEstablishedScore());
      }
    }
    return scores;
  }

  /**
   * Gets the application session.
   *
   * @param session the session
   * @param calcSize the calc size
   * @param addAttributes the add attributes
   * @return the application session
   */
  public static ApplicationSession getApplicationSession(Session session, boolean calcSize,
      boolean addAttributes) {

    ApplicationSession sbean = null;
    if (session != null && session.isValid()) {
      sbean = new ApplicationSession();

      sbean.setId(session.getId());
      sbean.setCreationTime(new Date(session.getCreationTime()));
      sbean.setLastAccessTime(new Date(session.getLastAccessedTime()));
      sbean.setMaxIdleTime(session.getMaxInactiveInterval() * 1000);
      sbean.setManagerType(session.getManager().getClass().getName());
      // sbean.setInfo(session.getInfo());
      // TODO:fixmee

      boolean sessionSerializable = true;
      int attributeCount = 0;
      long size = 0;

      HttpSession httpSession = session.getSession();
      Set<Object> processedObjects = new HashSet<Object>(1000);

      // Exclude references back to the session itself
      processedObjects.add(httpSession);
      try {
        for (Enumeration e = httpSession.getAttributeNames(); e.hasMoreElements();) {
          String name = (String) e.nextElement();
          Object obj = httpSession.getAttribute(name);
          sessionSerializable = sessionSerializable && obj instanceof Serializable;

          long objSize = 0;
          if (calcSize) {
            try {
              objSize += Instruments.sizeOf(name, processedObjects);
              objSize += Instruments.sizeOf(obj, processedObjects);
            } catch (Throwable th) {
              logger.error("Cannot estimate size of attribute \"" + name + "\"", th);
              //
              // make sure we always re-throw ThreadDeath
              //
              if (e instanceof ThreadDeath) {
                throw (ThreadDeath) e;
              }
            }
          }

          if (addAttributes) {
            Attribute saBean = new Attribute();
            saBean.setName(name);
            saBean.setType(ClassUtils.getQualifiedName(obj.getClass()));
            saBean.setValue(obj);
            saBean.setSize(objSize);
            saBean.setSerializable(obj instanceof Serializable);
            sbean.addAttribute(saBean);
          }
          attributeCount++;
          size += objSize;
        }
        String lastAccessedIp =
            (String) httpSession.getAttribute(ApplicationSession.LAST_ACCESSED_BY_IP);
        if (lastAccessedIp != null) {
          sbean.setLastAccessedIp(lastAccessedIp);
        }
        try {
          sbean.setLastAccessedIpLocale(InetAddressLocator.getLocale(InetAddress.getByName(
              lastAccessedIp).getAddress()));
        } catch (Throwable e) {
          logger.error("Cannot determine Locale of " + lastAccessedIp);
          //
          // make sure we always re-throw ThreadDeath
          //
          if (e instanceof ThreadDeath) {
            throw (ThreadDeath) e;
          }
        }


      } catch (IllegalStateException e) {
        logger.info("Session appears to be invalidated, ignore");
      }

      sbean.setObjectCount(attributeCount);
      sbean.setSize(size);
      sbean.setSerializable(sessionSerializable);
    }

    return sbean;
  }

  /**
   * Gets the application attributes.
   *
   * @param context the context
   * @return the application attributes
   */
  public static List<Attribute> getApplicationAttributes(Context context) {
    List<Attribute> attrs = new ArrayList<Attribute>();
    ServletContext servletCtx = context.getServletContext();
    for (Enumeration e = servletCtx.getAttributeNames(); e.hasMoreElements();) {
      String attrName = (String) e.nextElement();
      Object attrValue = servletCtx.getAttribute(attrName);

      Attribute attr = new Attribute();
      attr.setName(attrName);
      attr.setValue(attrValue);
      attr.setType(ClassUtils.getQualifiedName(attrValue.getClass()));
      attrs.add(attr);
    }
    return attrs;
  }

  /**
   * Gets the application init params.
   *
   * @param context the context
   * @param containerWrapper the container wrapper
   * @return the application init params
   */
  public static List<ApplicationParam> getApplicationInitParams(Context context,
      ContainerWrapperBean containerWrapper) {
    
    return containerWrapper.getTomcatContainer().getApplicationInitParams(context);
  }

  /**
   * Gets the application servlet.
   *
   * @param context the context
   * @param servletName the servlet name
   * @return the application servlet
   */
  public static ServletInfo getApplicationServlet(Context context, String servletName) {
    Container container = context.findChild(servletName);

    if (container instanceof Wrapper) {
      Wrapper wrapper = (Wrapper) container;
      return getServletInfo(wrapper, context.getName());
    } else {
      return null;
    }
  }

  /**
   * Gets the servlet info.
   *
   * @param wrapper the wrapper
   * @param contextName the context name
   * @return the servlet info
   */
  private static ServletInfo getServletInfo(Wrapper wrapper, String contextName) {
    ServletInfo si = new ServletInfo();
    si.setApplicationName(contextName.length() > 0 ? contextName : "/");
    si.setServletName(wrapper.getName());
    si.setServletClass(wrapper.getServletClass());
    si.setAvailable(!wrapper.isUnavailable());
    si.setLoadOnStartup(wrapper.getLoadOnStartup());
    si.setRunAs(wrapper.getRunAs());
    si.getMappings().addAll(Arrays.asList(wrapper.findMappings()));
    if (wrapper instanceof StandardWrapper) {
      StandardWrapper sw = (StandardWrapper) wrapper;
      si.setAllocationCount(sw.getCountAllocated());
      si.setErrorCount(sw.getErrorCount());
      si.setLoadTime(sw.getLoadTime());
      si.setMaxInstances(sw.getMaxInstances());
      si.setMaxTime(sw.getMaxTime());
      si.setMinTime(sw.getMinTime() == Long.MAX_VALUE ? 0 : sw.getMinTime());
      si.setProcessingTime(sw.getProcessingTime());
      si.setRequestCount(sw.getRequestCount());
      si.setSingleThreaded(sw.isSingleThreadModel());
    }
    return si;
  }

  /**
   * Gets the application servlets.
   *
   * @param context the context
   * @return the application servlets
   */
  public static List<ServletInfo> getApplicationServlets(Context context) {
    Container[] cns = context.findChildren();
    List<ServletInfo> servlets = new ArrayList<ServletInfo>(cns.length);
    for (Container container : cns) {
      if (container instanceof Wrapper) {
        Wrapper wrapper = (Wrapper) container;
        servlets.add(getServletInfo(wrapper, context.getName()));
      }
    }
    return servlets;
  }

  /**
   * Gets the application servlet maps.
   *
   * @param context the context
   * @return the application servlet maps
   */
  public static List<ServletMapping> getApplicationServletMaps(Context context) {
    String[] sms = context.findServletMappings();
    List<ServletMapping> servletMaps = new ArrayList<ServletMapping>(sms.length);
    for (String servletMapping : sms) {
      if (servletMapping != null) {
        String sn = context.findServletMapping(servletMapping);
        if (sn != null) {
          ServletMapping sm = new ServletMapping();
          sm.setApplicationName(context.getName().length() > 0 ? context.getName() : "/");
          sm.setUrl(servletMapping);
          sm.setServletName(sn);
          Container container = context.findChild(sn);
          if (container instanceof Wrapper) {
            Wrapper wrapper = (Wrapper) container;
            sm.setServletClass(wrapper.getServletClass());
            sm.setAvailable(!wrapper.isUnavailable());
          }
          servletMaps.add(sm);
        }
      }
    }
    return servletMaps;
  }

  /**
   * Gets the application filter.
   *
   * @param context the context
   * @param filterName the filter name
   * @return the application filter
   */
  public static FilterInfo getApplicationFilter(Context context, String filterName) {
    FilterDef fd = context.findFilterDef(filterName);
    if (fd != null) {
      return getFilterInfo(fd);
    } else {
      return null;
    }
  }

  /**
   * Gets the filter info.
   *
   * @param fd the fd
   * @return the filter info
   */
  private static FilterInfo getFilterInfo(FilterDef fd) {
    FilterInfo fi = new FilterInfo();
    fi.setFilterName(fd.getFilterName());
    fi.setFilterClass(fd.getFilterClass());
    fi.setFilterDesc(fd.getDescription());
    return fi;
  }

  /**
   * Gets the application filters.
   *
   * @param context the context
   * @param containerWrapper the container wrapper
   * @return the application filters
   */
  public static List getApplicationFilters(Context context, ContainerWrapperBean containerWrapper) {
    return containerWrapper.getTomcatContainer().getApplicationFilters(context);
  }

}
