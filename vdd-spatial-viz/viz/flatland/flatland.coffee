# Global vars
window.map = new Map()


# Handles receiving visualization data through WAMP.
onVizData = (topic, command)->
  console.log("visualization data received", command)

  map = window.map

  switch command.cmd
    when "reload"
      location.reload()
    when "remove-geometries"
      map.removeGeometries(command.ids)
    when "set-geometries"
      map.setGeometries(command.geometries)
    when "add-geometries"
      map.addGeometries(command.geometries)
    when "clear-geometries"
      map.clearGeometries()
    else throw "Unknown command: #{command.cmd}"

# Connect using the WAMP protocol and register callback for visualization data
window.vddSession = vdd_core.connection.connect(onVizData)

# # Resizing code
# $(window).resize(() -> window.autosizeCallback())

# window.autosizeCallback = ->
#   header_height = $("body header").outerHeight(true)
#   footer_height = $("body footer").outerHeight(true)
#   total_height = $(this).height()
#   total_width = $(this).width()
#   map = $("#map")
#   padding = 80;
#   new_height = total_height - header_height - footer_height - padding
#   map.height(new_height)
#   window.map.resize(total_width - padding, new_height)

# $(window).load(() ->
#   console.log "Window loaded"
#   autosizeCallback()
# )


