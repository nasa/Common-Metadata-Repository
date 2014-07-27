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