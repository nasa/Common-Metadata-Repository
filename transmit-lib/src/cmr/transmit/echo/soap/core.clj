(ns cmr.transmit.echo.soap.core
  "Helper to interact with the SOAP services."
  (:require [hiccup.core :as hiccup]
            [cmr.common.xml.gen :as xg]
            [clj-http.client :as http]
            [cmr.common.config :refer [defconfig]]
            [cmr.common.log :refer (debug info warn error)]
            [clojure.string :as s]))

(defconfig soap-url-base
  "Base URL for SOAP requests"
  {:default "http://localhost:3012/echo-v10/" :type String})

(defn post-xml
  "Post an XML object to the specified endpoint.  The XML must be a hiccup style object."
  [endpoint xml]
  (let [{status :status body :body}
        (http/post endpoint
          { :content-type "text/xml"
            :body (xg/xml xml)})]
    [status body]))

(defn post-soap
  "Wrap a SOAP body with all the boiler-plate enclosures and push to the endpoint indicated by the
    specified service key.  The XML body should be the contents of SOAP-ENV:Body and should be a
    vector in the hiccup format.  The service-key should be a keyword corresponding to the text
    prior to 'ServicePortImpl' in an ECHO-v10 API call.  e.g. :authentication corresponds to
    AuthenticationServicePortImpl"
  [service-key body]
  (let [service (str (s/capitalize (name service-key)) "ServicePortImpl")
        endpoint (str (soap-url-base) service)]
    (info "POST to " endpoint)
    (post-xml
        endpoint
        ["SOAP-ENV:Envelope"
                {"xmlns:SOAP-ENV" "http://schemas.xmlsoap.org/soap/envelope/"}
                ["SOAP-ENV:Header"]
                ["SOAP-ENV:Body" body]])))
