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
   "xmlns:ns4" "http://echo.nasa.gov/ingest/v10"
   "xmlns:xsi" "http://www.w3.org/2001/XMLSchema-instance"})

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
  (for [item items]
    (if (= (first item) "ns3:Item")
      item
      ["ns3:Item" item])))

(defn- keyword-from-xpath
  "Generate a keyword based on the last token of an xpath."
  [xpath]
  (-> (s/replace xpath #".*/" "")
      (csk/->kebab-case)
      (keyword)))

(defn parse-xpath-from-xml
  "Given a clojure.data.xml object and an xpath, return a vector containing a keyword
    constructed from the xpath and the value at the xpath."
  [xml xpath]
  (let [key (keyword-from-xpath xpath)]
    [key (xp/value-of xml xpath)]))

(defn parse-xpaths-from-xml
  "Given a clojure.data.xml object and a vector of xpaths, return a map of the values at each xpath."
  [xml xpaths]
  (into {} (map #(parse-xpath-from-xml xml %) xpaths)))

(defn parse-xpath-map-from-xml
  "Given an array of clojure.data.xml objects and two xpaths, return a map containing for each
   object the value of the first xpath as the key and the second xpath as the value."
  [xml key-xpath val-xpath]
  (->> xml
    (map #(vector
            (xp/value-of % key-xpath)
            (xp/value-of % val-xpath)))
    (into {})))

(defn- xpath-from-keyword
  "Generate a simple xpath using the syntax of the ECHO 10 SOAP API from a keyword."
  [key]
  (-> (name key)
      (csk/->PascalCase)))

(defn parse-keyword-from-xml
  "Given a clojure.data.xml object and an keyword, return a vector containing the keyword
    and the value found at an xpath constructed from that keyword.  If a keyword ends with 's'
    will look for a sequence of <Item> elements under the correponding xpath and return a vector of
    the values there associated with the keyword."
  [xml key]
  (let [xpath (xpath-from-keyword key)]
    ;; Generate vector value for a plural keyword, scalar otherwise.
    (if (and
          (.endsWith (str key) "s")
          (xp/value-of xml (str xpath "/Item")))
      [key (xp/values-at xml (str xpath "/Item"))]
      [key (xp/value-of xml xpath)])))

(defn parse-keywords-from-xml
  "Given a clojure.data.xml object and a vector of keywords, return a map of the values at xpaths
    constucted from each keyword."
  [xml keywords]
  (when xml
    (into {} (map #(parse-keyword-from-xml xml %) keywords))))

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
          (200, 201) response
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

(defn extract-string
  "Submits a SOAP request and returns the text contents of the response"
  [response-body operation]
  (xp/value-of response-body (str (response-element-xpath-from-keyword operation) "/result")))

(defn extract-item-list
  "Submits a SOAP request and returns a list of clojure.data.xml objects representing the items in the response."
  [response-body operation]
  (let [xpath-context (xpath/create-xpath-context-for-xml response-body)
        items-xpath (xpath/parse-xpath (str (response-element-xpath-from-keyword operation) "/result/Item"))]
      (:context (xpath/evaluate xpath-context items-xpath))))

(defn extract-item-map
  "Submits a SOAP request and returns a map representing the single item in the response."
  [response-body operation keywords]
  (let [xpath-context (xpath/create-xpath-context-for-xml response-body)
        item-xpath (xpath/parse-xpath (str (response-element-xpath-from-keyword operation) "/result"))
        item (-> (xpath/evaluate xpath-context item-xpath)
                 (:context)
                 (first))]
    (parse-keywords-from-xml item  keywords)))

(defn extract-item-map-list
  "Submits a SOAP request and returns a list of maps representing the items in the response."
  [response-body operation keywords]
  (let [xpath-context (xpath/create-xpath-context-for-xml response-body)
        items-xpath (xpath/parse-xpath (str (response-element-xpath-from-keyword operation) "/result/Item"))
        items (-> (xpath/evaluate xpath-context items-xpath)
                  (:context))]
    (map #(parse-keywords-from-xml % keywords) items)))
