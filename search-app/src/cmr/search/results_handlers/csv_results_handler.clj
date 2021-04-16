(ns cmr.search.results-handlers.csv-results-handler
  "Handles the CSV results format and related functions."
  (:require
   [cheshire.core :as json]
   [clojure.data.csv :as csv]
   [clojure.string :as str]
   [cmr.common-app.services.search :as qs]
   [cmr.common-app.services.search.elastic-results-to-query-results :as elastic-results]
   [cmr.common-app.services.search.elastic-search-index :as elastic-search-index]
   [cmr.common.services.errors :as svc-errors]
   [cmr.search.services.acls.acl-results-handler-helper :as acl-rhh]
   [cmr.search.services.query-execution.granule-counts-results-feature :as gcrf])
  (:import
    [java.io StringWriter]))

(def COLLECTION_CSV_HEADER
  ["Data Provider"
   "Short Name"
   "Version"
   "Entry Title"
   "Processing Level"
   "Platforms"
   "Start Time"
   "End Time"])

(defmethod elastic-search-index/concept-type+result-format->fields [:collection :csv]
 [concept-type query]
 (let [csv-fields ["provider-id"
                   "short-name"
                   "version-id"
                   "entry-title"
                   "processing-level-id"
                   "platforms"
                   "start-time"
                   "end-time"]]
   (distinct (concat csv-fields acl-rhh/collection-elastic-fields))))

(defmethod elastic-results/elastic-result->query-result-item [:collection :csv]
  [context query elastic-result]
  (let [{start-date :start-date
         end-date :end-date
         provider-id :provider-id
         processing-level :processing-level-id
         entry-title :entry-title
         platforms :platforms
         version :version-id
         short-name :short-name} (:_source elastic-result)
        start-date (when start-date (str/replace (str start-date) #"\+0000" "Z"))
        end-date (when end-date (str/replace (str end-date) #"\+0000" "Z"))
        platform-short-names (->> platforms
                                  (map :short-name)
                                  (str/join ","))
        collection-id (:_id elastic-result)]
    (merge {:row [provider-id
                  short-name
                  (str version)
                  entry-title
                  (str processing-level)
                  platform-short-names
                  start-date
                  end-date]
            :id collection-id}
           (acl-rhh/parse-elastic-item :collection elastic-result))))

(defmethod gcrf/query-results->concept-ids :csv
 [results]
 (svc-errors/throw-service-error
   :bad-request
   "Collections search in csv format is not supported with include_granule_counts option"))

(defmethod qs/search-results->response [:collection :csv]
  [context query results]
  (let [{:keys [hits took items]} results
        rows (cons COLLECTION_CSV_HEADER
                   (map :row items))
        string-writer (StringWriter.)]
    (csv/write-csv string-writer rows)
    (str string-writer)))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def GRANULE_CSV_HEADER
  ["Granule UR","Producer Granule ID","Start Time","End Time","Online Access URLs","Browse URLs","Cloud Cover","Day/Night","Size"])

(defmethod elastic-search-index/concept-type+result-format->fields [:granule :csv]
  [concept-type query]
  (let [csv-fields ["granule-ur"
                    "producer-gran-id"
                    "start-date"
                    "end-date"
                    "atom-links"
                    "cloud-cover"
                    "day-night"
                    "size"]]
    (distinct (concat csv-fields acl-rhh/granule-elastic-fields))))

(defmethod elastic-results/elastic-result->query-result-item [:granule :csv]
  [context query elastic-result]
  (let [{granule-ur :granule-ur
         producer-gran-id :producer-gran-id
         start-date :start-date
         end-date :end-date
         atom-links :atom-links
         cloud-cover :cloud-cover
         day-night :day-night
         size :size} (:_source elastic-result)
        start-date (when start-date (str/replace (str start-date) #"\+0000" "Z"))
        end-date (when end-date (str/replace (str end-date) #"\+0000" "Z"))
        atom-links (map #(json/decode % true) atom-links)
        downloadable-urls (seq (map :href
                                    (filter #(= (:link-type %) "data") atom-links)))
        browse-urls (seq (map :href
                              (filter #(= (:link-type %) "browse") atom-links)))]
    (merge {:row [granule-ur producer-gran-id start-date end-date (str/join "," downloadable-urls)
                  (str/join "," browse-urls) (str cloud-cover) day-night (str size)]}
           (acl-rhh/parse-elastic-item :granule elastic-result))))

(defmethod qs/search-results->response [:granule :csv]
  [context query results]
  (let [{:keys [hits took items]} results
        rows (cons GRANULE_CSV_HEADER
                   (map :row items))
        string-writer (StringWriter.)]
    (csv/write-csv string-writer rows)
    (str string-writer)))
