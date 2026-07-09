(ns cmr.audit-simulator.metadata
  "Metadata-quality checks for intentionally imperfect sample records."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as string]))

(def required-fields [:concept-id :provider-id :short-name :version-id])

(defn load-records []
  (-> "cmr_audit_simulator/metadata/sample_records.edn"
      io/resource
      slurp
      edn/read-string))

(defn blankish? [value]
  (or (nil? value)
      (and (string? value) (string/blank? value))))

(defn valid-instant? [value]
  (boolean
   (when (string? value)
     (try
       (java.time.Instant/parse value)
       (catch Exception _ false)))))

(defn required-field-errors [record]
  (for [field required-fields
        :when (blankish? (get record field))]
    {:field field
     :message "Required field is missing or blank."
     :suggested-fix {:op :add
                     :path [field]
                     :value (str "AUDIT_" (name field))}}))

(defn temporal-errors [record]
  (let [begin (get-in record [:temporal :beginning-date-time])
        end (get-in record [:temporal :ending-date-time])
        parsed-begin (when (valid-instant? begin) (java.time.Instant/parse begin))
        parsed-end (when (valid-instant? end) (java.time.Instant/parse end))]
    (cond-> []
      (and begin (not parsed-begin))
      (conj {:field :temporal
             :message "BeginningDateTime is not ISO-8601."
             :suggested-fix {:op :replace
                             :path [:temporal :beginning-date-time]
                             :value "2024-01-01T00:00:00Z"}})

      (and end (not parsed-end))
      (conj {:field :temporal
             :message "EndingDateTime is not ISO-8601."
             :suggested-fix {:op :remove
                             :path [:temporal :ending-date-time]}})

      (and parsed-begin parsed-end (.isAfter parsed-begin parsed-end))
      (conj {:field :temporal
             :message "BeginningDateTime is after EndingDateTime."
             :suggested-fix {:op :replace
                             :path [:temporal :ending-date-time]
                             :value begin}}))))

(defn spatial-errors [record]
  (let [{:keys [west south east north]} (:spatial record)]
    (cond-> []
      (and west (or (< west -180) (> west 180)))
      (conj {:field :spatial
             :message "West longitude is outside [-180, 180]."
             :suggested-fix {:op :replace :path [:spatial :west] :value -180.0}})

      (and east (or (< east -180) (> east 180)))
      (conj {:field :spatial
             :message "East longitude is outside [-180, 180]."
             :suggested-fix {:op :replace :path [:spatial :east] :value 180.0}})

      (and south (or (< south -90) (> south 90)))
      (conj {:field :spatial
             :message "South latitude is outside [-90, 90]."
             :suggested-fix {:op :replace :path [:spatial :south] :value -90.0}})

      (and north (or (< north -90) (> north 90)))
      (conj {:field :spatial
             :message "North latitude is outside [-90, 90]."
             :suggested-fix {:op :replace :path [:spatial :north] :value 90.0}}))))

(defn distribution-url-errors [record]
  (let [urls (:distribution-urls record)]
    (cond
      (empty? urls)
      [{:field :distribution-urls
        :message "No distribution URLs are present."
        :suggested-fix {:op :add
                        :path [:distribution-urls]
                        :value ["https://example.com/data/replacement"]}}]

      (some #(not (string/starts-with? % "https://")) urls)
      [{:field :distribution-urls
        :message "Distribution URLs should use HTTPS."
        :suggested-fix {:op :replace
                        :path [:distribution-urls]
                        :value (mapv #(string/replace % #"^ftp://" "https://") urls)}}]

      :else [])))

(defn validate-record [record]
  (let [errors (vec (concat (required-field-errors record)
                            (temporal-errors record)
                            (spatial-errors record)
                            (distribution-url-errors record)))]
    {:concept-id (:concept-id record)
     :valid? (empty? errors)
     :errors errors}))

(defn validation-report []
  (mapv validate-record (load-records)))

(defn finding-for-metadata-errors []
  {:category :metadata
   :id "META-001"
   :severity :high
   :title "Invalid sample metadata"
   :evidence (remove :valid? (validation-report))
   :fix "Apply generated patch suggestions in a provider-approved metadata correction workflow."})

(defn findings []
  [(finding-for-metadata-errors)])
