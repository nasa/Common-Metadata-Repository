(ns cmr.transmit.echo.soap.core
  "Helper to interact with the SOAP services."
  (:require [cmr.common.xml.gen :as xg]
            [cmr.common.xml.parse :as xp]
            [clj-http.client :as http]
            [cmr.common.config :refer [defconfig]]
            [cmr.common.log :refer (debug info warn error)]
            [clojure.string :as s]
            [camel-snake-kebab.core :as csk]))

(defconfig soap-url-base
  "Base URL for SOAP requests"
  {:default "http://localhost:3012/echo-v10/"})

(defn- sanitize-soap
  "Sanitize a SOAP request (e.g. remove user/pass)"
  [xml]
  (->
    (xg/xml xml)
    (s/replace #"<([^:>]*:username)>[^>]*>" "<$1>*****</$1>")
    (s/replace #"<([^:>]*:password)>[^>]*>" "<$1>*****</$1>")
    (s/replace #"<([^:>]*:token)>[^>]*>" "<$1>*****</$1>")))

(defn post-xml
  "Post an XML object to the specified endpoint.  The XML must be a hiccup style object."
  [endpoint xml]
  (let [{:keys [status body] :as full-resp}
        (http/post endpoint
          { :content-type "text/xml"
            :throw-exceptions false
            :body (xg/xml xml)})]
    [status body]))

(defn post-soap
  "Wrap a SOAP body with all the boiler-plate enclosures and push to the endpoint indicated by the
    specified service key.  The XML body should be the contents of SOAP-ENV:Body and should be a
    vector in the hiccup format.  The service-key should be a keyword corresponding to the text
    prior to 'ServicePortImpl' in an ECHO-v10 API call.  e.g. :authentication corresponds to
    AuthenticationServicePortImpl"
  [service-key body]
  (let [service (str (csk/->PascalCase (name service-key)) "ServicePortImpl")
        endpoint (str (soap-url-base) service)
        - (debug (format "POST SOAP request to %s" endpoint))
        - (debug (format "SOAP body: %s" (sanitize-soap body)))
        [status response] (post-xml endpoint
                            ["SOAP-ENV:Envelope"
                             {"xmlns:SOAP-ENV" "http://schemas.xmlsoap.org/soap/envelope/"}
                             ["SOAP-ENV:Header"]
                             ["SOAP-ENV:Body" body]])]
    (debug (format "SOAP request got %s response with body: %s"
            status response))
    (case status
          (200, 201) [status response]
          401 (cmr.common.services.errors/throw-service-error :unauthorized response)
          404 (cmr.common.services.errors/throw-service-error :not-found response)
          ;; soap-services returns all SOAP errors as 500.  Check if it is a SOAP error and if so,
          ;; extract the fault string from the XML.  If not, add the entire response to the exception
          500 (if-let [fault-msg (xp/value-of response "/Envelope/Body/Fault/faultstring")]
                (cmr.common.services.errors/throw-service-error :soap-fault fault-msg)
                (cmr.common.services.errors/internal-error! response))
          503 (cmr.common.services.errors/throw-service-error :unavailable response)
          (cmr.common.services.errors/internal-error! response))))
