(ns cmr.umm.iso-smap.granule.orbit
  (:require
   [clojure.data.xml :as x]
   [clojure.string :as str]
   [cmr.common.log :refer [info debug error]]
   [cmr.common.util :as util]
   [cmr.common.validations.core :as v]
   [cmr.common.xml :as cx]
   [cmr.spatial.encoding.gmd :as gmd]
   [cmr.spatial.messages :as msg]
   [cmr.spatial.validation :as sv]
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
  [{:ascending-crossing [v/required (v/within-range -180.0 180.0)]
    :start-lat [v/required (v/within-range -90.0 90.0)]
    :end-lat [v/required (v/within-range -90.0 90.0)]
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

(defn- parse-float
  "Coerce's string to float, catches exceptions and logs error message and returns nil if
  value is not parseable."
  [field value]
  (try
    (Float. value)
    (catch Exception e
      (info (format "For orbit field [%s] the value [%s] is not a number." field value))
      value)))

(defmethod gmd/encode cmr.umm.umm_granule.Orbit
  [orbit]
  (x/element :gmd:geographicElement {}
             (x/element :gmd:EX_GeographicDescription {}
                        (x/element :gmd:MD_Identifier {}
                                   (x/element :gmd:code {}
                                              (x/element :gco:CharacterString {} (build-orbit-string orbit)))
                                   (x/element :gmd:codeSpace {}
                                              (x/element :gco:CharacterString {} "gov.nasa.esdis.umm.orbitparameters"))
                                   (x/element :gmd:description {}
                                              (x/element :gco:CharacterString {} "OrbitParameters"))))))

(defmethod gmd/decode-geo-content :EX_GeographicDescription
  [geo-desc]
  (let [orbit-str (cx/string-at-path geo-desc [:geographicIdentifier :MD_Identifier :code :CharacterString])
        description-type (cx/string-at-path geo-desc [:geographicIdentifier :MD_Identifier :description :CharacterString])
        ascending-crossing (util/get-index-or-nil orbit-str "AscendingCrossing:")
        start-lat (util/get-index-or-nil orbit-str "StartLatitude:")
        start-direction (util/get-index-or-nil orbit-str "StartDirection:")
        end-lat (util/get-index-or-nil orbit-str "EndLatitude:")
        end-direction (util/get-index-or-nil orbit-str "EndDirection:")]
    (when (= description-type "OrbitParameters")
      (g/->Orbit
       (when ascending-crossing
         (let [asc-c (subs orbit-str
                           ascending-crossing
                           (or start-lat start-direction end-lat end-direction
                               (count orbit-str)))]
           (parse-float :ascending-crossing (str/trim (subs asc-c (inc (.indexOf asc-c ":")))))))
       (when start-lat
         (let [sl (subs orbit-str
                        start-lat
                        (or start-direction end-lat end-direction
                            (count orbit-str)))]
           (parse-float :start-lat (str/trim (subs sl (inc (.indexOf sl ":")))))))
       (when start-direction
         (let [sd (subs orbit-str
                        start-direction
                        (or end-lat end-direction
                            (count orbit-str)))]
           (convert-direction (str/trim (subs sd (inc (.indexOf sd ":")))))))
       (when end-lat
         (let [el (subs orbit-str
                        end-lat
                        (or end-direction
                            (count orbit-str)))]
           (parse-float :end-lat (str/trim (subs el (inc (.indexOf el ":")))))))
       (when end-direction
         (let [ed (subs orbit-str
                        end-direction)]
           (convert-direction (str/trim (subs ed (inc (.indexOf ed ":")))))))))))
