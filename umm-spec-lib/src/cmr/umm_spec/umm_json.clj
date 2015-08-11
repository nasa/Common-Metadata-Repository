(ns cmr.umm-spec.umm-json
  "Contains functions for converting a UMM into JSON and back out of JSON."
  (:require [cheshire.core :as json]
            [cheshire.generate :as json-gen]
            [cmr.umm-spec.json-schema :as js]))

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



