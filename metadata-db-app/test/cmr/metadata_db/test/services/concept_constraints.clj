(ns cmr.metadata-db.test.services.concept-constraints
  "Unit tests to verify post-commit concept constraints are enforced."
  (:require [clojure.test :refer :all]
            [cmr.metadata-db.services.concept-constraints :as cc]))

(def invalid-concepts
  [{:provider-id "PROV1" :metadata "xml here" :format "echo10" :concept-type :collection
    :concept-id "C1" :revision-id 1 :entry-title "E1" :deleted 0}
   {:provider-id "PROV1" :metadata "xml here" :format "echo10" :concept-type :collection
    :concept-id "C1" :revision-id 2 :entry-title "E1" :deleted 0}
   {:provider-id "PROV1" :metadata "xml here" :format "echo10" :concept-type :collection
    :concept-id "C2" :revision-id 1 :entry-title "E1" :deleted 0}
   {:provider-id "PROV1" :metadata "xml here" :format "echo10" :concept-type :collection
    :concept-id "C2" :revision-id 2 :entry-title "E1" :deleted 1}
   {:provider-id "PROV1" :metadata "xml here" :format "echo10" :concept-type :collection
    :concept-id "C3" :revision-id 1 :entry-title "E1" :deleted 0}
   {:provider-id "PROV1" :metadata "xml here" :format "echo10" :concept-type :collection
    :concept-id "C4" :revision-id 1 :entry-title "E1" :deleted 0}
   {:provider-id "PROV1" :metadata "xml here" :format "echo10" :concept-type :collection
    :concept-id "C4" :revision-id 2 :entry-title "E1" :deleted 1}
   {:provider-id "PROV1" :metadata "xml here" :format "echo10" :concept-type :collection
    :concept-id "C4" :revision-id 20 :entry-title "E1" :deleted 0}])

(def valid-concepts
  [{:provider-id "PROV1" :metadata "xml here" :format "echo10" :concept-type :collection
    :concept-id "C1" :revision-id 1 :entry-title "E1" :deleted 0}
   {:provider-id "PROV1" :metadata "xml here" :format "echo10" :concept-type :collection
    :concept-id "C1" :revision-id 2 :entry-title "E1" :deleted 1}
   {:provider-id "PROV1" :metadata "xml here" :format "echo10" :concept-type :collection
    :concept-id "C2" :revision-id 1 :entry-title "E1" :deleted 0}
   {:provider-id "PROV1" :metadata "xml here" :format "echo10" :concept-type :collection
    :concept-id "C2" :revision-id 2 :entry-title "E1" :deleted 1}
   {:provider-id "PROV1" :metadata "xml here" :format "echo10" :concept-type :collection
    :concept-id "C3" :revision-id 1 :entry-title "E1" :deleted 0}
   ;; Use this in integration test to make sure entry-title is successfully filtered on
   ; {:provider-id "PROV1" :metadata "xml here" :format "echo10" :concept-type :collection
   ; :concept-id "C4" :revision-id 1 :entry-title "E2" :deleted 0}
   ])

(deftest verify-remove-old-revisions-and-tombstones
  ;; Make sure that filtering out old revisions and tombstones works as expected.
  (testing "Verifying remove-old-revisions-and-tombstones returns the correct concepts"
    (is (= 3 (count (cc/remove-old-revisions-and-tombstones invalid-concepts))))
    (is (= 1 (count (cc/remove-old-revisions-and-tombstones valid-concepts))))))
