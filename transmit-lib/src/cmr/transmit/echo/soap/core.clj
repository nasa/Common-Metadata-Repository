(ns cmr.transmit.echo.soap.core
  "Helper to interact with the SOAP services."
  (:require [cmr.common.xml.gen :as xg]
            [cmr.common.xml.parse :as xp]
            [clj-http.client :as http]
            [cmr.common.config :refer [defconfig]]
            [cmr.common.log :refer (debug info warn error)]
            [clojure.string :as s]
            [camel-snake-kebab.core :as csk]
            [cmr.common.xml.simple-xpath :as xpath]))

(defconfig soap-url-base
  "Base URL for SOAP requests"
  {:default "http://localhost:3012/echo-v10/"})

(def soap-ns-map
  {"xmlns:ns2" "http://echo.nasa.gov/echo/v10"
   "xmlns:ns3" "http://echo.nasa.gov/echo/v10/types"
   "xmlns:ns4" "http://echo.nasa.gov/ingest/v10"})

(defn- sanitize-soap
  "Sanitize a SOAP request (e.g. remove user/pass/token)"
  [xml]
  (->
    (xg/xml xml)
    (s/replace #"<([^:>]*:username)>[^>]*>" "<$1>*****</$1>")
    (s/replace #"<([^:>]*:password)>[^>]*>" "<$1>*****</$1>")
    (s/replace #"<([^:>]*:token)>[^>]*>" "<$1>*****</$1>")))

(defn item-list
  "Return a list of 'Item' elements for the items in a vector."
  [items]
  (for [item items]["ns3:Item" item]))

(defn- xpath-to-keyword
  "Generate a keyword based on the last token of an xpath."
  [xpath]
  (-> (s/replace xpath #".*/" "")
      (csk/->kebab-case)
      (keyword)))

(defn xpath-to-map-pair
  "Given a clojure.data.xml object and an xpath, return a vector containing a keyword
    constructed from the xpath and the value at the xpath."
  [xml xpath]
  (let [key (xpath-to-keyword xpath)]
    [key (xp/value-of xml xpath)]))

(defn map-from-xpaths
  "Given a clojure.data.xml object and a vector of xpaths, return a map of the values at each xpath."
  [xml xpaths]
  (into {} (map #(xpath-to-map-pair xml %) xpaths)))

(defn xpath-key-value-map
  "Given an array of clojure.data.xml objects and two xpaths, return a map containing for each
   object the value of the first xpath as the key and the second xpath as the value."
  [xml key-xpath val-xpath]
  (->> xml
    (map #(vector
            (xp/value-of % key-xpath)
            (xp/value-of % val-xpath)))
    (into {})))

(defn- keyword-to-xpath
  "Generate a simple xpath using the syntax of teh ECHO 10 SOAP API from a keyword."
  [key]
  (-> (name key)
      (csk/->PascalCase)))

(defn keyword-to-map-pair
  "Given a clojure.data.xml object and an keyword, return a vector containing the keyword
    and the value found at an xpath constructed from that keyword."
  [xml key]
  (let [xpath (keyword-to-xpath key)]
    [key (xp/value-of xml xpath)]))

(defn map-from-keywords
  "Given a clojure.data.xml object and a vector of keywords, return a map of the values at xpaths
    constucted from each keyword."
  [xml keywords]
  (when xml
    (into {} (map #(keyword-to-map-pair xml %) keywords))))

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
          ;; Generally just want the most terse error text, but return more info for validation errors
          500 (if-let [fault-msg (xp/value-of response "/Envelope/Body/Fault/faultstring")]
                (if-let [validation-msg (xp/value-of response "/Envelope/Body/Fault/detail/SoapMessageValidationFault")]
                    (cmr.common.services.errors/throw-service-error :soap-fault validation-msg)
                    (cmr.common.services.errors/throw-service-error :soap-fault fault-msg))
                (cmr.common.services.errors/internal-error! response))
          503 (cmr.common.services.errors/throw-service-error :unavailable response)
          (cmr.common.services.errors/internal-error! response))))

(defn- response-element-xpath-from-keyword
  "Convert the keyword representing a soap operation name into the xpath in the response per the ECHO 10 schema.
    This mostly involves converting from a kebab-case keyword to a PascalCase string and appending 'Response', but if
    the operation name has a '2 in it, the 2 needs to move to the end (after 'Response').  Then the element name is
    appended to the boiler plate SOAP xpath."
  [operation]
  (->>
    (-> (csk/->PascalCaseString operation)
      (str "Response")
      (s/replace #"([^2]*)(2)(.*)" "$1$3$2"))
    (str "/Envelope/Body/")))

(defn string-from-soap-request
  "Submits a SOAP request and returns the text contents of the response"
  [service operation request-body]
  (let [[status body-xml] (post-soap service
                            request-body)]
      (xp/value-of body-xml (str (response-element-xpath-from-keyword operation) "/result"))))

(defn item-list-from-soap-request
  "Submits a SOAP request and returns a list of clojure.data.xml objects representing the items in the response."
  [service operation request-body]
  (let [[status body-xml] (post-soap service
                            request-body)
         xpath-context (xpath/create-xpath-context-for-xml body-xml)
         items-xpath (xpath/parse-xpath (str (response-element-xpath-from-keyword operation) "/result/Item"))
         items (-> (xpath/evaluate xpath-context items-xpath)
                   (:context))]
      items))

(defn item-map-from-soap-request
  "Submits a SOAP request and returns a map representing the single item in the response."
  [service operation request-body keywords]
  (let [[status body-xml] (post-soap service request-body)]
    (-> body-xml
        (xpath/create-xpath-context-for-xml)
        (xpath/evaluate (xpath/parse-xpath
                          (str (response-element-xpath-from-keyword operation) "/result")))
        (:context)
        (first)
        (map-from-keywords keywords))))

(defn item-map-list-from-soap-request
  "Submits a SOAP request and returns a list of maps representing the items in the response."
  [service operation request-body keywords]
  (let [[status body-xml] (post-soap service
                            request-body)
         xpath-context (xpath/create-xpath-context-for-xml body-xml)
         items-xpath (xpath/parse-xpath (str (response-element-xpath-from-keyword operation) "/result/Item"))
         items (-> (xpath/evaluate xpath-context items-xpath)
                   (:context))]
    (map #(map-from-keywords % keywords) items)))
