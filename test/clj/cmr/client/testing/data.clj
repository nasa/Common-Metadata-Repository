(ns cmr.client.testing.data
  (:require
   [clojure.java.io :as io]))

(defn get-data-file
  [path-segment]
  (slurp (io/resource path-segment)))

(defn get-payload
  [path-segment]
  (get-data-file (str "payload/" path-segment)))

(defn get-response
  [path-segment]
  (get-data-file (str "response/" path-segment)))
