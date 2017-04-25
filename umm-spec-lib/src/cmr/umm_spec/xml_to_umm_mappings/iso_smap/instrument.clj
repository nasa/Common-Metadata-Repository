(ns cmr.umm-spec.xml-to-umm-mappings.iso-smap.instrument
  "Functions for parsing UMM instrument records out of ISO SMAP XML documents."
  (:require
    [cmr.common.util :as util] 
    [cmr.common.xml.parse :refer :all]
    [cmr.common.xml.simple-xpath :refer [select text]]
    [cmr.umm-spec.iso19115-2-util :as iso :refer [char-string-value]]
    [cmr.umm-spec.util :refer [without-default-value-of]]
    [cmr.umm-spec.xml-to-umm-mappings.iso-smap.characteristics-and-operationalmodes :as char-and-opsmode]))

(def instruments-xpath1
  (str "/gmd:DS_Series/gmd:seriesMetadata/gmi:MI_Metadata/gmi:acquisitionInformation/gmi:MI_AcquisitionInformation/gmi:instrument"
    "/eos:EOS_Instrument"))

(def instruments-xpath2
  (str instruments-xpath1 "/eos:sensor/eos:EOS_Sensor"))

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
  [doc instrument-elem]
  (let [all-possible-instrument-ids (keep #(get-in % [:attrs :id]) (select doc instruments-xpath1))
        id (get-in instrument-elem [:attrs :id])
        mountedOn-id (first (keep #(get-in % [:attrs :xlink/href]) (select instrument-elem "gmi:mountedOn")))]
    ;; only include child instruments - when mountedOn-id = one of the instrument ids.
    (when mountedOn-id
      (let [mountedOn-id-wo-# (.replaceAll mountedOn-id "#" "")
            instrument (xml-elem->child-instrument instrument-elem)]
        (when (some #(= mountedOn-id-wo-# %) all-possible-instrument-ids)
          [(str id "-" mountedOn-id-wo-#) instrument])))))

(defn- xml-elem->instrument-sensor-mapping
  [doc instrument-elem]
  (let [all-possible-instrument-ids (keep #(get-in % [:attrs :id]) (select doc instruments-xpath1))
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
  [doc]
  (merge
    (into {}
          (map (partial xml-elem->child-instrument-mapping doc) (select doc instruments-xpath1)))
    (into {}
          (map (partial xml-elem->instrument-sensor-mapping doc) (select doc instruments-xpath2)))))

(defn- get-child-instruments
  "Returns the parsed child instruments from the instrument element.
   The child instruments' mountedOn = the instrument element's id"
  [doc instrument-elem]
  (let [id (get-in instrument-elem [:attrs :id])
        child-instruments-mapping (xml-elem->child-instruments-mapping doc)]
    (remove nil?
      (for [[k v] child-instruments-mapping]
        (when (.contains k (str "-" id))
          v)))))  

;;(defn- get-ops-modes
 ;; "Returns the operational modes"
  ;;[])  

(defn- xml-elem->instrument
  [doc instrument-elem]
  (let [child-instruments (get-child-instruments doc instrument-elem)]
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
  [doc instrument-elem]
  (let [all-possible-instrument-ids (keep #(get-in % [:attrs :id]) (select doc instruments-xpath1))
        id (get-in instrument-elem [:attrs :id])
        mountedOn-id (first (keep #(get-in % [:attrs :xlink/href]) (select instrument-elem "gmi:mountedOn")))]
    ;; exclude child instruments - when mountedOn-id = one of the instrument ids.
    (when mountedOn-id 
      (when-not (some #(= (.replaceAll mountedOn-id "#" "") %) all-possible-instrument-ids)  
        (let [instrument (xml-elem->instrument doc instrument-elem)]
          [(str "#" id) instrument])))))

(defn xml-elem->instruments-mapping
  "Returns the instrument id to Instrument mapping by parsing the given xml element"
  [doc]
  (into {}
        (map (partial xml-elem->instrument-mapping doc) (select doc instruments-xpath1))))

