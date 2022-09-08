(ns cmr.metadata-db.test.data.oracle.concepts
  (:require
   [clj-time.coerce :as cr]
   [clj-time.core :as t]
   [clj-time.format :as f]
   [clojure.java.jdbc :as j]
   [clojure.test :refer :all]
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.util :as util]
   [cmr.metadata-db.config :as mdb-config]
   [cmr.metadata-db.data.memory-db :as memory]
   [cmr.metadata-db.data.oracle.concepts :as c]
   [cmr.metadata-db.data.oracle.concepts.collection]
   [cmr.metadata-db.data.oracle.concepts.granule]
   [cmr.oracle.config :as oracle-config]
   [cmr.oracle.connection :as oracle])
  (:import
   (javax.sql.rowset.serial SerialBlob)
   (java.util.zip GZIPInputStream)
   (java.io ByteArrayInputStream)
   (oracle.sql TIMESTAMPTZ)))

(defn mock-blob
  "Create a mock blob"
  [value]
  (SerialBlob. (util/string->gzip-bytes value)))

(defn gzip-bytes->string
  "Convert compressed byte array to string"
  [input]
  (-> input ByteArrayInputStream. GZIPInputStream. slurp))

(defn fix-result
  [result]
  (let [vector-result (apply vector (map #(apply vector %) result))]
    (update-in vector-result [1 2] #(gzip-bytes->string %))))

;; This test is commented out until CMR-1303 is resolved
#_(deftest db-result->concept-map-test
   (let [db (->> (mdb-config/db-spec "metadata-db-test")
                 oracle/create-db
                 (#(lifecycle/start % nil)))]
     (try
       (j/with-db-transaction
         [db db]
         (let [revision-time (t/date-time 1986 10 14 4 3 27 456)
               oracle-timestamp (TIMESTAMPTZ. ^java.sql.Connection (oracle/db->oracle-conn db)
                                              ^java.sql.Timestamp (cr/to-sql-time revision-time))]
           (testing "collection results"
             (let [result {:native_id "foo"
                           :concept_id "C5-PROV1"
                           :metadata (mock-blob "<foo>")
                           :format "ECHO10"
                           :revision_id 2
                           :revision_date oracle-timestamp
                           :deleted 0
                           :short_name "short"
                           :version_id "v1"
                           :entry_id "short_v1"
                           :entry_title "entry"
                           :delete_time oracle-timestamp}]
               (is (= {:concept-type :collection
                       :native-id "foo"
                       :concept-id "C5-PROV1"
                       :provider-id "PROV1"
                       :metadata "<foo>"
                       :format "application/echo10+xml"
                       :revision-id 2
                       :revision-date "1986-10-14T04:03:27.456Z"
                       :deleted false
                       :extra-fields {:short-name "short"
                                      :version-id "v1"
                                      :entry-id "short_v1"
                                      :entry-title "entry"
                                      :delete-time "1986-10-14T04:03:27.456Z"}}
                      (c/db-result->concept-map :collection db "PROV1" result)))))
           (testing "granule results"
             (let [result {:native_id "foo"
                           :concept_id "G7-PROV1"
                           :metadata (mock-blob "<foo>")
                           :format "ECHO10"
                           :revision_date oracle-timestamp
                           :revision_id 2
                           :deleted 0
                           :parent_collection_id "C5-PROV1"
                           :delete_time oracle-timestamp
                           :granule_ur "foo-ur"}]
               (is (= {:concept-type :granule
                       :native-id "foo"
                       :concept-id "G7-PROV1"
                       :provider-id "PROV1"
                       :metadata "<foo>"
                       :format "application/echo10+xml"
                       :revision-id 2
                       :revision-date "1986-10-14T04:03:27.456Z"
                       :deleted false
                       :extra-fields {:parent-collection-id "C5-PROV1"
                                      :delete-time "1986-10-14T04:03:27.456Z"
                                      :granule-ur "foo-ur"}}
                      (c/db-result->concept-map :granule db "PROV1" result)))))))
       (finally
         (lifecycle/stop db nil)))))

(deftest concept->insert-args-test
  (testing "collection insert-args"
    (let [revision-time (t/date-time 1986 10 14 4 3 27 456)
          sql-timestamp (cr/to-sql-time revision-time)
          concept {:concept-type :collection
                   :native-id "foo"
                   :concept-id "C5-PROV1"
                   :provider-id "PROV1"
                   :metadata "<foo>"
                   :format "application/echo10+xml"
                   :revision-id 2
                   :deleted false
                   :user-id "usr-id"
                   :extra-fields {:short-name "short"
                                  :version-id "v1"
                                  :entry-id "short_v1"
                                  :entry-title "entry"
                                  :delete-time "1986-10-14T04:03:27.456Z"}}]
      (is (= [["native_id" "concept_id" "metadata" "format" "revision_id" "deleted"
               "short_name" "version_id" "entry_id" "entry_title" "delete_time" "user_id"]
              ["foo" "C5-PROV1" "<foo>" "ECHO10" 2 false "short" "v1" "short_v1" "entry"
               sql-timestamp "usr-id"]]
             (fix-result (c/concept->insert-args concept false))))
      (is (= [["native_id" "concept_id" "metadata" "format" "revision_id" "deleted"
               "short_name" "version_id" "entry_id" "entry_title" "delete_time"
               "user_id" "provider_id"]
              ["foo" "C5-PROV1" "<foo>" "ECHO10" 2 false "short" "v1" "short_v1" "entry"
               sql-timestamp "usr-id" "PROV1"]]
             (fix-result (c/concept->insert-args concept true))))))
  (testing "granule insert-args"
    (let [revision-time (t/date-time 1986 10 14 4 3 27 456)
          sql-timestamp (cr/to-sql-time revision-time)
          concept {:concept-type :granule
                   :native-id "foo"
                   :concept-id "G7-PROV1"
                   :provider-id "PROV1"
                   :metadata "<foo>"
                   :format "application/echo10+xml"
                   :revision-id 2
                   :deleted false
                   :extra-fields {:parent-collection-id "C5-PROV1"
                                  :delete-time "1986-10-14T04:03:27.456Z"
                                  :granule-ur "foo-ur"}}]
      (is (= [["native_id" "concept_id" "metadata" "format" "revision_id" "deleted"
               "parent_collection_id" "delete_time" "granule_ur"]
              ["foo" "G7-PROV1" "<foo>" "ECHO10" 2 false "C5-PROV1" sql-timestamp "foo-ur"]]
             (fix-result (c/concept->insert-args concept false))))
      (is (= [["native_id" "concept_id" "metadata" "format" "revision_id" "deleted"
               "parent_collection_id" "delete_time" "granule_ur" "provider_id"]
              ["foo" "G7-PROV1" "<foo>" "ECHO10" 2 false "C5-PROV1" sql-timestamp "foo-ur" "PROV1"]]
             (fix-result (c/concept->insert-args concept true)))))))
