(ns cmr.umm-spec.iso-keywords
  "Contains utility functions and constants for parsing and generating ISO-19115-2 and SMAP ISO
  keywords. Collection science keywords, platforms and instruments are all represented as
  descriptiveKeywords. It would be better if the type element within the descriptiveKeywords could
  identify the type of the keywords. But currently it is always set to 'theme'. We will propose to
  get this changed, but in the mean time, we will have to parse the keyword string to determine the
  type of the keyword."
  (:require
   [clojure.data.xml :as x]
   [clojure.set :as set]
   [clojure.string :as str]
   [cmr.common.services.errors :as errors]
   [cmr.common.xml :as cx]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer [select]]
   [cmr.umm-spec.iso19115-2-util :as iso]
   [cmr.umm-spec.models.umm-common-models :as c]
   [cmr.umm-spec.util :as su])
  (:import
   (clojure.lang PersistentHashSet)))

(def nil-science-keyword-field
  "String used in ISO19115-2 to indicate that a given science keyword field is not present."
  "NONE")

(def nil-location-keyword-field
  "String used in ISO-SMAP to indicate that a given location keyword field is not present."
  "NONE")

(def platform-categories
  "Category keywords that identifies a descriptive keyword as a platform.
  We are doing this because GCMD currently is not setting the proper value in the type element
  of descriptiveKeywords to identify its type. We should use the type element to identify
  the type of the keyword once GCMD fixes that."
  #{"Aircraft"
    "Balloons/Rockets"
    "Earth Observation Satellites"
    "In Situ Land-based Platforms"
    "In Situ Ocean-based Platforms"
    "Interplanetary Spacecraft"
    "Maps/Charts/Photographs"
    "Models/Analyses"
    "Navigation Platforms"
    "Solar/Space Observation Satellites"
    "Space Stations/Manned Spacecraft"})

(def ^PersistentHashSet science-keyword-categories
  #{"EARTH SCIENCE" "EARTH SCIENCE SERVICES"})

(def location-keyword-type
  "String used to define the keyword type for location keywords."
  "place")

(def science-keyword-type
  "String used to define the keyword type for science keywords."
  "theme")

(def science-keyword-title
  "String used to define the keyword title for science keywords."
  "GCMD")

(defn parse-keyword-str
  "Returns a seq of individual components of an ISO-19115-2 or SMAP keyword string."
  [iso-keyword]
  (for [s (str/split iso-keyword iso/keyword-separator-split)]
    (if (empty? s)
      nil
      s)))

(defn keyword-type
  "Returns a value indicating the category of the given keyword string."
  [iso-keyword]
  (let [category (first (parse-keyword-str iso-keyword))]
    (cond
      (science-keyword-categories category)    :science
      (re-matches #".*Instruments$" category)  :instrument
      (platform-categories category)           :platform
      :else                                    :other)))

(defn parse-instrument
  "Converts the SMAP keyword string into an Instrument and returns it"
  [iso-keyword]
  (let [[_ _ _ _ short-name long-name] (parse-keyword-str iso-keyword)]
    {:ShortName short-name
     :LongName long-name}))

(defn platform
  "Returns the platform with the given keyword string and instruments"
  [instruments keyword-str]
  (let [[_ _ short-name long-name] (parse-keyword-str keyword-str)]
    {:ShortName short-name
     :LongName long-name
     :Type "Spacecraft"
     :Instruments instruments}))

(defn parse-platforms
  "Returns UMM PlatformType records with associated instruments from a seq of ISO SMAP keywords."
  [iso-keywords]
  (let [groups (group-by keyword-type iso-keywords)
        instruments (seq (map parse-instrument (:instrument groups)))]
    ;; There is no nested relationship between platform and instrument in SMAP ISO xml
    ;; So we put all instruments in each platform in the UMM
    (seq (map (partial platform instruments) (:platform groups)))))

(defn descriptive-keywords
  "Returns the descriptive keywords values for the given parent element and keyword type"
  [md-data-id-el keyword-type]
  (values-at md-data-id-el
             (str "gmd:descriptiveKeywords/gmd:MD_Keywords"
                  (format "[gmd:type/gmd:MD_KeywordTypeCode/@codeListValue='%s']" keyword-type)
                  "/gmd:keyword/gco:CharacterString")))


(defn descriptive-keywords-with-title
  "Returns the descriptive keyword values for the given parent element, keyword type and
  keyword title"
  [md-data-id-el keyword-type keyword-title]
  (let [desc-kws-with-kw-type
         (select md-data-id-el
                 (str "gmd:descriptiveKeywords"
                      "[gmd:MD_Keywords/gmd:type/gmd:MD_KeywordTypeCode/@codeListValue='"
                      keyword-type "']"))

        ;;for each descriptiveKeyword with the keyword-type, get /gmd:keyword/gco:CharacterString
        ;;and /gmd:keyword/gmx:Anchor with the title that includes the substring keyword-title.
        gco-values
         (for [desc-kw desc-kws-with-kw-type]
           (values-at desc-kw
                      (str "gmd:MD_Keywords"
                           "[contains(gmd:thesaurusName/gmd:CI_Citation/gmd:title/gco:CharacterString, '"
                           keyword-title "')]"
                           "/gmd:keyword/gco:CharacterString")))
        gmx-values
         (for [desc-kw desc-kws-with-kw-type]
           (values-at desc-kw
                      (str "gmd:MD_Keywords"
                           "[contains(gmd:thesaurusName/gmd:CI_Citation/gmd:title/gco:CharacterString, '"
                           keyword-title "')]"
                           "/gmd:keyword/gmx:Anchor")))
        keyword-values (concat gco-values gmx-values)]
    (flatten keyword-values)))

(defn parse-location-keywords
  "Returns the location keywords parsed from the given xml document."
  [md-data-id-el]
  (when-let [location-keywords (seq (descriptive-keywords md-data-id-el location-keyword-type))]
    (for [lk location-keywords
          :let [[category type subregion1 subregion2 subregion3
                 detailed-location] (map #(if (= nil-location-keyword-field %) nil %)
                                         (str/split lk iso/keyword-separator-split))]]
      {:Category category
       :Type type
       :Subregion1 subregion1
       :Subregion2 subregion2
       :Subregion3 subregion3
       :DetailedLocation detailed-location})))

(defn parse-science-keywords
  "Returns the science keywords parsed from the given xml document."
  ([md-data-id-el sanitize?]
   (parse-science-keywords md-data-id-el sanitize? false))
  ([md-data-id-el sanitize? smap?]
   (if-let [science-keywords (if smap?
                               ;; smap keeps the current behavior. 
                               (seq (descriptive-keywords md-data-id-el science-keyword-type)) 
                               (seq (descriptive-keywords-with-title md-data-id-el
                                                                     science-keyword-type
                                                                     science-keyword-title)))]
     (for [sk science-keywords
           :let [[category topic term variable-level-1 variable-level-2 variable-level-3
                  detailed-variable] (->> (str/split sk iso/keyword-separator-split)
                                          (map #(when-not (= nil-science-keyword-field %)  %)))]]
 
         {:Category category
          :Topic (su/with-default topic)
          :Term (su/with-default term)
          :VariableLevel1 variable-level-1
          :VariableLevel2 variable-level-2
          :VariableLevel3 variable-level-3
          :DetailedVariable detailed-variable})
     (when sanitize?
       su/not-provided-science-keywords))))

(defmulti smap-keyword-str
  "Returns a SMAP keyword string for a given UMM record."
  (fn [record]
    (type record)))

(defmethod smap-keyword-str cmr.umm_spec.models.umm_common_models.PlatformType
  [platform]
  (format "Aircraft > Not provided > %s > %s"
          (:ShortName platform)
          ;; Because LongName is optional, we want an empty string instead of "null"
          ;; here to prevent problems when parsing.
          (str (:LongName platform))))

(defmethod smap-keyword-str cmr.umm_spec.models.umm_common_models.InstrumentType
  [instrument]
  (format "Instruments > Not provided > Not provided  > Not provided  > %s > %s"
          (:ShortName instrument)
          (str (:LongName instrument))))

(defn science-keyword->iso-keyword-string
  "Returns an ISO science keyword string from the given science keyword."
  [science-keyword]
  (let [{category :Category
         topic :Topic
         term :Term
         variable-level-1 :VariableLevel1
         variable-level-2 :VariableLevel2
         variable-level-3 :VariableLevel3
         detailed-variable :DetailedVariable} science-keyword]
    (->> [category topic term variable-level-1 variable-level-2 variable-level-3 detailed-variable]
         (map #(or % nil-science-keyword-field))
         (str/join iso/keyword-separator-join))))

(defn location-keyword->iso-keyword-string
  "Returns an ISO location keyword string from the given location keyword."
  [location-keyword]
  (let [{category :Category
         type :Type
         subregion1 :Subregion1
         subregion2 :Subregion2
         subregion3 :Subregion3
         detailed-location :DetailedLocation} location-keyword]
    (->> [category type subregion1 subregion2 subregion3 detailed-location]
         (map #(or % nil-location-keyword-field))
         (str/join iso/keyword-separator-join))))

(defn- generate-descriptive-keywords
  "Returns the content generator instructions for the given descriptive keywords."
  [keyword-type keywords code-lists]
  (when (seq keywords)
    [:gmd:descriptiveKeywords
     [:gmd:MD_Keywords
      (for [keyword keywords]
        [:gmd:keyword [:gco:CharacterString keyword]])
      (when keyword-type
        [:gmd:type
         [:gmd:MD_KeywordTypeCode
          {:codeList (str code-lists "#MD_KeywordTypeCode")
           :codeListValue keyword-type} keyword-type]])
      (if (= keyword-type science-keyword-type)
        [:gmd:thesaurusName
         [:gmd:CI_Citation
          [:gmd:title [:gco:CharacterString science-keyword-title]]
          [:gmd:date {:gco:nilReason "unknown"}]]]
        [:gmd:thesaurusName {:gco:nilReason "unknown"}])]]))

(defn generate-iso19115-descriptive-keywords
  "Returns the content generator instructions ISO19115-2 descriptive keywords."
  [keyword-type keywords]
  (generate-descriptive-keywords keyword-type keywords (:ngdc iso/code-lists)))

(defn generate-iso-smap-descriptive-keywords
  "Returns the content generator instructions for ISO-SMAP descriptive keywords."
  [keyword-type keywords]
  (generate-descriptive-keywords keyword-type keywords (:iso iso/code-lists)))
