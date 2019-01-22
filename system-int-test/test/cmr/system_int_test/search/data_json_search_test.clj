(ns cmr.system-int-test.search.data-json-search-test
  "This namespace contains tests for the data.json endpoint."
  (:require
   [clojure.test :refer :all]
   [cmr.common-app.test.side-api :as side-api]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.search.api.concepts-search :as concepts-search]
   [cmr.system-int-test.data2.core :as core]
   [cmr.system-int-test.data2.umm-spec-collection :as umm-spec-collection]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.index-util :as index-util]
   [cmr.system-int-test.utils.ingest-util :as ingest-util]
   [cmr.system-int-test.utils.search-util :as search-util]
   [cmr.system-int-test.utils.tag-util :as tag-util]
   [cmr.transmit.config :as config]))

(def ^:private coll-idx (atom 0))

(defn- data-json-fixture
  "Reset coll-idx for collection creation."
  [f]
  (reset! coll-idx 0)
  (f))

(use-fixtures :each (join-fixtures
                      [(ingest-util/reset-fixture {"provguid1" "PROV1"}
                                                  {:grant-all-search? false})
                       tag-util/grant-all-tag-fixture
                       data-json-fixture]))

(defn- create-collections
  "Create num-collections collections. Default provider is PROV1."
  ([num-collections]
   (create-collections num-collections "PROV1"))
  ([num-collections provider]
   (let [start-idx @coll-idx
         end-idx (+ start-idx num-collections)]
     (reset! coll-idx end-idx)
     (doall
      (for [idx (range start-idx end-idx)]
        (core/ingest-umm-spec-collection
         provider
         (umm-spec-collection/collection idx {})))))))

(defn- make-collections-public
  "Take collection of umm-spec collections and make public. Default provider is PROV1."
  ([collections]
   (make-collections-public collections "PROV1"))
  ([collections provider]
   (->> (mapv :EntryTitle collections)
        echo-util/coll-id
        (echo-util/coll-catalog-item-id provider)
        (echo-util/grant-guest (system/context)))))

(defn- associate-collections
  "Associate collections with tag."
  [tag collections]
  (tag-util/associate-by-concept-ids
   (config/echo-system-token)
   (:tag-key tag)
   (map #(hash-map :concept-id (:concept-id %)) collections)))

(deftest data-json-response
  (let [tag (tag-util/make-tag {:tag-key "gov.nasa.eosdis"})
        public-collections-with-tag (create-collections 7)
        public-collections-without-tag (create-collections 6)
        private-collections-with-tag (create-collections 5)
        private-collections-without-tag (create-collections 4)]
    (tag-util/create-tag (config/echo-system-token) tag)

    ;; Make collections public
    (make-collections-public public-collections-with-tag)
    (make-collections-public public-collections-without-tag)
    (ingest-util/reindex-collection-permitted-groups (config/echo-system-token))
    (index-util/wait-until-indexed)

    ;; Associate collections with tag
    (associate-collections tag public-collections-with-tag)
    (associate-collections tag private-collections-with-tag)
    (index-util/wait-until-indexed)

    (testing "Only the public collections with the gov.nasa.eosdis tag are in the response"
      (let [data-json (search-util/retrieve-data-json)
            datasets (get-in data-json [:results :dataset])]
        (is (= 200 (:status data-json)))
        (is (= (count public-collections-with-tag) (count datasets)))
        (is (= (set (map :concept-id public-collections-with-tag))
               (set (map :identifier datasets))))))))
