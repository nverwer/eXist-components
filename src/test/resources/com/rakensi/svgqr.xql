xquery version "3.1";

module namespace m="http://rakensi.com";

declare namespace test="http://exist-db.org/xquery/xqsuite";

import module namespace rxf = "http://rakensi.com/exist-db/xquery/functions";

declare
    %test:setUp
function m:setup() {
};

declare
    %test:tearDown
function m:cleanup() {
};

declare
    %test:assertEquals(10)
function m:testDummy() as xs:int {
    10
};

declare
    %test:assertEquals(10)
function m:test-generate-qr-svg() as xs:int {
    rxf:generate-qr-svg("hello world")
};