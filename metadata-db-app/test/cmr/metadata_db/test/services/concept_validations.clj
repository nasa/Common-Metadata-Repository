(ns cmr.metadata-db.test.services.concept-validations
  (:require
   [clojure.test :refer :all]
   [cmr.metadata-db.services.concept-validations :as v]
   [cmr.metadata-db.services.messages :as msg]
   [cmr.metadata-db.services.search-service :as search]))

(def valid-collection
  {:concept-id "C1-PROV1"
   :native-id "foo"
   :provider-id "PROV1"
   :concept-type :collection
   :user-id "user1"
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
                  :granule-ur "GR-UR"
                  :parent-entry-title "entry"}})

(def valid-tag
  {:concept-id "T1-CMR"
   :native-id "foo"
   :provider-id "CMR"
   :concept-type :tag})

(def valid-group
  {:concept-id "AG1-PROV1"
   :native-id "foo"
   :provider-id "PROV1"
   :concept-type :access-group
   :user-id "user1"})

(def valid-system-group
  {:concept-id "AG1-CMR"
   :native-id "foo"
   :provider-id "CMR"
   :concept-type :access-group
   :user-id "user1"})

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
    (is (= [(msg/find-not-supported :collection [:foo])]
           (search/find-params-validation {:concept-type "collection"
                                           :foo "f"})))
    (is (= [(msg/find-not-supported-combination :granule [:foo])]
           (search/find-params-validation {:concept-type "granule"
                                           :foo "f"}))))
  (testing "invalid concept-type"
    (is (= [(msg/find-not-supported :foo [:provider-id, :entry-title])]
           (search/find-params-validation {:concept-type "foo"
                                           :entry-title "e"
                                           :provider-id "p"})))))

(deftest tombstone-request-validations-test
  (testing "valid tombstone request"
    (is (= [] (v/tombstone-request-validation {:concept-id "C1-PROV1"
                                               :revision-id 1
                                               :revision-date "2015-02-13T17:00:35Z"
                                               :deleted true}))))
  (testing "missing concept-id"
    (is (= ["Concept must include concept-id."] (v/tombstone-request-validation
                                                  {:revision-id 1
                                                   :revision-date "2015-02-13T17:00:35Z"
                                                   :deleted true}))))

  (testing "extra fields"
    (is (= (set ["Tombstone concept cannot include [provider-id]"
                 "Tombstone concept cannot include [native-id]"
                 "Tombstone concept cannot include [format]"
                 "Tombstone concept cannot include [metadata]"])
           (set (v/tombstone-request-validation
                  {:concept-id "C120000000-PROV" :provider-id "PROV"
                   :deleted true :native-id "coll" :format "echo10" :metadata "xml"}))))))

(deftest collection-validation-test
  (testing "valid-concept"
    (is (= [] (v/default-concept-validation valid-collection))))
  (testing "missing concept type"
    (is (= [(msg/missing-concept-type)
            (msg/invalid-concept-id "C1-PROV1" "PROV1" nil)]
           (v/default-concept-validation (dissoc valid-collection :concept-type)))))
  (testing "missing provider id"
    (is (= [(msg/invalid-concept-id "C1-PROV1" nil :collection)
            (msg/missing-provider-id)]
           (v/default-concept-validation (dissoc valid-collection :provider-id)))))
  (testing "missing native id"
    (is (= [(msg/missing-native-id)]
           (v/default-concept-validation (dissoc valid-collection :native-id)))))
  (testing "invalid concept-id"
    (is (= ["Concept-id [1234] is not valid."]
           (v/default-concept-validation (assoc valid-collection :concept-id "1234")))))
  (testing "provider id and concept-id don't match"
    (is (= [(msg/invalid-concept-id "C1-PROV1" "PROV2" :collection)]
           (v/default-concept-validation (assoc valid-collection :provider-id "PROV2")))))
  (testing "concept type and concept-id don't match"
    (is (= [(msg/invalid-concept-id "C1-PROV1" "PROV1" :granule)]
           (v/default-concept-validation (assoc valid-collection
                                          :concept-type :granule
                                          :extra-fields (:extra-fields valid-granule))))))
  (testing "extra fields missing"
    (is (= [(msg/missing-extra-fields)]
           (v/default-concept-validation (dissoc valid-collection :extra-fields))))
    (are [field] (= [(msg/missing-extra-field field)]
                    (v/default-concept-validation (update-in valid-collection [:extra-fields] dissoc field)))
         :short-name
         :version-id
         :entry-id
         :entry-title)))

(deftest granule-validation-test
  (testing "valid-concept"
    (is (= [] (v/default-concept-validation valid-granule))))
  (testing "missing concept type"
    (is (= [(msg/missing-concept-type)
            (msg/invalid-concept-id "G1-PROV1" "PROV1" nil)]
           (v/default-concept-validation (dissoc valid-granule :concept-type)))))
  (testing "missing provider id"
    (is (= [(msg/invalid-concept-id "G1-PROV1" nil :granule)
            (msg/missing-provider-id)]
           (v/default-concept-validation (dissoc valid-granule :provider-id)))))
  (testing "missing native id"
    (is (= [(msg/missing-native-id)]
           (v/default-concept-validation (dissoc valid-granule :native-id)))))
  (testing "invalid concept-id"
    (is (= ["Concept-id [1234] is not valid."]
           (v/default-concept-validation (assoc valid-granule :concept-id "1234")))))
  (testing "provider id and concept-id don't match"
    (is (= [(msg/invalid-concept-id "G1-PROV1" "PROV2" :granule)]
           (v/default-concept-validation (assoc valid-granule :provider-id "PROV2")))))
  (testing "concept type and concept-id don't match"
    (is (= [(msg/invalid-concept-id "G1-PROV1" "PROV1" :collection)]
           (v/default-concept-validation (assoc valid-granule
                                          :concept-type :collection
                                          :extra-fields (:extra-fields valid-collection))))))
  (testing "extra fields missing"
    (is (= [(msg/missing-extra-fields)]
           (v/default-concept-validation (dissoc valid-granule :extra-fields))))
    (is (= [(msg/missing-extra-field :parent-collection-id)]
           (v/default-concept-validation (update-in valid-granule [:extra-fields] dissoc :parent-collection-id))))
    (is (= [(msg/missing-extra-field :granule-ur)]
           (v/default-concept-validation (update-in valid-granule [:extra-fields] dissoc :granule-ur))))))

(deftest tag-validation-test
  (testing "valid-concept"
    (is (= [] (v/tag-concept-validation valid-tag))))
  (testing "missing concept type"
    (is (= [(msg/missing-concept-type)
            (msg/invalid-concept-id "T1-CMR" "CMR" nil)]
           (v/tag-concept-validation (dissoc valid-tag :concept-type)))))
  (testing "missing native id"
    (is (= [(msg/missing-native-id)]
           (v/tag-concept-validation (dissoc valid-tag :native-id)))))
  (testing "invalid concept-id"
    (is (= ["Concept-id [1234] is not valid."]
           (v/tag-concept-validation (assoc valid-tag :concept-id "1234")))))
  (testing "concept type and concept-id don't match"
    (is (= [(msg/invalid-concept-id "T1-CMR" "CMR" :collection)]
           (v/tag-concept-validation (assoc valid-tag
                                        :concept-type :collection
                                        :extra-fields (:extra-fields valid-collection)))))))

(deftest group-validation-test
  (testing "valid-concept"
    (is (= [] (v/group-concept-validation valid-group))))
  (testing "missing concept type"
    (is (= [(msg/missing-concept-type)
            (msg/invalid-concept-id "AG1-PROV1" "PROV1" nil)]
           (v/group-concept-validation (dissoc valid-group :concept-type)))))
  (testing "missing native id"
    (is (= [(msg/missing-native-id)]
           (v/group-concept-validation (dissoc valid-group :native-id)))))
  (testing "invalid concept-id"
    (is (= ["Concept-id [1234] is not valid."]
           (v/group-concept-validation (assoc valid-group :concept-id "1234")))))
  (testing "concept type and concept-id don't match"
    (is (= [(msg/invalid-concept-id "AG1-PROV1" "PROV1" :collection)]
           (v/group-concept-validation (assoc valid-group
                                        :concept-type :collection
                                        :extra-fields (:extra-fields valid-collection)))))))

(deftest system-group-validation-test
  (testing "valid-concept"
    (is (= [] (v/group-concept-validation valid-system-group))))
  (testing "missing concept type"
    (is (= [(msg/missing-concept-type)
            (msg/invalid-concept-id "AG1-CMR" "CMR" nil)]
           (v/group-concept-validation (dissoc valid-system-group :concept-type)))))
  (testing "missing native id"
    (is (= [(msg/missing-native-id)]
           (v/group-concept-validation (dissoc valid-system-group :native-id)))))
  (testing "invalid concept-id"
    (is (= ["Concept-id [1234] is not valid."]
           (v/group-concept-validation (assoc valid-system-group :concept-id "1234")))))
  (testing "concept type and concept-id don't match"
    (is (= [(msg/invalid-concept-id "AG1-CMR" "CMR" :collection)]
           (v/group-concept-validation (assoc valid-system-group
                                        :concept-type :collection
                                        :extra-fields (:extra-fields valid-collection)))))))
