(ns cmr.system-int-test.data2.opendata
  "Contains functions for parsing opendata results."
  (:require
   [camel-snake-kebab.core :as csk]
   [cheshire.core :as json]
   [clojure.data.xml :as x]
   [clojure.string :as str]
   [clojure.test :refer [is]]
   [clojure.test]
   [cmr.common.util :as util]
   [cmr.search.validators.opendata :as opendata-json]
   [cmr.search.results-handlers.opendata-results-handler :as odrh]
   [cmr.spatial.line-string :as l]
   [cmr.spatial.mbr :as m]
   [cmr.spatial.point :as p]
   [cmr.spatial.polygon :as poly]
   [cmr.spatial.relations :as r]
   [cmr.spatial.ring-relations :as rr]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.umm-spec.date-util :as date-util]
   [cmr.umm-spec.util :as umm-spec-util]
   [cmr.umm.echo10.spatial :as echo-s]
   [cmr.umm.related-url-helper :as ru]
   [cmr.umm.start-end-date :as sed]
   [cmr.umm.umm-spatial :as umm-s])
  (:import
   (cmr.spatial.mbr Mbr)
   (cmr.umm.umm_collection UmmCollection)))

(defn parse-opendata-result
  "Returns the opendata result from a json string"
  [concept-type json-str]
  (let [json-struct (json/decode json-str true)]
    json-struct))

(defn personnel->contact-email
  "Returns a contact email from the personnel record or the default if one
  is not available."
  [personnel]
  (or (when-let [contacts (:contacts personnel)]
        (when-let [contact (first (filter #(= :email (:type %)) contacts))]
          (:value contact)))
      odrh/DEFAULT_CONTACT_EMAIL))

(defn- contact-point
  "Creates the contactPoint field including the name and email address"
  [personnel]
  {:fn (odrh/personnel->contact-name personnel)
   :hasEmail (str "mailto:" (personnel->contact-email personnel))})

(defn- email-contact?
  "Return true if the given person has an email."
  [person]
  (some #(= :email (:type %)) (:contacts person)))

(defn- person-with-email
  "Returns the first Personnel record for the list with an email contact or
  nil if none exists."
  [personnel]
  (some #(when (email-contact? %) %) personnel))

(defn- flatten-science-keywords
  "Convert the science keywords into a flat list composed of the category, topic, and term values."
  [collection]
  (distinct (mapcat (fn [science-keyword]
                      (let [{:keys [category topic term]} science-keyword]
                        (filter identity [category topic term])))
                    (:science-keywords collection))))

(defn collection->expected-opendata
  "Convert to expcted opendata. First convert to native format metadata then back to UMM to mimic
  ingest. If umm-json leave as is since parse-concept will convert to echo10."
  [collection]
  (let [{:keys [format-key concept-id data-format provider-id]} collection
        ;; Normally :revision-date field doesn't exist in collection. Only when
        ;; it's needed to populate modified field, this field is manually added
        ;; in the test from umm-json result.
        revision-date (:revision-date collection)
        collection-citations (:collection-citations collection)
        collection (data-core/mimic-ingest-retrieve-metadata-conversion collection)
        {:keys [short-name keywords projects related-urls summary entry-title organizations
                access-value personnel publication-references]} collection
        spatial-representation (get-in collection [:spatial-coverage :spatial-representation])
        temporal (:temporal collection)
        start-date  (if temporal
                      (sed/start-date :collection temporal)
                      ;; no temporal in collection will be treated as start date 1970-01-01T00:00:00 by CMR
                      date-util/parsed-default-date)
        end-date (sed/end-date :collection temporal)
        start-date (when start-date (str/replace (str start-date) #"\.000Z" "Z"))
        end-date (when end-date (str/replace (str end-date) #"\.000Z" "Z"))
        ;; ECSE-158 - We will use UMM-C's DataDates to get insert-time, update-time for DIF9/DIF10.
        ;; DIF9 doesn't support DataDates in umm-spec-lib:
        ;;  So its insert-time and update-time are nil.
        ;;  But we when they are nil, we will use the granule-start/end-date-stored, which
        ;;  are collection's start-date, end-date when there are no granules.
        update-time (or (when-not (= :dif format-key)
                          (get-in collection [:data-provider-timestamps :update-time]))
                        end-date)
        insert-time (or (when-not (= :dif format-key)
                          (get-in collection [:data-provider-timestamps :insert-time]))
                        start-date)
        shapes (map (partial umm-s/set-coordinate-system spatial-representation)
                    (get-in collection [:spatial-coverage :geometries]))
        distribution (not-empty (map odrh/related-url->distribution related-urls))
        project-sn (not-empty (map :short-name projects))
        personnel (person-with-email personnel)
        contact-point (contact-point personnel)
        collection-citation (first collection-citations)
        browse-image-related-url (odrh/get-best-browse-image-related-url related-urls)
        archive-center (:org-name (first (filter #(= :archive-center (:type %)) organizations)))]
    (util/remove-nil-keys {:title entry-title
                           :description summary
                           :keyword (flatten-science-keywords collection)
                           ;; when update-time is nil, we will use the revision-date,
                           :modified (str (or update-time revision-date))
                           :publisher (odrh/publisher provider-id archive-center)
                           :contactPoint contact-point
                           :identifier concept-id
                           :accessLevel "public"
                           :bureauCode [odrh/BUREAU_CODE]
                           :programCode [odrh/PROGRAM_CODE]
                           :spatial (odrh/spatial shapes)
                           :temporal (odrh/temporal start-date end-date)
                           :theme (conj project-sn "geospatial")
                           :distribution distribution
                           :graphic-preview-file (:url browse-image-related-url)
                           :graphic-preview-description (:description browse-image-related-url)
                           :graphic-preview-type (odrh/graphic-preview-type browse-image-related-url)
                           :landingPage (format "http://localhost:3003/concepts/%s.html" concept-id)
                           :language [odrh/LANGUAGE_CODE]
                           :citation (odrh/citation collection-citation {:provider provider-id
                                                                         :archive-center archive-center})
                           :references (not-empty
                                        (map ru/related-url->encoded-url publication-references))
                           :issued (when (and insert-time
                                              (not= (str date-util/parsed-default-date)
                                                    (str/replace (str insert-time) #"Z" ".000Z")))
                                     (str insert-time))})))

(defn collections->expected-opendata
  [collections]
  {:status 200
   :results {:conformsTo odrh/OPENDATA_SCHEMA
             :dataset (map collection->expected-opendata collections)}})

(defn- opendata-results-map->opendata-results-map-using-sets
  "Converts all of the collections within an opendata results map to sets. This allows maps to be
  compared such that the order of elements in a collection is ignored."
  [opendata-results-map]
  (update-in opendata-results-map [:results :dataset]
             (fn [dataset]
               (into #{} (for [field dataset]
                           ;; Dissoc title from distribtion.
                           ;; mimic-ingest-retrieve-metadata-conversion
                           ;; is returning a umm-lib representation after generating
                           ;; and parsing metadata. This is an issue with retrieving
                           ;; the correct URLContentType and will cause this test
                           ;; to fail since the url-content-type is not retrieved/in
                           ;; the umm-lib model.
                           (->> (assoc field :distribution (mapv #(dissoc % :title) (:distribution field)))
                                (util/map-values #(if (sequential? %) (set %) %))))))))

(defn assert-collection-opendata-results-match
  "Returns true if the opendata results are for the expected items.
  Also validates the dataset against the Opendata v1.1 json schema."
  [collections actual-result]
  (is (empty? (opendata-json/validate-dataset (json/generate-string (:results actual-result)))))
  (is (= (opendata-results-map->opendata-results-map-using-sets
          (collections->expected-opendata collections))
         (opendata-results-map->opendata-results-map-using-sets actual-result))))
