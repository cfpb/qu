(function ($) {
  var $form = $("#query-form");

  var buildQueryUrl = function () {

    if ($form.length == 0) return;

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
      .join("&");

    $form.attr("action", action);
    $("#query-url").html((formString === "") ? action : action + "?" + formString)

    if ($('#field-callback').length > 0) {
      var callback_container = $form.find("#field-callback").closest('.control-group');
      if (format === 'jsonp') {
          callback_container.removeClass('hide');
          $("#field-callback").prop('disabled', '');
      } else {
          callback_container.addClass('hide');
          $("#field-callback").val('').prop('disabled', 'disabled');
      }
    }
  };


  $(document).ready(function () {
    buildQueryUrl();
    $form.on("keyup", "input[type=text]", buildQueryUrl);
    $form.on("click", "input[type=radio]", buildQueryUrl);

    $form.find('#field-select, #field-group, #field-where, #field-orderBy').typeahead({
        source: (jQuery('#typeahead-candidates').val() || '').split(','),
        matcher: function (item) {
            var term;

            // To allow typeahead within an aggregation function, strip away the function and its parens
            term = this.query.split(',').pop().toLowerCase();
            term = term.replace(/[a-zA-Z]+\(/, '');
            term = term.replace(')', '');
            term = term.trim();

            if (!term) {
                return false;
            }

            return ~item.toLowerCase().indexOf(term)
        },
        updater: function(item) {
            var field, parens_index, query_tail;
            field = this.$element;
            item = item.trim();
            query_tail = this.query.split(',').pop();


            // If the field value ends with an empty aggregation
            // function, place the item inside it.  Otherwise, just
            // append.
            if (query_tail.match(/\([a-z]+\)$/)) {
                item = field.val().replace(query_tail, query_tail.replace(/\(.*/, '(') + item + ')');
            } else {
                item = field.val().replace(/[^,]*$/,'') + ' ' + item;
                item = item.trim();
            }

            // If there is an empty aggregation function, place the cursor inside it.
            parens_index = item.lastIndexOf('()');

            if (parens_index > 0) {
                setTimeout(function () {
                    field.textrange('setcursor', parens_index + 1);
                });
            }

            return item;
        },
        minLength: 1,
        items: 5
    });

    // tooltips via boostrap-popover.js
    $('.icon-help-alt').popover({
        trigger: 'click',
        placement: 'bottom'
    }).on('click', function (e) { e.preventDefault(); e.stopPropagation() });    
  });

})(jQuery);
