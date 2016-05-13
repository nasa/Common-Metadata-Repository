(ns cmr.search.services.transformer
  "Provides functions for retrieving concepts in a desired format."
  (:require [cmr.metadata-db.services.concept-service :as metadata-db]
            [cmr.umm.core :as ummc]
            [cmr.umm.start-end-date :as sed]
            [cmr.umm-spec.core :as umm-spec]
            [cmr.umm-spec.umm-json :as umm-json]
            [cmr.common.cache :as cache]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.mime-types :as mt]
            [cmr.common.services.errors :as errors]
            [clojure.java.io :as io]
            [cmr.search.services.acl-service :as acl-service]
            [cmr.search.services.acls.acl-helper :as acl-helper]
            [cmr.search.services.acls.acl-results-handler-helper :as acl-rhh]
            [cmr.common.xml.xslt :as xslt]
            [cmr.common.util :as u]
            [cmr.umm.iso-smap.granule :as smap-g]
            [cmr.collection-renderer.services.collection-renderer :as collection-renderer]))


(def transformer-supported-format?
  "The set of formats supported by the transformer."
  #{:echo10 :dif :dif10 :iso19115 :iso-smap})

(def types->xsl
  "Defines the [metadata-format target-format] to xsl mapping"
  {[:echo10 :iso19115] (io/resource "xslt/echo10_to_iso19115.xsl")})

(def xsl-transformer-cache-name
  "This is the name of the cache to use for XSLT transformer templates. Templates are thread
  safe but transformer instances are not.
  http://www.onjava.com/pub/a/onjava/excerpt/java_xslt_ch5/?page=9"
  :xsl-transformer-templates)

(defn context->metadata-db-context
  "Converts the context into one that can be used to invoke the metadata-db services."
  [context]
  (assoc context :system (get-in context [:system :embedded-systems :metadata-db])))

(defn- get-template
  "Returns a XSLT template from the filename, using the context cache."
  [context f]
  (cache/get-value
   (cache/context->cache context xsl-transformer-cache-name)
   f
   #(xslt/read-template f)))

(defn- generate-html-response
  "TODO"
  [context concept]
  (let [collection (umm-spec/parse-metadata
                    context :collection (:format concept) (:metadata concept))]
    (collection-renderer/render-collection context collection)))

(defn- transform-metadata
  "Transforms the metadata of the concept to the given format"
  [context concept target-format]
  (let [concept-format (:format concept)]
    (if-let [xsl (types->xsl [(mt/mime-type->format concept-format) target-format])]
      ; xsl is defined for the transformation, so use xslt
      (xslt/transform (:metadata concept) (get-template context xsl))
      (cond
        (= :html target-format)
        (generate-html-response context concept)

        (mt/umm-json? concept-format)
        (umm-spec/generate-metadata context
          (umm-spec/parse-metadata context :collection concept-format (:metadata concept))
          target-format)

        (= :umm-json target-format)
        (umm-json/umm->json
         (umm-spec/parse-metadata context :collection concept-format (:metadata concept)))

        :else
        (-> concept
            ummc/parse-concept
            (ummc/umm->xml target-format))))))

(defn- concept->value-map
  "Convert a concept into a map containing metadata in a desired format as well as
  concept-id, revision-id, and possibly collection-concept-id"
  [context concept target-format]
  (let [collection-concept-id (get-in concept [:extra-fields :parent-collection-id])
        concept-format (mt/mime-type->format (:format concept))
        _ (when-not concept-format
            (errors/internal-error! "Did not recognize concept format" (pr-str (:format concept))))
        value-map (if (or (contains? #{:xml :native} target-format) ;; xml is also a native format
                          (= target-format concept-format))
                    (select-keys concept [:metadata :concept-id :revision-id :format])
                    (let [metadata (transform-metadata context concept target-format)]
                      (assoc (select-keys concept [:concept-id :revision-id])
                             :metadata metadata
                             :format (mt/format->mime-type target-format))))]
    (if collection-concept-id
      (assoc value-map :collection-concept-id collection-concept-id)
      value-map)))

(defn get-formatted-concept-revisions
  "Get concepts with given concept-id, revision-id pairs in a given format. Does not apply acls to
  the concepts found."
  [context concepts-tuples target-format allow-missing?]
  (info "Transforming" (count concepts-tuples) "concept(s) to" target-format)
  (let [mdb-context (context->metadata-db-context context)
        [t1 concepts] (u/time-execution
                        (doall (metadata-db/get-concepts mdb-context concepts-tuples allow-missing?)))
        [t2 values] (u/time-execution
                      (doall (pmap #(concept->value-map context % target-format) concepts)))]
    (debug "get-concept-revisions time:" t1
           "concept->value-map time:" t2)
    values))

(defn get-latest-formatted-concepts
  "Get latest version of concepts with given concept-ids in a given format. Applies ACLs to the concepts
  found."
  ([context concept-ids target-format]
   (get-latest-formatted-concepts context concept-ids target-format false))
  ([context concept-ids target-format skip-acls?]
   (info "Getting latest version of" (count concept-ids) "concept(s) in" target-format "format")

   (let [mdb-context (context->metadata-db-context context)
         [t1 concepts] (u/time-execution
                         (doall (metadata-db/get-latest-concepts mdb-context concept-ids true)))
         ;; Filtering deleted concepts
         [t2 concepts] (u/time-execution (doall (filter #(not (:deleted %)) concepts)))]

     (if skip-acls?
       ;; Convert concepts to results without acl enforcment
       (let [[t3 values] (u/time-execution
                           (doall (pmap #(concept->value-map context % target-format) concepts)))]
         (debug "get-latest-concepts time:" t1
                "tombstone-filter time:" t2
                "concept->value-map time:" t3)
         values)

       ;; Convert concepts to results with acl enforcment
       (let [[t3 concepts] (u/time-execution (acl-rhh/add-acl-enforcement-fields concepts))
             [t4 concepts] (u/time-execution (acl-service/filter-concepts context concepts))
             [t5 values] (u/time-execution
                           (doall (pmap #(concept->value-map context % target-format) concepts)))]
         (debug "get-latest-concepts time:" t1
                "tombstone-filter time:" t2
                "add-acl-enforcement-fields time:" t3
                "acl-filter-concepts time:" t4
                "concept->value-map time:" t5)
         values)))))

(defn get-formatted-concept
  "Get a specific revision of a concept with the given concept-id in a given format.
  Applies ACLs to the concept found."
  [context concept-id revision-id target-format]
  (info "Getting revision" revision-id "of concept" concept-id "in" target-format "format")
  (let [mdb-context (context->metadata-db-context context)
        [t1 concept] (u/time-execution
                       (metadata-db/get-concept mdb-context concept-id revision-id))
        ;; Throw a service error for deleted concepts
        _ (when (:deleted concept)
            (errors/throw-service-errors
              :bad-request
              [(format
                 "The revision [%d] of concept [%s] represents a deleted concept and does not contain metadata."
                 revision-id
                 concept-id)]))
        [t2 concept] (u/time-execution (acl-rhh/add-acl-enforcement-fields-to-concept concept))
        [t3 [concept]] (u/time-execution (acl-service/filter-concepts context [concept]))
        ;; format concept
        [t4 value] (u/time-execution (when concept (concept->value-map context concept target-format)))]
    (debug "get-concept time:" t1
           "add-acl-enforcement-fields time:" t2
           "acl-filter-concepts time:" t3
           "concept->value-map time:" t4)
    value))
