(ns cmr.umm.iso-smap.granule.orbit
  "Functions for parsing the orbit information from ISO SMAP granule metadata as well as
  generating ISO SMAP granule metadata XML from the UMM granule model."
  (:require
   [clojure.data.xml :as x]
   [clojure.set :as set]
   [clojure.string :as str]
   [cmr.common.date-time-parser :as date-time-parser]
   [cmr.common.log :refer [info debug error]]
   [cmr.common.util :as util]
   [cmr.common.validations.core :as v]
   [cmr.common.xml :as cx]
   [cmr.spatial.encoding.gmd :as gmd]
   [cmr.spatial.messages :as msg]
   [cmr.spatial.validation :as sv]
   [cmr.umm.iso-smap.helper :as h]
   [cmr.umm.umm-granule :as g])
  (:import (cmr.umm.umm_granule Orbit)))

(defn- start-end-direction
  [field-path direction]
  (when (and (not= :asc direction)
             (not= :desc direction))
    {field-path [(msg/start-end-direction field-path direction)]}))

(defn- convert-direction
  "Converts :asc <=> A and :desc <=> D"
  [direction]
  (cond
    (= "A" direction) :asc
    (= "D" direction) :desc
    (= :asc direction) "A"
    (= :desc direction) "D"
    :default direction))

(def orbit-validations
  [{:ascending-crossing [v/required v/validate-number (v/within-range -180.0 180.0)]
    :start-lat [v/required v/validate-number (v/within-range -90.0 90.0)]
    :end-lat [v/required v/validate-number (v/within-range -90.0 90.0)]
    :start-direction start-end-direction
    :end-direction start-end-direction}])

(extend-protocol sv/SpatialValidation
  cmr.umm.umm_granule.Orbit
  (validate
    [record]
    (v/create-error-messages (v/validate orbit-validations record))))

(def ocsd-validations
  [{:orbit-number [v/validate-integer]
    :start-orbit-number [v/validate-integer]
    :stop-orbit-number [v/validate-integer]
    ;; note we don't need to validate that it's a number because
    ;; it's been checked in the parse-double, and if it's not a number,
    ;; it will be returned as nil to ensure a string is not passed to within-range.
    :equator-crossing-longitude [(v/within-range -180.0 180.0)]
    :equator-crossing-date-time [v/validate-datetime]}])

(extend-protocol sv/SpatialValidation
  cmr.umm.umm_granule.OrbitCalculatedSpatialDomain
  (validate
    [record]
    (v/create-error-messages (v/validate ocsd-validations record))))

(extend-protocol sv/SpatialValidation
  nil
  (validate
    [record]
    [(str "Unsupported gmd:description inside gmd:EX_GeographicDescription - "
          "The supported ones are: OrbitParameters and OrbitCalculatedSpatialDomains")]))

(defn- build-orbit-string
  "Builds ISO SMAP orbit string using umm values."
  [orbit]
  (let [{:keys [ascending-crossing start-lat start-direction end-lat end-direction]} orbit]
    (format "AscendingCrossing: %s StartLatitude: %s StartDirection: %s EndLatitude: %s EndDirection: %s"
            ascending-crossing
            start-lat (convert-direction start-direction)
            end-lat (convert-direction end-direction))))

(defn- build-ocsd-string
  "Builds ISO SMAP orbit calculated spatial domain string using umm values."
  [ocsd]
  (let [{:keys [orbital-model-name orbit-number start-orbit-number stop-orbit-number
                equator-crossing-longitude equator-crossing-date-time]} ocsd
        OrbitalModelName (when orbital-model-name
                           (str "OrbitalModelName: " orbital-model-name))
        OrbitNumber (when orbit-number
                      (str "OrbitNumber: " orbit-number))
        StartOrbitNumber (when start-orbit-number
                           (str "BeginOrbitNumber: " start-orbit-number))
        StopOrbitNumber (when stop-orbit-number
                          (str "EndOrbitNumber: " stop-orbit-number))
        EquatorCrossingLongitude (when equator-crossing-longitude
                                   (str "EquatorCrossingLongitude: " equator-crossing-longitude))
        EquatorCrossingDateTime (when equator-crossing-date-time
                                  (str "EquatorCrossingDateTime: " equator-crossing-date-time))]
    (str OrbitalModelName " " OrbitNumber " " StartOrbitNumber " " StopOrbitNumber " "
         EquatorCrossingLongitude " " EquatorCrossingDateTime)))

(defn- parse-integer
  "Coerce's string to integer, catches exceptions and logs error message and returns value if
  value is not parseable so that the validation can catch the error and return to the user."
  [field value]
  (try
    (Integer/parseInt value)
    (catch Exception e
      (info (format "For Orbit calculated spatial domain field [%s] the value [%s] is not an integer." field value))
      value)))

(defn- parse-datetime
  "Coerce's string to datetime, catches exceptions and logs error message and returns value if
  value is not parseable. This wrapper is used so that the error can be returned together
  with other validation errors."
  [field value]
  (try
    (date-time-parser/parse-datetime value)
    (catch Exception e
      (info (format "For Orbit calculated spatial domain field [%s] the value [%s] is not a datetime." field value))
      value)))

(defn- parse-double
  "Coerce's string to double, catches exceptions and logs error message and returns nil if
  value is not parseable."
  [field value]
  (try
    (Double/parseDouble value)
    (catch Exception e
      (info (format "For Orbit calculated spatial domain field [%s] the value [%s] is not an double." field value))
      ;; We return nil here instead of value because within-range validation can't handle comparing a
      ;; string to a double.
      nil)))

(def orbit-str-field-mapping
  "Returns mapping of fields in orbit string and fields in UmmGranule."
  {"AscendingCrossing" :ascending-crossing
   "StartLatitude" :start-lat
   "StartDirection" :start-direction
   "EndLatitude" :end-lat
   "EndDirection" :end-direction
   "OrbitalModelName" :orbital-model-name
   "OrbitNumber" :orbit-number
   "BeginOrbitNumber" :start-orbit-number
   "EndOrbitNumber" :stop-orbit-number
   "EquatorCrossingLongitude" :equator-crossing-longitude
   "EquatorCrossingDateTime" :equator-crossing-date-time})

(def orbit-str-field-re-pattern
  "Returns the pattern that matches all the related fields in orbit-str."
  (let [pstr (str "AscendingCrossing:|StartLatitude:|StartDirection:|"
                  "EndLatitude:|EndDirection:|OrbitalModelName:|OrbitNumber:|"
                  "BeginOrbitNumber:|EndOrbitNumber:|EquatorCrossingLongitude:|EquatorCrossingDateTime:")]
    (re-pattern pstr)))

(defn- convert-orbit-str-to-map
  "Convert orbit-str to a map. The fields with nil or no values are removed.
  For orbit-str \"AscendingCrossing: StartLatitude: nil StartDirection: A EndLatitude: 0.0 EndDirection: A\"
  orbit-str-map is:
  {\"StartDirection\" \"A\" \"EndLatitude\" \"0.0\" \"EndDirection\" \"A\"}"
  [orbit-str]
  (let [;; Add a special string around each field and trim all the spaces around the values.
        orbit-str (-> orbit-str
                      (str/replace orbit-str-field-re-pattern #(str "HSTRING" %1 "TSTRING"))
                      (str/trim)
                      (str/replace #"\s+HSTRING" "HSTRING")
                      (str/replace #":TSTRING\s+" ":TSTRING"))
        ;; split against the special string - now each element in orbit-str-list
        ;; contains things like "OrbitModelName:TSTRINGModel:Name"
        orbit-str-list (str/split orbit-str #"HSTRING")]
    (->> orbit-str-list
         ;; split each string in the orbit-str-list
         (map #(str/split % #":TSTRING"))
         ;; keep the ones with values.
         (filter #(= 2 (count %)))
         (into {})
         ;; remove "nil" valued keys
         (util/remove-map-keys #(= "nil" %)))))

(defn- parse-values-for-orbit
  "Update values in the orbit-str-map with the parsed values."
  [orbit-str-map]
  (-> orbit-str-map
      (update :ascending-crossing #(parse-double :ascending-crossing %))
      (update :start-lat #(parse-double :start-lat %))
      (update :start-direction #(convert-direction %))
      (update :end-lat #(parse-double :end-lat %))
      (update :end-direction #(convert-direction %))))

(defn- parse-values-for-ocsd
  "Update values in the orbit-str-map with the parsed values."
  [orbit-str-map]
  (-> orbit-str-map
      (update :orbit-number #(parse-integer :orbit-number %))
      (update :start-orbit-number #(parse-integer :start-orbit-number %))
      (update :stop-orbit-number #(parse-integer :stop-orbit-number %))
      (update :equator-crossing-longitude #(parse-double :equator-crossing-longitude %))
      (update :equator-crossing-date-time #(parse-datetime :equator-crossing-date-time %))))

(defmethod gmd/encode cmr.umm.umm_granule.Orbit
  [orbit]
  (x/element :gmd:geographicElement {}
             (x/element :gmd:EX_GeographicDescription {}
                        (x/element :gmd:geographicIdentifier {}
                         (x/element :gmd:MD_Identifier {}
                                    (h/iso-string-element :gmd:code (build-orbit-string orbit))
                                    (h/iso-string-element :gmd:codeSpace
                                                          "gov.nasa.esdis.umm.orbitparameters")
                                    (h/iso-string-element :gmd:description "OrbitParameters"))))))

(defmethod gmd/encode cmr.umm.umm_granule.OrbitCalculatedSpatialDomain
  [ocsd]
  (x/element :gmd:geographicElement {}
             (x/element :gmd:EX_GeographicDescription {}
                        (x/element :gmd:geographicIdentifier {}
                         (x/element :gmd:MD_Identifier {}
                                    (h/iso-string-element :gmd:code (build-ocsd-string ocsd))
                                    (h/iso-string-element :gmd:codeSpace
                                                          "gov.nasa.esdis.umm.orbitcalculatedspatialdomains")
                                    (h/iso-string-element :gmd:description "OrbitCalculatedSpatialDomains"))))))

(defmethod gmd/decode-geo-content :EX_GeographicDescription
  [geo-desc]
  (let [orbit-str (cx/string-at-path geo-desc [:geographicIdentifier :MD_Identifier :code :CharacterString])
        description-type (cx/string-at-path geo-desc [:geographicIdentifier :MD_Identifier :description :CharacterString])
        orbit-str-map (-> orbit-str
                          convert-orbit-str-to-map
                          (set/rename-keys orbit-str-field-mapping))]
    (case description-type
      "OrbitParameters"
      (let [orbit-map (parse-values-for-orbit orbit-str-map)]
        (g/map->Orbit orbit-map))
      "OrbitCalculatedSpatialDomains"
      (let [ocsd-map (parse-values-for-ocsd orbit-str-map)]
        (g/map->OrbitCalculatedSpatialDomain ocsd-map))
      nil)))
