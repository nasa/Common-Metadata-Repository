(ns cmr.client.http.impl
  (:require
   [clj-http.client :as http]
   [clojure.data.json :as json]
   [clojure.data.xml :as xml]
   [clojure.string :as string])
  (:refer-clojure :exclude [get]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-default-options
  [client]
  {})

(defn get-conn-mgr-option
  [client]
  (if-let [conn-mgr (get-in client
                            [:parent-client-options :connection-manager])]
    {:connection-manager conn-mgr}
    {}))

(defn make-http-options
  [client call-options]
  (merge (get-default-options client)
         (get-conn-mgr-option client)
         (:options client)
         call-options))

(defn make-http-client-args
  [client url options]
  [url
   (make-http-options client options)])

(defn parse-content-type
  [response]
  (let [content-type (get-in response [:headers "Content-Type"])]
    (cond
      (string/includes? content-type "json") :json
      (string/includes? content-type "xml") :xml
      :else :unsupported)))

(defn read-json-str
  [string-data]
  (json/read-str string-data :key-fn keyword))

(defn read-xml-str
  [string-data]
  (xml/parse-str string-data))

(defn convert-body
  [response content-type]
  (case content-type
     :json (read-json-str (:body response))
     :xml (read-xml-str (:body response))
     :unsupported (:body response)))

(defn parse-body!
  ([response]
   (parse-body! response (parse-content-type response)))
  ([response content-type]
   (assoc response :body (convert-body response content-type))))

(defn- handle-response
  ([client response]
   (handle-response client {} response))
  ([client options response]
   (if (or (:return-body? options)
           (get-in client [:parent-client-options :return-body?]))
     (:body (parse-body! response))
     (parse-body! response))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Records &tc.   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
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
    (->> (make-http-client-args this url options)
         (apply http/get)
         (handle-response this options))))

(defn- head
  ([this url]
    (head this url {}))
  ([this url options]
    :not-implemented))

(defn- put
  ([this url]
    (put this url {}))
  ([this url options]
    :not-implemented))

(defn- post
  ([this url]
    (post this url {}))
  ([this url options]
    :not-implemented))

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
  {:get get
   :head head
   :put put
   :post post
   :delete delete
   :copy copy
   :move move
   :patch patch
   :options options})
