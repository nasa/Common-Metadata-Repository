(ns cmr.ous.app.handler.concept-cache
  "This namespace defines the handlers for the cache REST API resources."
  (:require
   [clojure.java.io :as io]
   [clojusc.twig :as twig]
   [cmr.metadata.proxy.components.caching :as caching]
   [cmr.ous.util.http.response :as response]
   [taoensso.timbre :as log]))

(defn lookup-all
  [component]
  (fn [request]
    (->> component
         caching/lookup-all
         (response/json request))))

(defn evict-all
  [component]
  (fn [request]
    (log/debug "Evicting all cached items ...")
    (->> component
         caching/evict-all
         (response/json request))))

(defn lookup
  [component]
  (fn [request]
    (let [item-key (get-in request [:path-params :item-key])]
      (response/json
       request
       (caching/lookup component item-key)))))

(defn evict
  [component]
  (fn [request]
    (let [item-key (get-in request [:path-params :item-key])]
      (log/debugf "Evicting value cached at key %s ..." item-key)
      (caching/evict component item-key)
      (response/json
       request
       (caching/lookup component item-key)))))
