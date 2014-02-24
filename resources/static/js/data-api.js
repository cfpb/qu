(function ($) {
  var $form = $("#query-form");

  var buildQueryUrl = function () {
    var formVals = _($form.serializeArray())
      .chain()
      .reject(function (field) {
        return $.trim(field.value) === "";
      })
      .reduce(function (memo, field) {
        memo[field.name] = field.value;
        return memo;
      }, {})
      .value();

    var href = $form.data('href');

    var format = formVals["$format"] || "html";
    delete formVals["$format"];

    var action = href + "." + format;

    var formString = _(formVals)
      .chain()
      .pairs()
      .map(function (pair) {
        return pair[0] + "=" +
          encodeURIComponent(pair[1]).replace(/%20/g,'+');
      })
      .value()
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

    $form.find('#field-select, #field-group, #field-where, #field-orderBy').typeahead({
        source: jQuery('#typeahead-candidates').val().split(','),
        matcher: function (item) {
            var term;
            term = this.query.split(',').pop().trim().toLowerCase();
             if(!term) return false;
            return ~item.toLowerCase().indexOf(term)
        },
        updater: function(item) {
            item = this.$element.val().replace(/[^,]*$/,'') + ' ' + item;
            return item;
        },
        minLength: 1,
        items: 5
    });
  });
})(jQuery);
