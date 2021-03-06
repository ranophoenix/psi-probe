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

package com.googlecode.psiprobe;

import com.googlecode.psiprobe.tokenizer.StringTokenizer;
import com.googlecode.psiprobe.tokenizer.Token;
import com.googlecode.psiprobe.tokenizer.Tokenizer;
import com.googlecode.psiprobe.tokenizer.TokenizerSymbol;

import com.uwyn.jhighlight.renderer.Renderer;
import com.uwyn.jhighlight.renderer.XhtmlRendererFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.modeler.Registry;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Misc. static helper methods.
 * 
 * @author Vlad Ilyushchenko
 * @author Mark Lewis
 */
public class Utils {

  /** The logger. */
  private static Log logger = LogFactory.getLog(Utils.class.getName());

  /**
   * Calc pool usage score.
   *
   * @param max the max
   * @param value the value
   * @return the int
   */
  public static int calcPoolUsageScore(int max, int value) {
    return max > 0 ? Math.max(0, value) * 100 / max : 0;
  }

  /**
   * Reads a file on disk. The method uses default file encoding (see: file.encoding system
   * property)
   *
   * @param file to be read
   * @param charsetName the charset name
   * @return String representation of the file contents
   * @throws IOException Signals that an I/O exception has occurred.
   */
  public static String readFile(File file, String charsetName) throws IOException {
    String result = null;
    FileInputStream fis = new FileInputStream(file);
    try {
      result = readStream(fis, charsetName);
    } finally {
      fis.close();
    }
    return result;
  }

  /**
   * Reads strings from the intput stream using the given charset. This method closes the input
   * stream after it has been consumed.
   * 
   * <p>
   * This method uses the system's default charset if the given one is unsupported.
   * </p>
   *
   * @param is the stream from which to read
   * @param charsetName the charset to use when reading the stream
   * @return the contents of the given input stream
   * @throws IOException if reading from the stream fails spectacularly
   */
  public static String readStream(InputStream is, String charsetName) throws IOException {

    //
    // use system's default encoding if the passed encoding is unsupported
    //
    Charset charset = Charset.forName(System.getProperty("file.encoding"));
    if (Charset.isSupported(charsetName)) {
      charset = Charset.forName(charsetName);
    }

    StringBuffer out = new StringBuffer();
    BufferedReader reader = new BufferedReader(new InputStreamReader(is, charset), 4096);
    try {
      String line;
      while ((line = reader.readLine()) != null) {
        out.append(line).append("\n");
      }
    } finally {
      reader.close();
    }

    return out.toString();
  }

  /**
   * Delete.
   *
   * @param file the file
   */
  public static void delete(File file) {
    if (file != null && file.exists()) {
      if (file.isDirectory()) {
        for (File child : file.listFiles()) {
          delete(child);
        }
      }
      if (!file.delete()) {
        logger.debug("Cannot delete " + file.getAbsolutePath());
      }
    } else {
      logger.debug(file + " does not exist");
    }
  }

  /**
   * To int.
   *
   * @param num the num
   * @param defaultValue the default value
   * @return the int
   */
  public static int toInt(String num, int defaultValue) {
    if (num != null) {
      try {
        return Integer.parseInt(num);
      } catch (NumberFormatException e) {
        // ignore
      }
    }
    return defaultValue;
  }

  /**
   * To int.
   *
   * @param num the num
   * @param defaultValue the default value
   * @return the int
   */
  public static int toInt(Integer num, int defaultValue) {
    return num == null ? defaultValue : num;
  }

  /**
   * To int hex.
   *
   * @param num the num
   * @param defaultValue the default value
   * @return the int
   */
  public static int toIntHex(String num, int defaultValue) {
    try {
      if (num != null && num.startsWith("#")) {
        num = num.substring(1);
      }
      return Integer.parseInt(num, 16);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  /**
   * To long.
   *
   * @param num the num
   * @param defaultValue the default value
   * @return the long
   */
  public static long toLong(String num, long defaultValue) {
    if (num != null) {
      try {
        return Long.parseLong(num);
      } catch (NumberFormatException e) {
        // ignore
      }
    }
    return defaultValue;
  }

  /**
   * To long.
   *
   * @param num the num
   * @param defaultValue the default value
   * @return the long
   */
  public static long toLong(Long num, long defaultValue) {
    return num == null ? defaultValue : num;
  }

  /**
   * To float.
   *
   * @param num the num
   * @param defaultValue the default value
   * @return the float
   */
  public static float toFloat(String num, float defaultValue) {
    if (num != null) {
      try {
        return Float.parseFloat(num);
      } catch (NumberFormatException e) {
        // ignore
      }
    }
    return defaultValue;
  }

  /**
   * Gets the jsp encoding.
   *
   * @param is the is
   * @return the jsp encoding
   * @throws IOException Signals that an I/O exception has occurred.
   */
  public static String getJspEncoding(InputStream is) throws IOException {

    String encoding = null;
    String contentType = null;

    Tokenizer jspTokenizer = new Tokenizer();
    jspTokenizer.addSymbol("\n", true);
    jspTokenizer.addSymbol(" ", true);
    jspTokenizer.addSymbol("\t", true);
    jspTokenizer.addSymbol(new TokenizerSymbol("dir", "<%@", "%>", false, false, true, false));

    StringTokenizer directiveTokenizer = new StringTokenizer();
    directiveTokenizer.addSymbol("\n", true);
    directiveTokenizer.addSymbol(" ", true);
    directiveTokenizer.addSymbol("\t", true);
    directiveTokenizer.addSymbol("=");
    directiveTokenizer.addSymbol("\"", "\"", false);
    directiveTokenizer.addSymbol("'", "'", false);

    StringTokenizer contentTypeTokenizer = new StringTokenizer();
    contentTypeTokenizer.addSymbol(" ", true);
    contentTypeTokenizer.addSymbol(";", true);


    Reader reader = new InputStreamReader(is, "ISO-8859-1");
    try {
      jspTokenizer.setReader(reader);
      while (jspTokenizer.hasMore()) {
        Token token = jspTokenizer.nextToken();
        if ("dir".equals(token.getName())) {
          directiveTokenizer.setString(token.getInnerText());
          if (directiveTokenizer.hasMore()
              && directiveTokenizer.nextToken().getText().equals("page")) {
            while (directiveTokenizer.hasMore()) {
              Token directiveToken = directiveTokenizer.nextToken();
              if ("pageEncoding".equals(directiveToken.getText())) {
                if (directiveTokenizer.hasMore()
                    && "=".equals(directiveTokenizer.nextToken().getText())) {
                  if (directiveTokenizer.hasMore()) {
                    encoding = directiveTokenizer.nextToken().getInnerText();
                    break;
                  }
                }
              } else if ("contentType".equals(directiveToken.getText())) {
                if (directiveTokenizer.hasMore()
                    && "=".equals(directiveTokenizer.nextToken().getText())) {
                  if (directiveTokenizer.hasMore()) {
                    contentType = directiveTokenizer.nextToken().getInnerText();
                  }
                }
              }
            }
          }
        }
      }
    } finally {
      reader.close();
    }

    if (encoding == null && contentType != null) {
      contentTypeTokenizer.setString(contentType);
      while (contentTypeTokenizer.hasMore()) {
        String token = contentTypeTokenizer.nextToken().getText();
        if (token.startsWith("charset=")) {
          encoding = token.substring("charset=".length());
          break;
        }
      }
    }

    return encoding != null ? encoding : "ISO-8859-1";
  }

  /**
   * Send file.
   *
   * @param request the request
   * @param response the response
   * @param file the file
   * @throws IOException Signals that an I/O exception has occurred.
   */
  public static void sendFile(HttpServletRequest request, HttpServletResponse response, File file)
      throws IOException {

    OutputStream out = response.getOutputStream();
    RandomAccessFile raf = new RandomAccessFile(file, "r");
    try {
      long fileSize = raf.length();
      long rangeStart = 0;
      long rangeFinish = fileSize - 1;

      // accept attempts to resume download (if any)
      String range = request.getHeader("Range");
      if (range != null && range.startsWith("bytes=")) {
        String pureRange = range.replaceAll("bytes=", "");
        int rangeSep = pureRange.indexOf("-");

        try {
          rangeStart = Long.parseLong(pureRange.substring(0, rangeSep));
          if (rangeStart > fileSize || rangeStart < 0) {
            rangeStart = 0;
          }
        } catch (NumberFormatException e) {
          // ignore the exception, keep rangeStart unchanged
        }

        if (rangeSep < pureRange.length() - 1) {
          try {
            rangeFinish = Long.parseLong(pureRange.substring(rangeSep + 1));
            if (rangeFinish < 0 || rangeFinish >= fileSize) {
              rangeFinish = fileSize - 1;
            }
          } catch (NumberFormatException e) {
            // ignore the exception
          }
        }
      }

      // set some headers
      response.setContentType("application/x-download");
      response.setHeader("Content-Disposition", "attachment; filename=" + file.getName());
      response.setHeader("Accept-Ranges", "bytes");
      response.setHeader("Content-Length", Long.toString(rangeFinish - rangeStart + 1));
      response.setHeader("Content-Range", "bytes " + rangeStart + "-" + rangeFinish + "/"
          + fileSize);

      // seek to the requested offset
      raf.seek(rangeStart);

      // send the file
      byte[] buffer = new byte[4096];

      long len;
      int totalRead = 0;
      boolean nomore = false;
      while (true) {
        len = raf.read(buffer);
        if (len > 0 && totalRead + len > rangeFinish - rangeStart + 1) {
          // read more then required?
          // adjust the length
          len = rangeFinish - rangeStart + 1 - totalRead;
          nomore = true;
        }

        if (len > 0) {
          out.write(buffer, 0, (int) len);
          totalRead += len;
          if (nomore) {
            break;
          }
        } else {
          break;
        }
      }
    } finally {
      raf.close();
    }
  }

  /**
   * Gets the thread by name.
   *
   * @param name the name
   * @return the thread by name
   */
  public static Thread getThreadByName(String name) {
    if (name != null) {
      // get top ThreadGroup
      ThreadGroup masterGroup = Thread.currentThread().getThreadGroup();
      while (masterGroup.getParent() != null) {
        masterGroup = masterGroup.getParent();
      }


      Thread[] threads = new Thread[masterGroup.activeCount()];
      int numThreads = masterGroup.enumerate(threads);

      for (int i = 0; i < numThreads; i++) {
        if (threads[i] != null && name.equals(threads[i].getName())) {
          return threads[i];
        }
      }
    }
    return null;
  }

  /**
   * Highlight stream.
   *
   * @param name the name
   * @param input the input
   * @param rendererName the renderer name
   * @param encoding the encoding
   * @return the string
   * @throws IOException Signals that an I/O exception has occurred.
   */
  public static String highlightStream(String name, InputStream input, String rendererName,
      String encoding) throws IOException {

    Renderer jspRenderer = XhtmlRendererFactory.getRenderer(rendererName);
    if (jspRenderer != null) {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      jspRenderer.highlight(name, input, bos, encoding, true);

      ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());

      Tokenizer tokenizer = new Tokenizer(new InputStreamReader(bis, encoding));
      tokenizer.addSymbol(new TokenizerSymbol("EOL", "\n", null, false, false, true, false));
      tokenizer.addSymbol(new TokenizerSymbol("EOL", "\r\n", null, false, false, true, false));

      //
      // JHighlight adds HTML comment as the first line, so if
      // we number the lines we could end up with a line number and no line
      // to avoid that we just ignore the first line alltogether.
      //
      StringBuffer buffer = new StringBuffer();
      long counter = 0;
      while (tokenizer.hasMore()) {
        Token tk = tokenizer.nextToken();
        if ("EOL".equals(tk.getName())) {
          counter++;
          buffer.append(tk.getText());
        } else if (counter > 0) {
          buffer.append("<span class=\"codeline\">");
          buffer.append("<span class=\"linenum\">");
          buffer.append(leftPad(Long.toString(counter), 6, "&nbsp;"));
          buffer.append("</span>");
          buffer.append(tk.getText());
          buffer.append("</span>");
        }
      }
      return buffer.toString();
    }
    return null;
  }

  /**
   * Left pad.
   *
   * @param str the str
   * @param len the len
   * @param fill the fill
   * @return the string
   */
  public static String leftPad(String str, int len, String fill) {
    StringBuffer sb = new StringBuffer(len);
    if (str.length() < len) {
      for (int i = str.length(); i < len; i++) {
        sb.append(fill);
      }
    }
    sb.append(str);
    return sb.toString();
  }

  /**
   * Gets the names for locale.
   *
   * @param baseName the base name
   * @param locale the locale
   * @return the names for locale
   */
  public static List<String> getNamesForLocale(String baseName, Locale locale) {
    List<String> result = new ArrayList<String>(3);
    String language = locale.getLanguage();
    String country = locale.getCountry();
    String variant = locale.getVariant();
    StringBuffer temp = new StringBuffer(baseName);

    if (language.length() > 0) {
      temp.append('_').append(language);
      result.add(0, temp.toString());
    }

    if (country.length() > 0) {
      temp.append('_').append(country);
      result.add(0, temp.toString());
    }

    if (variant.length() > 0) {
      temp.append('_').append(variant);
      result.add(0, temp.toString());
    }

    return result;
  }

  /**
   * Checks if is threading enabled.
   *
   * @return true, if is threading enabled
   */
  public static boolean isThreadingEnabled() {
    try {
      MBeanServer mbeanServer = new Registry().getMBeanServer();
      ObjectName threadingOName = new ObjectName("java.lang:type=Threading");
      Set<ObjectInstance> threading = mbeanServer.queryMBeans(threadingOName, null);
      return threading != null && threading.size() > 0;
    } catch (MalformedObjectNameException e) {
      return false;
    }
  }
}
