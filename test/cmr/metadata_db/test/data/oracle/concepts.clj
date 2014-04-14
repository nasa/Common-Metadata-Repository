(ns cmr.metadata-db.test.data.oracle.concepts
  (:require [clojure.test :refer :all]
            [cmr.metadata-db.data.oracle.concepts :as c]))


(deftest db-result->concept-map-test
  (testing "collection results"
    (let [result {:native_id "foo"
                  :concept_id "C5-PROV1"
                  :metadata "<foo>"
                  :format "xml"
                  :revision_id 2
                  :deleted 0}]
      (is (= {:concept-type :collection
              :native-id "foo"
              :concept-id "C5-PROV1"
              :provider-id "PROV1"
              :metadata "<foo>"
              :format "xml"
              :revision-id 2
              :deleted false}
             (c/db-result->concept-map :collection "PROV1" result))))
    (testing "granule results"
      (let [result {:native_id "foo"
                    :concept_id "G7-PROV1"
                    :metadata "<foo>"
                    :format "xml"
                    :revision_id 2
                    :deleted 0
                    :parent_collection_id "C5-PROV1"}]
        (is (= {:concept-type :granule
                :native-id "foo"
                :concept-id "G7-PROV1"
                :provider-id "PROV1"
                :metadata "<foo>"
                :format "xml"
                :revision-id 2
                :deleted false
                :parent-collection-id "C5-PROV1"}
               (c/db-result->concept-map :granule "PROV1" result)))))))

(deftest concept->insert-args-test
  (testing "collection insert-args"
    (let [concept {:concept-type :collection
                   :native-id "foo"
                   :concept-id "C5-PROV1"
                   :provider-id "PROV1"
                   :metadata "<foo>"
                   :format "xml"
                   :revision-id 2
                   :deleted false}]
      (is (= [["native_id" "concept_id" "metadata" "format" "revision_id" "deleted"]
              ["foo" "C5-PROV1" "<foo>" "xml" 2 false]]
             (c/concept->insert-args concept)))))
  (testing "granule insert-args"
    (let [concept {:concept-type :granule
                   :native-id "foo"
                   :concept-id "G7-PROV1"
                   :provider-id "PROV1"
                   :metadata "<foo>"
                   :format "xml"
                   :revision-id 2
                   :deleted false
                   :parent-collection-id "C5-PROV1"}]
      (is (= [["native_id" "concept_id" "metadata" "format" "revision_id" "deleted" "parent_collection_id"]
              ["foo" "G7-PROV1" "<foo>" "xml" 2 false "C5-PROV1"]]
             (c/concept->insert-args concept))))))