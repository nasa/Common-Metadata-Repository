(ns cmr.opendap.rest.handler
  (:require
   [clojure.java.io :as io]
   [clojusc.twig :as twig]
   [cmr.opendap.health :as health]
   [cmr.opendap.rest.response :as response]
   [ring.middleware.file :as file-middleware]
   [ring.util.codec :as codec]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   OPeNDAP Handlers   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TBD

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

(def ok
  (fn [request]
    (response/ok request)))

(def fallback
  (fn [request]
    (response/not-found request)))

(defn static-files
  [docroot]
  (fn [request]
    (if-let [doc-resource (.getPath (io/resource docroot))]
      (file-middleware/file-request request doc-resource))))
