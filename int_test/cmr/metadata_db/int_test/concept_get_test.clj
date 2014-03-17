(ns cmr.metadata-db.int-test.concept-get-test
  "Contains integration tests for getting concepts. Tests gets with various 
  configurations including checking for proper error handling."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [cmr.metadata-db.int-test.utility :as util]))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn setup-database-fixture
  "Load the database with test data."
  [f]
  ;; setup database
  (let [concept1 (util/concept)
        concept2 (assoc concept1 :concept-id "C2-PROV1")]
    ;; save a concept
    (util/save-concept concept1)
    ;; save a revision
    (util/save-concept concept1)
    ;; save it a third time
    (util/save-concept concept1)
   	;; save another concept
    (util/save-concept concept2))
  
  (f)
  
  ;; clear out the database
  (util/reset-database))


(use-fixtures :once setup-database-fixture)

;;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest mdb-get-concept-test
  "Get the latest version of a concept by concept-id."
  (let [{:keys [status concept]} (util/get-concept-by-id (:concept-id (util/concept)))]
    (is (and (= status 200) (= (:revision-id concept) 2)))))

(deftest mdb-get-concept-with-version-test
  "Get a concept by concept-id and version-id."
  (let [{:keys [status concept]} (util/get-concept-by-id-and-revision (:concept-id (util/concept)) 1)]
    (is (and (= status 200) (= (:revision-id concept) 1)))))
    
    
    