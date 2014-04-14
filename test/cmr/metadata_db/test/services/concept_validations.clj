(ns cmr.metadata-db.test.services.concept-validations
  (:require [clojure.test :refer :all]
            [cmr.metadata-db.services.concept-validations :as v]
            [cmr.metadata-db.services.messages :as msg]))

(def valid-concept
  {:concept-id "C1-PROV1"
   :native-id "foo"
   :provider-id "PROV1"
   :concept-type :collection})


(deftest concept-validation-test
  (testing "valid-concept"
    (is (= [] (v/concept-validation valid-concept))))
  (testing "missing concept type"
    (is (= [(msg/missing-concept-type)
            (msg/invalid-concept-id "C1-PROV1" "PROV1" nil)]
           (v/concept-validation (dissoc valid-concept :concept-type)))))
  (testing "missing provider id"
    (is (= [(msg/missing-provider-id)
            (msg/invalid-concept-id "C1-PROV1" nil :collection)]
           (v/concept-validation (dissoc valid-concept :provider-id)))))
  (testing "missing native id"
    (is (= [(msg/missing-native-id)]
           (v/concept-validation (dissoc valid-concept :native-id)))))
  (testing "invalid concept-id"
    (is (= ["Concept-id [1234] is not valid."]
           (v/concept-validation (assoc valid-concept :concept-id "1234")))))
  (testing "provider id and concept-id don't match"
    (is (= [(msg/invalid-concept-id "C1-PROV1" "PROV2" :collection)]
           (v/concept-validation (assoc valid-concept :provider-id "PROV2")))))
  (testing "concept type and concept-id don't match"
    (is (= [(msg/invalid-concept-id "C1-PROV1" "PROV1" :granule)]
           (v/concept-validation (assoc valid-concept :concept-type :granule))))))