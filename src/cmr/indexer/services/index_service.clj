(ns cmr.indexer.services.index-service
  "Provide functions to index concept"
  (:require [clojure.string :as s]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.services.errors :as errors]
            [cmr.indexer.data.metadata-db :as meta-db]
            [cmr.indexer.data.elasticsearch :as es]
            [cmr.umm.echo10.collection :as collection]
            [cmr.umm.echo10.granule :as granule]
            [cheshire.core :as cheshire]
            [cmr.system-trace.core :refer [deftracefn]]))


(def concept-prefix->type
  {\C :collection
   \G :granule})

(defn- concept-id->type
  "Returns concept type for the given concept-id"
  [concept-id]
  (concept-prefix->type (first concept-id)))

(defmulti parse-concept
  "Returns the UMM model of the concept by parsing its metadata field"
  (fn [concept]
    (concept-id->type (:concept-id concept))))

(defmulti concept->elastic-doc
  "Returns elastic json that can be used to insert into Elasticsearch for the given concept"
  (fn [concept umm-concept]
    (concept-id->type (:concept-id concept))))

(deftracefn index-concept
  "Index the given concept and revision-id"
  [context concept-id revision-id ignore-conflict]
  (info (format "Indexing concept %s, revision-id %s" concept-id revision-id))
  (let [concept (meta-db/get-concept context concept-id revision-id)
        concept-type (concept-id->type concept-id)
        umm-concept (parse-concept concept)
        es-doc (concept->elastic-doc concept umm-concept)]
    (es/save-document-in-elastic
      context (@es/es-concept-indices concept-type) (@es/es-concept-mapping-types concept-type) es-doc (Integer. revision-id) ignore-conflict)))


(deftracefn delete-concept
  "Delete the concept with the given id"
  [context id revision-id ignore-conflict]
  (info (format "Deleting concept %s, revision-id %s" id revision-id))
  ;; Assuming ingest will pass enough info for deletion
  ;; We should avoid making calls to metadata db to get the necessary info if possible
  (let [es-config (-> context :system :db :config)
        concept-type (concept-id->type id)]
    (es/delete-document-in-elastic
      context es-config (@es/es-concept-indices concept-type) (@es/es-concept-mapping-types concept-type) id revision-id ignore-conflict)))


(deftracefn reset-indexes
  "Delegate reset elastic indices operation to index-set app"
  [context]
  (es/reset-es-store))

(comment
  (let [concept-id "G1234-PROV1"
        revision-id "2"
        ignore-conflict true
        concept {:concept-id concept-id
                 :provider-id "PROV1"
                 :granule-ur "DummyGranuleUR"
                 :extra-fields {:parent-collection-id "C1234-PROV1"}}
        concept-type (concept-id->type concept-id)
        umm-concept nil
        es-doc (concept->elastic-doc concept umm-concept)]
    (es/save-document-in-elastic
      {} (es-index concept-type) (es-mapping-type concept-type) es-doc (Integer. revision-id) ignore-conflict))

  (def valid-collection-xml
  "<Collection>
    <ShortName>MINIMAL</ShortName>
    <VersionId>1</VersionId>
    <InsertTime>1999-12-31T19:00:00-05:00</InsertTime>
    <LastUpdate>1999-12-31T19:00:00-05:00</LastUpdate>
    <LongName>A minimal valid collection</LongName>
    <DataSetId>A minimal valid collection V 1</DataSetId>
    <Description>A minimal valid collection</Description>
    <Orderable>true</Orderable>
    <Visible>true</Visible>
  </Collection>")

(let [concept-id "C1234-PROV1"
        revision-id "1"
        ignore-conflict true
        concept {:concept-id concept-id
                 :provider-id "PROV1"
                 :metadata valid-collection-xml}
        concept-type (concept-id->type concept-id)
        umm-concept (parse-concept concept)
        es-doc (concept->elastic-doc concept umm-concept)]
    (es/save-document-in-elastic
      {} (es-index concept-type) (es-mapping-type concept-type) es-doc (Integer. revision-id) ignore-conflict))

  )