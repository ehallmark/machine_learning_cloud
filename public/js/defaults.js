$(document).ready(function() {
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
        var $items = $this.parent().next().find('.draggable .collapsible-header label');

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
        var toDisplay = $this.parent().next().find('.draggable').get(parseInt(value,10));
        showDraggable(toDisplay);
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
        }
    });
    // open search forms
    $('.collapse').filter(":hidden").find('.collapsible-form').each(function() {
        $(this).closest('.collapse').addClass("show");
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
        $content.toggleClass("show");
        if($content.hasClass("show") && $content.attr("id")==="main-content-id") {
            $("html, body").animate({
                scrollTop: 0
            }, 500);
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

var downloadTable = function(selector) {
    var $table = $(selector);
    var $headers = $table.find('thead th').map(function() { return $(this).text(); }).get();
    var $dataRows = $table.find('tbody tr').map(function() {
        var $row = $(this);
        var $data = $row.find('td').map(function() { return $(this).text(); }).get();
        return [$data];
    }).get();
    var $results = {
        headers: $headers,
        rows: $dataRows
    };

    var action = $table.attr("data-url");

    var form = document.createElement("form");
    form.setAttribute("method","post");
    form.setAttribute("action",action);
    form.setAttribute("target","_blank");
    form.style.display="none";
    var input = document.createElement('input');
    input.setAttribute("type","hidden");
    input.setAttribute("name","data");
    input.setAttribute("value", JSON.stringify($results));
    form.appendChild(input);
    document.body.appendChild(form);
    $(form).submit();
    $(form).remove();
};


var loadEvent = function(){
    alert("LOAD EVENT FUNCTION TRIGGERED!!!!!!");
    var ticks = $.map(this.axes[0].ticks, function(t){return t;});
    ticks[ticks.length-2].render(0);
};