(ns cmr.client.common.util
  (:require
   [cmr.client.common.const :as const]
   #?(:clj [clojure.core.async :as async :refer [go go-loop]]
      :cljs [cljs.core.async :as async]))
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go go-loop]])))

(def default-environment-type :prod)

(defn get-endpoint
  [environment-type service-key]
  (->> (environment-type const/deployment-type)
       (vector service-key)
       (get-in const/endpoints)
       (str (environment-type const/hosts))))

(defn get-default-endpoint
  [options service-key]
  (or (:endpoint options)
      (get-endpoint default-environment-type service-key)))

(defn parse-endpoint
  ([endpoint]
   (parse-endpoint endpoint nil))
  ([endpoint service-key]
   (if (string? endpoint)
     endpoint
     (get-endpoint endpoint service-key))))

(defn ^:export with-callback
  [chan callback]
  (go-loop []
    (if-let [response (async/<! chan)]
      (callback response)
      (recur))))
