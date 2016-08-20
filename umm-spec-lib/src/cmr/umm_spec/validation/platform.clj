(ns cmr.umm-spec.validation.platform
  "Defines validations for UMM collection platform."
  (:require [cmr.common.validations.core :as v]
            [cmr.umm-spec.validation.umm-spec-validation-utils :as vu]))

(def ^:private sensor-validations
  "Defines the sensor validations for collections"
  {:Characteristics (vu/unique-by-name-validator :Name)})

(def ^:private instrument-validations
  "Defines the instrument validations for collections"
  {:Sensors [(v/every sensor-validations)
             (vu/unique-by-name-validator :ShortName)]
   :Characteristics (vu/unique-by-name-validator :Name)})

(def ^:private platform-validations
  "Defines the platform validations for collections"
  {:Instruments [(v/every instrument-validations)
                 (vu/unique-by-name-validator :ShortName)]
   :Characteristics (vu/unique-by-name-validator :Name)})

(def platforms-validation
  "Defines the list of validation functions for validating collection platforms"
  [(v/every platform-validations)
   (vu/unique-by-name-validator :ShortName)])
