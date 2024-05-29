(ns cmr.common.test.concepts
  (:require
   [clojure.test :refer [are deftest is testing]]
   [cmr.common.test.test-check-ext :refer [defspec]]
   [clojure.test.check.properties :refer [for-all]]
   [clojure.test.check.generators :as gen]
   [cmr.common.concepts :as c]))

(deftest parse-concept-id-test
  (are [concept-type concept-id provider-id]
       (= {:concept-type concept-type
           :sequence-number 12
           :provider-id provider-id}
          (c/parse-concept-id concept-id))

       :collection "C12-PROV_A42" "PROV_A42"
       :granule "G12-PROV_A42" "PROV_A42"
       :service "S12-PROV_A42" "PROV_A42"
       :tag "T12-PROV_A42" "PROV_A42"
       :tag-association "TA12-PROV_A42" "PROV_A42"
       :access-group "AG12-PROV_A42" "PROV_A42"
       :access-group "AG12-CMR" "CMR"))

(deftest concept-type-validation-test
  (testing "valid types"
    (are [type] (and (nil? (c/concept-type-validation type))
                     (nil? (c/concept-type-validation (name type))))
         :collection
         :granule
         :service
         :tag
         :tag-association
         :access-group))
  (testing "invalid type"
    (is (= ["[foo] is not a valid concept type."]
           (c/concept-type-validation "foo")))
    (is (= ["[foo] is not a valid concept type."]
           (c/concept-type-validation :foo)))))

(def concept-id-maps
  "A generator for concept id maps"
  (gen/hash-map :concept-type (gen/elements (vec c/concept-types))
                :sequence-number gen/s-pos-int
                :provider-id (gen/not-empty gen/string-alphanumeric)))

(declare parse-build-concept-id-test)
;; Tests that all generated concept id maps can converted to concept ids and parsed.
(defspec parse-build-concept-id-test 100
  (for-all [concept-id-map concept-id-maps]
    (let [concept-id (c/build-concept-id concept-id-map)]
      (= concept-id-map (c/parse-concept-id concept-id)))))

(deftest concept-id-validation-test
  (testing "Testing concept-id-validation working case"
    (is (= nil (c/concept-id-validation "TL1222-PROV1")))
    (is (= nil (c/concept-id-validation :concept-id "TL1222-PROV1")))
    (is (= ["Concept-id [{&quot;0&quot; &quot;TL1222-PROV1&quot;}] is not valid."]
           (c/concept-id-validation {"0" "TL1222-PROV1"})))))
