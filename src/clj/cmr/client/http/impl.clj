(ns cmr.client.http.impl
  "The Clojure implementation of the CMR service HTTP client."
  (:require
   [clj-http.client :as http]
   [clojure.data.json :as json]
   [clojure.data.xml :as xml]
   [clojure.string :as string])
  (:refer-clojure :exclude [get]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def default-options
  "The default HTTP option for the Clojure implementation of the CMR HTTP
  client."
  {})

(defn get-conn-mgr-option
  "Given a client object, return a connection manager based on the client
  options set in the parent service API client."
  [client]
  (if-let [conn-mgr (get-in client
                            [:parent-client-options :connection-manager])]
    {:connection-manager conn-mgr}
    {}))

(defn create-http-options
  "This function is intended to be used with every call, giving the call the
  opportunity to override the HTTP client options saved when the client was
  instantiated."
  [client call-options]
  (merge default-options
         (get-conn-mgr-option client)
         (:options client)
         call-options))

(defn create-http-client-args
  "Create a list of args that can be usd with the CMR HTTP client by applying
  them."
  [client url options]
  [url
   (create-http-options client options)])

(defn parse-content-type
  "Parse the content type of the given response."
  [response]
  (let [content-type (get-in response [:headers "Content-Type"])]
    (cond
      (string/includes? content-type "json") :json
      (string/includes? content-type "xml") :xml
      :else :unsupported)))

(defn convert-body
  "Given a response and a content type, convert the body according to the type. d"
  [response content-type]
  (case content-type
     :json (json/read-str (:body response) :key-fn keyword)
     :xml (xml/parse-str (:body response))
     :unsupported (:body response)))

(defn parse-body!
  "Parse the body of a response, converting the body based on the provided
  content type."
  ([response]
   (parse-body! response (parse-content-type response)))
  ([response content-type]
   (assoc response :body (convert-body response content-type))))

(defn- handle-response
  "A callback function for handling response data when it is returned."
  ([client response]
   (handle-response client {} response))
  ([client options response]
   (if (or (:return-body? options)
           (get-in client [:parent-client-options :return-body?]))
     (:body (parse-body! response))
     (parse-body! response))))

(defn get-http-func
  [method]
  (case method
    :get http/get
    :head http/head
    :put http/put
    :post http/post))

(defn- call
  [client method args options]
  (let [func (get-http-func method)]
    (try
      (->> args
           (apply func)
           (handle-response client options))
      (catch Exception e
        ;; XXX check here for return-body? option; if true, don't return
        ;;     everything that's in ex-data (e.g., headers, etc.), but
        ;;     rather just {:status ... :errors ...}
        (let [data (ex-data e)
              content-type (parse-content-type data)
              errors (:errors (convert-body data content-type))]
        (assoc data :errors errors))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Records &tc.   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord HTTPClientData [
  parent-client-options
  options])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- get
  ([this url]
    (get this url {}))
  ([this url options]
    (call this
          :get
          (create-http-client-args this url options)
          options)))

(defn- head
  ([this url]
    (head this url {}))
  ([this url options]
    (call this
          :head
          (create-http-client-args this url options)
          options)))

(defn- put
  ([this url]
    (put this url {}))
  ([this url data]
    (put this url data {}))
  ([this url data options]
    (call this
          :put
          (->> {:body data}
               (merge options)
               (create-http-client-args this url))
          options)))

(defn- post
  ([this url]
    (post this url {}))
  ([this url data]
    (put this url data {}))
  ([this url data options]
    (call this
          :post
          (->> {:body data}
               (merge options)
               (create-http-client-args this url))
          options)))

(defn- delete
  ([this url]
    (delete this url {}))
  ([this url options]
    :not-implemented))

(defn- copy
  ([this url]
    (copy this url {}))
  ([this url options]
    :not-implemented))

(defn- move
  ([this url]
    (move this url {}))
  ([this url options]
    :not-implemented))

(defn- patch
  ([this url]
    (patch this url {}))
  ([this url options]
    :not-implemented))

(defn- options
  ([this url]
    (options this url {}))
  ([this url options]
    :not-implemented))

(def client-behaviour
  "A map of method names to implementations.

  Intended for use by the `extend` protocol function."
  {:get get
   :head head
   :put put
   :post post
   :delete delete
   :copy copy
   :move move
   :patch patch
   :options options})
