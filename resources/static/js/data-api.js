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
      .join("<br />&");

    $form.attr("action", action);
    $("#query-url").html((formString === "") ? action : action + "<br />?" + formString)

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


    // show saved queries as a list of links
    var storage_key_base = window.location.pathname.replace(/\.htm.*$/, ''); // discard file extension if present

    var showSavedQueries = function () {
        var query_list = [];
        localforage.getItem(storage_key_base).then(function (queries) {
            queries = queries || {};
            $.each(queries, function (url, name) {
                query_list.push('<a href="' + url + '">' + name + '</a>');
            });

            if (query_list.length > 0) {
                $('#saved-queries P').html(query_list.join(', '));
            } else {
                $('#saved-queries P').html('None so far.');
            }
        });
    };

    if ($('#saved-queries').length > 0) {
        showSavedQueries();
    }

    var toggleQueryActions = function () {
        var current_query = $('#query-url').text();
        localforage.getItem(storage_key_base).then(function (queries) {
            queries = queries || {};

            if (queries.hasOwnProperty(current_query)) {
                $('#forget-query').removeClass('hide');
                $('#save-query').addClass('hide');
            } else {
                $('#forget-query').addClass('hide');
                $('#save-query').removeClass('hide');
            }
        });
    };

    if ($('#query-url').length > 0) {
      toggleQueryActions();
    }

    // Save queries via localforage

    $('#save-query').on('click', function (e) {
        e.preventDefault();
        var current_query, name;
        current_query = $('#query-url').text();
        name = prompt('What should this query be named?');

        // scope the storage path by the url
        localforage.getItem(storage_key_base).then(function (queries) {
            queries = queries || {};
            queries[current_query] = name;
            localforage.setItem(storage_key_base, queries).then(function () {
                showSavedQueries();
                toggleQueryActions();
            });
        });
    });

      $('#forget-query').on('click', function (e) {
          e.preventDefault();
          var current_query = $('#query-url').text();
          localforage.getItem(storage_key_base).then(function (queries) {
              delete queries[current_query];
              localforage.setItem(storage_key_base, queries, function () {
                showSavedQueries();
                toggleQueryActions();
            });
        });
      });
  });

})(jQuery);
