(ns cmr.opendap.app.handler.core
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
   [cmr.http.kit.app.handler :as base-handler]
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

(def status base-handler/status)
(def ok base-handler/ok)
(def dynamic-page base-handler/dynamic-page)
(def permanent-redirect base-handler/permanent-redirect)

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

