(ns cmr.search.test.services.legacy-parameters
  (:require [clojure.test :refer :all]
            [cmr.search.services.parameters.legacy-parameters :as lp]
            [clojure.test.check.generators :refer [such-that] :as gen]
            [clojure.test.check.properties :refer [for-all]]
            [ring.util.codec :as rc]
            [cmr.search.services.messages.attribute-messages :as a-msg]
            [cmr.common.test.test-check-ext :as tc :refer [defspec]]
            [clojure.string :as s]
            [cmr.common.test.test-util :as tu])
  (:import clojure.lang.ExceptionInfo))


(defn- legacy-psa-maps->legacy-psa-query
  "Convert a vector of maps of psa fields into a legacy psa url query string"
  [psa-maps]
  (reduce (fn [full-query psa-map]
            (let [{:keys [name type value minValue maxValue]} psa-map
                  [name type value minValue maxValue] (map #(when % (rc/url-encode %))
                                                           [name type value minValue maxValue])
                  query (format "attribute%%5B%%5D%%5Bname%%5D=%s&attribute%%5B%%5D%%5Btype%%5D=%s"
                                name
                                type)
                  query (if value
                          (str query "&attribute%5B%5D%5Bvalue%5D=" value)
                          (str query
                               (when minValue (str "&attribute%5B%5D%5BminValue%5D=" minValue))
                               (when maxValue (str "&attribute%5B%5D%5BmaxValue%5D=" maxValue))))]
              (if full-query
                (str full-query "&" query)
                query)))
          nil
          psa-maps))

(defn- legacy-psa-maps->cmr-psa-query
  "Converts a vector of maps of psa fields into a string with several attributes as comma-separated
  values"
  [psa-maps]
  (map (fn [psa-map]
         (let [{:keys [name type value minValue maxValue]} psa-map
               [name type value minValue maxValue] (map #(when % (s/replace % "," "\\,"))
                                                        [name type value minValue maxValue])
               query (format "%s,%s" type name)
               query (if value
                       (str query "," value)
                       (str query "," minValue "," maxValue))]
           query))
       psa-maps))

;; NOTE - these don't generate valid psa fields, but that doesn't matter because we are only
;; testing that the legacy->cmr functionality reformats the query parameters correctly.
(def legacy-psa-maps
  "A generator for legacy psa attributes with single values"
  (gen/hash-map :name (gen/not-empty gen/string)
                :type (gen/not-empty gen/string)
                :value (gen/not-empty gen/string)
                :minValue (gen/not-empty gen/string)
                :maxValue (gen/not-empty gen/string)))


;; Tests that all generated legacy-psa attribute stings can be converted to cmr format.
(defspec legacy-psa->cmr-test 100
  (for-all [legacy-psa (such-that not-empty (gen/vector legacy-psa-maps))]
    (let [legacy-query (legacy-psa-maps->legacy-psa-query legacy-psa)
          cmr-psa-query (legacy-psa-maps->cmr-psa-query legacy-psa)
          result (lp/process-legacy-psa {:page-size 10} legacy-query)]
      (= {:page-size 10 :attribute cmr-psa-query} result))))

;; Test for specific cases
(deftest handle-legacy-psa
  (are [name type value min-value max-value]
       (let [m {:name name :type type :value value :minValue min-value :maxValue max-value}]
         (= {:attribute (legacy-psa-maps->cmr-psa-query [m])}
            (lp/process-legacy-psa {} (legacy-psa-maps->legacy-psa-query [m]))))
       "A,B" "string" nil "B,C" "D,E"
       "A&B" "string" nil "P[C" "Q?r"
       "M[]N" "string" "A&? ;" nil nil))

;; Test for mixed parameters
(deftest mixed-paramter-types
  (tu/assert-exception-thrown-with-errors
    :bad-request
    [(a-msg/mixed-legacy-and-cmr-style-parameters-msg)]
    (lp/process-legacy-psa {:attribute ["string,abc,xyz"
                                        {:name "PDQ"}
                                        {:type "string"}
                                        {:value "ABC"}]}
                           (legacy-psa-maps->legacy-psa-query
                             [{:name "PDQ" :type "string" :value "ABC"}]))))
