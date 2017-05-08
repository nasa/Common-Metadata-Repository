(ns cmr.umm-spec.xml-to-umm-mappings.iso-shared.instrument
  "Functions for parsing UMM instrument records out of ISO SMAP XML documents."
  (:require
    [cmr.common.util :as util]
    [cmr.common.xml.parse :refer :all]
    [cmr.common.xml.simple-xpath :refer [select text]]
    [cmr.umm-spec.iso19115-2-util :as iso :refer [char-string-value]]
    [cmr.umm-spec.util :refer [without-default-value-of]]
    [cmr.umm-spec.xml-to-umm-mappings.iso-shared.characteristics-and-operationalmodes :as char-and-opsmode]))

(def instrument-xpath
  (str "/gmi:MI_Metadata/gmi:acquisitionInformation/gmi:MI_AcquisitionInformation/gmi:instrument"
    "/eos:EOS_Instrument"))

(def composed-of-xpath
  (str instrument-xpath "/eos:sensor/eos:EOS_Sensor"))

(defn- xml-elem->child-instrument
  "Returns the parsed child instrument from the child instrument element.
   This child instrument element could be the reqular instrument element or
   the sensor instrument element"
  [child-instr-elem]
  (util/remove-nil-keys
    (if (value-of child-instr-elem iso/short-name-xpath)
      {:ShortName (value-of child-instr-elem iso/short-name-xpath)
       :LongName (value-of child-instr-elem iso/long-name-xpath)
       :Technique (without-default-value-of child-instr-elem "gmi:type/gco:CharacterString")
       :Characteristics (char-and-opsmode/parse-characteristics child-instr-elem)}
      {:ShortName (char-string-value child-instr-elem "eos:identifier/gmd:MD_Identifier/gmd:code")
       :LongName (char-string-value child-instr-elem "eos:identifier/gmd:MD_Identifier/gmd:description")
       :Technique (without-default-value-of child-instr-elem "eos:type/gco:CharacterString")
       :Characteristics (char-and-opsmode/parse-characteristics child-instr-elem)})))

(defn- xml-elem->child-instrument-mapping
  [doc base-xpath instrument-elem]
  (let [all-possible-instrument-ids (keep #(get-in % [:attrs :id]) (select doc (str base-xpath instrument-xpath)))
        id (get-in instrument-elem [:attrs :id])
        mountedOn-id (first (keep #(get-in % [:attrs :xlink/href]) (select instrument-elem "gmi:mountedOn")))]
    ;; only include child instruments - when mountedOn-id = one of the instrument ids.
    (when mountedOn-id
      (let [mountedOn-id-wo-# (.replaceAll mountedOn-id "#" "")
            instrument (xml-elem->child-instrument instrument-elem)]
        (when (some #(= mountedOn-id-wo-# %) all-possible-instrument-ids)
          [(str id "-" mountedOn-id-wo-#) instrument])))))

(defn- xml-elem->instrument-sensor-mapping
  [doc base-xpath instrument-elem]
  (let [all-possible-instrument-ids (keep #(get-in % [:attrs :id]) (select doc (str base-xpath instrument-xpath)))
        id (get-in instrument-elem [:attrs :id])
        mountedOn-id (first (keep #(get-in % [:attrs :xlink/href]) (select instrument-elem "eos:mountedOn")))]
    ;; only include child instruments - when mountedOn-id = one of the instrument ids.
    (when mountedOn-id
      (let [mountedOn-id-wo-# (.replaceAll mountedOn-id "#" "")
            instrument (xml-elem->child-instrument instrument-elem)]
        (when (some #(= mountedOn-id-wo-# %) all-possible-instrument-ids)
          [(str id "-" mountedOn-id-wo-#) instrument])))))

(defn- xml-elem->child-instruments-mapping
  "Returns the child instrument id-mountedOnid to child Instrument mapping
   by parsing the given xml element"
 [doc base-xpath]
 (merge
   (into {}
         (map (partial xml-elem->child-instrument-mapping doc base-xpath) (select doc (str base-xpath instrument-xpath))))
   (into {}
         (map (partial xml-elem->instrument-sensor-mapping doc base-xpath) (select doc (str base-xpath composed-of-xpath))))))

(defn- get-child-instruments
  "Returns the parsed child instruments from the instrument element.
   The child instruments' mountedOn = the instrument element's id"
  [doc base-xpath instrument-elem]
  (let [id (get-in instrument-elem [:attrs :id])
        child-instruments-mapping (xml-elem->child-instruments-mapping doc base-xpath)]
    (remove nil?
      (for [[k v] child-instruments-mapping]
        (when (.contains k (str "-" id))
          v)))))

(defn- xml-elem->instrument
  "Returns instrument record from the instrument element."
  [doc base-xpath instrument-elem]
  (let [child-instruments (get-child-instruments doc base-xpath instrument-elem)]
    (util/remove-nil-keys
     (if (> (count child-instruments) 0)
       {:ShortName (value-of instrument-elem iso/short-name-xpath)
        :LongName (value-of instrument-elem iso/long-name-xpath)
        :Characteristics (char-and-opsmode/parse-characteristics instrument-elem)
        :Technique (without-default-value-of instrument-elem "gmi:type/gco:CharacterString")
        :NumberOfInstruments (count child-instruments)
        :ComposedOf child-instruments
        :OperationalModes (char-and-opsmode/parse-operationalmodes instrument-elem)}
       {:ShortName (value-of instrument-elem iso/short-name-xpath)
        :LongName (value-of instrument-elem iso/long-name-xpath)
        :Characteristics (char-and-opsmode/parse-characteristics instrument-elem)
        :Technique (without-default-value-of instrument-elem "gmi:type/gco:CharacterString")
        :OperationalModes (char-and-opsmode/parse-operationalmodes instrument-elem)}))))

(defn- xml-elem->instrument-mapping
  "Returns the instrument id and the instrument mapping {id instrument} for the instrument element.
   It excludes the child instruments"
  [doc base-xpath instrument-elem]
  (let [all-possible-instrument-ids (keep #(get-in % [:attrs :id]) (select doc (str base-xpath instrument-xpath)))
        id (get-in instrument-elem [:attrs :id])
        mountedOn-id (first (keep #(get-in % [:attrs :xlink/href]) (select instrument-elem "gmi:mountedOn")))]
    ;; exclude child instruments - when mountedOn-id = one of the instrument ids.
    (when mountedOn-id
      (when-not (some #(= (.replaceAll mountedOn-id "#" "") %) all-possible-instrument-ids)
        (let [instrument (xml-elem->instrument doc base-xpath instrument-elem)]
          [(str "#" id) instrument])))))

(defn xml-elem->instruments-mapping
  "Returns the instrument id to Instrument mapping by parsing the given xml element"
  [doc base-xpath]
  (into {}
        (map (partial xml-elem->instrument-mapping doc base-xpath) (select doc (str base-xpath instrument-xpath)))))
