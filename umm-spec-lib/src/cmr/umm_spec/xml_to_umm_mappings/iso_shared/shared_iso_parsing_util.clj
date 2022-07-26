(ns cmr.umm-spec.xml-to-umm-mappings.iso-shared.shared-iso-parsing-util
  "Shared parsing functions for ISO 19115-2 MENDS and ISO 19115-2 SMAP."
  (:require
   [clojure.string :as string]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]))

;; This section allows an ISO description that is encoded by key: value key: value etc.
;; to be parsed by a map. This was taken from
;; cmr.umm-spec.xml-to-umm-mappings.iso-shared.distributions-related-url so that it can
;; be used generically.
(def key-split-string
  "Special string to help separate keys from values in ISO strings"
  "HSTRING")

(def value-split-string
  "Special string to help separate keys from values in ISO strings"
  "TSTRING")

(defn convert-iso-description-string-to-string-map
  "Convert Description string to a map, removing fields that are empty or nil.
  Description string: \"key: value key: value\"
  Description map: {\"key\" \"value\"
                    \"key\" \"value\"}
  The passed in description-string is the value of the ISO description element.
  The passed in description-regex is the regular expression that gets used to
  create the map. The function that creates it should pass back the following:
  (re-pattern \"key:|key:|key<etc.>\")"
  [description-string description-regex]
  (when-let [description-string (-> description-string
                                    (string/replace description-regex
                                                    #(str key-split-string
                                                          %1
                                                          value-split-string))
                                    string/trim
                                    (string/replace #"\s+HSTRING" key-split-string)
                                    (string/replace #":TSTRING\s+" (str ":" value-split-string)))]
    (->> (string/split description-string (re-pattern key-split-string))
           ;; split each string in the description-str-list
         (map #(string/split % #":TSTRING"))
           ;; keep the ones with values.
         (filter #(= 2 (count %)))
         (into {})
           ;; remove "nil" valued keys
         (util/remove-map-keys #(= "nil" %)))))

(defn convert-key-strings-to-keywords
  "This function converts strings that are keys to keywords.
   From: {\"key-string\" \"value-string\"}
   To:   {:key-string \"value-string\"}"
  [map]
  (into {}
        (for [[k v] map]
          [(keyword k) v])))

(defn convert-select-values-from-string-to-number
  "Inputs a map of key-values and a list of keys where the value should be a number. The function
   returns a map of key-values where any values where the key is in the number-key-list has been
   converted to a number."
  [map number-key-list]
  (when (and (not (nil? map))
             (not (empty? map)))
    (into {}
          (for [[k v] map]
            (try
              (if (and (some #(= k %) number-key-list)
                       (not (nil? v)))
                (let [string-number (re-find #"-?\d+\.?\d+|-?\.?\d+" v)]
                  (if (string/includes? string-number ".")
                    {k (Double/parseDouble string-number)}
                    {k (Long/parseLong string-number)}))
                {k v})
              (catch Exception e
                (errors/throw-service-error
                 :invalid-data (format "Error parsing the field %s with value %s" k v))))))))

(defn convert-iso-description-string-to-map
  "Convert Description string to a map, removing fields that are empty or nil.
  and converts the string key into actual keywords.
  Description string: \"key: value key: value\"
  Description map: {:key \"value\"
                    :key \"value\"}
  The passed in description-string is the value of the ISO description element.
  The passed in description-regex is the regular expression that gets used to
  create the map. The function that creates it should pass back the following:
  (re-pattern \"key:|key:|key<etc.>\")"
  ([description-string description-regex]
   (convert-iso-description-string-to-map description-string description-regex nil))
  ([description-string description-regex number-key-list]
   (-> (convert-iso-description-string-to-string-map description-string description-regex)
       convert-key-strings-to-keywords
       (convert-select-values-from-string-to-number number-key-list))))
