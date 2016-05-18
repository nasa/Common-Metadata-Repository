module DraftsHelper
  CollectionDataTypeOptions ||= [
    ['Science Quality', 'SCIENCE_QUALITY'],
    ['Near Real Time', 'NEAR_REAL_TIME'],
    ['Other', 'OTHER']
  ]
  CollectionProgressOptions ||= [
    ['Planned', 'PLANNED'],
    ['In work', 'IN WORK'],
    ['Complete', 'COMPLETE']
  ]
  CoordinateSystemOptions ||= [
    ['Cartesian', 'CARTESIAN'],
    ['Geodetic', 'GEODETIC']
  ]
  ContactTypeOptions ||= [
    ['Direct Line'],
    ['Email'],
    ['Facebook'],
    ['Fax'],
    ['Mobile'],
    ['Modem'],
    ['Primary'],
    ['TDD/TTY Phone'],
    ['Telephone'],
    ['Twitter'],
    ['U.S. toll free'],
    ['Other'],
  ]
  DataTypeOptions ||= [
    ['String', 'STRING'],
    ['Float', 'FLOAT'],
    ['Integer', 'INT'],
    ['Boolean', 'BOOLEAN'],
    ['Date', 'DATE'],
    ['Time', 'TIME'],
    ['Date time', 'DATETIME'],
    ['Date String', 'DATESTRING'],
    ['Time String', 'TIMESTRING'],
    ['Date Time String', 'DATETIMESTRING']
  ]
  DurationOptions ||= [
    ['Day', 'DAY'],
    ['Month', 'MONTH'],
    ['Year', 'YEAR']
  ]
  DateTypeOptions ||= [
    ['Creation', 'CREATE'],
    ['Last Revision', 'UPDATE'],
    ['Future Review', 'REVIEW'],
    ['Planned Deletion', 'DELETE']
  ]
  FileSizeUnitTypeOptions ||= [
    ['KB'],
    ['MB'],
    ['GB'],
    ['TB'],
    ['PB']
  ]
  GranuleSpatialRepresentationOptions ||= [
    ['Cartesian', 'CARTESIAN'],
    ['Geodetic', 'GEODETIC'],
    ['Orbit', 'ORBIT'],
    ['No Spatial', 'NO_SPATIAL'],
  ]
  ISOTopicCategoriesOptions ||= [
    ['Farming', 'farming'],
    ['Biota', 'biota'],
    ['Boundaries', 'boundaries'],
    ['Climatology / Meteorology / Atmosphere', 'climatologyMeteorologyAtmosphere'],
    ['Economy', 'economy'],
    ['Elevation', 'elevation'],
    ['Environment', 'environment'],
    ['Geoscientific Information', 'geoscientificInformation'],
    ['Health', 'health'],
    ['Imagery / Base Maps / Earth Cover', 'imageryBaseMapsEarthCover'],
    ['Intelligence / Military', 'intelligenceMilitary'],
    ['Inland Waters', 'inlandWaters'],
    ['Location', 'location'],
    ['Oceans', 'oceans'],
    ['Planning / Cadastre', 'planningCadastre'],
    ['Society', 'society'],
    ['Structure', 'structure'],
    ['Transportation', 'transportation'],
    ['Utilities / Communication', 'utilitiesCommunication']
  ]
  MetadataAssociationTypeOptions ||= [
    ['Science Associated', 'SCIENCE ASSOCIATED'],
    ['Dependent', 'DEPENDENT'],
    ['Input', 'INPUT'],
    ['Parent', 'PARENT'],
    ['Child', 'CHILD'],
    ['Related', 'RELATED'],
    ['Larger Citation Works', 'LARGER CITATION WORKS']
  ]
  MimeTypeOptions ||= [
    ['application/msword'],
    ['application/pdf'],
    ['application/xml'],
    ['image/jpeg'],
    ['text/html'],
    ['text/plain']
  ]
  ProcessingLevelIdOptions ||= [
    ['Not Provided'],
    ['Level 0'],
    ['Level 1'],
    ['Level 1A'],
    ['Level 1B'],
    ['Level 1C'],
    ['Level 1T'],
    ['Level 2'],
    ['Level 2G'],
    ['Level 2P'],
    ['Level 3'],
    ['Level 4'],
    ['NA']
  ]
  RoleOptions ||= [
    ['Resource Provider', 'RESOURCEPROVIDER'],
    ['Custodian', 'CUSTODIAN'],
    ['Owner', 'OWNER'],
    ['User', 'USER'],
    ['Distributor', 'DISTRIBUTOR'],
    ['Originator', 'ORIGINATOR'],
    ['Point of Contact', 'POINTOFCONTACT'],
    ['Principal Investigator', 'PRINCIPALINVESTIGATOR'],
    ['Processor', 'PROCESSOR'],
    ['Publisher', 'PUBLISHER'],
    ['Author', 'AUTHOR'],
    ['Sponsor', 'SPONSOR'],
    ['Co-Author', 'COAUTHOR'],
    ['Collaborator', 'COLLABORATOR'],
    ['Editor', 'EDITOR'],
    ['Mediator', 'MEDIATOR'],
    ['Rights Holder', 'RIGHTSHOLDER'],
    ['Contributor', 'CONTRIBUTOR'],
    ['Funder', 'FUNDER'],
    ['Stakeholder', 'STAKEHOLDER']
  ]
  SpatialCoverageTypeOptions ||= [
    ['Horizontal', 'HORIZONTAL'],
    ['Vertical', 'VERTICAL'],
    ['Orbital', 'ORBITAL'],
    ['Both', 'BOTH'] # Perhaps 'Both' should actually read 'Horizontal and Vertical', to be more clear to the user
  ]

  def construct_keyword_string(hash_obj, str)
    # Assumes hash is passed in as ordered
    hash_obj.each do |_key, value|
      if value.is_a?(String)
        str << ' > ' unless str.blank?
        str = str << value
      else # Use tail recursion to construct the string found in the sub-hash
        str = construct_keyword_string(value, str)
      end
    end
    str
  end

  # Takes a html element name (draft_|metadata_lineage|_index_role) and
  # outputs a param name (draft[metadata_lineage][index][role])
  # Words that should keep their underscore should be wrapped in pipes, like "_|metadata_lineage|_"
  def name_to_param(name)
    # convert good words (wrapped in pipes) to dashes
    name.gsub!(/(_?)\|(\w+)\|(_?)/) { "#{Regexp.last_match[1]}#{Regexp.last_match[2].dasherize}#{Regexp.last_match[3]}" }

    # split words on underscores, wrap in brackets, and convert good words back to underscores
    name = name.split('_').map.with_index do |word, index|
      word = word.gsub(/(?<!new)index/, '').underscore
      if index == 0
        word
      else
        "[#{word}]"
      end
    end

    # join wrapped words
    name.join
  end

  def remove_pipes(string)
    string.delete('|')
  end

  # Change json keys like 'FileSize' to acceptable html class names like 'file-size'
  def name_to_class(key)
    if key == 'URLs'
      'urls'
    else
      key.to_s.underscore.dasherize
    end
  end

  def name_to_title(name)
    is_id = name.end_with?('Id') && name.size > 2 ? ' Id' : ''

    if name == 'URLs'
      'URLs'
    else
      name.underscore.titleize + is_id
    end
  end

  # Used to derive the displayed string of a select type control from the value stored in json
  def map_value_onto_display_string(str, options)
    options_hash = Hash[options.map { |key, value| [value, key] }]
    options_hash[str]
  end

  def keyword_string(keywords)
    keywords.map { |_key, value| value }.join(' > ')
  end

  def options_for_subregion_select(country, value = nil)
    return nil unless country

    options = country.subregions.map(&:name).sort
    options.unshift ['Select State/Province', '']
    options_for_select(options, selected: value)
  end
end
