(ns cmr.umm.iso-smap.granule.orbit
  (:require
   [clojure.data.xml :as x]
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
  (:import cmr.umm.umm_granule.Orbit))

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

(def validations
  [{:ascending-crossing [v/required v/number (v/within-range -180.0 180.0)]
    :start-lat [v/required v/number (v/within-range -90.0 90.0)]
    :end-lat [v/required v/number (v/within-range -90.0 90.0)]
    :start-direction start-end-direction
    :end-direction start-end-direction}])

(extend-protocol sv/SpatialValidation
  cmr.umm.umm_granule.Orbit
  (validate
    [record]
    (v/create-error-messages (v/validate validations record))))

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
                           (str "OrbitalModelName: " orbital-model-name " "))
        OrbitNumber (when orbit-number
                      (str "OrbitNumber: " orbit-number))
        StartOrbitNumber (when start-orbit-number
                           (str "BeginOrbitNumber: " start-orbit-number " "))
        StopOrbitNumber (when stop-orbit-number
                          (str "EndOrbitNumber: " stop-orbit-number " "))
        EquatorCrossingLongitude (when equator-crossing-longitude
                                   (str "EquatorCrossingLongitude: " equator-crossing-longitude " "))
        EquatorCrossingDateTime (when equator-crossing-date-time
                                  (str "EquatorCrossingDateTime: " equator-crossing-date-time))]
    (str OrbitalModelName OrbitNumber StartOrbitNumber StopOrbitNumber
         EquatorCrossingLongitude EquatorCrossingDateTime)))

(defn- parse-float
  "Coerce's string to float, catches exceptions and logs error message and returns nil if
  value is not parseable."
  ([field value]
   (parse-float field value nil))
  ([field value field-name]
  (try
    (Float. value)
    (catch Exception e
      (info (format "For [%s] field [%s] the value [%s] is not a number." (or field-name "Orbit") field value))
      ;; We return nil her instead of value because within-range validation can't handle comparing a
      ;; string to a double.
      nil))))

(defn- parse-integer
  "Coerce's string to integer, catches exceptions and logs error message and returns nil if
  value is not parseable."
  [field value]
  (try
    (Integer. value)
    (catch Exception e
      (info (format "For Orbit calculated spatial domain field [%s] the value [%s] is not an integer." field value))
      nil)))

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
        ;; geo-desc could be Orbit or OrbitCalculatedSpatialDomain or sth else.
        ;; In Orbit case:
        ascending-crossing (util/get-index-or-nil orbit-str "AscendingCrossing:")
        start-lat (util/get-index-or-nil orbit-str "StartLatitude:")
        start-direction (util/get-index-or-nil orbit-str "StartDirection:")
        end-lat (util/get-index-or-nil orbit-str "EndLatitude:")
        end-direction (util/get-index-or-nil orbit-str "EndDirection:")
        ;; In OrbitCalculatedSpatialDomain case:
        orbital-model-name (util/get-index-or-nil orbit-str "OrbitalModelName:")
        orbit-number (util/get-index-or-nil orbit-str "OrbitNumber:")
        start-orbit-number (util/get-index-or-nil orbit-str "BeginOrbitNumber:")
        stop-orbit-number (util/get-index-or-nil orbit-str "EndOrbitNumber:")
        equator-crossing-longitude (util/get-index-or-nil orbit-str "EquatorCrossingLongitude:")
        equator-crossing-date-time (util/get-index-or-nil orbit-str "EquatorCrossingDateTime:")
        count-orbit-str (count orbit-str)]
    (case description-type
      "OrbitParameters"
      (g/->Orbit
       (when ascending-crossing
         (let [asc-c (subs orbit-str
                           ascending-crossing
                           (or start-lat start-direction end-lat end-direction
                               count-orbit-str))]
           (parse-float :ascending-crossing (str/trim (subs asc-c (inc (.indexOf asc-c ":")))))))
       (when start-lat
         (let [sl (subs orbit-str
                        start-lat
                        (or start-direction end-lat end-direction
                            count-orbit-str))]
           (parse-float :start-lat (str/trim (subs sl (inc (.indexOf sl ":")))))))
       (when start-direction
         (let [sd (subs orbit-str
                        start-direction
                        (or end-lat end-direction
                            count-orbit-str))]
           (convert-direction (str/trim (subs sd (inc (.indexOf sd ":")))))))
       (when end-lat
         (let [el (subs orbit-str
                        end-lat
                        (or end-direction
                            count-orbit-str))]
           (parse-float :end-lat (str/trim (subs el (inc (.indexOf el ":")))))))
       (when end-direction
         (let [ed (subs orbit-str
                        end-direction)]
           (convert-direction (str/trim (subs ed (inc (.indexOf ed ":"))))))))
      "OrbitCalculatedSpatialDomains"
      (g/->OrbitCalculatedSpatialDomain
        (when orbital-model-name
         (let [o-m-name (subs orbit-str
                          orbital-model-name
                          (or orbit-number start-orbit-number stop-orbit-number
                              equator-crossing-longitude equator-crossing-date-time count-orbit-str))]
           (str/trim (subs o-m-name (inc (.indexOf o-m-name ":"))))))
        (when orbit-number
         (let [o-number (subs orbit-str
                          orbit-number
                          (or start-orbit-number stop-orbit-number equator-crossing-longitude
                              equator-crossing-date-time count-orbit-str))]
           (parse-integer :orbit-number (str/trim (subs o-number (inc (.indexOf o-number ":")))))))
        (when start-orbit-number
         (let [b-o-number (subs orbit-str
                            start-orbit-number
                            (or stop-orbit-number equator-crossing-longitude
                                equator-crossing-date-time count-orbit-str))]
           (parse-integer :start-orbit-number (str/trim (subs b-o-number (inc (.indexOf b-o-number ":")))))))
        (when stop-orbit-number
         (let [e-o-number (subs orbit-str
                            stop-orbit-number
                            (or equator-crossing-longitude equator-crossing-date-time count-orbit-str))]
           (parse-integer :stop-orbit-number (str/trim (subs e-o-number (inc (.indexOf e-o-number ":")))))))
        (when equator-crossing-longitude
         (let [e-c-lon (subs orbit-str
                         equator-crossing-longitude
                         (or equator-crossing-date-time count-orbit-str))]
           (parse-float :equator-crossing-longitude (str/trim (subs e-c-lon (inc (.indexOf e-c-lon ":"))))
                        "Orbit calculated spatial domain")))
        (when equator-crossing-date-time
         (let [e-c-dt (subs orbit-str
                         equator-crossing-date-time)]
           (date-time-parser/parse-datetime (str/trim (subs e-c-dt (inc (.indexOf e-c-dt ":"))))))))
      nil)))
