(ns cmr.metadata-db.int-test.concepts.search-test
  "Contains integration tests for getting concepts. Tests gets with various
  configurations including checking for proper error handling."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [clojure.data]
            [cmr.metadata-db.int-test.utility :as util]
            [cmr.metadata-db.services.messages :as msg]))

(use-fixtures :each (util/reset-database-fixture "PROV1" "PROV2"))

(deftest search-by-concept-revision-id-tuples
  (let [coll1 (util/create-and-save-collection "PROV1" 1 3)
        coll2 (util/create-and-save-collection "PROV1" 2 3)
        coll3 (util/create-and-save-collection "PROV2" 3 3)
        gran1 (util/create-and-save-granule "PROV1" (:concept-id coll1) 1 2)
        gran2 (util/create-and-save-granule "PROV1" (:concept-id coll2) 2 2)]
    (are [item-revision-tuples]
         (let [tuples (map #(update-in % [0] :concept-id) item-revision-tuples)
               {:keys [status concepts]} (util/get-concepts tuples)
               expected-concepts (map (fn [[item revision]]
                                        (assoc item :revision-id revision))
                                      item-revision-tuples)]
           (and (= 200 status)
                (= expected-concepts concepts)))
         ; one collection
         [[coll1 0]]
         ;; two collections
         [[coll1 1] [coll2 0]]
         ;; multiple versions of same collection
         [[coll1 1] [coll1 0]]
         ; granules and collections
         [[gran1 1] [gran1 0] [gran2 1] [coll3 2] [coll1 1]])))

(deftest get-concepts-with-one-invalid-revision-id-test
  (let [coll1 (util/create-and-save-collection "PROV1" 1)
        tuples [[(:concept-id coll1) 0]
                [(:concept-id coll1) 1]
                ["C2-PROV1" 0] ]
        result (util/get-concepts tuples)]
    (is (= {:status 404
            :errors  [(msg/concept-with-concept-id-and-rev-id-does-not-exist (:concept-id coll1) 1)
                      (msg/concept-with-concept-id-and-rev-id-does-not-exist "C2-PROV1" 0)]}
           result))))
