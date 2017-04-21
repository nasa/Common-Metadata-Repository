# encoding: utf-8
# Bootstraps an empty JRuby environment in the CMR so that it will have the appropriate functions
# available for rendering collections. Sets up enough functions so that the ERB partials from MMT
# will be renderable.

# Unset gem related environment variables to prevent jruby from loading gems from client's GEM_PATH
ENV.delete('GEM_HOME')
ENV.delete('GEM_PATH')

require 'erb'
require 'ostruct'
require 'java'
require 'set'
require 'active_support/all'
require 'action_view'
require 'action_dispatch'
require 'gems/cmr_metadata_preview-0.0.1/app/helpers/cmr_metadata_preview/cmr_metadata_preview_helper'
require 'gems/cmr_metadata_preview-0.0.1/app/helpers/cmr_metadata_preview/options_helper'
require 'gems/cmr_metadata_preview-0.0.1/app/helpers/cmr_metadata_preview/data_contacts_helper'

include ActionView::Helpers
include ActionDispatch::Routing

include CmrMetadataPreview::DataContactsHelper
include CmrMetadataPreview::OptionsHelper
include CmrMetadataPreview::CmrMetadataPreviewHelper


## Thesee need to work but they don't need to return real URLs.
def url_for(options)
  "http://example.com"
end

def edit_collection_path(*args)
  "http://example.com/edit_collection_path"
end

def resource_prefix
  "gems/cmr_metadata_preview-0.0.1/app/views/"
end

####################################################################################################
## Rendering
## Based on https://gist.github.com/ato/5935594

# Takes a Java InputStream to an ERB template and a map of variables to pass to the template.
# Renders the ERB template returning the generating content string.
def java_render(template_input_stream, variables)
  context = OpenStruct.new(variables).instance_eval do
    variables.each do |k, v|
      instance_variable_set(k, v) if k[0] == '@'
    end
    # MMT ERBs expect a current user
    @current_user = OpenStruct.new()

    def partial(partial_name, options={})
      new_variables = marshal_dump.merge(options[:locals] || {})
      Java::Pavo::ERB.render(partial_name, new_variables)
    end

    binding
  end
  ERB.new(template_input_stream.to_io.read).result(context);
end

# Takes a path like foo/bar and returns the path to the ERB partial file name: foo/_bar.html.erb
def partial_path_to_resource_path(partial_path)
  parts = partial_path.split("/")
  file_name = "_#{parts.last}.html.erb"
  parts = parts[0..-2] + [file_name]
  relative_path = parts.join("/")
  "#{resource_prefix}#{relative_path}"
end

# Renders an ERB template against a hashmap of variables.
# Implements the normal Rails render method but makes it find resources on the classpath and delegates
# to the java_render function.
def render(args)
  begin
    if args.is_a? Hash
      partial_path = args[:partial]
      locals = args[:locals]

      if partial_path.respond_to? :call
        partial_path = partial_path()
      end
    else
      partial_path = args
      locals = {}
    end

    resource_path = partial_path_to_resource_path(partial_path)
    class_loader = Java::Java::lang::Thread.currentThread.getContextClassLoader
    input_stream = class_loader.getResourceAsStream(resource_path)

    # Uncomment this for debugging.
    # puts "Rendering #{resource_path} with #{locals.inspect}"
    java_render(input_stream, locals)
  rescue Exception => e
    puts e.message
    puts e.backtrace
    raise e
   end
end
