$(document).ready(function() {
    // display-item-select
    $('.display-item-select').select2({width: '100%', placeholder: "Search available..."});
    $('.display-item-select').on("select2:opening", function(e){
        $this = $(this);
        $this.empty();
        $this.append("<option></option>");
        $this.parent().next().find('.draggable .collapsible-header label').each(function(index, elem) {
            $this.append('<option value="'+index.toString()+'">'+$(elem).text()+"</option>");
        });
        $this.trigger('change');
    });
    $('.display-item-select').on("select2:select", function(e){
        e.preventDefault();
        $this = $(this);
        var value = $(e.currentTarget).find("option:selected").val();
        var toDisplay = $this.parent().next().find('.draggable').get(parseInt(value,10));
        showDraggable(toDisplay);
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

    // hack to dropdown select2 when clicking on a placeholder
    $('span.select2-selection__placeholder').click(function(e){
        $(this).parent().click();
        e.stopPropagation();
    });

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
        $(this).parent().removeClass("show");
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
        $('#main-content-id').addClass("show"):
    }

    if(special.length==0) return null;

    //

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
            if(special.includes(key)) {
                $checkbox.parent().next().addClass("show");
                // highlight and keep open
                if(!$input.hasClass("highlighted-special")) {
                    $input.addClass('highlighted-special');
                    if($input.hasClass("multiselect")) {
                        $input.next().find('.selection .select2-selection').addClass('highlighted-special');
                    }
                }
            } else {
                // hide dropdown if not special
                $checkbox.parent().next().removeClass("show");
            }
        }
    });
    // open search forms
    $('.collapse').filter(":hidden").find('.collapsible-form').each(function() {
        $(this).parent().addClass("show");
    });
};

var findByValue= function(inputs, value) {
    for(var i = 0; i < inputs.length; i++) {
        if(inputs[i].value == value) return inputs[i];
    }
    return null;
}


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
    });
}

var resetCheckbox = function(elem,target,shouldShow) {
    var $draggable = $(elem);
    $draggable.detach().css({top: 0,left: 0}).appendTo(target);
    $handle = $draggable.find(".collapsible-header");
    $draggable.find(".collapse").css('display','');

    var $checkbox = $draggable.find(".mycheckbox")
    $checkbox.prop("checked", shouldShow);
    //$checkbox.prop("disabled", !shouldShow);
    // security concern ?
    $draggable.find('input').prop("disabled",!shouldShow);
    $draggable.find('textarea').prop("disabled",!shouldShow);
    $draggable.find('select').prop("disabled",!shouldShow);
}

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