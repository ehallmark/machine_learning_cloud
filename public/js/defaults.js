$(document).ready(function() {

    $('#save-template-form-id-button').click(function(e){
        e.preventDefault();

        $.ajax({
          type: "POST",
          url: $(this).closest('form').attr('action'),
          data: {
            template_name: $('#template-name').val(),
            template_html: $('#generate-reports-form').html()
          },
          success: function(data) {
              $('#results').html(data.message);
          }
          dataType: "json"
        });

        return false;
    });

    $('.template-remove-button').click(function(e){
        e.preventDefault();

        $.ajax({
          type: "POST",
          url: $(this).attr('data-action'),
          data: {
            path_to_remove: $(this).attr("data-file")
          },
          success: function(data) {
            $(this).closest('li').remove();
          }
          dataType: "json"
        });

        return false;
    });
    // display-item-select
    $('.display-item-select').each(function(){
        $this = $(this);
        var displayItemSelectOptions = {width: '100%', placeholder: $this.find('option.placeholder').text()};
        $this.select2(displayItemSelectOptions);
    });

    // On opening
    $('.display-item-select').on("select2:opening", function(e){
        $this = $(this);

        // placeholder data
        var $placeholder = $this.find('option.placeholder').detach();

        // delete all items of the native select element
        $this.parent().find(".hidden-placeholder").html($placeholder);
        $this.empty();

        // add hidden elements
        $this.append('<option></option>');
        var $items = $this.parent().next().find('.draggable .collapsible-header:not(.nested) label');

        // add new items
        $items.each(function(index, elem) {
            $this.append('<option value="'+index.toString()+'">'+$(elem).text()+"</option>");
        });

        $this.trigger('change');
    });

    // on select
    $('.display-item-select').on("select2:select", function(e){
        $this = $(this);
        var value = $this.parent().find(".value").val();
        var toDisplay = $this.parent().next().find('.draggable .collapsible-header:not(.nested)').get(parseInt(value,10));
        showDraggable(toDisplay.parentElement);
    });

    // on close
    $('.display-item-select').on("select2:close", function(e){
        $this = $(this);
        // place holder stuff
        var value = $(e.currentTarget).find("option:selected").val();
        if($.isNumeric(value)) {
            $this.parent().find(".value").val(value);
        }
        var $placeholder = $this.parent().find(".hidden-placeholder").find('option.placeholder').clone();
        $this.html($placeholder);
        $this.trigger('change');
    });

    // nested forms
    $('.nested-form-select select').each(function() {
        $this = $(this);
        var displayItemSelectOptions = {width: '100%', placeholder: 'Search', closeOnSelect: true};
        $this.select2(displayItemSelectOptions);
    });

    $('.nested-form-select select').on("select2:select", function(e) {
        var id = e.params.data.id;
        $('.draggable[data-model="'+id+'"]').parent().show().find('input, select, textarea').prop('disabled', false);
        return true;
    });

    $('.nested-form-select select').on("select2:unselect", function(e) {
        var id = e.params.data.id;
        $('.draggable[data-model="'+id+'"]').parent().hide().find('input, select, textarea').prop('disabled', true);
        return true;
    });

    $('.sidebar .nav-item .btn').click(function(e){
        $('.sidebar .nav-item .btn').removeClass('active');
        $(this).addClass('active');
    });

    $(".mycheckbox").on("click", function (e) {
        var checkbox = $(this);
        // do the confirmation thing here
        e.preventDefault();
        return false;
    });

    $('.draggable .collapsible-header .remove-button').click(function(e) {
        e.stopPropagation();
        hideDraggable($(this).parent().get(0));
    });

    $('.multiselect').select2({
        minimumResultsForSearch: 5,
        closeOnSelect: false
    });


    $('.single-select2').select2({
        minimumResultsForSearch: 5,
        width: "100%"
    });

    setCollapsibleHeaders(".collapsible-header");
});

var setupDataTable = function (dataTable) {
    $(dataTable).find('th.sortable').on("click",function() {
        var f = -1;
        if($(this).attr("data-sorted")) {
            f=1;
            $(this).removeAttr("data-sorted");
        } else {
            $(this).attr("data-sorted","true");
        }

        var sortOnField = $(this).data('field');
        var $wrapper = $(dataTable).find('tbody');
        var rows = $wrapper.find("tr").get();

        rows.sort(function(a, b) {

            var A = getVal(a,sortOnField);
            var B = getVal(b,sortOnField);

            if(A > B) {
                return 1*f;
            } else if(A < B) {
                return -1*f;
            }
            return 0;
        });


        $.each(rows, function(){
            $wrapper.append(this);
        });


    });

    var getVal = function (elm,sortOnField) {
        var v = $(elm).data(sortOnField);
        if($.isNumeric(v)){
            v = parseFloat(v);
        }
        return v;
    };
};

var resetSearchForm = function() {
    $('.target .collapsible-header .remove-button').click();
    $('.highlighted').removeClass('highlighted');
    $('.highlighted-special').removeClass('highlighted-special');
    $('.collapsible-form').filter(':visible').each(function() {
        $(this).closest('.collapse').removeClass("show");
    });
    $('.draggable .multiselect').val(null).trigger("change");
    $('#results').html('');
};

var applyParams = function(params,searchOptions,special=[]) {
    // add search options
    $.each(searchOptions, function(key,value){
        var $input = $('[name="'+key+'"]');
        $input.val(value);
        if($input.hasClass("single-select2")) {
            $input.trigger('change');
        }
    });

    if(!$('#main-content-id').hasClass('show')) {
        $('#main-content-id').addClass("show");
    }

    if(special.length==0) return null;

    // add other params
    $.each(params, function(key,value){
        var $input = $('[name="'+key+'"]');
        if(Array.isArray(value) && $input.hasClass("mycheckbox")) {
            $.each(value, function(i,val) {
                var input = findByValue($input.get(),val);
                paramsHelper($(input),null);
            });
        } else {
            var $checkbox = paramsHelper($input,value);
            $checkbox.parent().next().addClass("show");
            if(special.includes(key)) {
                // highlight and keep open
                if(!$input.hasClass("highlighted-special")) {
                    $input.addClass('highlighted-special');
                    if($input.hasClass("multiselect")) {
                        $input.next().find('.selection .select2-selection').addClass('highlighted-special');
                    }
                }
            }
            // trigger change
            if($input.hasClass("single-select2")) {
                $input.trigger('change');
            }
        }
    });
    // open search forms
    $('.collapse').filter(":hidden").find('.collapsible-form').each(function() {
        $(this).closest('.collapse').addClass("show");
    });

    $('.miniTip').miniTip({
        title: 'Advanced Keyword Syntax',
        event: 'click',
        content: "<p>+ signifies AND operation</p>"+
                 "<p>| signifies OR operation</p>"+
                 "<p>- negates a single token</p>"+
                 "\"\" wraps a number of tokens to signify a phrase for searching</p>"+
                 "<p>* at the end of a term signifies a prefix query</p>"+
                 "<p>( and ) signify precedence</p>"+
                 "<p>~N after a word signifies edit distance (fuzziness)</p>"+
                 "<p>~N after a phrase signifies slop amount</p>"
    });

};

var findByValue = function(inputs, value) {
    for(var i = 0; i < inputs.length; i++) {
        if(inputs[i].value == value) return inputs[i];
    }
    return null;
};


var paramsHelper = function(input,value) {
    var $checkbox = input;
    if(! input.hasClass("mycheckbox")) {
        $checkbox = input.closest('.draggable').find('.mycheckbox');
        input.val(value);
        if(input.hasClass("multiselect")) {
            input.trigger('change');
        }
    }
    var $dropZone = $checkbox.closest('.droppable');
    if(! $dropZone.hasClass('target')) {
        if($checkbox.hasClass('mycheckbox')) {
            showDraggable($checkbox.parent().get(0));
        }
    }
    if(!$checkbox.parent().hasClass("highlighted")) {
        $checkbox.parent().addClass('highlighted');
    }
    return $checkbox;
};

var setCollapsibleHeaders = function(selector) {
    $(selector).click(function () {
        $header = $(this);
        //getting the next element
        $content = $($header.attr("data-target"));
        if((! $content.hasClass("show")) && $content.attr("id")==="main-content-id") {
            $("html, body").animate({
                scrollTop: 0
            }, 500);
        }
        if($content.hasClass("show")) {
            $content.css("max-height", "none");
            var height = $content.outerHeight();
            $content.css("max-height",height.toString());
            $content.animate({
                maxHeight: 0
            }, {
                duration: 400,
                always: function() {
                    $content.removeClass("show");
                    $content.css("max-height","");
                }
            });

        } else {
            // get height
            $content.css("max-height", "none");
            var height = $content.outerHeight();

            // reset to 0 then animate with small delay
            $content.addClass("show");
            $content.css("max-height", "0");
            $content.animate({
                maxHeight: height
            }, {
                duration: 400,
                always: function() {
                    $content.css("max-height", "");
                }
            });
        }
    });
};

var resetCheckbox = function(elem,target,shouldShow) {
    var $draggable = $(elem);
    $draggable.detach().css({top: 0,left: 0}).appendTo(target);
    var $handle = $draggable.find(".collapsible-header");
    var $collapse = $draggable.find(".collapse");
    $collapse.css('display','');
    if(shouldShow && !$collapse.hasClass('show')) {
        $collapse.addClass('show');
    } else if ($collapse.hasClass('show') && !shouldShow) {
        $collapse.removeClass('show');
    }

    var $checkbox = $draggable.find(".mycheckbox")
    $checkbox.prop("checked", shouldShow);
    //$checkbox.prop("disabled", !shouldShow);
    // security concern ?
    $draggable.find('input').prop("disabled",!shouldShow);
    $draggable.find('textarea').prop("disabled",!shouldShow);
    $draggable.find('select').prop("disabled",!shouldShow);
};

var showDraggable = function(elem) {
    var $draggable = $(elem);
    if(!$draggable.hasClass("draggable")) $draggable = $draggable.parent();
    var id = $draggable.attr('data-target');
    if(id) {
        var target = "target";
        $target = $('#'+id+'-'+target);
        if($target) {
              resetCheckbox($draggable.get(0),$target.get(0),true);
        }
    }
};

var hideDraggable = function(elem) {
    var $draggable = $(elem);
    if(!$draggable.hasClass("draggable")) $draggable = $draggable.parent();
    var id = $draggable.attr('data-target');
    if(id) {
        var target = "start";
        $target = $('#'+id+'-'+target);
        if($target) {
              resetCheckbox($draggable.get(0),$target.get(0),false);
        }
    }
};

var loadEvent = function(){
 // do nothing :(
};


