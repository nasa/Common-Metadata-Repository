(ns cmr.system-int-test.data2.provider-holdings
  "Contains helper functions for converting provider holdings into the expected map of parsed results."
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cheshire.core :as json]))

(defn- xml-elem->provider-holding
  "Returns the provider holding entry by parsing the given xml struct"
  [xml-elem]
  {:entry-title (cx/string-at-path xml-elem [:entry-title])
   :concept-id (cx/string-at-path xml-elem [:concept-id])
   :granule-count (cx/long-at-path xml-elem [:granule-count])
   :provider-id (cx/string-at-path xml-elem [:provider-id])})

(defn- parse-xml-provider-holdings
  "Returns the parsed provider holdings for the given xml string"
  [xml]
  (let [xml-struct (x/parse-str xml)]
    (map xml-elem->provider-holding
         (cx/elements-at-path xml-struct [:provider-holding]))))

(defn- parse-json-provider-holdings
  "Returns the parsed provider holdings for the given json string"
  [json-str]
  (json/decode json-str true))

(defn parse-provider-holdings
  "Returns the parsed provider holdings based on the given format and result string"
  [format result]
  (if (= :xml format)
    (parse-xml-provider-holdings result)
    (parse-json-provider-holdings result)))

