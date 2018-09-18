(ns cmr.exchange.common.util
  (:require
   [clojure.string :as string]
   [taoensso.timbre :as log])
  (:import
   (clojure.lang Symbol)))

(defn resolve-fully-qualified-fn
  [^Symbol fqfn]
  (when fqfn
    (try
      (let [[name-sp fun] (mapv symbol (string/split (str fqfn) #"/"))]
        (require name-sp)
        (var-get (ns-resolve name-sp fun)))
      (catch  Exception _
        (log/warn "Couldn't resolve one or more of" fqfn)))))
