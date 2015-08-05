(ns cmr.search.services.transformer
  "Provides functions for retrieving concepts in a desired format."
  (:require [cmr.system-trace.core :refer [deftracefn]]
            [cmr.metadata-db.services.concept-service :as metadata-db]
            [cmr.umm.core :as ummc]
            [cmr.common.cache :as cache]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.mime-types :as mt]
            [cmr.common.services.errors :as errors]
            [clojure.java.io :as io]
            [cmr.search.services.acl-service :as acl-service]
            [cmr.common.xml.xslt :as xslt]
            [cmr.common.util :as u]
            [cmr.umm.iso-smap.granule :as smap-g]))

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

(defn- transform-metadata
  "Transforms the metadata of the concept to the given format"
  [context concept target-format]
  (let [native-format (mt/mime-type->format (:format concept))]
    (if-let [xsl (types->xsl [native-format target-format])]
      ; xsl is defined for the transformation, so use xslt
      (xslt/transform (:metadata concept) (get-template context xsl))
      (-> concept
          ummc/parse-concept
          (ummc/umm->xml target-format)))))

(defn- concept->value-map
  "Convert a concept into a map containing metadata in a desired format as well as
  concept-id, revision-id, and possibly collection-concept-id"
  [context concept format]
  (let [collection-concept-id (get-in concept [:extra-fields :parent-collection-id])
        concept-format (mt/mime-type->format (:format concept))
        _ (when-not concept-format
            (errors/internal-error! "Did not recognize concept format" (pr-str (:format concept))))
        value-map (if (or (contains? #{:xml :native} format) ;; xml is also a native format
                          (= format concept-format))
                    (select-keys concept [:metadata :concept-id :revision-id :format])
                    (let [metadata (transform-metadata context concept format)]
                      (assoc (select-keys concept [:concept-id :revision-id])
                             :metadata metadata
                             :format (mt/format->mime-type format))))]
    (if collection-concept-id
      (assoc value-map :collection-concept-id collection-concept-id)
      value-map)))

(deftracefn get-formatted-concept-revisions
  "Get concepts with given concept-id, revision-id pairs in a given format. Does not apply acls to
  the concepts found."
  [context concepts-tuples format allow-missing?]
  (info "Transforming" (count concepts-tuples) "concept(s) to" format)
  (let [mdb-context (context->metadata-db-context context)
        [t1 concepts] (u/time-execution
                        (doall (metadata-db/get-concepts mdb-context concepts-tuples allow-missing?)))
        [t2 values] (u/time-execution
                      (doall (pmap #(concept->value-map context % format) concepts)))]
    (debug "get-concept-revisions time:" t1
           "concept->value-map time:" t2)
    values))

(defn- get-iso-access-value
  "Returns the iso-mends access value by parsing MENDS ISO xml"
  [concept]
  (when-let [[_ restriction-flag-str]
             (re-matches #"(?s).*<gco:CharacterString>Restriction Flag:(.+?)</gco:CharacterString>.*"
                         (:metadata concept))]
    (when-not (re-find #".*<.*" restriction-flag-str)
      (Double. ^String restriction-flag-str))))

(defmulti extract-access-value
  "Extracts access value (aka. restriction flag) from the concept."
  (fn [concept]
    (:format concept)))

(defmethod extract-access-value mt/echo10
  [concept]
  (let [^String metadata (:metadata concept)]
    ;; This contains check is a performance enhancement. This saves a lot of time versus the regular
    ;; expression below when the metadata is a large string.
    (when (.contains metadata "<RestrictionFlag>")
      (when-let [[_ restriction-flag-str] (re-matches #"(?s).*<RestrictionFlag>(.+)</RestrictionFlag>.*"
                                                      metadata)]
        (Double. ^String restriction-flag-str)))))

(defmethod extract-access-value mt/dif
  [concept]
  ;; DIF doesn't support restriction flag yet.
  nil)

(defmethod extract-access-value mt/dif10
  [concept]
  ;; TODO: Add support for Access Constraints, CMR-1574
  nil)

(defmethod extract-access-value mt/iso
  [concept]
  (get-iso-access-value concept))

(defmethod extract-access-value mt/iso-smap
  [concept]
  (when (= :granule (:concept-type concept))
    (smap-g/xml->access-value (:metadata concept))))

(defmulti add-acl-enforcement-fields
  "Adds the fields necessary to enforce ACLs to the concept"
  (fn [concept]
    (:concept-type concept)))

(defmethod add-acl-enforcement-fields :collection
  [concept]
  (assoc concept
         :access-value (extract-access-value concept)
         :entry-title (get-in concept [:extra-fields :entry-title])))

(defmethod add-acl-enforcement-fields :granule
  [concept]
  (assoc concept
         :access-value (extract-access-value concept)
         :collection-concept-id (get-in concept [:extra-fields :parent-collection-id])))

(deftracefn get-latest-formatted-concepts
  "Get latest version of concepts with given concept-ids in a given format. Applies ACLs to the concepts
  found."
  ([context concept-ids format]
   (get-latest-formatted-concepts context concept-ids format false))
  ([context concept-ids format skip-acls?]
   (info "Getting latest version of" (count concept-ids) "concept(s) in" format "format")
   (let [mdb-context (context->metadata-db-context context)
         [t1 concepts] (u/time-execution
                         (doall (metadata-db/get-latest-concepts mdb-context concept-ids true)))
         [t2 concepts] (u/time-execution (if skip-acls?
                                           concepts
                                           (doall (acl-service/filter-concepts
                                                    context
                                                    (pmap add-acl-enforcement-fields concepts)))))
         ;; Filtering deleted concepts
         [t3 concepts] (u/time-execution (doall (filter #(not (:deleted %)) concepts)))
         [t4 values] (u/time-execution
                       (doall (pmap #(concept->value-map context % format) concepts)))]
     (debug "get-latest-concepts time:" t1
            "acl-filter-concepts time:" t2
            "tombstone-filter time:" t3
            "concept->value-map time:" t4)
     values)))

(deftracefn get-formatted-concept
  "Get a specific revision of a concept with the given concept-id in a given format.
  Applies ACLs to the concept found."
  [context concept-id revision-id format]
   (info "Getting revision" revision-id "of concept" concept-id "in" format "format")
   (let [mdb-context (context->metadata-db-context context)
         [t1 concept] (u/time-execution
                         (metadata-db/get-concept mdb-context concept-id revision-id))
         [t2 concepts] (u/time-execution (doall (acl-service/filter-concepts
                                                    context
                                                    [(add-acl-enforcement-fields concept)])))
         concept (first concepts)
         ;; Throw a service error for deleted concepts
         _ (when (:deleted concept)
             (errors/throw-service-errors :bad-request ["Deleted concepts do not contain metadata."]))
         ;; format concept
         [t4 value] (u/time-execution (when concept (concept->value-map context concept format)))]
     (debug "get-concept time:" t1
            "acl-filter-concepts time:" t2
            "concept->value-map time:" t4)
     value))

(deftracefn get-concepts
  "Get multiple concepts by concept-id and revision-id. Returns concepts in order requested"
  [context concept-id-revision-id-tuples]
   (info "Getting concepts by concept-id & revision-id tuples.")
   (let [mdb-context (context->metadata-db-context context)
         [t1 concepts] (u/time-execution
                         (metadata-db/get-concepts mdb-context concept-id-revision-id-tuples false))]
     (debug "get-concepts time:" t1)
     concepts))
