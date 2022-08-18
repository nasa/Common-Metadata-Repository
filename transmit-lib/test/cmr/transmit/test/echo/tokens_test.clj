(ns cmr.transmit.test.echo.tokens-test
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.transmit.echo.tokens :as tokens])
  (:import
   clojure.lang.ExceptionInfo
   java.lang.Exception))

(def bad-gateway-body "<html>\r\n<head><title>504 Gateway Time-out</title></head>\r\n<body>\r\n<center><h1>504 Gateway Time-out</h1></center>\r\n</body>\r\n</html>\r\n")

(def upstream-server-error-body "<!DOCTYPE html>\n<html>\n<head>\n  <title>We're sorry, but something went wrong (500)</title>\n  <style type=\"text/css\">\n    body { background-color: #fff; color: #666; text-align: center; font-family: arial, sans-serif; }\n    div.dialog {\n      width: 25em;\n      padding: 0 4em;\n      margin: 4em auto 0 auto;\n      border: 1px solid #ccc;\n      border-right-color: #999;\n      border-bottom-color: #999;\n    }\n    h1 { font-size: 100%; color: #f00; line-height: 1.5em; }\n  </style>\n</head>\n\n<body>\n  <!-- This file lives in public/500.html -->\n  <div class=\"dialog\">\n    <h1>We're sorry, but something went wrong.</h1>\n    <p>We've been notified about this issue and we'll take a look at it shortly.</p>\n  </div>\n</body>\n</html>")

(deftest handle-get-user-id-test
  (are3 [status response ex-type msg-fragment]
    (is (thrown-with-msg?
         ex-type
         (re-pattern msg-fragment)
         (tokens/handle-get-user-id "foo-token" status nil response)))

    "504 Gateway Timeout"
    504 bad-gateway-body ExceptionInfo "gateway timeout"

    "General server error"
    500 upstream-server-error-body Exception "We're sorry"))
