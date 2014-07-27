
window.Map =
  # Creates the initial map state
  createMap: ()->
    board: JXG.JSXGraph.initBoard('map', {boundingbox: [-180, 90, 180, -90], axis:true})
    geometries: []



