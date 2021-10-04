# frozen_string_literal: true

require 'json'
require 'jsonpath'
require 'nokogiri'

# Parse CMR response bodies to extract meaningful data
module CmrResultHandling
  # Pull results from response bodies by result mime-type
  def extract_results(mime_type, data)
    mime_type_handlers = { 'application/json' => ->(d) { JsonPath.new('$..entry[*]').on(d) },
                           'application/vnd.nasa.cmr.umm_results+json' => ->(d) { JsonPath.new('$..items[*]').on(d) },
                           'application/vnd.nasa.cmr.legacy_umm_results+json' => ->(d) { JsonPath.new('$..items[*]').on(d) },
                           'application/xml' => ->(d) { Nokogiri::XML(d).xpath('//results/references/reference') },
                           'application/iso19115+xml' => ->(d) { Nokogiri::XML(d).xpath('//results/result') },
                           'application/echo10+xml' => ->(d) { Nokogiri::XML(d).xpath('//results/result') },
                           'application/dif+xml' => ->(d) { Nokogiri::XML(d).xpath('//results/result') },
                           'application/dif10+xml' => ->(d) { Nokogiri::XML(d).xpath('//results/result') },
                           'application/metadata+xml' => ->(d) { Nokogiri::XML(d).xpath('//results/result') },
                           'application/atom+xml' => ->(d) { Nokogiri::XML(d).xpath('//feed/entry') } }

    raise "No result extractor found for #{mime_type} found" unless mime_type_handlers.key?(mime_type)

    mime_type_handlers[mime_type].call(data)
  end
end
World CmrResultHandling

When(/^(I )?save the response body as "(.*)"/) do |_, save_as|
  @stashes ||= {}

  mime_type = @response.headers['Content-Type'].split(';')[0]
  mime_type.strip!

  data = if mime_type.match?(%r{^application/(.*\+)?json$})
           JSON.parse(@response.body)
         else
           @response.body
         end

  @stashes = @stashes.merge({ save_as => data })
end

When(/I save the results as "(.*)"/) do |save_as|
  @stashes ||= {}

  mime_type = @response.headers['Content-Type'].split(';')[0]
  mime_type.strip!

  data = extract_results(mime_type, @response.body)
  @stashes = @stashes.merge({ save_as => data })
end

When('I save/saved the {string} result {string} as {string}') do |nth, key, save_as|
  mime_type = @response.headers['Content-Type'].split(';')[0]
  mime_type.strip!

  index = case nth.downcase
          when 'first'
            0
          when 'second'
            1
          when 'last'
            -1
          when /\d+/
            Integer(nth)
          else
            raise "Cardinality index #{nth} not supported, first, second, and last supported or specify number"
          end

  concept = extract_results(mime_type, @response.body)[index]

  raise "No entry found for #{key} on concept #{@response.body}" unless concept.key?(key)

  @stashes ||= {}
  @stashes = @stashes.merge({ save_as => concept[key] })
end
