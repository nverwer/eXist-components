# eXist-db components

These components were developed for John Benjamins Publishing Company (JB).
Because there was some interest from the eXist community and because we at JB have had a lot of benefit from eXist-db, JB has made these components open source.

There are two projects in this repository, which are independent. You can use one or both by integrating them in your own project.
You can also compile the whole repo into one jar file and use that.

# Resource servlet filter

This is a servlet filter for eXist-DB (https://github.com/eXist-db/exist) that serves static resources from Jetty,
before the request reaches the eXist controller / router.

Serving static resources from eXist can be inefficient, depending on the controller and URL rewriting you have.
It also keeps eXist busy figuring out which static resources (images, CSS, ...) to serve, when it has better
things to do like executing XQueries and interacting with the database.
This servlet filter catches requests for static resouces and serves them from file, before these requests are
passed on to the eXist servlet.

How to use.

- Compile this class into a jar-file.
Note that the class is in the nl.benjamins.exist.servlet namespace, because I developed this for the
Jonh Benjamins Publishing Company (https://www.benjamins.com/), who generously decided to provide this
to the eXist-DB community.
You may use another namespace if you want.
- Put this jar-file on the class path, by copying the jar to the `lib` directory inside your eXist installation.
- Edit the `web.xml` file in the `etc/webapp/WEB-INF` directory inside your eXist installation.
Add the following inside the `web-app` element, just after `<display-name>eXist XML Database</display-name>`:
```
  <filter>
    <filter-name>static-resource</filter-name>
    <filter-class>nl.benjamins.exist.servlet.JBResourceServletFilter</filter-class>
    <init-param>
      <param-name>loggerName</param-name>
      <param-value>com.benjamins.resource-servlet</param-value>
    </init-param>
    <init-param>
      <param-name>basePath</param-name>
      <param-value>... the base path for all resource files ...</param-value>
    </init-param>
    <init-param>
      <param-name>mappingsPath</param-name>
      <param-value>... the path to the resource mappings file, relative to the base path ...</param-value>
    </init-param>
  </filter>

  <filter-mapping>
    <filter-name>static-resource</filter-name>
    <url-pattern>/*</url-pattern>
  </filter-mapping>
```

The resource mappings are specified in a file, indicated by `mappingsPath`. This file must use the following format:
```
<!-- Ignore some eXist apps that have their own static resources. -->
<mappings ignore="^/(webstart|xmlrpc|webdav|status)">
  <!-- Match an arbitrary path, followed by 'resources/' followed by 'one/', 'two/', or 'three/' followed by a resource file name.
       Map this to a relative path 'one/', 'two/', or 'three/' followed by 'resources/' followed by the resource file name.
  -->
  <map url=".*/resources/(one|two|three)/(.*)" to="$1/resources/$2"/>
  <!-- There can be multiple maps. The first one that matches @url will be used.
       If @to for a map with matching @url does not point to an existing file, or there is no matching @url,
       the next servlet (filter) will be used, which usually is the eXist servlet.
  -->
  <map url="..." to="..."/>
</mappings>
```

Before a request URL is matched with the mappings, the resource servlet checks if the mappings file has changed,
but only if at least `REFRESH_MAPPINGS_MS` milliseconds have passed since the last check.

The servlet filter logs to a logger with the name specified in `loggerName`. This should be configured in `etc/log4j2.xml`.
For example:
```
<RollingRandomAccessFile name="resource-servlet.log" filePattern="..." fileName="...">
  <Policies>
    <SizeBasedTriggeringPolicy size="10MB" />
  </Policies>
  <DefaultRolloverStrategy max="10" />
  <PatternLayout pattern="%d{ISO8601} | %-5p | %m %n" />
</RollingRandomAccessFile>
...
<Logger name="com.benjamins.resource-servlet" additivity="false" level="trace">
  <AppenderRef ref="resource-servlet.log" />
</Logger>
```
The Logger/@name is what has been specified in the loggerName init-param.


# XQuery library to create QR codes

This is a Java library that provides a XQuery functions to generate QR codes in SVG, for eXist-db.

## Usage

Install the module into eXist.

```
xquery version "3.1";
import module namespace svg-qr="http://rakensi.com/svg-qr";

<html>
    <body>
        <div style="width:200px;">{ svg-qr:generate-qr-svg("hello world") }</div>
        <br/>
        <div style="width:200px;">{ svg-qr:generate-qr-text-svg("hello world", "example") }</div>
    </body>
</html>
```
## To do

* There is an icon in the xar-resources, but I don't know how to put it into the xar-file.
* When re-installing the module into eXist, the older versions remain, and I cannot uninstall them from eXist.
