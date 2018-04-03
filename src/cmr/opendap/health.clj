(ns cmr.opendap.health
  (:require
   [clj-http.client :as httpc]))

(defn opendap-ok?
  [url]
  (if (= 200 (:status (httpc/head url)))
    true
    false))

(defn has-data?
  [x]
  (if (nil? x)
    false
    true))

(defn config-ok?
  [component]
  (has-data? (:config component)))

(defn logging-ok?
  [component]
  (has-data? (:logging component)))

(defn components-ok?
  [component]
  {:config {:ok? (config-ok? component)}
   :httpd {:ok? true}
   :logging {:ok? (logging-ok? component)}
   :opendap {:ok? (opendap-ok?)}})
