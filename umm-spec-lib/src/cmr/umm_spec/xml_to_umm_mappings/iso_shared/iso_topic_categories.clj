(ns cmr.umm-spec.xml-to-umm-mappings.iso-shared.iso-topic-categories
  "Functions for parsing UMM iso topic categories out of ISO XML documents."
  (:require
    [clojure.set :as set]
    [cmr.common.util :as util]
    [cmr.common.xml.parse :refer :all]
    [cmr.common.xml.simple-xpath :refer [select text]]
    [cmr.umm-spec.iso19115-2-util :as iso]
    [cmr.umm-spec.util :as su :refer [without-default-value-of]]))

(def iso-topic-category-xpath
  (str "/gmi:MI_Metadata/gmd:identificationInfo"
       "/gmd:MD_DataIdentification/gmd:topicCategory"))

(def umm->xml-iso-topic-category-map
  {"INLAND WATERS" "inlandWaters"
   "INTELLIGENCE/MILITARY" "intelligenceMilitary"
   "CLIMATOLOGY/METEOROLOGY/ATMOSPHERE" "climatologyMeteorologyAtmosphere"
   "UTILITIES/COMMUNICATIONS" "utilitiesCommunication"
   "FARMING" "farming"
   "IMAGERY/BASE MAPS/EARTH COVER" "imageryBaseMapsEarthCover"
   "STRUCTURE" "structure"
   "HEALTH" "health"
   "ELEVATION" "elevation"
   "SOCIETY" "society"
   "ENVIRONMENT" "environment"
   "BIOTA" "biota"
   "TRANSPORTATION" "transportation"
   "GEOSCIENTIFIC INFORMATION" "geoscientificInformation"
   "OCEANS" "oceans"
   "ECONOMY" "economy"
   "PLANNING CADASTRE" "planningCadastre"
   "LOCATION" "location"
   "BOUNDARIES" "boundaries"})

(def xml->umm-iso-topic-category-map
  (dissoc (set/map-invert umm->xml-iso-topic-category-map) nil))

(defn parse-iso-topic-categories
  "Returns parsed ISOTopicCategories"
  [doc base-xpath]
  (let [iso-topic-categories-els (select doc (str base-xpath iso-topic-category-xpath))
        parsed-categories (seq (map #(value-of % "gmd:MD_TopicCategoryCode")
                                   iso-topic-categories-els))]
    (map xml->umm-iso-topic-category-map parsed-categories)))
