(function ($) {
  var $form = $("#query-form");

  var buildQueryUrl = function () {
    var formVals = _($form.serializeArray())
      .reject(function (field) {
        return $.trim(field.value) === "";
      })
      .reduce(function (memo, field) {
        memo[field.name] = field.value;
        return memo;
      }, {});
    
    var href = $form.data('href');
    
    var format = formVals["$format"] || "html";
    delete formVals["$format"];
    
    var action = href + "." + format;
    
    var formString = _(formVals).pairs()
      .map(function (pair) {
        return pair[0] + "=" +
          encodeURIComponent(pair[1]).replace(/%20/g,'+');
      })
      .join("<br />&");

    $form
      .attr("action", action)
      .find("#query-url")
      .html((formString === "") ?
            action :
            action + "<br />?" + formString)
      .end()
      .find("#field-callback")
      .prop({disabled: (format === "jsonp" ? "" : "disabled")});
  };


  $(document).ready(function () {
    buildQueryUrl();
    $form.on("keyup", "input[type=text]", buildQueryUrl);
    $form.on("click", "input[type=radio]", buildQueryUrl);
  });
})(jQuery);
