# encoding: utf-8
#From https://gist.github.com/ato/5935594

require 'erb'
require 'ostruct'
require 'java'
require 'set'
require 'active_support/all'
require 'action_view'
require 'action_dispatch'
require 'collection_preview/drafts_helper'
require 'collection_preview/collections_helper'
require 'collection_preview/pages_helper'

include ActionView::Helpers
include ActionDispatch::Routing

include DraftsHelper
include CollectionsHelper
include PagesHelper


## TODO implementthis
def url_for(options)
  puts "url_for #{options.inspect}"
  "http://good_url.com"
end

def edit_collection_path(*args)
  "http://good_url.com/edit_collection_path"
end

####################################################################################################
## Rendering

# Renders an ERB template against a hashmap of variables.
# template should be a Java InputStream
def java_render(template, variables)
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
  ERB.new(template.to_io.read).result(context);
end

def partial_path_to_resource_path(partial_path)
  parts = partial_path.split("/")
  file_name = "_#{parts.last}.html.erb"
  parts = parts[0..-2] + [file_name]
  parts.join("/")
end

# Renders an ERB template against a hashmap of variables.
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

# Renders an ERB template wrapped in an ERB layout.
# template and layout should both be Java InputStreams
def render_with_layout(template, layout, variables)
  render(layout, variables) do
    render(template, variables)
  end
end
