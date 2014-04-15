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
                  :deleted 0
                  :short_name "short"
                  :version_id "v1"
                  :entry_title "entry"}]
      (is (= {:concept-type :collection
              :native-id "foo"
              :concept-id "C5-PROV1"
              :provider-id "PROV1"
              :metadata "<foo>"
              :format "xml"
              :revision-id 2
              :deleted false
              :extra-fields {:short-name "short"
                             :version-id "v1"
                             :entry-title "entry"}}
             (c/db-result->concept-map :collection "PROV1" result)))))
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
                :extra-fields {:parent-collection-id "C5-PROV1"}}
               (c/db-result->concept-map :granule "PROV1" result))))))

(deftest concept->insert-args-test
  (testing "collection insert-args"
    (let [concept {:concept-type :collection
                   :native-id "foo"
                   :concept-id "C5-PROV1"
                   :provider-id "PROV1"
                   :metadata "<foo>"
                   :format "xml"
                   :revision-id 2
                   :deleted false
                   :extra-fields {:short-name "short"
                                  :version-id "v1"
                                  :entry-title "entry"}}]
      (is (= [["native_id" "concept_id" "metadata" "format" "revision_id" "deleted"
               "short_name" "version_id" "entry_title"]
              ["foo" "C5-PROV1" "<foo>" "xml" 2 false "short" "v1" "entry"]]
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
                   :extra-fields {:parent-collection-id "C5-PROV1"}}]
      (is (= [["native_id" "concept_id" "metadata" "format" "revision_id" "deleted" "parent_collection_id"]
              ["foo" "G7-PROV1" "<foo>" "xml" 2 false "C5-PROV1"]]
             (c/concept->insert-args concept))))))