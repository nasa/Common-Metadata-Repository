(ns cmr.system-int-test.search.facets.facets-util
  "Helper vars and functions for testing collection facet responses."
  (:require
   [clj-http.client :as client]
   [cmr.common.util :as util]
   [cmr.search.services.query-execution.facets.facets-v2-helper :as v2h]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-spec]
   [cmr.system-int-test.data2.umm-spec-common :as umm-spec-common]))

(defn make-coll
  "Helper for creating and ingesting an ECHO10 collection"
  [n prov & attribs]
  (d/ingest-umm-spec-collection
   prov
   (data-umm-spec/collection-without-minimal-attribs
    n (apply merge {:EntryTitle (str "coll" n)} attribs))))

(defn projects
  [& project-names]
  {:Projects (apply data-umm-spec/projects project-names)})

(def platform-names
  "List of platform short names that exist in the test KMS hierarchy. Note we are testing case
  insensivity of the short name. DIADEM-1D is the actual short-name value in KMS, but we expect
  diadem-1D to match."
  [{:short-name "diadem-1D" :long-name nil}
   {:short-name "DMSP 5B/F3" :long-name "Defense Meteorological Satellite Program-F3"}
   {:short-name "A340-600" :long-name nil}
   {:short-name "SMAP" :long-name "Soil Moisture Active and Passive Observatory"}])


(def instrument-short-names
  "List of instrument short names that exist in the test KMS hierarchy. Note we are testing case
  insensivity of the short name. LVIS is the actual short-name value in KMS, but we expect
  lVIs to match."
  ["ATM" "lVIs" "ADS" "SMAP L-BAND RADIOMETER"])

(def FROM_KMS
  "Constant indicating that the short name for the field should be a short name found in KMS."
  "FROM_KMS")

(defn platforms
  "Creates a specified number of platforms each with a certain number of instruments and sensors"
  ([prefix num-platforms]
   (platforms prefix num-platforms 0 0))
  ([prefix num-platforms num-instruments]
   (platforms prefix num-platforms num-instruments 0))
  ([prefix num-platforms num-instruments num-sensors]
   {:Platforms
    (for [pn (range 0 num-platforms)
          :let [platform-name (str prefix "-p" pn)]]
      (data-umm-spec/platform
        {:ShortName (if (= FROM_KMS prefix)
                      (or (:short-name (get platform-names pn)) platform-name)
                      platform-name)
         :LongName (if (= FROM_KMS prefix)
                     (or (:long-name (get platform-names pn)) platform-name)
                     platform-name)
         :Instruments
           (for [instrument (range 0 num-instruments)
                 :let [instrument-name (str platform-name "-i" instrument)]]
             (apply data-umm-spec/instrument-with-childinstruments
                    (if (= FROM_KMS prefix)
                        (or (get instrument-short-names instrument) instrument-name)
                        instrument-name)
                    (for [sn (range 0 num-sensors)
                          :let [sensor-name (str instrument-name "-s" sn)]]
                      sensor-name)))}))}))

(defn twod-coords
  [& names]
  {:TilingIdentificationSystems (apply data-umm-spec/tiling-identification-systems names)})

(defn science-keywords
  [& sks]
  {:ScienceKeywords sks})

(defn processing-level-id
  [id]
  {:ProcessingLevel {:Id id}})

(defn generate-science-keywords
  "Generate science keywords based on a unique number."
  [n]
  (dc/science-keyword {:Category (str "Cat-" n)
                       :Topic (str "Topic-" n)
                       :Term (str "Term-" n)
                       :VariableLevel1 "Level1-1"
                       :VariableLevel2 "Level1-2"
                       :VariableLevel3 "Level1-3"
                       :DetailedVariable (str "Detail-" n)}))

(defn prune-facet-response
  "Recursively limit the facet response to only the keys provided to make it easier to test
  different parts of the response in different tests."
  [facet-response keys]
  (if (:children facet-response)
    (assoc (select-keys facet-response keys)
           :children
           (for [child-facet (:children facet-response)]
             (prune-facet-response child-facet keys)))
    (select-keys facet-response keys)))

(defn facet-group
  "Returns the facet group for the given field."
  [facet-response field]
  (let [child-facets (:children facet-response)
        field-title (v2h/fields->human-readable-label field)]
    (first (filter #(= (:title %) field-title) child-facets))))

(defn applied?
  "Returns whether the provided facet field is marked as applied in the facet response."
  [facet-response field]
  (:applied (facet-group facet-response field)))

(defn facet-values
  "Returns the values of the facet for the given field"
  [facet-response field]
  (map :title (:children (facet-group facet-response field))))

(defn facet-index
  "Returns the (first) index of the facet value in the list"
  [facet-response field value]
  (first (keep-indexed #(when (= value %2) %1)
                       (facet-values facet-response field))))

(defn facet-included?
  "Returns whether the provided facet value is included in the list"
  [facet-response field value]
  (some #(= value %) (facet-values facet-response field)))

(defn in-alphabetical-order?
  "Returns true if the values in the collection are sorted in alphabetical order."
  [coll]
  ;; Note that compare-natural-strings is thoroughly unit tested so we can use it to verify
  ;; alphabetical order
  (= coll (sort-by first util/compare-natural-strings coll)))

(defn get-lowest-hierarchical-depth
  "Returns the lowest hierachical depth within the facet response for any hierarchical fields."
  ([facet]
   (get-lowest-hierarchical-depth facet -1))
  ([facet current-depth]
   (apply max
          current-depth
          (map #(get-lowest-hierarchical-depth % (inc current-depth)) (:children facet)))))

(defn- find-first-apply-link
  "Takes a facet response and recursively finds the first apply link starting at the top node."
  [facet-response]
  (or (get-in facet-response [:links :apply])
      (some find-first-apply-link (:children facet-response))))

(defn traverse-hierarchical-links-in-order
  "Takes a facet response and recursively clicks on the first apply link in the hierarchy until
   every link has been applied."
  [facet-response]
  (if-let [apply-link (find-first-apply-link facet-response)]
    (let [response (get-in (client/get apply-link {:as :json}) [:body :feed :facets])]
      (traverse-hierarchical-links-in-order response))
    ;; All links have been applied
    facet-response))

(defn get-science-keyword-indexes-in-link
  "Returns a sequence of all of the science keyword indexes in link or nil if no science keywords
  are in the link."
  [link]
  (let [index-regex #"science_keywords_h%5B(\d+)%5D"
        matcher (re-matcher index-regex link)]
    (loop [matches (re-find matcher)
           all-indexes nil]
      (if-not matches
        all-indexes
        (recur (re-find matcher) (conj all-indexes (Integer/parseInt (second matches))))))))

(defn get-all-links
  "Returns all of the links in a facet response."
  ([facet-response]
   (get-all-links facet-response nil))
  ([facet-response links]
   (let [link (first (vals (:links facet-response)))
         sublinks (mapcat #(get-all-links % links) (:children facet-response))]
     (if link
       (conj sublinks link)
       sublinks))))

(defn traverse-links
  "Takes a collection of title strings and follows the apply links for each title in order. Returns
  the final facet response after clicking the apply links.
  Example: [\"Keywords\" \"Agriculture\" \"Agricultural Aquatic Sciences\" \"Aquaculture\"]"
  [facet-response titles]
  (loop [child-facet (first (filter #(= (first titles) (:title %)) (:children facet-response)))
         remaining-titles titles]
    (if (seq remaining-titles)
      ;; Check to see if any links need to be applied
      (if-let [link (get-in child-facet [:links :apply])]
        ;; Need to apply the link and start again
        (let [facet-response (get-in (client/get link {:as :json}) [:body :feed :facets])]
          (traverse-links facet-response titles))
        ;; Else check if the next title in the hierarchy has an apply link
        (let [remaining-titles (rest remaining-titles)]
          (recur (first (filter #(= (first remaining-titles) (:title %)) (:children child-facet)))
                 remaining-titles)))
      ;; We are done return the facet response
      facet-response)))

(defn click-link
  "Navigates through the hierarchical titles and clicks the link at the last level. Returns the
  facet response returned by that link. Works for either an apply or remove link"
  [facet-response titles]
  (if-let [first-title (first titles)]
    (let [child-facet (first (filter #(= first-title (:title %)) (:children facet-response)))]
      (recur child-facet (rest titles)))
    (let [link (-> (get facet-response :links) vals first)]
      (get-in (client/get link {:as :json}) [:body :feed :facets]))))
