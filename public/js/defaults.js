$(document).ready(function() {
    $('.sidebar .nav-item .btn').click(function(e){
        $('.sidebar .nav-item .btn').removeClass('active');
        $(this).addClass('active');
    });

    var resetCheckbox = function(elem,target) {
        var $draggable = $(elem);
        $draggable.detach().css({top: 0,left: 0}).appendTo(target);
        var shouldShow = $(target).hasClass('target');
        $toggle = $draggable.find(".collapse");
        $handle = $draggable.find(".collapsible-header");
        if(shouldShow) {
            if($handle.attr('data-hidden-target')) {
                $handle.attr('data-target',$handle.attr('data-hidden-target'));
                $handle.removeAttr('data-hidden-target');
            }
            if(! $toggle.is(':visible')) {
                $toggle.show();
            }
        } else {
            if($handle.attr('data-target')) {
                $handle.attr('data-hidden-target',$handle.attr('data-target'));
                $handle.removeAttr('data-target');
            }
            if($toggle.is(':visible')) {
                $toggle.hide();
            }
        }
        var $checkbox = $draggable.find(".mycheckbox")
        $checkbox.prop("checked", shouldShow);
        //$checkbox.prop("disabled", !shouldShow);
        // security concern ?
        $draggable.find('input').prop("disabled",!shouldShow);
        $draggable.find('textarea').prop("disabled",!shouldShow);
        $draggable.find('select').prop("disabled",!shouldShow);
    }

    var dropFunc = function(event, ui) {
        resetCheckbox(ui.draggable,this);
    };

    $('.draggable').draggable({
        revert: true
    });

    $('.droppable.filters').droppable({
        accept: '.draggable.filters',
        drop: dropFunc
    });

    $(".mycheckbox").on("click", function (e) {
        var checkbox = $(this);
        // do the confirmation thing here
        e.preventDefault();
        return false;
    });

    $('.droppable.values').droppable({
        accept: '.draggable.values',
        drop: dropFunc
    });

    $('.droppable.attributes').droppable({
        accept: '.draggable.attributes',
        drop: dropFunc
    });

    $('.droppable.charts').droppable({
        accept: '.draggable.charts',
        drop: dropFunc
    });

    var doubleClickWhileCollapsingHelper = function(elem) {
        var $draggable = $(elem).parent();
        var id = $draggable.data('target');
        if(id) {
            var target;
            $parent = $draggable.parent();
            if($parent.hasClass('target') || $parent.parent().hasClass('target')) {
                target = "start";
            } else {
                target = "target";
            }
            $target = $('#'+id+'-'+target);
            if($target) {
                  resetCheckbox($draggable.get(0),$target.get(0));
            }
        }
    }

    $('.draggable .double-click').dblclick(function() {
        doubleClickWhileCollapsingHelper(this);
    });

    $('.multiselect').select2({
        minimumResultsForSearch: 20,
        closeOnSelect: false
    });

    setCollapsibleHeaders();

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
    $('.target .double-click').dblclick();
    $('.highlighted').removeClass('highlighted');
    $('.highlighted-special').removeClass('highlighted-special');
    $('.collapse').filter(":visible").find('.collapsible-form').each(function() {
        $(this).parent().hide();
    });
    $('.draggable .multiselect').val(null).trigger("change");
    $('.start .double-click .collapse').hide();
};

var applyParams = function(params,searchOptions,special=[]) {
    $.each(searchOptions, function(key,value){
        var $input = $('[name="'+key+'"]');
        $input.val(value);
    });
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
                if(!$input.hasClass("highlighted-special")) {
                    $input.addClass('highlighted-special');
                    if($input.hasClass("multiselect")) {
                        $input.next().find('.selection .select2-selection').addClass('highlighted-special');
                    }
                    // only open if special
                    waitForDoneCollapsing("#"+$checkbox.attr("group-id"),$checkbox);
                }
            }
        }
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
            $checkbox.parent().dblclick();
        }
    }

    if(!$checkbox.parent().hasClass("highlighted")) {
        $checkbox.parent().addClass('highlighted');
    }

    return $checkbox;
};


var waitForDoneCollapsing = function(groupId,checkbox) {
    var group = $(groupId);
    if(! group.is(":visible")) {
        group.show();
    }
}


var setCollapsibleHeaders = function() {
    $(".collapsible-header").click(function () {
        $header = $(this);
        //getting the next element
        $content = $($header.attr("data-target"));
        $content.slideToggle({
            duration: 300
        })
    });
}
