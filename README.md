# XQuery library to create QR codes

This is a failed attempt to make a Java library that provides an XQuery function to generate QR codes in SVG, for eXist-db.

I could not get this working, which is partly because I /don't know enough about|hate/ maven.

# Usage, if it would work

```
xquery version "3.1";
import module namespace svg-qr="http://rakensi.com/svg-qr";

svg-qr:create-for("hello world")
```
