(ns cmr.ous.util.query
  (:require
   [clojure.string :as string]
   [cmr.exchange.query.core :as query]
   [taoensso.timbre :as log])
  (:refer-clojure :exclude [parse]))

(defn parse
  "This is a convenience function for calling code that wants to create a
  collection params instance. By default, the params are converted to the
  default internal representation. However, in the case of the 2-arity an
  explicit desired type is indicated so no conversion is performed."
  ([raw-params]
    (parse raw-params nil))
  ([raw-params destination]
    (query/parse raw-params
                 destination
                 {:required-params #{:collection-id}})))
