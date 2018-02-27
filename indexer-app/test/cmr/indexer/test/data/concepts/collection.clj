(ns cmr.indexer.test.data.concepts.collection
  "Functions for testing cmr.indexer.data.concepts.collection namespace"
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.indexer.data.concepts.collection.collection-util :as collection-util]))

(deftest parse-version-id
  (are3 [version-id parsed-version-id]
    (is (= parsed-version-id
           (collection-util/parse-version-id version-id)))

    "Regular integer version"
    "15" "15"

    "Leading 0's version"
    "006" "6"

    "Leading and trailing 0's"
    "010" "10"

    "String version"
    "Not provided" "Not provided"

    "Nil version"
    nil nil))
