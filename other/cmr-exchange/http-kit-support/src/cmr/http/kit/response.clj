(ns cmr.http.kit.response
  "This namespace defines a default set of transform functions suitable for use
  in presenting results to HTTP clients.

  Note that ring-based middleware may take advantage of these functions either
  by single use or composition."
  (:require
   [cheshire.core :as json]
   [cheshire.generate :as json-gen]
   [clojure.data.xml :as xml]
   [clojure.string :as string]
   [cmr.exchange.common.results.errors :as errors]
   [ring.util.http-response :as response]
   [taoensso.timbre :as log]
   [xml-in.core :as xml-in])
  (:import
   (java.lang.ref SoftReference))
  (:refer-clojure :exclude [error-handler]))

(defn stream->str
  [body]
  (if (string? body)
    body
    (slurp body)))

(defn maybe-deref
  [maybe-ref]
  (try
    @maybe-ref
    (catch Exception _
      maybe-ref)))

;; XXX This is currently broken ... with a stackoverflow error. The issue is
;;     that soft-reference->json! calls json/generate-string, but that in
;;     turn calls soft-reference->json!, when attempting to encode a soft-
;;     reference. One fix for this would be to use a low-level string-
;;     generating function instead of the API-level one.
(defn soft-reference->json!
  "Given a soft reference object and a Cheshire JSON generator, write the
  data stored in the soft reference to the generator as a JSON string.

  Note, however, that sometimes the value is not a soft reference, but rather
  a raw value from the response. In that case, we need to skip the object
  conversion, and just do the realization."
  [obj json-generator]
  (let [data (maybe-deref
              (if (isa? obj SoftReference)
                (.get obj)
                obj))
        data-str (json/generate-string data)]
    (log/trace "Encoder got data: " data)
    (.writeString json-generator data-str)))

;; This adds support for JSON-encoding the data cached in a SoftReference.
(json-gen/add-encoder SoftReference soft-reference->json!)

(defn parse-json-result
  [result]
  (let [str-data (stream->str result)
        json-data (json/parse-string str-data true)]
    (log/trace "result:" result)
    (log/trace "json-data:" json-data)
    json-data))

(defn json-errors
  [body]
  (:errors (:body (parse-json-result body))))

(defn parse-xml-body
  [body]
  (let [str-data (if (string? body) body (slurp body))
        xml-data (xml/parse-str str-data)]
    (log/trace "str-data:" str-data)
    (log/trace "xml-data:" xml-data)
    xml-data))

(defn xml-errors
  [body]
  (vec (xml-in/find-all (parse-xml-body body)
                        [:errors :error])))
(defn ok
  [_request & args]
  (response/ok args))

(defn not-found
  [_request]
  (response/content-type
   (response/not-found "Not Found")
   "text/html"))

(defn cors
  [request response]
  (case (:request-method request)
    :options (-> response
                 (response/content-type "text/plain; charset=utf-8")
                 (response/header "Access-Control-Allow-Origin" "*")
                 (response/header "Access-Control-Allow-Methods" "POST, PUT, GET, DELETE, OPTIONS")
                 (response/header "Access-Control-Allow-Headers" "Content-Type")
                 (response/header "Access-Control-Max-Age" "2592000"))
    (response/header response "Access-Control-Allow-Origin" "*")))

(defn add-header
  [response field value]
  (assoc-in response [:headers (if (string? field) field (name field))] value))

(defn version-media-type
  [response value]
  (add-header response :cmr-media-type value))

(defn search-after
  [response value]
  (add-header response :cmr-search-after value))

(defn add-request-id
  [response id]
  (add-header response :cmr-request-id id))

(defn errors
  [errors]
  {:errors errors})

(defn error
  [error]
  (errors [error]))

(defn not-allowed
  ([message]
   (not-allowed message []))
  ([message other-errors]
   (-> (conj other-errors message)
       errors
       json/generate-string
       response/forbidden
       (response/content-type "application/json"))))

(defn error-handler
  ([status headers body]
   (error-handler
    status
    headers
    body
    (format "Unexpected cmr-authz error (%s)." status)))
  ([status headers body default-msg]
   (let [ct (:content-type headers)]
     (log/trace "Headers:" headers)
     (log/trace "Content-Type:" ct)
     (log/trace "Body:" body)
     (cond (nil? ct)
           (do
             (log/error body)
             {:errors [body]})

           (string/starts-with? ct "application/xml")
           (let [errs (xml-errors body)]
             (log/error errs)
             {:errors errs})

           (string/starts-with? ct "application/json")
           (let [errs (json-errors body)]
             (log/error errs)
             {:errors errs})

           :else
           {:errors [default-msg]}))))

(defn general-response-handler
  "This function does some initial processing of response to handle errors and otherwise
   return the body and headers of response"
  ([response]
   (general-response-handler response error-handler))
  ([response err-handler]
   (general-response-handler response err-handler identity))
  ([{:keys [status headers body error]} err-handler parse-fn]
   (log/debug "Handling client response ...")
   (log/trace "headers:" headers)
   (log/trace "body:" body)
   (cond error
         (do
           (log/error error)
           {:errors [error]})

         (>= status 400)
         (err-handler status headers body)

         :else
         {:body (parse-fn (stream->str body)) :headers headers})))


(defn one-field-response-handler
  "Helper function to specify a specific field to return from the general-response-handler"
  [response err-handler parse-fn field]
  (field (general-response-handler response err-handler parse-fn)))

;; Specific versions of one-field-response-handler
(def body-only-response-handler #(one-field-response-handler %1 %2 %3 :body))
(def headers-only-response-handler #(one-field-response-handler %1 %2 %3 :headers))

(def identity-handler #(general-response-handler % error-handler identity))
(def json-handler #(general-response-handler % error-handler parse-json-result))
(def xml-handler #(general-response-handler % error-handler parse-xml-body))

;; Handlers for extracting different fields from general-response-handler
(def json-body-handler #(body-only-response-handler % error-handler parse-json-result))
(def json-header-handler #(headers-only-response-handler % error-handler parse-json-result))

(def xml-body-handler #(body-only-response-handler % error-handler parse-xml-body))
(def xml-header-handler #(headers-only-response-handler % error-handler parse-xml-body))

(defn process-ok-results
  [data]
  (if (contains? data :search-after)
    {:headers {"CMR-Took" (:took data)
               "CMR-Hits" (:hits data)
               "CMR-Search-After" (:search-after data)}
     :status 200}
    {:headers {"CMR-Took" (:took data)
               "CMR-Hits" (:hits data)}
     :status 200}))

(defn process-err-results
  [data]
  {:status errors/default-error-code})

(defn process-results
  ([data]
   (process-results process-err-results data))
  ([err-fn data]
   (process-results err-fn process-ok-results data))
  ([err-fn ok-fn data]
   (if (:errors data)
     (err-fn data)
     (ok-fn data))))

(defn sanitize
  [data]
  (dissoc data :search-after))

(defn json
  ([request data]
   (json request process-results data))
  ([request process-fn data]
   (log/trace "Got data for JSON:" data)
   (-> data
       (assoc :request-id (:request-id request))
       process-fn
       (assoc :body (json/generate-string (sanitize data)))
       (response/content-type "application/json"))))

(defn text
  ([request data]
   (text request process-results data))
  ([request process-fn data]
   (-> data
       (assoc :request-id (:request-id request))
       process-fn
       (assoc :body (sanitize data))
       (response/content-type "text/plain"))))

(defn html
  ([request data]
   (html request process-results data))
  ([request process-fn data]
   (-> data
       (assoc :request-id (:request-id request))
       process-fn
       (assoc :body (sanitize data))
       (response/content-type "text/html"))))
