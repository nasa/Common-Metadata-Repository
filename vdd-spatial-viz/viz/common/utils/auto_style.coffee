$(window).resize(() -> window.autosizeCallback())

window.autosizeCallback = ->
  header_height = $("body header").outerHeight(true)
  footer_height = $("body footer").outerHeight(true)
  total_height = $(this).height()
  map = $("#map")
  padding = 40;
  new_height = total_height - header_height - footer_height - padding
  map.height(new_height)

$(window).load(() ->
  console.log "Window loaded"
  autosizeCallback()
  )


