xquery version "3.1";

module namespace m="http://rakensi.com/svg-qr";

declare namespace test="http://exist-db.org/xquery/xqsuite";

import module namespace svg-qr="http://rakensi.com/svg-qr" at "java:com.rakensi.SVGQRModule";

declare
    %test:assertEquals(10)
function m:testDummy() as xs:int {
    10
};

declare
    %test:assertEquals(10)
function m:createFor() as xs:int {
    svg-qr:create-for("hello world")
};