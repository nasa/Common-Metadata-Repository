(ns cmr.umm-spec.umm-json
  "Contains functions for converting a UMM into JSON and back out of JSON."
  (:require [cheshire.core :as json]
            [cheshire.generate :as json-gen]
            [cmr.umm-spec.json-schema :as js]

            ;; Temporary require statements for demo

            [cmr.umm-spec.xml-mappings.xml-generator :as xg]
            [cmr.umm-spec.xml-mappings.iso19115-2 :as xm-iso2]
            [cmr.umm-spec.xml-mappings.echo10 :as xm-echo10]
            [cmr.umm-spec.umm-mappings.iso19115-2 :as um-iso2]
            [cmr.umm-spec.umm-mappings.echo10 :as um-echo10]
            [cmr.umm-spec.umm-mappings.parser :as xp]
            [cmr.umm-spec.models.collection :as umm-c]
            [cmr.umm-spec.models.common :as umm-cmn]
            [cmr.umm-spec.json-schema :as js]

            ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UMM To JSON

;; Adds the ability for Joda DateTimes to be converted to JSON.
(json-gen/add-encoder
  org.joda.time.DateTime
  (fn [c jsonGenerator]
    (.writeString jsonGenerator (str c))))

(defprotocol ToJsonAble
  "A function for converting data into data that can easily be converted to JSON"
  (to-jsonable
    [o]
    "Takes in object and returns a new object that can be converted to JSON"))

(extend-protocol ToJsonAble

  ;; Records contain every key with a value of nil. We don't want the JSON to contain that.
  ;; This converts them into a standard map without the nil keys.
  clojure.lang.IRecord
  (to-jsonable
    [r]
    (into {}
          (for [[k v] r
                :when (some? v)]
            [(to-jsonable k) (to-jsonable v)])))


  ;; Default implementations
  Object
  (to-jsonable [o] o)

  nil
  (to-jsonable [o] o))

(defn umm->json
  "Converts the UMM record to JSON."
  [umm-record]
  (json/generate-string (to-jsonable umm-record)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; JSON to UMM


(defn json->umm
  [json-str]
  ;; TODO we should be converting this into the appropriate types. For now it works as a demo to
  ;; convert to the XML

  (json/decode json-str true)
  )


;; Temporary Demo code

(defn echo10->umm-json
  [xml]
  (umm->json (xp/parse-xml um-echo10/echo10-xml-to-umm-c xml)))

(defn umm-json->echo10
  [json-str]
  (xg/generate-xml xm-echo10/umm-c-to-echo10-xml (json->umm json-str)))

(defn iso19115->umm-json
  [xml]
  (umm->json (xp/parse-xml um-iso2/iso19115-2-xml-to-umm-c xml)))

(defn umm-json->iso19115
  [json-str]
  (xg/generate-xml xm-iso2/umm-c-to-iso19115-2-xml (json->umm json-str)))


(defn pretty-print-json
  [json-str]
  (println (json/generate-string (json/decode json-str) {:pretty true})))


(defn pretty-print-xml
  [xml]
  (println (cmr.common.xml/pretty-print-xml xml)))
