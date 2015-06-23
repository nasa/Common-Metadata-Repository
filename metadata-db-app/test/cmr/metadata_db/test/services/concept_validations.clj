(ns cmr.metadata-db.test.services.concept-validations
  (:require [clojure.test :refer :all]
            [cmr.metadata-db.services.concept-validations :as v]
            [cmr.metadata-db.services.search-service :as search]
            [cmr.metadata-db.services.messages :as msg]))

(def valid-collection
  {:concept-id "C1-PROV1"
   :native-id "foo"
   :provider-id "PROV1"
   :concept-type :collection
   :extra-fields {:short-name "short"
                  :version-id "v1"
                  :entry-id "short_v1"
                  :entry-title "entry"}})

(def valid-granule
  {:concept-id "G1-PROV1"
   :native-id "foo"
   :provider-id "PROV1"
   :concept-type :granule
   :extra-fields {:parent-collection-id "C1-PROV1"
                  :granule-ur "GR-UR"}})


(deftest find-params-validation-test
  (testing "valid params"
    (are [params]
         (= [] (search/find-params-validation (assoc params :concept-type :collection)))
         {:provider-id "p"}
         {:provider-id "p" :entry-id "i"}
         {:provider-id "p" :entry-title "t"}
         {:provider-id "p" :short-name "s" :version-id "v"}
         {:provider-id "p" :entry-title "t" :short-name "s"}
         {:provider-id "p" :entry-title "t" :version-id "v"}
         {:provider-id "p" :entry-title "t" :short-name "s" :version-id "v"}))
  (testing "invalid param"
    (is (= [(msg/find-not-supported-combination :collection [:foo])]
           (search/find-params-validation {:concept-type "collection"
                                      :foo "f"})))
    (is (= [(msg/find-not-supported-combination :granule [:foo])]
           (search/find-params-validation {:concept-type "granule"
                                      :foo "f"}))))
  (testing "invalid concept-type"
    (is (= [(msg/find-not-supported-combination :foo [:provider-id :entry-title])]
           (search/find-params-validation {:concept-type "foo"
                                      :entry-title "e"
                                      :provider-id "p"})))))

(deftest collection-validation-test
  (testing "valid-concept"
    (is (= [] (v/concept-validation valid-collection))))
  (testing "missing concept type"
    (is (= [(msg/missing-concept-type)
            (msg/invalid-concept-id "C1-PROV1" "PROV1" nil)]
           (v/concept-validation (dissoc valid-collection :concept-type)))))
  (testing "missing provider id"
    (is (= [(msg/missing-provider-id)
            (msg/invalid-concept-id "C1-PROV1" nil :collection)]
           (v/concept-validation (dissoc valid-collection :provider-id)))))
  (testing "missing native id"
    (is (= [(msg/missing-native-id)]
           (v/concept-validation (dissoc valid-collection :native-id)))))
  (testing "invalid concept-id"
    (is (= ["Concept-id [1234] is not valid."]
           (v/concept-validation (assoc valid-collection :concept-id "1234")))))
  (testing "provider id and concept-id don't match"
    (is (= [(msg/invalid-concept-id "C1-PROV1" "PROV2" :collection)]
           (v/concept-validation (assoc valid-collection :provider-id "PROV2")))))
  (testing "concept type and concept-id don't match"
    (is (= [(msg/invalid-concept-id "C1-PROV1" "PROV1" :granule)]
           (v/concept-validation (assoc valid-collection
                                        :concept-type :granule
                                        :extra-fields (:extra-fields valid-granule))))))
  (testing "extra fields missing"
    (is (= [(msg/missing-extra-fields)]
           (v/concept-validation (dissoc valid-collection :extra-fields))))
    (are [field] (= [(msg/missing-extra-field field)]
                    (v/concept-validation (update-in valid-collection [:extra-fields] dissoc field)))
         :short-name
         :version-id
         :entry-id
         :entry-title)))

(deftest granule-validation-test
  (testing "valid-concept"
    (is (= [] (v/concept-validation valid-granule))))
  (testing "missing concept type"
    (is (= [(msg/missing-concept-type)
            (msg/invalid-concept-id "G1-PROV1" "PROV1" nil)]
           (v/concept-validation (dissoc valid-granule :concept-type)))))
  (testing "missing provider id"
    (is (= [(msg/missing-provider-id)
            (msg/invalid-concept-id "G1-PROV1" nil :granule)]
           (v/concept-validation (dissoc valid-granule :provider-id)))))
  (testing "missing native id"
    (is (= [(msg/missing-native-id)]
           (v/concept-validation (dissoc valid-granule :native-id)))))
  (testing "invalid concept-id"
    (is (= ["Concept-id [1234] is not valid."]
           (v/concept-validation (assoc valid-granule :concept-id "1234")))))
  (testing "provider id and concept-id don't match"
    (is (= [(msg/invalid-concept-id "G1-PROV1" "PROV2" :granule)]
           (v/concept-validation (assoc valid-granule :provider-id "PROV2")))))
  (testing "concept type and concept-id don't match"
    (is (= [(msg/invalid-concept-id "G1-PROV1" "PROV1" :collection)]
           (v/concept-validation (assoc valid-granule
                                        :concept-type :collection
                                        :extra-fields (:extra-fields valid-collection))))))
  (testing "extra fields missing"
    (is (= [(msg/missing-extra-fields)]
           (v/concept-validation (dissoc valid-granule :extra-fields))))
    (is (= [(msg/missing-extra-field :parent-collection-id)]
           (v/concept-validation (update-in valid-granule [:extra-fields] dissoc :parent-collection-id))))
    (is (= [(msg/missing-extra-field :granule-ur)]
           (v/concept-validation (update-in valid-granule [:extra-fields] dissoc :granule-ur))))))


