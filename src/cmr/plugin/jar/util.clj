(ns cmr.plugin.jar.util
  (:require
    [clojure.string :as string])
  (:import
    (clojure.lang Symbol)))

(defn resolve-fully-qualified-fn
  [^Symbol fqfn]
  (let [[name-sp fun] (mapv symbol (string/split (str fqfn) #"/"))]
    (require name-sp)
    (var-get (ns-resolve name-sp fun))))
