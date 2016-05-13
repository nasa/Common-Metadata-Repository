module CollectionsHelper

  def render_change_provider_collection_action_link(collection_action, concept_id, revision_id = nil)
    case collection_action
    when 'edit'
      link_to('Edit Collection', edit_collection_path(concept_id, revision_id: revision_id),
        class: 'is-invisible', id: 'change-provider-edit-collection')
    when 'clone'
      link_to('Clone Collection', clone_collection_path(concept_id),
        class: 'is-invisible', id: 'change-provider-clone-collection')
    when 'delete'
      link_to('Edit Collection', collection_path(concept_id), method: :delete,
        class: 'is-invisible', id: 'change-provider-delete-collection')
    when 'revert'
      link_to('Edit Collection', edit_collection_path(concept_id, revision_id: revision_id),
        class: 'is-invisible', id: 'change-provider-revert-collection')
    end
  end

  def display_collection_entry_title(collection)
    collection['EntryTitle'] || 'Entry Title Not Provided'
  end

  # Calculate spatial extent display information
  def get_preview_spatial(metadata)
    point_coordinate_array = []
    rectangle_coordinate_array = []

    if metadata['SpatialExtent'] &&
       metadata['SpatialExtent']['HorizontalSpatialDomain'] &&
       metadata['SpatialExtent']['HorizontalSpatialDomain']['Geometry']

      # needs to be aligned with size of map image on metadata preview
      map_width = 309
      map_height = 154.5

      geometry = metadata['SpatialExtent']['HorizontalSpatialDomain']['Geometry']

      unless geometry['Points'].blank?

        geometry['Points'].each do |point|
          x, y = convert_lat_lon_to_image_x_y(point['Latitude'], point['Longitude'], map_width, map_height)
          point_coordinate_array << {
            'lat' => point['Latitude'],
            'lon' => point['Longitude'],
            'x' => x,
            'y' => y
          }
        end

      end

      unless geometry['BoundingRectangles'].blank?
        geometry['BoundingRectangles'].each do |bounding_rectangle|
          north_bounding_coordinate = bounding_rectangle['NorthBoundingCoordinate']
          west_bounding_coordinate = bounding_rectangle['WestBoundingCoordinate']
          south_bounding_coordinate = bounding_rectangle['SouthBoundingCoordinate']
          east_bounding_coordinate = bounding_rectangle['EastBoundingCoordinate']

          x1, y1 = convert_lat_lon_to_image_x_y(north_bounding_coordinate, west_bounding_coordinate, map_width, map_height)
          x2, y2 = convert_lat_lon_to_image_x_y(south_bounding_coordinate, east_bounding_coordinate, map_width, map_height)
          if x1 && x2 && y1 && y2
            min_x = [x1, x2].min
            max_x = [x1, x2].max
            min_y = [y1, y2].min
            max_y = [y1, y2].max

            rectangle_coordinate_array << {
              'north_bounding_coordinate' => north_bounding_coordinate,
              'west_bounding_coordinate' => west_bounding_coordinate,
              'south_bounding_coordinate' => south_bounding_coordinate,
              'east_bounding_coordinate' => east_bounding_coordinate,
              'min_x' => min_x,
              'min_y' => min_y,
              'max_x' => max_x,
              'max_y' => max_y
            }
          end
        end

      end
    end

    preview_spatial_hash = {}
    preview_spatial_hash['point_coordinate_array'] = point_coordinate_array
    preview_spatial_hash['rectangle_coordinate_array'] = rectangle_coordinate_array

    preview_spatial_hash
  end

  # Collect temporal extent display information
  def get_preview_temporal(metadata)
    single_date_times = []
    range_date_times = []
    periodic_date_times = []

    unless metadata['TemporalExtents'].blank?
      metadata['TemporalExtents'].each do |temporal_extent|
        if temporal_extent['SingleDateTimes']

          temporal_extent['SingleDateTimes'].each do |single_date_time|
            single_date_times << single_date_time
          end

        elsif temporal_extent['RangeDateTimes']

          temporal_extent['RangeDateTimes'].each do |range_date_time|
            range_date_times << { 'beginning_date_time' => range_date_time['BeginningDateTime'], 'ending_date_time' => range_date_time['EndingDateTime'] }
          end

        elsif temporal_extent['PeriodicDateTimes']

          temporal_extent['PeriodicDateTimes'].each do |periodic_date_time|
            periodic_date_times << { 'start_date' => periodic_date_time['StartDate'], 'end_date' => periodic_date_time['EndDate'] }
          end

        end
      end
    end

    preview_temporal_hash = {}
    preview_temporal_hash['single_date_times'] = single_date_times unless single_date_times.blank?
    preview_temporal_hash['range_date_times'] = range_date_times unless range_date_times.blank?
    preview_temporal_hash['periodic_date_times'] = periodic_date_times unless periodic_date_times.blank?

    preview_temporal_hash
  end

  # Do a simple transformation to map lat/lon onto y/x of map image
  def convert_lat_lon_to_image_x_y(lat, lon, map_width, map_height)
    begin
      y = ((-1 * lat) + 90) * (map_height / 180.0)
      x = (180 + lon) * (map_width / 360.0)
      [x, y]
    rescue
      nil
    end
  end
end
