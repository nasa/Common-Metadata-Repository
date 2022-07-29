(ns cmr.virtual-product.services.translation-service
  "Handles translate virtual products granule entries to source granule entries."
  (:require
   [cheshire.core :as json]
   [cmr.common-app.services.search.parameter-validation :as parameter-validation]
   [cmr.common.concepts :as concepts]
   [cmr.common.services.errors :as errors]
   [cmr.common.validations.json-schema :as js]
   [cmr.transmit.config :as transmit-config]
   [cmr.transmit.search :as search]
   [cmr.virtual-product.config :as config]
   [cmr.virtual-product.data.source-to-virtual-mapping :as svm]))

(def granule-entries-schema
  "Schema for the JSON request to the translate-granule-entries end-point"
  (js/parse-json-schema
    {:type :array
     :items {:type :object
             :properties {:concept-id {:type :string}
                          :entry-title {:type :string}
                          :granule-ur {:anyOf [{:type :string}
                                               {:type :null}]}}
             :required [:concept-id :entry-title :granule-ur]}
     :minItems 1}))

(defn- annotate-entries
  "Annotate entries with provider id derived from concept-id present in the entry"
  [entries]
  (for [entry entries
        :let [{:keys [provider-id]} (concepts/parse-concept-id (:concept-id entry))]]
    (with-meta entry {:provider-id provider-id})))

(defn- get-prov-id-gran-ur
  "Get a vector consisting of provider id and granule ur for an annotated entry"
  [entry]
  [(:provider-id (meta entry)) (:granule-ur entry)])

;; We can find source granule from virtual granule-ur by a query on the virtual granules, all of which
;; have the additional attribute source_granule_ur which holds the source granule ur. But we avoid
;; the query by computing the inverse of generate-granule-ur function in config (which is used to generate
;; virtual granule ur from source granule ur)
(defn- compute-source-granule-urs
  "Compute source granule-urs from virtual granule-urs"
  [provider-id src-entry-title gran-entries]
  (for [{:keys [granule-ur entry-title]} gran-entries]
    (if (= entry-title src-entry-title)
      [granule-ur granule-ur]
      (let [{:keys [source-short-name short-name]}
            (get svm/virtual-product-to-source-mapping
                 [(svm/provider-alias->provider-id provider-id) entry-title])]
        [granule-ur
         (svm/compute-source-granule-ur
           provider-id source-short-name short-name granule-ur)]))))

(defn- create-source-entries
  "Fetch granule ids from the granule urs of granules that belong to a collection with the given
  provider id and entry title and create the entries using the information."
  [context provider-id entry-title granule-urs]
  (let [query-params {"provider-id[]" provider-id
                      "entry_title" entry-title
                      "granule_ur[]" (vec (set granule-urs))
                      "token" (transmit-config/echo-system-token)
                      "page-size" parameter-validation/max-page-size}
        gran-refs (search/find-concept-references context query-params :granule)]
    (for [{:keys [concept-id granule-ur]} gran-refs]
      {:concept-id concept-id
       :entry-title entry-title
       :granule-ur granule-ur})))

(defn- get-provider-id-src-entry-title
  "Get a vector consisting of provider id and source entry title for a given virtual entry"
  [entry]
  (let [provider-id (:provider-id (meta entry))
        entry-title (:entry-title entry)
        source-entry-title (get-in svm/virtual-product-to-source-mapping
                                   [[(svm/provider-alias->provider-id provider-id) entry-title]
                                    :source-entry-title])]
    ;; If source entry title is null, that means it is not a virtual entry in which case we use
    ;; the entry title of the entry itself.
    [provider-id (or source-entry-title entry-title)]))

(defn- map-granule-ur-src-entry
  "Map granule urs for the subset of original entries which correspond to a single
  source collection to the corresponding source entry"
  [context entries-for-src-collection]
  (let [[[provider-id src-entry-title] entries] entries-for-src-collection
        ;; An array of vectors, each vector consisting of granule ur of an entry and the granule
        ;; ur of the corresponding source entry. If the original entry is not a virtual entry
        ;; the granule ur of the source entry is same as the granule ur of the original entry.
        arr-gran-ur-src-gran-ur (compute-source-granule-urs provider-id src-entry-title entries)
        src-entries (create-source-entries context provider-id src-entry-title
                                           (map second arr-gran-ur-src-gran-ur))
        src-gran-ur-entry-map (reduce #(assoc %1 (:granule-ur %2) %2) {} src-entries)]
    (for [[gran-ur src-gran-ur] arr-gran-ur-src-gran-ur]
      [[provider-id gran-ur] (get src-gran-ur-entry-map src-gran-ur)])))

(defn- validate-granule-entries
  "Validate the given granule entries, throws exception if it is not valid"
  [granule-entries]
  (when (> (count granule-entries) parameter-validation/max-page-size)
    (errors/throw-service-error
     :bad-request (format "The maximum allowed granule entries in a request is %s, but was %s."
                          parameter-validation/max-page-size (count granule-entries)))))

(defn- translate-granule-entries
  "Translate virtual granules in the granule-entries into the corresponding source entries. See routes.clj for the JSON schema of granule-entries."
  [context granule-entries]
  (validate-granule-entries granule-entries)
  (if (config/virtual-products-enabled)
    (let [annotated-entries (annotate-entries granule-entries)
          ;; Group entries by the combination of provider-id and entry-title of source collection for
          ;; each entry. If the entry is not a virtual entry, source collection is the same as the
          ;; collection to which the granule belongs. Entries whose granule-ur is nil are considered
          ;; collection entries and are ignored
          entries-by-src-collection (group-by get-provider-id-src-entry-title
                                              (filter :granule-ur annotated-entries))
          ;; Create a map of granule-ur to the corresponding source entry in batches, each batch
          ;; corresponding to a group in entries-by-src-collection
          gran-ur-src-entry-map (into {} (mapcat #(map-granule-ur-src-entry context %)
                                                 entries-by-src-collection))]
      (map (fn [annotated-entry]
             (let [[provider-id granule-ur] (get-prov-id-gran-ur annotated-entry)]
               (if granule-ur
                 (get gran-ur-src-entry-map [provider-id granule-ur])
                 annotated-entry)))
           annotated-entries))
    granule-entries))

(defn translate
  [context json-str]
  ;; Checks the json-str for validity as well as well-formedness
  (js/validate-json! granule-entries-schema json-str)
  (translate-granule-entries context (json/parse-string json-str true)))
