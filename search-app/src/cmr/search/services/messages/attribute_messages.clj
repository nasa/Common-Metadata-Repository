(ns cmr.search.services.messages.attribute-messages
  "Contains messages for reporting responses to the user")

;; Client error messages

(defn invalid-num-parts-msg
  []
  (str "Invalid number of additional attribute parts. "
       "Format is \"type,name,value\" or \"type,name,min,max\"."))

(defn invalid-type-msg
  [type]
  (format "[%s] is an invalid type" (str type)))

(defn invalid-value-msg
  [type value]
  (format "[%s] is an invalid value for type [%s]" (pr-str value) (name type)))

(defn invalid-name-msg
  [n]
  (format "[%s] is not a valid name for an attribute." (str n)))

(defn one-of-min-max-msg
  []
  "At least one of min or max must be provided for an additional attribute search.")

(defn max-must-be-greater-than-min-msg
  [minv maxv]
  (format "The maximum value [%s] must be greater than the minimum value [%s]" maxv minv))

(defn attributes-must-be-sequence-msg
  []
  "'attribute' is not a valid parameter. You must use 'attribute[]'.")

;; This message is for clients that mistakenly specify a field of an attribute twice when
;; using the legacy format, e.g., attribute[][name]=ABC&attribute[][name]=DEF.
(defn duplicate-parameter-msg
  [parameter]
  (let [[param value] parameter]
    (format "Duplicate parameters are not allowed [%s = %s]." (name param) value)))

(defn mixed-legacy-and-cmr-style-parameters-msg
  []
  (str "Product specific attributes queries must be either legacy format "
       "\"attribute[][name]=name&attribute[][type]=type&attribute[value]=value\" or current"
       "format \"type,name,value\", not both."))

(def missing-name-and-group
  "One of 'group' or 'name' must be provided.")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Internal error messages

(defn expected-map-or-str-parameter-msg
  [value]
  (str "Expcected attribute parameters [" value "] to be a map or string"))

