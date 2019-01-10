(ns cmr.opendap.health)

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
   :logging {:ok? (logging-ok? component)}})
