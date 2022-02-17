# XQuery library to create QR codes

This is a failed attempt to make a Java library that provides an XQuery function to generate QR codes in SVG, for eXist-db.

I could not get this working, which is partly because I /don't know enough about|hate/ maven.

# Usage, if it would work

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
