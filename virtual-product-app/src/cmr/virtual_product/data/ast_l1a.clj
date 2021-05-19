(ns cmr.virtual-product.data.ast-l1a
  "Defines virtual collections additional attributes mapping for AST_L1A.")

(def additional-attributes-05-08-09T
  "The set of additional attributes for AST_05, AST_08 and AST_09T."
  #{"ASTERGains" "ASTERGRANULEID" "ASTERMapOrientationAngle" "ASTERProcessingCenter"
    "ASTERReceivingCenter" "ASTERSceneOrientationAngle" "ASTERTIRPointingAngle" "DAR_ID"
    "GenerationDateandTime" "GeometricDBVersion" "LowerLeftQuadCloudCoverage" "LowerRightQuadCloudCoverage"
    "RadiometricDBVersion" "SceneCloudCoverage" "Solar_Azimuth_Angle" "Solar_Elevation_Angle"
    "UpperLeftQuadCloudCoverage" "UpperRightQuadCloudCoverage" "source_granule_ur"})

(def additional-attributes-07-07XT-09-09XT
  "The set of additional attributes for AST_07, AST_07XT, AST_09, AST_09XT."
  #{"ASTERGains" "ASTERGRANULEID" "ASTERMapOrientationAngle" "ASTERProcessingCenter"
    "ASTERReceivingCenter" "ASTERSceneOrientationAngle" "ASTERSWIRPointingAngle"
    "ASTERVNIRPointingAngle" "Band4_Available" "Band5_Available" "Band6_Available" "Band7_Available"
    "Band8_Available" "Band9_Available" "DAR_ID" "GenerationDateandTime" "GeometricDBVersion"
    "LowerLeftQuadCloudCoverage" "LowerRightQuadCloudCoverage" "RadiometricDBVersion"
    "SceneCloudCoverage" "Solar_Azimuth_Angle" "Solar_Elevation_Angle" "UpperLeftQuadCloudCoverage"
    "UpperRightQuadCloudCoverage" "source_granule_ur"})

(def additional-attributes-14DEM
  "The set of additional attributes for AST14DEM."
  #{"ASTERGains" "ASTERGRANULEID" "ASTERMapOrientationAngle" "ASTERProcessingCenter"
    "ASTERReceivingCenter" "ASTERSceneOrientationAngle" "ASTERSWIRPointingAngle"
    "ASTERVNIRPointingAngle" "Band4_Available" "Band5_Available" "Band6_Available" "Band7_Available"
    "Band8_Available" "Band9_Available" "DAR_ID" "GenerationDateandTime" "GeometricDBVersion"
    "LowerLeftQuadCloudCoverage" "LowerRightQuadCloudCoverage" "RadiometricDBVersion"
    "SceneCloudCoverage" "Solar_Azimuth_Angle" "Solar_Elevation_Angle" "UpperLeftQuadCloudCoverage"
    "UpperRightQuadCloudCoverage" "source_granule_ur"})

(def additional-attributes-14DMO-14OTH
  "The set of additional attributes for AST14DMO, AST14OTH."
  #{"ASTERGains" "ASTERGRANULEID" "ASTERMapOrientationAngle" "ASTERProcessingCenter"
    "ASTERReceivingCenter" "ASTERSceneOrientationAngle" "ASTERSWIRPointingAngle"
    "ASTERTIRPointingAngle" "ASTERVNIRPointingAngle" "Band4_Available" "Band5_Available"
    "Band6_Available" "Band7_Available" "Band8_Available" "Band9_Available" "DAR_ID"
    "GenerationDateandTime" "GeometricDBVersion" "LowerLeftQuadCloudCoverage"
    "LowerRightQuadCloudCoverage" "RadiometricDBVersion" "SceneCloudCoverage" "Solar_Azimuth_Angle"
    "Solar_Elevation_Angle" "UpperLeftQuadCloudCoverage" "UpperRightQuadCloudCoverage"
    "source_granule_ur"})

(def additional-attributes-L1B
  "The set of additional attributes for AST_L1B."
  #{"ASTERGains" "ASTERMapOrientationAngle" "ASTERMapProjection" "ASTERProcessingCenter"
    "ASTERReceivingCenter" "ASTERSWIRPointingAngle" "ASTERTIRPointingAngle"
    "ASTERVNIRPointingAngle" "Band10_Available" "Band11_Available" "Band12_Available"
    "Band13_Available" "Band14_Available" "Band1_Available" "Band2_Available"
    "Band3B_Available" "Band3N_Available" "Band4_Available" "Band5_Available"
    "Band6_Available" "Band7_Available" "Band8_Available" "Band9_Available" "DAR_ID"
    "GenerationDateandTime" "GeometricDBVersion" "LowerLeftQuadCloudCoverage"
    "LowerRightQuadCloudCoverage" "RadiometricDBVersion" "SWIR_ObservationMode"
    "SceneCloudCoverage" "Solar_Azimuth_Angle" "Solar_Elevation_Angle" "TIR_ObservationMode"
    "UpperLeftQuadCloudCoverage" "UpperRightQuadCloudCoverage" "VNIR1_ObservationMode"
    "VNIR2_ObservationMode" "source_granule_ur"})

(def additional-attributes-L1T-031
  "The set of additional attributes for AST_L1T.031."
  #{"ASTERGains" "ASTERMapOrientationAngle" "ASTERMapProjection" "ASTERProcessingCenter"
    "ASTERReceivingCenter" "ASTERSWIRPointingAngle" "ASTERTIRPointingAngle"
    "ASTERVNIRPointingAngle" "Band10_Available" "Band11_Available" "Band12_Available"
    "Band13_Available" "Band14_Available" "Band1_Available" "Band2_Available"
    "Band3B_Available" "Band3N_Available" "Band4_Available" "Band5_Available"
    "Band6_Available" "Band7_Available" "Band8_Available" "Band9_Available" "DAR_ID"
    "GenerationDateandTime" "GeometricDBVersion" "LowerLeftQuadCloudCoverage"
    "LowerRightQuadCloudCoverage" "RadiometricDBVersion" "SWIR_ObservationMode"
    "SceneCloudCoverage" "Solar_Azimuth_Angle" "Solar_Elevation_Angle" "TIR_ObservationMode"
    "UpperLeftQuadCloudCoverage" "UpperRightQuadCloudCoverage" "VNIR1_ObservationMode"
    "VNIR2_ObservationMode" "source_granule_ur"})

(def short-name->additional-attributes
  "Defines virtual collection short-name to additional-attributes mapping."
  {"AST_05" additional-attributes-05-08-09T
   "AST_08" additional-attributes-05-08-09T
   "AST_09T" additional-attributes-05-08-09T
   "AST_07" additional-attributes-07-07XT-09-09XT
   "AST_07XT" additional-attributes-07-07XT-09-09XT
   "AST_09" additional-attributes-07-07XT-09-09XT
   "AST_09XT" additional-attributes-07-07XT-09-09XT
   "AST14DEM" additional-attributes-14DEM
   "AST14DMO" additional-attributes-14DMO-14OTH
   "AST14OTH" additional-attributes-14DMO-14OTH
   "AST_L1B" additional-attributes-L1B
   "AST_L1T" additional-attributes-L1T-031})
