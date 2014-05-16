(ns cmr.search.test.services.legacy-parameters
  (:require [clojure.test :refer :all]
            [cmr.search.services.legacy-parameters :as lp]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :refer [for-all]]
            [cmr.common.test.test-check-ext :as tc :refer [defspec]]))


(defn- legacy-psa-map->legacy-psa-query
  "Convert a map of psa fields into a legacy psa url query string"
  [psa-map]
  (let [{:keys [name type value minValue maxValue]} psa-map
        query (format "attribute[][name]=%s&attribute[][type]=%s" name type)]
    (if value
      (str query "&attribute[][value]=" value)
      (str query
           (when minValue (str "&attribute[][minValue]=" minValue))
           ","
           (when maxValue (str "&attribute[][maxValue]=" maxValue))))))


(defn- legacy-psa-map->cmr-psa-query
  "Converts a map of psa fields into a map with a single attribute as a comma-separated string"
  [psa-map]
  (let [{:keys [name type value minValue maxValue]} psa-map
        query (format "%s,%s" type name)
        query (if value
                (str query "," value)
                (str query "," minValue "," maxValue))]
    query))


;; NOTE - these don't generate valid psa fields, but that doesn't matter because we are only
;; tesing that the legacy->cmr functionality reformats the query parameters correctly.
(def legacy-psa-maps
  "A generator for legacy psa attributes with single values"
  (gen/hash-map :name (gen/not-empty (tc/string-alpha-numeric 1 10))
                :type (gen/not-empty (tc/string-alpha-numeric 1 10))
                :value (gen/not-empty (tc/string-alpha-numeric 1 10))
                :minValue gen/string-alpha-numeric
                :maxValue gen/string-alpha-numeric))


;; Tests that all generated legacy-psa attribute stings can be converted to cmr format.
(defspec legacy-psa->cmr-test 100
  (for-all [legacy-psa legacy-psa-maps]
    (let [legacy-query (legacy-psa-map->legacy-psa-query legacy-psa)
          cmr-psa-query (legacy-psa-map->cmr-psa-query legacy-psa)
          result (lp/process-legacy-psa {} legacy-query)
          result-query (first (:attribute result))]
      (= cmr-psa-query result-query))))



