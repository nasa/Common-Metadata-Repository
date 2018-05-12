(ns cmr.opendap.rest.handler.core
  "This namespace defines the handlers for general resources.

  Simple handlers will only need to make a call to a library and then have that
  data prepared for the client by standard response function. More complex
  handlers will need to perform additional tasks. For example, in order of
  increasing complexity:
  * utilize non-default, non-trivial response functions
  * operate on the obtained data with various transformations, including
    extracting form data, query strings, etc.
  * take advantage of middleware functions that encapsulate complicated
    business logic"
  (:require
   [clojure.java.io :as io]
   [clojusc.twig :as twig]
   [cmr.opendap.health :as health]
   [cmr.opendap.http.response :as response]
   [ring.middleware.file :as file-middleware]
   [ring.util.codec :as codec]
   [ring.util.http-response]
   [ring.util.response :as ring-response]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Admin Handlers   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn health
  [component]
  (fn [request]
    (->> component
         health/components-ok?
         (response/json request))))

(def ping
  (fn [request]
    (response/json request {:result :pong})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility Handlers   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn status
  [status-keyword]
  (fn [request]
    ((ns-resolve 'ring.util.http-response (symbol (name status-keyword))) {})))

(def ok
  (fn [request]
    (response/ok request)))

(defn text-file
  [filepath]
  (fn [request]
    (if-let [file-resource (io/resource filepath)]
      (response/text request (slurp file-resource)))))

(defn html-file
  [filepath]
  (fn [request]
    (if-let [file-resource (io/resource filepath)]
      (response/html request (slurp file-resource)))))

(defn dynamic-page
  [page-fn data]
  #(page-fn % data))

(defn permanent-redirect
  [location]
  (fn [request]
    (ring-response/redirect location :moved-permanently)))
