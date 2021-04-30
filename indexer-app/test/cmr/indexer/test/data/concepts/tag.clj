(ns cmr.indexer.test.data.concepts.tag
  "Code coverage tests for the functions of testing cmr.indexer.data.concepts.tag
   namespace."
  (:require
   [clojure.string :as str]
   [clojure.test :refer :all]
   [cmr.common.util :as util]
   [cmr.indexer.data.concepts.tag :as tag]))

(defn- create-tag
  "Create a tag map"
  [tag-key originator data]
  {:tag-key-lowercase (str/lower-case tag-key)
   :originator-id-lowercase (util/safe-lowercase originator)
   :tag-value-lowercase (when (string? data) (str/lower-case data))})

(deftest does-has-cloud-s3-tag-work
  "The function has-cloud-s3-tag should only return true if there is a
   earthdata-cloud-s3-tag in the list"
  (let [bad1 (create-tag "This-Is-Not-The-Tag-Your-Looking-For" "My-Id" "Value")
        bad2 (create-tag "This-Is-Also-Not-The-Tag-Your-Looking-For" "My-Id" "Value")
        good (create-tag tag/earthdata-cloud-s3-tag "My-Id" "Value")
        will-not-be-found [bad1]
        these-will-not-be-found [bad1 bad2]
        will-be-found [good]
        one-will-be-found [bad1 bad2 good]]
    (is (false? (tag/has-cloud-s3-tag? will-not-be-found)))
    (is (false? (tag/has-cloud-s3-tag? these-will-not-be-found)))
    (is (true? (tag/has-cloud-s3-tag? will-be-found)))
    (is (true? (tag/has-cloud-s3-tag? one-will-be-found)))))
