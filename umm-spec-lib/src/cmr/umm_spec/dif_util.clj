(ns cmr.umm-spec.dif-util
  "Contains common definitions and functions for DIF9 and DIF10 metadata parsing and generation."
  (:require
   [clojure.set :as set]
   [cmr.common.util :as common-util]
   [cmr.common.xml.parse :refer :all]
   [cmr.umm-spec.util :as util]))

(def iso-639-2->dif10-dataset-language
  "Mapping from ISO 639-2 to the enumeration supported for dataset languages in DIF10."
  {"eng" "English"
   "afr" "Afrikaans"
   "ara" "Arabic"
   "bos" "Bosnian"
   "bul" "Bulgarian"
   "chi" "Chinese"
   "zho" "Chinese"
   "hrv" "Croatian"
   "cze" "Czech"
   "ces" "Czech"
   "dan" "Danish"
   "dum" "Dutch"
   "dut" "Dutch"
   "nld" "Dutch"
   "est" "Estonian"
   "fin" "Finnish"
   "fre" "French"
   "fra" "French"
   "gem" "German"
   "ger" "German"
   "deu" "German"
   "gmh" "German"
   "goh" "German"
   "gsw" "German"
   "nds" "German"
   "heb" "Hebrew"
   "hun" "Hungarian"
   "ind" "Indonesian"
   "ita" "Italian"
   "jpn" "Japanese"
   "kor" "Korean"
   "lav" "Latvian"
   "lit" "Lithuanian"
   "nno" "Norwegian"
   "nob" "Norwegian"
   "nor" "Norwegian"
   "pol" "Polish"
   "por" "Portuguese"
   "rum" "Romanian"
   "ron" "Romanian"
   "rup" "Romanian"
   "rus" "Russian"
   "slo" "Slovak"
   "slk" "Slovak"
   "spa" "Spanish"
   "ukr" "Ukrainian"
   "vie" "Vietnamese"})

(def dif10-dataset-language->iso-639-2
  "Mapping from the DIF10 enumeration dataset languages to ISO 639-2 language code."
  (set/map-invert iso-639-2->dif10-dataset-language))

(def ^:private dif10-dataset-languages
  "Set of Dataset_Languages supported in DIF10"
  (set (vals iso-639-2->dif10-dataset-language)))

(defn- umm-langage->dif-language
  "Return DIF9/DIF10 dataset language for the given umm DataLanguage.
  Currenlty, the UMM JSON schema does mandate the language as an enum, so we try our best to match
  the possible arbitrary string to a defined DIF10 language enum."
  [language]
  (let [language (util/capitalize-words language)]
    (if (dif10-dataset-languages language)
        language
        (get iso-639-2->dif10-dataset-language language "English"))))

(defn dif-language->umm-langage
  "Return UMM DataLanguage for the given DIF9/DIF10 dataset language."
  [language]
  (when-let [language (util/capitalize-words language)]
    (get dif10-dataset-language->iso-639-2 language "eng")))

(defn generate-dataset-language
  "Return DIF9/DIF10 dataset language generation instruction with the given element key
  and UMM DataLanguage"
  [element-key data-language]
  (when data-language
    [element-key (umm-langage->dif-language data-language)]))

(defn parse-access-constraints
  "if both value and Description are nil, return nil.
  Otherwise, if Description is nil, assoc it with u/not-provided"
  [doc apply-default?]
  (let [access-constraints-record
        {:Description (value-of doc "/DIF/Access_Constraints")
         :Value (value-of doc "/DIF/Extended_Metadata/Metadata[Name='Restriction']/Value")}]
    (when (seq (common-util/remove-nil-keys access-constraints-record))
      (update access-constraints-record :Description #(util/with-default % apply-default?)))))
