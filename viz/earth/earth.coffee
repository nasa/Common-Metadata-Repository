# Handles receiving visualization data through WAMP.
onVizData = (topic, command)->
  console.log("visualization data received", command)

  switch command.cmd
    when "reload"
      location.reload()
    when "remove-geometries"
      Map.map.removeGeometries(command.ids)
    when "set-geometries"
      Map.map.setGeometries(command.geometries)
    when "add-geometries"
      Map.map.addGeometries(command.geometries)
    when "clear-geometries"
      Map.map.clearGeometries()
    else throw "Unknown command: #{command.cmd}"

# Connect using the WAMP protocol and register callback for visualization data
window.vddSession = vdd_core.connection.connect(onVizData)

