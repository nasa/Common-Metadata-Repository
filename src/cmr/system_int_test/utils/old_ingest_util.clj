(ns cmr.system-int-test.utils.old-ingest-util
  "Deprecated namespace that will eventually be removed. New tests should not use this"
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [clojure.string :as str]
            [cheshire.core :as cheshire]
            [cmr.system-int-test.data.collection-helper :as ch]
            [cmr.system-int-test.data.granule-helper :as gh]
            [cmr.umm.echo10.collection :as c]
            [cmr.umm.echo10.granule :as g]
            [cmr.umm.echo10.core :as echo10]
            [cmr.system-int-test.utils.url-helper :as url]
            [cmr.system-int-test.utils.ingest-util :as ingest]))

(def default-collection {:short-name "MINIMAL"
                         :version-id "1"
                         :long-name "A minimal valid collection"
                         :entry-title "MinimalCollectionV1"})

(defn collection-xml
  "Returns metadata xml of the collection"
  [field-values]
  (echo10/umm->echo10-xml (ch/collection field-values)))

(defn granule-xml
  "Returns metadata xml of the granule"
  [field-values]
  (echo10/umm->echo10-xml (gh/granule field-values)))

(defn update-granule
  "Update granule (given or the default one) through CMR metadata API.
  TODO Returns cmr-granule id eventually"
  ([provider-id]
   (update-granule provider-id {}))
  ([provider-id granule]
   (let [granule-xml (granule-xml granule)
         response (client/put (url/ingest-url provider-id :granule (:granule-ur granule))
                              {:content-type :echo10+xml
                               :body granule-xml
                               :throw-exceptions false
                               :connection-manager (url/conn-mgr)})]
     (is (some #{201 200} [(:status response)])))))

(defn delete-collection
  "Delete the collection with the matching native-id from the CMR metadata repo.
  native-id is equivalent to dataset id.
  I call it native-id because the id in the URL used by the provider-id does not have to be
  the dataset id in the collection in general even though catalog rest will enforce this."
  [provider-id native-id]
  (let [response (client/delete (url/ingest-url provider-id :collection native-id)
                                {:throw-exceptions false
                                 :connection-manager (url/conn-mgr)})
        status (:status response)]
    (is (some #{200 404} [status]))))

(defn delete-granule
  "Delete the granule with the matching native-id from the CMR metadata repo.
  native-id is equivalent to dataset id.
  I call it native-id because the id in the URL used by the provider-id does not have to be
  the dataset id in the granule in general even though catalog rest will enforce this."
  [provider-id native-id]
  (let [response (client/delete (url/ingest-url provider-id :granule native-id)
                                {:throw-exceptions false
                                 :connection-manager (url/conn-mgr)})
        status (:status response)]
    (is (some #{200 404} [status]))))

;;; data/functions used in concept_ingest_test.clj
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; DEPRECATED function
;; Use data2 namespace instead
(def base-concept-attribs
  {:short-name "SN-Sedac88"
   :version-id "Ver88"
   :entry-title "ABCDEF"
   :long-name "LongName Sedac88"
   :dataset-id "LarcDatasetId88"})

;; DEPRECATED function
;; Use data2 namespace instead
(defn collection-concept
  "Creates a collection concept"
  [provider-id uniq-num]
  {:concept-type :collection
   :native-id (str "native-id " uniq-num)
   :provider-id provider-id
   :metadata (collection-xml base-concept-attribs)
   :content-type "application/echo10+xml"
   :deleted false
   :extra-fields {:short-name (str "short" uniq-num)
                  :version-id (str "V" uniq-num)
                  :entry-title (str "dataset" uniq-num)}})

;; DEPRECATED function
;; Use data2 namespace instead
(defn granule-concept
  "Creates a granule concept"
  [provider-id parent-collection-id uniq-num & concept-id]
  (let [granule {:concept-type :granule
                 :native-id (str "native-id " uniq-num)
                 :provider-id provider-id
                 :metadata (granule-xml base-concept-attribs)
                 :content-type "application/echo10+xml"
                 :deleted false
                 :extra-fields {:parent-collection-id parent-collection-id}}]
    (if concept-id
      (assoc granule :concept-id (first concept-id))
      granule)))

;; DEPRECATED function
;; Use data2 namespace instead
(defn distinct-concept
  "Generate a concept"
  [provider-id unique-id]
  (let [concept (hash-map :concept-type :collection
                          :provider-id provider-id
                          :native-id (str "nativeId" unique-id)
                          :metadata (collection-xml base-concept-attribs)
                          :content-type "application/echo10+xml")]
    concept))

;; DEPRECATED function
;; Use data2 namespace instead
(defn distinct-concept-w-concept-id
  "Simulates a concept with provider supplied concept-id"
  [provider-id unique-id]
  (let [concept (hash-map :concept-type :collection
                          :provider-id provider-id
                          :native-id (str "nativeId" unique-id)
                          :metadata (collection-xml base-concept-attribs)
                          :content-type "application/echo10+xml")
        concept-id (format "C%s-%s" unique-id provider-id)]
    (assoc concept :concept-id concept-id)))
