# XQuery library to create QR codes

This is a Java library that provides an XQuery function to generate QR codes in SVG, for eXist-db.

This is an early version, which will be expanded in the near future.

# Usage

```
xquery version "3.1";
import module namespace svg-qr="http://rakensi.com/svg-qr";

svg-qr:create-for("hello world")
```
