(ns cmr.search.test.unit.services.generic-association-service
  (:require
   [clojure.test :refer [deftest is testing]]
   [cmr.metadata-db.services.concept-service :as mdb-cs]
   [cmr.search.services.association-service :as assoc-service]
   [cmr.search.services.generic-association-service :as g-assoc-service]))

#_{:clj-kondo/ignore [:unused-binding]}
(deftest save-concept-in-mdb-test
  (with-redefs [mdb-cs/save-concept-revision (fn [context concept] {:concept-id "T123-TEST", :revision-id 1})]
    (testing "Testing generic association service save concept in mdb"
      (is (= {:concept-id "T123-TEST", :revision-id 1} (#'g-assoc-service/save-concept-in-mdb {} {}))))
    
    (testing "Testing association service save concept in mdb"
      (is (= {:concept-id "T123-TEST", :revision-id 1} (#'assoc-service/save-concept-in-mdb {} {}))))))
