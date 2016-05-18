module PagesHelper
  def notification_time_span(notification)
    return nil unless notification && notification['start_date']

    start_date = DateTime.parse(notification['start_date']).in_time_zone('Eastern Time (US & Canada)')
    end_date = notification['end_date'].nil? ? 'ongoing' : DateTime.parse(notification['start_date']).in_time_zone('Eastern Time (US & Canada)')

    start_date = start_date.strftime('%Y-%m-%d %H:%M:%S')
    end_date = end_date.strftime('%Y-%m-%d %H:%M:%S') unless end_date.is_a? String

    "(#{start_date} - #{end_date})"
  end

  def display_entry_id(metadata, type)
    blank_short_name = type == 'draft' ? '<Blank Short Name>' : 'New Collection'
    short_name = metadata['ShortName'] || blank_short_name

    version = metadata['Version'].nil? ? '' : "_#{metadata['Version']}"

    entry_id = short_name + version
    entry_id
  end
end
