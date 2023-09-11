package nl.benjamins.exist.servlet;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jetty.http.HttpContent;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.http.ResourceHttpContent;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.resource.PathResource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

// A servlet filter for eXist-DB (https://github.com/eXist-db/exist) that serves static resources from Jetty,
// before the request reaches the eXist controller / router.
//
// Serving static resources from eXist can be inefficient, depending on the controller and URL rewriting you have.
// It also keeps eXist busy figuring out which static resources (images, CSS, ...) to serve, when it has better
// things to do like executing XQueries and interacting with the database.
// This servlet filter catches requests for static resouces and serves them from file, before these requests are
// passed on to the eXist servlet.
//
// How to use.
//
// - Compile this class into a jar-file.
// Note that the class is in the nl.benjamins.exist.servlet namespace, because I developed this for the
// Jonh Benjamins Publishing Company (https://www.benjamins.com/), who generously decided to provide this
// to the eXist-DB community.
// You may use another namespace if you want.
// - Put this jar-file on the class path, by copying the jar to the `lib` directory inside your eXist installation.
// - Edit the `web.xml` file in the `etc/webapp/WEB-INF` directory inside your eXist installation.
// Add the following inside the `web-app` element, just after `<display-name>eXist XML Database</display-name>`:
// ```
//   <filter>
//     <filter-name>static-resource</filter-name>
//     <filter-class>nl.benjamins.exist.servlet.JBResourceServletFilter</filter-class>
//     <init-param>
//       <param-name>loggerName</param-name>
//       <param-value>com.benjamins.resource-servlet</param-value>
//     </init-param>
//     <init-param>
//       <param-name>basePath</param-name>
//       <param-value>... the base path for all resource files ...</param-value>
//     </init-param>
//     <init-param>
//       <param-name>mappingsPath</param-name>
//       <param-value>... the path to the resource mappings file, relative to the base path ...</param-value>
//     </init-param>
//   </filter>
//
//   <filter-mapping>
//     <filter-name>static-resource</filter-name>
//     <url-pattern>/*</url-pattern>
//   </filter-mapping>
// ```
//
// The resource mappings are specified in a file, indicated by `mappingsPath`. This file must use the following format:
// ```
// <!-- Ignore some eXist apps that have their own static resources. -->
// <mappings ignore="^/(webstart|xmlrpc|webdav|status)">
//   <!-- Match an arbitrary path, followed by 'resources/' followed by 'one/', 'two/', or 'three/' followed by a resource file name.
//        Map this to a relative path 'one/', 'two/', or 'three/' followed by 'resources/' followed by the resource file name.
//   -->
//   <map url=".*/resources/(one|two|three)/(.*)" to="$1/resources/$2"/>
//   <!-- There can be multiple maps. The first one that matches @url will be used.
//        If @to for a map with matching @url does not point to an existing file, or there is no matching @url,
//        the next servlet (filter) will be used, which usually is the eXist servlet.
//   -->
//   <map url="..." to="..."/>
// </mappings>
// ```
//
// Before a request URL is matched with the mappings, the resource servlet checks if the mappings file has changed,
// but only if at least `REFRESH_MAPPINGS_MS` milliseconds have passed since the last check.
//
// The servlet filter logs to a logger with the name specified in `loggerName`. This should be configured in `etc/log4j2.xml`.
// For example:
// ```
// <RollingRandomAccessFile name="resource-servlet.log" filePattern="..." fileName="...">
//   <Policies>
//     <SizeBasedTriggeringPolicy size="10MB" />
//   </Policies>
//   <DefaultRolloverStrategy max="10" />
//   <PatternLayout pattern="%d{ISO8601} | %-5p | %m %n" />
// </RollingRandomAccessFile>
// ...
// <Logger name="com.benjamins.resource-servlet" additivity="false" level="trace">
//   <AppenderRef ref="resource-servlet.log" />
// </Logger>
// ```
// The Logger/@name is what has been specified in the loggerName init-param.


public class JBResourceServletFilter implements Filter
{

  @SuppressWarnings("unused")
  private static final long serialVersionUID = 1L;

  // Name of the logger.
  private static final String loggerNameDefault = "com.benjamins.resource-servlet";

  // Check if mappings have changed every REFRESH_MAPPINGS_MS milliseconds.
  private static final int REFRESH_MAPPINGS_MS = 10000;

  private Logger logger;
  private String loggerName;
  private String basePath;
  private String mappingsPath;
  private String mostRecentMessage;
  private int messageRepeats;
  private Pattern ignoreUrlPattern;
  private List<Pair<Pattern, String>> mappings;
  private long mappingsFileLastModified = 0L;
  private long whenToLoadMappingsAgain = 0L;

  public JBResourceServletFilter()
  {
  }

  @Override
  public void init(FilterConfig config) throws ServletException
  {
    // Get the log file name from the initialization parameter
    loggerName = config.getInitParameter("loggerName");
    if (loggerName == null) loggerName = loggerNameDefault;
    logger = Logger.getLogger(loggerName);

    // Get the base path from the initialization parameter
    basePath = config.getInitParameter("basePath");
    if (basePath == null)
    {
      throw new ServletException("Missing 'basePath' initialization parameter.");
    }
    if (!basePath.endsWith("/")) basePath = basePath + "/";

    // Get the mappings path from the initialization parameter
    mappingsPath = config.getInitParameter("mappingsPath");
    if (mappingsPath == null)
    {
      throw new ServletException("Missing 'mappingsPath' initialization parameter.");
    }

    // Read the mappings for the first time.
    readMappingsIfNeeded();

    log(Level.INFO, "JBResourceServletFilter: started.");
  }

  @Override
  public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
      throws IOException, ServletException
  {
    HttpServletRequest request = (HttpServletRequest) servletRequest;
    HttpServletResponse response = (HttpServletResponse) servletResponse;
    String urlPath = request.getRequestURI().substring(request.getContextPath().length());

    // If this URL is ignored, pass on immediately.
    if  (ignoreUrlPattern != null && ignoreUrlPattern.matcher(urlPath).matches()) {
      log(Level.INFO, urlPath, " is ignored and passed on.");
      chain.doFilter(request, response);
      return;
    }

    // Maybe the mappings should be read again.
    readMappingsIfNeeded();

    // Try to find the file that corresponds to this URL.
    File file = findFile(urlPath);
    if (file == null)
    {
      // If there is no resource file for this URL, pass on the request.
      chain.doFilter(request, response);
      return;
    }

    returnFileContent(urlPath, file, response);
  }

  @Override
  public void destroy()
  {
    log(Level.WARNING, "An instance of the JBResourceServletFilter has been destroyed.");
  }

  private synchronized void readMappingsIfNeeded() throws ServletException
  {
    // Wait some time before checking if mappings must be reloaded.
    if (System.currentTimeMillis() > whenToLoadMappingsAgain) {
      log(Level.INFO, "JBResourceServletFilter: Check if mappings should be read again.");

      whenToLoadMappingsAgain = System.currentTimeMillis() + REFRESH_MAPPINGS_MS;

      String mappingsFile = basePath + mappingsPath;
      File file = new File(mappingsFile);

      if (file.lastModified() > mappingsFileLastModified) {
        log(Level.WARNING, "JBResourceServletFilter: Reading resource mappings from [" + mappingsFile + "]");

        mappingsFileLastModified = file.lastModified();

        try
        {
          DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
          DocumentBuilder db = dbf.newDocumentBuilder();
          Document doc = db.parse(file);
          doc.getDocumentElement().normalize();

          String ignore = doc.getDocumentElement().getAttribute("ignore");
          if (ignore != null && ignore.length() > 0) {
            ignoreUrlPattern = Pattern.compile(ignore);
          } else {
            ignoreUrlPattern = null;
          }

          NodeList mapNodes = doc.getElementsByTagName("map");

          // getLength() gives a NPE sometimes.
          int mappingsLength;
          try {
            mappingsLength = mapNodes.getLength();
          } catch (Exception e) {
            log(Level.SEVERE, "JBResourceServletFilter: Computing mapNodes.getLength() gave a "+e.getClass().getName()+". No mappings are available.");
            mappings = null;
            return;
          }

          mappings = new ArrayList<Pair<Pattern,String>>(mappingsLength);

          for (int i = 0; i < mappingsLength; i++)
          {
            Node mappingNode = mapNodes.item(i);
            if (mappingNode.getNodeType() == Node.ELEMENT_NODE)
            {
              Element mappingElement = (Element) mappingNode;
              String regex = mappingElement.getAttribute("url");
              String filePath = mappingElement.getAttribute("to");
              Pattern pattern = Pattern.compile(regex);
              mappings.add(Pair.of(pattern, filePath));
            }
          }

          log(Level.WARNING, "JBResourceServletFilter: Finished reading " + mappings.size() + " / " + mappingsLength + " resource mappings from [" + mappingsFile + "]");
        }
        catch (ParserConfigurationException | SAXException | IOException e)
        {
          log(Level.SEVERE, "JBResourceServletFilter: Error parsing XML file " + mappingsFile + " : " + e.getMessage());
          throw new ServletException("Error parsing XML file " + mappingsFile, e);
        }
      }
    }
  }

  private synchronized File findFile(String urlPath) throws ServletException, IOException
  {
    if (mappings == null)
    {
      log(Level.SEVERE, urlPath, "No mappings have been read. No resources can be served.");
      return null;
    }

    for (int i = 0; i < mappings.size(); ++i)
    {
      Pair<Pattern, String> mapping = mappings.get(i);
      Pattern pattern = mapping.getLeft();
      String filePath = mapping.getRight();
      Matcher matcher = pattern.matcher(urlPath);
      if (matcher.matches())
      {
        // Substitute regex groups in the filePath
        for (int j = 1; j <= matcher.groupCount(); j++)
        {
          String group = matcher.group(j);
          if (group == null) group = "";
          filePath = filePath.replace("$" + j, group);
        }
        // Try to find the file at this filePath.
        File file = new File(basePath + filePath);
        if (file.exists() && file.isFile()) {
          log(Level.INFO, urlPath, "matches [" + pattern.pattern() + "] (" + (i+1) + ") which maps to [" + basePath + " " + filePath + "].");
          return file;
        } else {
          log(Level.WARNING, urlPath, "matches [" + pattern.pattern() + "] (" + (i+1) + ") which maps to [" + basePath + " " + filePath + "] but that is not a readable file.");
          return null;
        }
      }
    }
    // No pattern matched. Some other filter or servlet will handle this URL.
    log(Level.INFO, urlPath, "has no mapping and will be passed on.");
    return null;
  }

  private void returnFileContent(String urlPath, File file, HttpServletResponse response) throws ServletException, IOException
  {
// without Jetty:
//    response.setContentType(getMimeType(file));
//    response.setContentLength((int) file.length());

    PathResource resource = new PathResource(file);
    String mimeType = getMimeType(urlPath, file);
    HttpContent content = new ResourceHttpContent(resource, mimeType);
    Response.putHeaders(response, content, Response.USE_KNOWN_CONTENT_LENGTH, true);
    try (OutputStream out = response.getOutputStream())
    {
      Files.copy(file.toPath(), out);
    }
    if (resource != null) resource.close();
    if (content != null) content.release();
  }

  private String getMimeType(String urlPath, File file) throws IOException
  {
    String mimeType = MimeTypes.getDefaultMimeByExtension(file.getName());
    log(Level.INFO, urlPath, "File [" + file.getName() + "] has mime-type " + mimeType);
    return mimeType;
  }

  private void log(Level level, String message) {
    log(level, null, message);
  }

  private void log(Level level, String urlPath, String message) {
    if (urlPath != null) {
      message = "[" + urlPath + "] " + message;
    }
    if (message.equals(mostRecentMessage)) {
      ++messageRepeats;
      return;
    }
    if (messageRepeats > 0) {
      logger.info(messageRepeats+" more like the above.");
    }
    mostRecentMessage = message;
    messageRepeats = 0;
    if (Level.SEVERE.equals(level)) {
      logger.severe (message);
    } else if (Level.WARNING.equals(level)) {
      logger.warning(message);
    } else {
      logger.info(message);
    }
  }

}
