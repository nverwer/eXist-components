# XQuery library to create QR codes

This is a Java library that provides a XQuery functions to generate QR codes in SVG, for eXist-db.

# Usage

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
# To do

* There is an icon in the xar-resources, but I don't know how to put it into the xar-file.
* When re-installing the module into eXist, the older versions remain, and I cannot uninstall them from eXist.
